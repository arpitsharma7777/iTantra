package com.itantra.app.transport

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val isConnecting = MutableStateFlow(false)

    val connectionState: StateFlow<ConnectionState> = combine(
        wifiDirectManager.connectionState,
        socketManager.isConnected,
        isConnecting
    ) { p2pState, socketConnected, connecting ->
        when {
            p2pState is ConnectionState.Connected && socketConnected -> p2pState
            connecting -> ConnectionState.Connecting
            p2pState is ConnectionState.Connected && !socketConnected -> ConnectionState.Disconnected
            else -> p2pState
        }
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionState.Disconnected)

    val discoveredDevices: StateFlow<List<WifiDirectDevice>> = wifiDirectManager.discoveredDevices

    val incomingMessages: Flow<Message> = socketManager.incomingMessages
        .mapNotNull { bytes ->
            val result = decoder.decode(bytes)
            result.onFailure { Log.e(TAG, "Failed to decode message: ${it.message}") }
            result.getOrNull()
        }

    init {
        scope.launch {
            wifiDirectManager.connectionState.collect { state ->
                if (state !is ConnectionState.Connected && socketManager.isConnected.value) {
                    Log.d(TAG, "WiFi Direct connection lost, closing socket")
                    socketManager.close()
                }
            }
        }

        scope.launch {
            combine(socketManager.isConnected, isConnecting) { connected, connecting ->
                connected to connecting
            }.collect { (connected, connecting) ->
                if (!connected && !connecting && wifiDirectManager.connectionState.value is ConnectionState.Connected) {
                    Log.d(TAG, "Socket connection lost unexpectedly, resetting WiFi stack")
                    wifiDirectManager.disconnect()
                }
            }
        }
    }

    fun startDiscovery() {
        wifiDirectManager.startDiscovery()
    }

    fun stopDiscovery() {
    }

    suspend fun connect(device: WifiDirectDevice): Result<Unit> {
        isConnecting.value = true
        Log.d(TAG, "Initiating connection to ${device.name}")

        try {
            wifiDirectManager.connect(device)

            if (wifiDirectManager.connectionState.value !is ConnectionState.Connected) {
                return Result.failure(Exception("Wi-Fi Direct connection failed or timed out"))
            }

            val info = wifiDirectManager.connectionInfo.value
                ?: return Result.failure(Exception("Failed to get connection info"))

            if (info.isGroupOwner) {
                socketManager.startServer()
            } else {
                val host = info.groupOwnerAddress
                    ?: return Result.failure(Exception("Client role: Group Owner address missing"))
                socketManager.startClient(host)
            }

            val success = withTimeoutOrNull(10000) {
                merge<Boolean>(
                    socketManager.isConnected.filter { it }.map { true },
                    socketManager.errorEvents.map { false }
                ).first()
            }

            if (success != true) {
                Log.e(TAG, "Socket failed to connect or timed out")
                disconnect()
                return Result.failure(Exception("TCP handshake failed or timed out"))
            }

            Log.d(TAG, "Full transport stack established with ${device.name}")
            return Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error during connection: ${e.message}")
            disconnect()
            return Result.failure(e)
        } finally {
            isConnecting.value = false
        }
    }

    suspend fun send(message: Message) {
        val bytes = encoder.encode(message)
        socketManager.send(bytes)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting transport stack")
        socketManager.close()
        wifiDirectManager.disconnect()
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up TransportManager")
        socketManager.cleanup()
        wifiDirectManager.teardown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TransportManager"
    }
}
