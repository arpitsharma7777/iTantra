package com.itantra.app.transport

import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import org.json.JSONObject

class MessageDecoder {
    fun decode(data: ByteArray): Result<Message> {
        return runCatching {
            val jsonString = String(data, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            
            val id = json.getString("i")
            val language = Language.valueOf(json.getString("l"))
            val text = json.getString("t")
            val timestamp = json.getLong("ts")
            
            Message(
                id = id,
                text = text,
                sender = Sender.REMOTE,
                timestamp = timestamp,
                language = language
            )
        }
    }
}
