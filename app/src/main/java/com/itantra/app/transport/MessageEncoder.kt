package com.itantra.app.transport

import com.itantra.app.core.model.Message
import org.json.JSONObject

class MessageEncoder {
    fun encode(message: Message): ByteArray {
        val json = JSONObject().apply {
            put("i", message.id)
            put("s", message.sender)
            put("l", message.language.name)
            put("t", message.text)
            put("ts", message.timestamp)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }
}
