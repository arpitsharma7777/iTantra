package com.itantra.app.core.model

data class Message(
    val id: String,
    val text: String,
    val sender: String,
    val timestamp: Long,
    val language: Language
)

object Sender {
    const val LOCAL = "LOCAL"
    const val REMOTE = "REMOTE"
}
