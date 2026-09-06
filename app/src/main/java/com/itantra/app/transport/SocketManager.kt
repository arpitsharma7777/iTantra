package com.itantra.app.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _incomingMessages = MutableSharedFlow<ByteArray>()
    val incomingMessages: SharedFlow<ByteArray> = _incomingMessages.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    fun startServer() {
        scope.launch {
            try {
                Log.d(TAG, "Starting server on port $PORT")
                // Case 5: Catch server socket bind failure (e.g. port already in use)
                serverSocket = ServerSocket(PORT).apply {
                    reuseAddress = true
                }
                val socket = serverSocket?.accept() ?: return@launch
                handleSocketConnection(socket)
            } catch (e: Exception) {
                handleError("Server error: ${e.message}")
                close()
            }
        }
    }

    fun startClient(host: String) {
        scope.launch {
            try {
                Log.d(TAG, "Connecting to $host:$PORT")
                // Case 6: Catch client socket connect failure (group owner not reachable)
                val socket = Socket(host, PORT)
                handleSocketConnection(socket)
            } catch (e: Exception) {
                handleError("Client connection error: ${e.message}")
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
                // 1. Read 4-byte length prefix
                val lengthBytes = readExactly(input, 4) ?: break
                val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int
                
                // 2. Validate length
                if (length <= 0 || length > MAX_MESSAGE_SIZE) {
                    handleError("Invalid message length: $length")
                    break
                }
                
                // 3. Read payload
                val payload = readExactly(input, length) ?: break
                _incomingMessages.emit(payload)
            }
        } catch (e: Exception) {
            // Case 7: Catch socket read failure mid-stream
            handleError("Read loop error: ${e.message}")
        } finally {
            close()
        }
    }

    /**
     * Reads exactly [n] bytes from the input stream.
     * Returns null if EOF is reached before [n] bytes are read.
     */
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
            // Case 7: Treat mid-stream IOException as EOF/disconnect
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
                    
                    // 1. Prepare and write length prefix
                    val lengthHeader = ByteBuffer.allocate(4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(data.size)
                        .array()
                    
                    out.write(lengthHeader)
                    
                    // 2. Write payload
                    out.write(data)
                    out.flush()
                    Log.d(TAG, "Sent message of size ${data.size}")
                } catch (e: Exception) {
                    // Case 8: Catch socket write failure (remote gone)
                    handleError("Send error: ${e.message}")
                    close()
                }
            }
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, message)
        scope.launch {
            _errorEvents.emit(message)
        }
    }

    fun close() {
        if (!_isConnected.value && serverSocket == null) return
        
        Log.d(TAG, "Closing SocketManager")
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
        private const val MAX_MESSAGE_SIZE = 1_048_576 // 1 MB
    }
}
