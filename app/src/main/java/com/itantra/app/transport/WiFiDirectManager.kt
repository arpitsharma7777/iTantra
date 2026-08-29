package com.itantra.app.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.itantra.app.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Clean wrapper for UI to avoid exposing framework classes.
 */
data class WifiDirectDevice(
    val name: String,
    val address: String
)

/**
 * Connection details required by SocketManager.
 */
data class WifiDirectConnectionInfo(
    val isGroupOwner: Boolean,
    val groupOwnerAddress: String?
)

@SuppressLint("MissingPermission")
class WiFiDirectManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(appContext, Looper.getMainLooper(), null)
    
    // Case 1: Check if Wi-Fi Direct is supported on this device at init time
    val isSupported: Boolean = manager != null && channel != null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateMutex = Mutex()
    
    // Independent counters to prevent internal bookkeeping from invalidating high-level operations
    private var activeConnectOpId = 0L
    private var activeInfoRequestId = 0L

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<WifiDirectDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<WifiDirectDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiDirectConnectionInfo?>(null)
    val connectionInfo: StateFlow<WifiDirectConnectionInfo?> = _connectionInfo.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        updateState(ConnectionState.ERROR, "Wi-Fi P2P is disabled")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    @Suppress("DEPRECATION")
                    if (networkInfo?.isConnected == true) {
                        handleConnected()
                    } else if (_connectionState.value == ConnectionState.CONNECTED) {
                        updateState(ConnectionState.DISCONNECTED, "Connection lost")
                    }
                }
            }
        }
    }

    init {
        if (!isSupported) {
            updateState(ConnectionState.ERROR, "Wi-Fi Direct not supported on this device")
        } else {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            appContext.registerReceiver(receiver, filter)
            cleanupStaleGroups()
        }
    }

    private fun updateState(newState: ConnectionState, reason: String) {
        scope.launch {
            stateMutex.withLock {
                if (_connectionState.value == newState && newState != ConnectionState.ERROR) return@withLock
                
                Log.d(TAG, "State transition: ${_connectionState.value} -> $newState | Reason: $reason")
                _connectionState.value = newState
                
                if (newState == ConnectionState.ERROR) {
                    delay(1000) 
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }

    private fun requestPeers() {
        if (!isSupported) return
        try {
            manager?.requestPeers(channel) { peerList ->
                val devices = peerList.deviceList.map { device ->
                    WifiDirectDevice(device.deviceName, device.deviceAddress)
                }
                _discoveredDevices.value = devices
                Log.d(TAG, "Peers updated: ${devices.size} devices found")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in requestPeers: ${e.message}")
        }
    }

    private fun handleConnected() {
        val reqId = ++activeInfoRequestId
        Log.d(TAG, "P2P Connected, requesting connection info (ReqId: $reqId)")
        
        manager?.requestConnectionInfo(channel) { info ->
            if (reqId != activeInfoRequestId) return@requestConnectionInfo
            
            if (info.groupFormed) {
                // Case 4: Validate groupOwnerAddress is non-null before emitting CONNECTED
                val p2pInfo = WifiDirectConnectionInfo(
                    isGroupOwner = info.isGroupOwner,
                    groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                )
                
                if (p2pInfo.groupOwnerAddress != null || p2pInfo.isGroupOwner) {
                    _connectionInfo.value = p2pInfo
                    updateState(ConnectionState.CONNECTED, "Connection info received and populated")
                } else {
                    // Case 4: Connection info unavailable / null group owner address after connect
                    updateState(ConnectionState.ERROR, "Connection info incomplete (missing IP)")
                }
            } else {
                updateState(ConnectionState.ERROR, "Group not formed after connection")
            }
        }
    }

    fun startDiscovery() {
        if (!isSupported) {
            // Case 1: Guard against unsupported hardware
            updateState(ConnectionState.ERROR, "Cannot start discovery: Wi-Fi Direct unsupported")
            return
        }
        updateState(ConnectionState.DISCOVERING, "Manual discovery start")
        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started successfully")
                }

                override fun onFailure(reason: Int) {
                    // Case 2: Discovery failure (WifiP2pManager reason codes)
                    updateState(ConnectionState.ERROR, "Discovery failed: $reason")
                }
            })
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for discovery: ${e.message}")
        }
    }

    suspend fun connect(device: WifiDirectDevice) {
        if (!isSupported) {
            // Case 1: Guard against unsupported hardware
            updateState(ConnectionState.ERROR, "Cannot connect: Wi-Fi Direct unsupported")
            return
        }
        val opId = ++activeConnectOpId
        updateState(ConnectionState.CONNECTING, "Connecting to ${device.name}")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }

        try {
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connect command accepted for ${device.name}")
                }

                override fun onFailure(reason: Int) {
                    // Case 3: Connection failure (explicit failure callback)
                    if (opId == activeConnectOpId) {
                        updateState(ConnectionState.ERROR, "Connect failed: $reason")
                    }
                }
            })

            // Strict wait for CONNECTED state, failure, or supersession
            val success = withTimeoutOrNull(20000) {
                while (true) {
                    val state = _connectionState.value
                    if (state == ConnectionState.CONNECTED) return@withTimeoutOrNull true
                    if (state == ConnectionState.ERROR) return@withTimeoutOrNull false
                    if (opId != activeConnectOpId) {
                        Log.d(TAG, "Connect operation $opId superseded by $activeConnectOpId")
                        return@withTimeoutOrNull false
                    }
                    delay(500)
                }
            }

            if (success == null && opId == activeConnectOpId) {
                updateState(ConnectionState.ERROR, "Connection timed out after 20s")
                cancelConnect()
            }
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for connect: ${e.message}")
        }
    }

    private fun cancelConnect() {
        manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Connection canceled") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Failed to cancel connection: $reason") }
        })
    }

    private fun cleanupStaleGroups() {
        manager?.requestGroupInfo(channel) { group ->
            if (group != null) {
                Log.d(TAG, "Found stale group, removing: ${group.networkName}")
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Log.d(TAG, "Stale group removed") }
                    override fun onFailure(reason: Int) { Log.e(TAG, "Failed to remove stale group: $reason") }
                })
            }
        }
    }

    /**
     * Partial reset: Stops current operations but keeps the receiver registered.
     * Case 9: Allows connect() to be called again successfully.
     */
    fun disconnect() {
        Log.d(TAG, "WiFiDirectManager: performing partial disconnect")
        manager?.stopPeerDiscovery(channel, null)
        manager?.removeGroup(channel, null)
        
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionInfo.value = null
        _discoveredDevices.value = emptyList()
        activeConnectOpId++
        activeInfoRequestId++
    }

    /**
     * Full reset: Final release of resources, unregisters receiver.
     */
    fun teardown() {
        disconnect()
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
        Log.d(TAG, "WiFiDirectManager: full teardown complete")
    }

    companion object {
        private const val TAG = "WiFiDirectManager"
    }
}
