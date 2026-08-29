package com.itantra.app.transport

import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import org.json.JSONObject

class MessageDecoder {
    fun decode(data: ByteArray): Result<Message> {
        return runCatching {
            val jsonString = String(data, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            
            val id = json.getString("i")
            val sender = json.getString("s")
            val language = Language.valueOf(json.getString("l"))
            val text = json.getString("t")
            val timestamp = json.getLong("ts")
            
            Message(id, sender, language, text, timestamp)
        }
    }
}
