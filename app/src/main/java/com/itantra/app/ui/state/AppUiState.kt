package com.itantra.app.ui.state

import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.stt.SttState
import com.itantra.app.tts.TtsState
import com.itantra.app.transport.WifiDirectDevice

data class AppUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedLanguage: Language = Language.ENGLISH,
    val connectedDeviceName: String? = null,
    val messages: List<Message> = emptyList(),
    val discoveredDevices: List<WifiDirectDevice> = emptyList(),
    val sttState: SttState = SttState.IDLE,
    val ttsState: TtsState = TtsState.INITIALIZING,
    val errorMessage: String? = null
)
