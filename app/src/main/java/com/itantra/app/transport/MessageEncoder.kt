package com.itantra.app.transport

import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import org.json.JSONObject

class MessageEncoder {
    fun encode(message: Message): ByteArray {
        val json = JSONObject().apply {
            put("i", message.id)
            put("s", message.sender.name)
            put("t", message.text)
            put("tr", message.translatedText ?: "")
            put("ts", message.timestamp)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }
}
