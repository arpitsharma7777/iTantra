package com.itantra.app.transport

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.itantra.app.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val manager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val wifiManager: WifiManager? =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(appContext, Looper.getMainLooper(), null)

    val isSupported: Boolean = manager != null && channel != null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateMutex = Mutex()

    private var activeConnectOpId = 0L
    private var activeInfoRequestId = 0L
    private var discoveryTimeoutJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<WifiDirectDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<WifiDirectDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiDirectConnectionInfo?>(null)
    val connectionInfo: StateFlow<WifiDirectConnectionInfo?> = _connectionInfo.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        updateState(ConnectionState.ERROR, "Wi-Fi Direct is disabled")
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo =
                        intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    @Suppress("DEPRECATION")
                    if (networkInfo?.isConnected == true) {
                        handleConnected()
                    } else if (_connectionState.value == ConnectionState.CONNECTED ||
                        _connectionState.value == ConnectionState.CONNECTING
                    ) {
                        updateState(ConnectionState.DISCONNECTED, "Connection lost")
                    }
                }
            }
        }
    }

    init {
        if (!isSupported) {
            updateState(ConnectionState.ERROR, "Wi-Fi Direct is not supported on this device")
        } else {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            cleanupStaleGroups()
        }
    }

    private fun updateState(newState: ConnectionState, reason: String) {
        scope.launch {
            var shouldAutoReset = false
            stateMutex.withLock {
                if (_connectionState.value == newState && newState != ConnectionState.ERROR) {
                    return@withLock
                }

                Log.d(TAG, "State transition: ${_connectionState.value} -> $newState | $reason")
                _connectionState.value = newState

                if (newState == ConnectionState.ERROR) {
                    _errorEvents.tryEmit(reason)
                    shouldAutoReset = true
                }
            }

            if (shouldAutoReset) {
                delay(ERROR_STATE_VISIBLE_MS)
                stateMutex.withLock {
                    if (_connectionState.value == ConnectionState.ERROR) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }

    private fun hasRequiredPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun failIfPermissionMissing(operation: String): Boolean {
        if (hasRequiredPermission()) return false
        updateState(ConnectionState.ERROR, "Permission missing for Wi-Fi Direct $operation")
        return true
    }

    private fun requestPeers() {
        if (!isSupported || failIfPermissionMissing("peer list")) return
        try {
            manager?.requestPeers(channel) { peerList ->
                val devices = peerList.deviceList.map { device ->
                    WifiDirectDevice(
                        name = device.deviceName.ifBlank { "Unknown device" },
                        address = device.deviceAddress
                    )
                }
                _discoveredDevices.value = devices
                Log.d(TAG, "Peers updated: ${devices.size} devices found")
            }
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for peer list: ${e.message}")
        }
    }

    private fun handleConnected() {
        val reqId = ++activeInfoRequestId
        discoveryTimeoutJob?.cancel()
        Log.d(TAG, "P2P connected, requesting connection info (request $reqId)")

        try {
            manager?.requestConnectionInfo(channel) { info ->
                if (reqId != activeInfoRequestId) return@requestConnectionInfo

                if (!info.groupFormed) {
                    updateState(ConnectionState.ERROR, "Wi-Fi Direct group was not formed")
                    return@requestConnectionInfo
                }

                val p2pInfo = WifiDirectConnectionInfo(
                    isGroupOwner = info.isGroupOwner,
                    groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                )

                if (p2pInfo.isGroupOwner || p2pInfo.groupOwnerAddress != null) {
                    _connectionInfo.value = p2pInfo
                    updateState(ConnectionState.CONNECTED, "Connection info received")
                } else {
                    updateState(ConnectionState.ERROR, "Connection info is missing group owner IP")
                }
            }
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for connection info: ${e.message}")
        }
    }

    fun startDiscovery() {
        if (!isSupported) {
            updateState(ConnectionState.ERROR, "Cannot discover: Wi-Fi Direct is unsupported")
            return
        }
        if (wifiManager?.isWifiEnabled == false) {
            updateState(ConnectionState.ERROR, "Turn on Wi-Fi to discover devices")
            return
        }
        if (failIfPermissionMissing("discovery")) return

        _discoveredDevices.value = emptyList()
        updateState(ConnectionState.DISCOVERING, "Manual discovery start")

        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started")
                    startDiscoveryTimeout()
                }

                override fun onFailure(reason: Int) {
                    updateState(ConnectionState.ERROR, "Discovery failed: ${reasonText(reason)}")
                }
            })
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for discovery: ${e.message}")
        }
    }

    fun stopDiscovery() {
        discoveryTimeoutJob?.cancel()
        try {
            manager?.stopPeerDiscovery(channel, null)
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for stopping discovery: ${e.message}")
        } finally {
            if (_connectionState.value == ConnectionState.DISCOVERING) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun startDiscoveryTimeout() {
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (_connectionState.value == ConnectionState.DISCOVERING &&
                _discoveredDevices.value.isEmpty()
            ) {
                stopDiscovery()
                updateState(ConnectionState.ERROR, "No nearby Wi-Fi Direct devices found")
            }
        }
    }

    suspend fun connect(device: WifiDirectDevice) {
        if (!isSupported) {
            updateState(ConnectionState.ERROR, "Cannot connect: Wi-Fi Direct is unsupported")
            return
        }
        if (failIfPermissionMissing("connect")) return

        val opId = ++activeConnectOpId
        discoveryTimeoutJob?.cancel()
        updateState(ConnectionState.CONNECTING, "Connecting to ${device.name}")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }

        try {
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connect request accepted for ${device.name}")
                }

                override fun onFailure(reason: Int) {
                    if (opId == activeConnectOpId) {
                        updateState(ConnectionState.ERROR, "Connect failed: ${reasonText(reason)}")
                    }
                }
            })

            val success = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                while (true) {
                    val state = _connectionState.value
                    if (state == ConnectionState.CONNECTED) return@withTimeoutOrNull true
                    if (state == ConnectionState.ERROR) return@withTimeoutOrNull false
                    if (opId != activeConnectOpId) return@withTimeoutOrNull false
                    delay(500)
                }
            }

            if (success == null && opId == activeConnectOpId) {
                updateState(ConnectionState.ERROR, "Connection timed out")
                cancelConnect()
            }
        } catch (e: SecurityException) {
            updateState(ConnectionState.ERROR, "Permission missing for connect: ${e.message}")
        }
    }

    private fun cancelConnect() {
        try {
            manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection canceled")
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to cancel connection: ${reasonText(reason)}")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing while canceling connection: ${e.message}")
        }
    }

    private fun cleanupStaleGroups() {
        if (!hasRequiredPermission()) return
        try {
            manager?.requestGroupInfo(channel) { group ->
                if (group != null) {
                    Log.d(TAG, "Found stale group, removing: ${group.networkName}")
                    manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "Stale group removed")
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG, "Failed to remove stale group: ${reasonText(reason)}")
                        }
                    })
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing while cleaning stale groups: ${e.message}")
        }
    }

    fun disconnect() {
        Log.d(TAG, "WiFiDirectManager disconnect")
        discoveryTimeoutJob?.cancel()
        try {
            manager?.stopPeerDiscovery(channel, null)
            manager?.removeGroup(channel, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing during disconnect: ${e.message}")
        }

        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionInfo.value = null
        _discoveredDevices.value = emptyList()
        activeConnectOpId++
        activeInfoRequestId++
    }

    fun teardown() {
        disconnect()
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
        Log.d(TAG, "WiFiDirectManager teardown complete")
    }

    private fun reasonText(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is unsupported"
        WifiP2pManager.ERROR -> "internal framework error"
        WifiP2pManager.BUSY -> "Wi-Fi Direct framework is busy"
        else -> "reason $reason"
    }

    companion object {
        private const val TAG = "WiFiDirectManager"
        private const val DISCOVERY_TIMEOUT_MS = 15000L
        private const val CONNECTION_TIMEOUT_MS = 20000L
        private const val ERROR_STATE_VISIBLE_MS = 1000L
    }
}
