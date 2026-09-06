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

    // Tracks if an active connect() operation is in flight to drive CONNECTING state
    private val isConnecting = MutableStateFlow(false)

    /**
     * Unified state: Reports CONNECTED only when both WiFi Direct and Socket are ready.
     * Reports CONNECTING only while an active connect() call is in progress.
     */
    val connectionState: StateFlow<ConnectionState> = combine(
        wifiDirectManager.connectionState,
        socketManager.isConnected,
        isConnecting
    ) { p2pState, socketConnected, connecting ->
        when {
            // Full stack is up
            p2pState == ConnectionState.CONNECTED && socketConnected -> ConnectionState.CONNECTED
            // Actively trying to bridge the gap between layers
            connecting -> ConnectionState.CONNECTING
            // WiFi is up but socket is gone (and we aren't trying to connect) -> DISCONNECTED
            p2pState == ConnectionState.CONNECTED && !socketConnected -> ConnectionState.DISCONNECTED
            // Default to underlying WiFi state (handles DISCOVERING, ERROR, etc.)
            else -> p2pState
        }
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    val discoveredDevices: StateFlow<List<WifiDirectDevice>> = wifiDirectManager.discoveredDevices

    /**
     * Incoming messages: Decoded and filtered for valid messages.
     */
    val incomingMessages: Flow<Message> = socketManager.incomingMessages
        .mapNotNull { bytes ->
            val result = decoder.decode(bytes)
            result.onFailure { Log.e(TAG, "Failed to decode message: ${it.message}") }
            result.getOrNull()
        }

    init {
        // Bi-directional teardown: If WiFi drops, close the socket.
        scope.launch {
            wifiDirectManager.connectionState.collect { state ->
                if (state != ConnectionState.CONNECTED && socketManager.isConnected.value) {
                    Log.d(TAG, "WiFi Direct connection lost, closing socket")
                    socketManager.close()
                }
            }
        }

        // Bi-directional teardown: If Socket drops unexpectedly, tear down WiFi too.
        scope.launch {
            combine(socketManager.isConnected, isConnecting) { connected, connecting ->
                connected to connecting
            }.collect { (connected, connecting) ->
                // If socket drops while we are NOT actively trying to connect, reset everything
                if (!connected && !connecting && wifiDirectManager.connectionState.value == ConnectionState.CONNECTED) {
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
        // WiFiDirectManager handles discovery state internally
    }

    /**
     * Connects to a device: Manages the full stack (WiFi -> Socket).
     * Returns Result.success once the socket is fully established.
     */
    suspend fun connect(device: WifiDirectDevice): Result<Unit> {
        isConnecting.value = true
        Log.d(TAG, "Initiating connection to ${device.name}")
        
        try {
            // 1. Establish Wi-Fi Direct connection
            wifiDirectManager.connect(device)
            
            if (wifiDirectManager.connectionState.value != ConnectionState.CONNECTED) {
                return Result.failure(Exception("Wi-Fi Direct connection failed or timed out"))
            }

            // 2. Start Socket connection based on P2P role
            val info = wifiDirectManager.connectionInfo.value
                ?: return Result.failure(Exception("Failed to get connection info after WiFi Direct connected"))

            if (info.isGroupOwner) {
                socketManager.startServer()
            } else {
                val host = info.groupOwnerAddress 
                    ?: return Result.failure(Exception("Client role: Group Owner address missing"))
                socketManager.startClient(host)
            }

            // 3. Wait for socket to confirm connection OR fail fast on error
            val success = withTimeoutOrNull(10000) {
                // Case 5/6: Race connection success against bind/unreachable errors
                merge(
                    socketManager.isConnected.filter { it }.map { true },
                    socketManager.errorEvents.map { false }
                ).first()
            }

            if (success != true) {
                Log.e(TAG, "Socket failed to connect or timed out")
                disconnect() // Case 5/6: Tear down WiFi group too
                return Result.failure(Exception("TCP handshake failed or timed out"))
            }

            Log.d(TAG, "Full transport stack established with ${device.name}")
            return Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error during socket initialization: ${e.message}")
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

    /**
     * User-initiated disconnect that allows reconnection.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting transport stack (allowing reconnection)")
        socketManager.close()
        // Case 9: Partial reset allows reconnection while keeping the receiver registered
        wifiDirectManager.disconnect() 
    }

    /**
     * Final destruction of the transport stack.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up TransportManager (final)")
        socketManager.cleanup()
        wifiDirectManager.teardown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TransportManager"
    }
}
