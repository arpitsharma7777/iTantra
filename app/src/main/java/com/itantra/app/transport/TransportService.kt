package com.itantra.app.transport

import com.itantra.app.core.model.Message
import kotlinx.coroutines.flow.Flow

interface TransportService {

    suspend fun connect()

    suspend fun send(message: Message)

    fun observeMessages(): Flow<Message>

    fun disconnect()
}
