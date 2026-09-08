package com.itantra.app.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.itantra.app.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class WifiDirectDevice(
    val name: String,
    val address: String
)

data class WifiDirectConnectionInfo(
    val isGroupOwner: Boolean,
    val groupOwnerAddress: String?
)

@SuppressLint("MissingPermission")
class WiFiDirectManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(appContext, Looper.getMainLooper(), null)

    val isSupported: Boolean = manager != null && channel != null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateMutex = Mutex()

    private var activeConnectOpId = 0L
    private var activeInfoRequestId = 0L
    private var connectedDeviceName: String? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
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
                        updateState(ConnectionState.Error("Wi-Fi P2P is disabled"), "Wi-Fi P2P is disabled")
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
                    } else if (_connectionState.value is ConnectionState.Connected) {
                        updateState(ConnectionState.Disconnected, "Connection lost")
                    }
                }
            }
        }
    }

    init {
        if (!isSupported) {
            updateState(ConnectionState.Error("Wi-Fi Direct not supported"), "Wi-Fi Direct not supported on this device")
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
                val current = _connectionState.value
                if (current == newState && newState !is ConnectionState.Error) return@withLock

                Log.d(TAG, "State transition: $current -> $newState | Reason: $reason")
                _connectionState.value = newState

                if (newState is ConnectionState.Error) {
                    delay(3000)
                    if (_connectionState.value == newState) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
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
                val p2pInfo = WifiDirectConnectionInfo(
                    isGroupOwner = info.isGroupOwner,
                    groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                )

                if (p2pInfo.groupOwnerAddress != null || p2pInfo.isGroupOwner) {
                    _connectionInfo.value = p2pInfo
                    val deviceName = connectedDeviceName ?: "Unknown Device"
                    updateState(ConnectionState.Connected(deviceName), "Connection info received")
                } else {
                    updateState(ConnectionState.Error("Missing IP"), "Connection info incomplete")
                }
            } else {
                updateState(ConnectionState.Error("Group not formed"), "Group not formed after connection")
            }
        }
    }

    private var discoveryTimeoutJob: kotlinx.coroutines.Job? = null

    fun startDiscovery() {
        if (!isSupported) {
            updateState(ConnectionState.Error("Wi-Fi Direct unsupported"), "Cannot start discovery")
            return
        }

        discoveryTimeoutJob?.cancel()
        _discoveredDevices.value = emptyList()
        updateState(ConnectionState.Discovering, "Manual discovery start")

        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started successfully")
                }

                override fun onFailure(reason: Int) {
                    updateState(ConnectionState.Error("Discovery failed: $reason"), "Discovery failed: $reason")
                }
            })
        } catch (e: SecurityException) {
            updateState(ConnectionState.Error("Permission missing"), "Permission missing for discovery: ${e.message}")
            return
        }

        discoveryTimeoutJob = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            val current = _connectionState.value
            if (current is ConnectionState.Discovering) {
                val devices = _discoveredDevices.value
                if (devices.isEmpty()) {
                    updateState(ConnectionState.Error("No devices found"), "Discovery timeout with 0 peers")
                } else {
                    Log.d(TAG, "Discovery timeout but ${devices.size} devices found, keeping Discovering state")
                }
            }
        }
    }

    suspend fun connect(device: WifiDirectDevice) {
        if (!isSupported) {
            updateState(ConnectionState.Error("Wi-Fi Direct unsupported"), "Cannot connect")
            return
        }
        val opId = ++activeConnectOpId
        connectedDeviceName = device.name
        updateState(ConnectionState.Connecting, "Connecting to ${device.name}")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }

        try {
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connect command accepted for ${device.name}")
                }

                override fun onFailure(reason: Int) {
                    if (opId == activeConnectOpId) {
                        updateState(ConnectionState.Error("Connect failed: $reason"), "Connect failed: $reason")
                    }
                }
            })

            val success = withTimeoutOrNull(20000) {
                while (true) {
                    val state = _connectionState.value
                    if (state is ConnectionState.Connected) return@withTimeoutOrNull true
                    if (state is ConnectionState.Error) return@withTimeoutOrNull false
                    if (opId != activeConnectOpId) {
                        Log.d(TAG, "Connect operation $opId superseded by $activeConnectOpId")
                        return@withTimeoutOrNull false
                    }
                    delay(500)
                }
            }

            if (success == null && opId == activeConnectOpId) {
                updateState(ConnectionState.Error("Connection timed out"), "Connection timed out after 20s")
                cancelConnect()
            }
        } catch (e: SecurityException) {
            updateState(ConnectionState.Error("Permission missing"), "Permission missing for connect: ${e.message}")
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

    fun disconnect() {
        Log.d(TAG, "WiFiDirectManager: performing partial disconnect")
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = null
        manager?.stopPeerDiscovery(channel, null)
        manager?.removeGroup(channel, null)

        _connectionState.value = ConnectionState.Disconnected
        _connectionInfo.value = null
        _discoveredDevices.value = emptyList()
        connectedDeviceName = null
        activeConnectOpId++
        activeInfoRequestId++
    }

    fun teardown() {
        discoveryTimeoutJob?.cancel()
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
        private const val DISCOVERY_TIMEOUT_MS = 30_000L
    }
}
