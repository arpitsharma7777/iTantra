package com.itantra.app.transport

import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import org.json.JSONObject

class MessageDecoder {
    fun decode(data: ByteArray): Result<Message> {
        return runCatching {
            val jsonString = String(data, Charsets.UTF_8)
            val json = JSONObject(jsonString)

            val id = json.getString("i")
            val senderName = json.getString("s")
            val sender = try { Sender.valueOf(senderName) } catch (_: Exception) { Sender.RECEIVER }
            val text = json.getString("t")
            val translatedText = json.optString("tr", "").ifBlank { null }
            val timestamp = json.getLong("ts")

            Message(id, text, translatedText, sender, timestamp)
        }
    }
}
