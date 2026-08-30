package com.itantra.app.ui.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import com.itantra.app.stt.SttState
import com.itantra.app.tts.TtsState
import com.itantra.app.ui.state.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    uiState: AppUiState,
    onLanguageSelected: (Language) -> Unit,
    onMicPressed: () -> Unit,
    onMicReleased: () -> Unit,
    onClearError: () -> Unit = {},
    onBack: () -> Unit
) {
    var isSpeaking by remember { mutableStateOf(false) }
    val canSpeak = uiState.connectionState == ConnectionState.CONNECTED &&
        uiState.sttState == SttState.READY

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Communication", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = uiState.connectedDeviceName ?: uiState.connectionState.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LanguageSelector(
                selectedLanguage = uiState.selectedLanguage,
                onLanguageSelected = onLanguageSelected,
                enabled = uiState.sttState != SttState.LISTENING
            )

            Spacer(modifier = Modifier.height(8.dp))
            StatusText(uiState)
            ErrorMessage(uiState.errorMessage, onClearError)

            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages yet. Hold the microphone to speak.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    reverseLayout = true
                ) {
                    items(uiState.messages.reversed(), key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {},
                    enabled = canSpeak,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(64.dp)
                        .then(
                            if (canSpeak) {
                                Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.any { it.pressed }
                                            if (pressed && !isSpeaking) {
                                                isSpeaking = true
                                                onMicPressed()
                                            } else if (!pressed && isSpeaking) {
                                                isSpeaking = false
                                                onMicReleased()
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpeaking) Color.Red else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            uiState.connectionState != ConnectionState.CONNECTED -> "Connect First"
                            uiState.sttState == SttState.LOADING -> "Loading Speech"
                            isSpeaking -> "Release to Send"
                            else -> "Hold to Speak"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(uiState: AppUiState) {
    val ttsText = when (uiState.ttsState) {
        TtsState.INITIALIZING -> "TTS initializing"
        TtsState.READY -> "TTS ready"
        TtsState.SPEAKING -> "Speaking"
        TtsState.ERROR -> "TTS error"
        TtsState.SHUTDOWN -> "TTS stopped"
    }
    val sttText = when (uiState.sttState) {
        SttState.IDLE -> "Speech idle"
        SttState.LOADING -> "Loading speech model"
        SttState.READY -> "Speech ready"
        SttState.LISTENING -> "Listening"
        SttState.ERROR -> "Speech error"
    }
    Text(
        text = "$sttText | $ttsText",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorMessage(message: String?, onClearError: () -> Unit) {
    if (message == null) return
    Spacer(modifier = Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClearError) {
                Text("Dismiss")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    enabled: Boolean = true
) {
    val options = Language.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, language ->
            SegmentedButton(
                selected = language == selectedLanguage,
                onClick = { onLanguageSelected(language) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(language.displayName)
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isLocal = message.sender != Sender.REMOTE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isLocal) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(
                    color = if (isLocal) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Column {
                Text(text = message.text)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.language.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
