package com.itantra.app.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectionAttemptId = 0L

    private val _incomingMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    val incomingMessages: SharedFlow<ByteArray> = _incomingMessages.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    fun startServer() {
        if (_isConnected.value || serverSocket != null || clientSocket != null) return
        val attemptId = ++connectionAttemptId

        scope.launch {
            try {
                Log.d(TAG, "Starting server on port $PORT")
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                    soTimeout = ACCEPT_TIMEOUT_MS.toInt()
                }
                val socket = serverSocket?.accept() ?: return@launch
                if (attemptId != connectionAttemptId) {
                    socket.close()
                    return@launch
                }
                handleSocketConnection(socket)
            } catch (e: SocketTimeoutException) {
                handleError("No TCP client connected before timeout")
                close()
            } catch (e: Exception) {
                handleError("Server error: ${e.message}")
                close()
            }
        }
    }

    fun startClient(host: String) {
        if (_isConnected.value || serverSocket != null || clientSocket != null) return
        val attemptId = ++connectionAttemptId

        scope.launch {
            val deadline = System.currentTimeMillis() + CONNECT_RETRY_WINDOW_MS
            var lastError: String? = null

            while (attemptId == connectionAttemptId && System.currentTimeMillis() < deadline) {
                try {
                    Log.d(TAG, "Connecting to $host:$PORT")
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS.toInt())
                    if (attemptId != connectionAttemptId) {
                        socket.close()
                        return@launch
                    }
                    handleSocketConnection(socket)
                    return@launch
                } catch (e: Exception) {
                    lastError = e.message
                    delay(CONNECT_RETRY_DELAY_MS)
                }
            }

            if (attemptId == connectionAttemptId && !_isConnected.value) {
                handleError("Client connection error: ${lastError ?: "timed out"}")
                close()
            }
        }
    }

    private suspend fun handleSocketConnection(socket: Socket) {
        withContext(Dispatchers.IO) {
            clientSocket = socket
            inputStream = socket.getInputStream()
            outputStream = socket.getOutputStream()
            _isConnected.value = true
            Log.d(TAG, "Socket connected: ${socket.remoteSocketAddress}")

            readLoop()
        }
    }

    private suspend fun readLoop() {
        try {
            val input = inputStream ?: return
            while (_isConnected.value) {
                val lengthBytes = readExactly(input, LENGTH_PREFIX_BYTES) ?: break
                val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int

                if (length <= 0 || length > MAX_MESSAGE_SIZE) {
                    handleError("Invalid message length: $length")
                    break
                }

                val payload = readExactly(input, length) ?: break
                _incomingMessages.emit(payload)
            }
        } catch (e: Exception) {
            handleError("Read loop error: ${e.message}")
        } finally {
            close()
        }
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray? {
        val buffer = ByteArray(n)
        var totalRead = 0
        try {
            while (totalRead < n) {
                val bytesRead = input.read(buffer, totalRead, n - totalRead)
                if (bytesRead == -1) {
                    Log.d(TAG, "EOF reached after reading $totalRead/$n bytes")
                    return null
                }
                totalRead += bytesRead
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during readExactly: ${e.message}")
            return null
        }
        return buffer
    }

    suspend fun send(data: ByteArray) {
        if (!_isConnected.value) {
            _errorEvents.emit("Not connected")
            return
        }

        if (data.size > MAX_MESSAGE_SIZE) {
            _errorEvents.emit("Message too large: ${data.size}")
            return
        }

        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    val out = outputStream ?: return@withLock
                    val lengthHeader = ByteBuffer.allocate(LENGTH_PREFIX_BYTES)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(data.size)
                        .array()

                    out.write(lengthHeader)
                    out.write(data)
                    out.flush()
                    Log.d(TAG, "Sent message of size ${data.size}")
                } catch (e: Exception) {
                    handleError("Send error: ${e.message}")
                    close()
                }
            }
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, message)
        _errorEvents.tryEmit(message)
    }

    fun close() {
        if (!_isConnected.value && serverSocket == null && clientSocket == null) return

        Log.d(TAG, "Closing SocketManager")
        connectionAttemptId++
        _isConnected.value = false

        try {
            inputStream?.close()
            outputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            clientSocket = null
            serverSocket = null
        }
    }

    fun cleanup() {
        close()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SocketManager"
        private const val PORT = 8888
        private const val LENGTH_PREFIX_BYTES = 4
        private const val MAX_MESSAGE_SIZE = 1_048_576
        private const val ACCEPT_TIMEOUT_MS = 15000L
        private const val CONNECT_TIMEOUT_MS = 1500L
        private const val CONNECT_RETRY_WINDOW_MS = 15000L
        private const val CONNECT_RETRY_DELAY_MS = 500L
    }
}
