package com.itantra.app.communication

import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import java.util.UUID

/**
 * Utility to create [Message] instances with consistent metadata.
 */
object MessageFactory {

    /**
     * Creates a new [Message] with LOCAL sender, a unique ID, and current timestamp.
     * The [language] and [senderId] are currently unused by the model but passed for future extension
     * or logging purposes as requested.
     */
    fun createOutgoingMessage(text: String, language: Language, senderId: String): Message {
        return Message(
            id = UUID.randomUUID().toString(),
            text = text,
            sender = senderId,
            timestamp = System.currentTimeMillis(),
            language = language
        )
    }
}
