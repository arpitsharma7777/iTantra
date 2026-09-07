package com.itantra.app.ui.state

import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message

data class AppUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedLanguage: Language = Language.ENGLISH,
    val connectedDeviceName: String? = null,
    val messages: List<Message> = emptyList()
)