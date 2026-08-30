package com.itantra.app.transport

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class TransportManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val wifiDirectManager = WiFiDirectManager(context)
    private val socketManager = SocketManager()
    private val encoder = MessageEncoder()
    private val decoder = MessageDecoder()

    private val isP2pConnectRequested = MutableStateFlow(false)
    private val isSocketConnecting = MutableStateFlow(false)
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var socketStartJob: Job? = null

    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()
    val discoveredDevices: StateFlow<List<WifiDirectDevice>> = wifiDirectManager.discoveredDevices

    val errorEvents: Flow<String> = merge(
        wifiDirectManager.errorEvents,
        socketManager.errorEvents,
        _errorEvents
    )

    val connectionState: StateFlow<ConnectionState> = combine(
        wifiDirectManager.connectionState,
        socketManager.isConnected,
        isP2pConnectRequested,
        isSocketConnecting
    ) { p2pState, socketConnected, p2pRequested, socketConnecting ->
        when {
            p2pState == ConnectionState.CONNECTED && socketConnected -> ConnectionState.CONNECTED
            p2pState == ConnectionState.ERROR -> ConnectionState.ERROR
            p2pRequested || socketConnecting -> ConnectionState.CONNECTING
            p2pState == ConnectionState.CONNECTED && !socketConnected -> ConnectionState.CONNECTING
            else -> p2pState
        }
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    val incomingMessages: Flow<Message> = socketManager.incomingMessages
        .mapNotNull { bytes ->
            val result = decoder.decode(bytes)
            result.onFailure { Log.e(TAG, "Failed to decode message: ${it.message}") }
            result.getOrNull()
        }

    init {
        scope.launch {
            combine(
                wifiDirectManager.connectionState,
                wifiDirectManager.connectionInfo
            ) { state, info -> state to info }
                .collect { (state, info) ->
                    if (state == ConnectionState.CONNECTED && info != null) {
                        ensureSocketStarted(info)
                    } else if (state != ConnectionState.CONNECTED && socketManager.isConnected.value) {
                        Log.d(TAG, "Wi-Fi Direct dropped, closing socket")
                        socketManager.close()
                    }
                }
        }

        scope.launch {
            socketManager.isConnected.collect { connected ->
                if (!connected && connectionState.value == ConnectionState.CONNECTED) {
                    _errorEvents.tryEmit("Socket connection lost")
                    disconnect()
                }
            }
        }
    }

    fun startDiscovery() {
        wifiDirectManager.startDiscovery()
    }

    fun stopDiscovery() {
        wifiDirectManager.stopDiscovery()
    }

    suspend fun connect(device: WifiDirectDevice): Result<Unit> {
        isP2pConnectRequested.value = true
        _connectedDeviceName.value = device.name
        Log.d(TAG, "Initiating connection to ${device.name}")

        try {
            wifiDirectManager.connect(device)

            if (wifiDirectManager.connectionState.value != ConnectionState.CONNECTED) {
                _connectedDeviceName.value = null
                return Result.failure(Exception("Wi-Fi Direct connection failed or timed out"))
            }

            val info = wifiDirectManager.connectionInfo.value
                ?: return Result.failure(Exception("Wi-Fi Direct connection info is unavailable"))
            ensureSocketStarted(info)

            val success = withTimeoutOrNull(SOCKET_HANDSHAKE_TIMEOUT_MS) {
                merge(
                    socketManager.isConnected.filter { it }.map { true },
                    wifiDirectManager.connectionState
                        .filter { it == ConnectionState.ERROR || it == ConnectionState.DISCONNECTED }
                        .map { false },
                    socketManager.errorEvents.map { false }
                ).first()
            }

            if (success != true) {
                _connectedDeviceName.value = null
                disconnect()
                return Result.failure(Exception("TCP handshake failed or timed out"))
            }

            Log.d(TAG, "Full transport stack established with ${device.name}")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            _connectedDeviceName.value = null
            disconnect()
            return Result.failure(e)
        } finally {
            isP2pConnectRequested.value = false
        }
    }

    private fun ensureSocketStarted(info: WifiDirectConnectionInfo) {
        if (socketManager.isConnected.value || isSocketConnecting.value) return

        socketStartJob?.cancel()
        socketStartJob = scope.launch {
            isSocketConnecting.value = true
            try {
                if (info.isGroupOwner) {
                    socketManager.startServer()
                } else {
                    val host = info.groupOwnerAddress
                    if (host == null) {
                        _errorEvents.emit("Group owner address is unavailable")
                        disconnect()
                        return@launch
                    }
                    socketManager.startClient(host)
                }

                val success = withTimeoutOrNull(SOCKET_HANDSHAKE_TIMEOUT_MS) {
                    merge(
                        socketManager.isConnected.filter { it }.map { true },
                        socketManager.errorEvents.map { false },
                        wifiDirectManager.connectionState
                            .filter { it != ConnectionState.CONNECTED }
                            .map { false }
                    ).first()
                }

                if (success != true && !socketManager.isConnected.value) {
                    _errorEvents.emit("TCP handshake failed or timed out")
                    disconnect()
                }
            } finally {
                isSocketConnecting.value = false
            }
        }
    }

    suspend fun send(message: Message) {
        val bytes = encoder.encode(message)
        socketManager.send(bytes)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting transport stack")
        socketStartJob?.cancel()
        isSocketConnecting.value = false
        isP2pConnectRequested.value = false
        _connectedDeviceName.value = null
        socketManager.close()
        wifiDirectManager.disconnect()
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up TransportManager")
        socketStartJob?.cancel()
        socketManager.cleanup()
        wifiDirectManager.teardown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TransportManager"
        private const val SOCKET_HANDSHAKE_TIMEOUT_MS = 15000L
    }
}
