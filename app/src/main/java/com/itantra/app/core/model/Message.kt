package com.itantra.app.core.model

data class Message(
    val id: String,
    val text: String,
    val sender: Sender,
    val timestamp: Long
)

enum class Sender {
    LOCAL,
    REMOTE
}
