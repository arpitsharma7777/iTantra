package com.itantra.app.ui.communication

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import com.itantra.app.ui.theme.BubblePink
import com.itantra.app.ui.theme.ITantraTheme
import com.itantra.app.ui.theme.PrimaryRed
import com.itantra.app.ui.theme.ScanningPurple
import com.itantra.app.ui.theme.SecondaryLavender
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    messages: List<Message>,
    selectedLanguage: Language,
    connectedDeviceName: String?,
    isRecording: Boolean,
    partialText: String,
    recognizedText: String,
    onBackClick: () -> Unit,
    onMicClick: () -> Unit,
    onLanguageSelected: (Language) -> Unit,
    onClearClick: () -> Unit
) {
    var showLanguageMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Communication", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text("Press to speak · Real-time STT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        Surface(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(CircleShape)
                                .clickable { showLanguageMenu = true },
                            color = SecondaryLavender
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedLanguage.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PrimaryRed
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryRed)
                            }
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            Language.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${language.displayName} (${language.code})",
                                            fontWeight = if (language == selectedLanguage) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onLanguageSelected(language)
                                        showLanguageMenu = false
                                    },
                                    leadingIcon = {
                                        if (language == selectedLanguage) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryRed)
                                        } else {
                                            Spacer(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            connectedDeviceName?.let {
                Text(
                    text = "Connected to: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Messages list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            // Real-time transcription display
            if (isRecording || recognizedText.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRecording) SecondaryLavender.copy(alpha = 0.5f) else Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Listening...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val displayText = if (recognizedText.isNotBlank() && !isRecording) recognizedText else partialText
                        if (displayText.isNotBlank()) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        } else if (isRecording) {
                            Text(
                                text = "Speak now...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Single mic button
            BigMicButton(
                isRecording = isRecording,
                onClick = onMicClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClearClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryLavender)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear", fontSize = 13.sp, color = PrimaryRed)
                }
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryLavender)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Home", fontSize = 13.sp, color = PrimaryRed)
                }
            }
        }
    }
}

@Composable
fun BigMicButton(isRecording: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .size(120.dp)
            .then(
                if (isRecording) Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) Color(0xFFD32F2F) else PrimaryRed
        ),
        shape = CircleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isSender = message.sender == Sender.SENDER
    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (isSender) "$time · You" else "Other · $time",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
            color = if (isSender) PrimaryRed.copy(alpha = 0.1f) else BubblePink,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSender) 16.dp else 4.dp,
                bottomEnd = if (isSender) 4.dp else 16.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.text, fontWeight = FontWeight.Medium, color = Color(0xFF333333), fontSize = 16.sp)
                message.translatedText?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = it, color = PrimaryRed, fontSize = 14.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CommunicationScreenPreview() {
    ITantraTheme {
        CommunicationScreen(
            messages = listOf(
                Message("1", "Hello, I need help", null, Sender.RECEIVER, System.currentTimeMillis()),
                Message("2", "मुझे मदद चाहिए", null, Sender.SENDER, System.currentTimeMillis() + 60000)
            ),
            selectedLanguage = Language.HINDI,
            connectedDeviceName = "Device A",
            isRecording = false,
            partialText = "",
            recognizedText = "",
            onBackClick = {},
            onMicClick = {},
            onLanguageSelected = {},
            onClearClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CommunicationScreenRecordingPreview() {
    ITantraTheme {
        CommunicationScreen(
            messages = emptyList(),
            selectedLanguage = Language.HINDI,
            connectedDeviceName = "Device A",
            isRecording = true,
            partialText = "मुझे सहायता चा",
            recognizedText = "",
            onBackClick = {},
            onMicClick = {},
            onLanguageSelected = {},
            onClearClick = {}
        )
    }
}
