package com.itantra.app.ui.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import com.itantra.app.ui.theme.BubblePink
import com.itantra.app.ui.theme.ITantraTheme
import com.itantra.app.ui.theme.PrimaryRed
import com.itantra.app.ui.theme.SecondaryLavender
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    messages: List<Message>,
    selectedLanguage: Language,
    connectedDeviceName: String?,
    onBackClick: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    onClearClick: () -> Unit
) {
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
                        Text("Speech-to-Text", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 12.dp).clip(CircleShape),
                        color = SecondaryLavender
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedLanguage == Language.ENGLISH) "English / हिन्दी" else "हिन्दी / English",
                                style = MaterialTheme.typography.labelMedium,
                                color = PrimaryRed
                            )
                        }
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
            connectedDeviceName?.let {
                Text(
                    text = "Connected to: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Interaction Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BigActionCard(
                    title = "MIC / बोलें",
                    subtitle = "Hold to Record / दबाकर बोलें",
                    icon = Icons.Default.Mic,
                    modifier = Modifier.weight(1f),
                    onClick = onMicClick
                )
                BigActionCard(
                    title = "PUSH TO SEND",
                    subtitle = "तुरंत भेजें",
                    icon = Icons.AutoMirrored.Filled.Send,
                    modifier = Modifier.weight(1f),
                    onClick = onSendClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onClearClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryLavender)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear / Re-record", fontSize = 13.sp, color = PrimaryRed)
                }
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryLavender)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Home", fontSize = 13.sp, color = PrimaryRed)
                }
            }
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
            text = if (isSender) "$time • SENDER / प्रेषक" else "RECEIVER / प्राप्तकर्ता • $time",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        Surface(
            color = BubblePink,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSender) 16.dp else 4.dp,
                bottomEnd = if (isSender) 4.dp else 16.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = message.text, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037), fontSize = 18.sp)
                message.translatedText?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = it, color = PrimaryRed, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun BigActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(130.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryRed),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CommunicationScreenPreview() {
    ITantraTheme {
        CommunicationScreen(
            messages = listOf(
                Message("1", "tell location", "स्थान बताएं", Sender.RECEIVER, System.currentTimeMillis()),
                Message("2", "help help", "मदद मदद", Sender.SENDER, System.currentTimeMillis() + 60000)
            ),
            selectedLanguage = Language.ENGLISH,
            connectedDeviceName = "iTantra Pro v2",
            onBackClick = {},
            onMicClick = {},
            onSendClick = {},
            onClearClick = {}
        )
    }
}
