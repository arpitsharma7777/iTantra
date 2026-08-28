package com.itantra.app.transport

import com.itantra.app.core.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class TransportManager {

    private val _messages = MutableSharedFlow<Message>()

    suspend fun connect() {
        // Placeholder for connection logic
    }

    suspend fun send(message: Message) {
        // Placeholder for sending message
    }

    fun observeMessages(): Flow<Message> = _messages

    fun disconnect() {
        // Placeholder for disconnection logic
    }
}
