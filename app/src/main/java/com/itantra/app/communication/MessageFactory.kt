package com.itantra.app.communication

import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import java.util.UUID

object MessageFactory {

    fun createOutgoingMessage(text: String, language: Language, senderId: String): Message {
        return Message(
            id = UUID.randomUUID().toString(),
            text = text,
            translatedText = null,
            sender = Sender.SENDER,
            timestamp = System.currentTimeMillis()
        )
    }
}
