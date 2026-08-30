package com.itantra.app.ui.state

import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.stt.SttState
import com.itantra.app.transport.WifiDirectDevice
import com.itantra.app.tts.TtsState

data class AppUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val selectedLanguage: Language = Language.ENGLISH,
    val connectedDeviceName: String? = null,
    val messages: List<Message> = emptyList(),
    val discoveredDevices: List<WifiDirectDevice> = emptyList(),
    val errorMessage: String? = null,
    val sttState: SttState = SttState.IDLE,
    val ttsState: TtsState = TtsState.INITIALIZING
)
