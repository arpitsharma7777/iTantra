package com.itantra.app.core.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Discovering : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
}