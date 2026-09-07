package com.itantra.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.ui.state.AppUiState
import com.itantra.app.ui.theme.ITantraTheme
import com.itantra.app.ui.theme.PrimaryRed
import com.itantra.app.ui.theme.SecondaryLavender
import com.itantra.app.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: AppUiState,
    onLanguageSelect: (Language) -> Unit,
    onConnectClick: () -> Unit,
    onCommunicateClick: () -> Unit,
    onNavigateToTab: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = { onNavigateToTab("Features") },
                    icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                    label = { Text("Features") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = SecondaryLavender,
                        selectedIconColor = PrimaryRed,
                        selectedTextColor = PrimaryRed
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateToTab("About") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateToTab("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Language Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LanguageSelector(
                    selectedLanguage = uiState.selectedLanguage,
                    onLanguageSelect = onLanguageSelect
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logo Placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SecondaryLavender),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hub, // Network/Connect icon from screenshot
                    contentDescription = null,
                    tint = PrimaryRed,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "iTantra",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                text = "Indian Multilingual Communication",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status Card
            StatusCard(connectionState = uiState.connectionState)

            Spacer(modifier = Modifier.height(16.dp))

            // Warning Banner
            if (uiState.connectionState is ConnectionState.Disconnected) {
                WarningBanner()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Cards
            ActionCard(
                title = "Connect Device",
                subtitle = "Pair via Wi-Fi Direct & Bluetooth",
                icon = Icons.Default.WifiTethering,
                onClick = onConnectClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            ActionCard(
                title = "Start Communication",
                subtitle = "Multilingual PTT voice relay",
                icon = Icons.Default.RecordVoiceOver,
                onClick = onCommunicateClick,
                enabled = uiState.connectionState is ConnectionState.Connected
            )
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelect: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { expanded = true },
            color = SecondaryLavender
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedLanguage == Language.ENGLISH) "English / हिन्दी" else "हिन्दी / English",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = PrimaryRed)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("English") },
                onClick = {
                    onLanguageSelect(Language.ENGLISH)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("हिन्दी") },
                onClick = {
                    onLanguageSelect(Language.HINDI)
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun StatusCard(connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (connectionState is ConnectionState.Connected) Color.Green else Color.Red)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Disconnected -> "Status: Disconnected"
                        is ConnectionState.Discovering -> "Status: Discovering..."
                        is ConnectionState.Connecting -> "Status: Connecting..."
                        is ConnectionState.Connected -> "Status: Connected to ${connectionState.deviceName}"
                        is ConnectionState.Error -> "Status: Error - ${connectionState.reason}"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search for nearby responder devices via Wi-Fi Direct to start talking or transmitting emergency alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun WarningBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WarningYellow.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningYellow.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Please connect a device first",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5D4037)
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val cardColor = if (enabled) PrimaryRed else PrimaryRed.copy(alpha = 0.6f)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(text = subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            
            // White circle with arrow
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (title.contains("Communication")) Icons.Default.ChevronRight else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (enabled) PrimaryRed else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ITantraTheme {
        HomeScreen(
            uiState = AppUiState(),
            onLanguageSelect = {},
            onConnectClick = {},
            onCommunicateClick = {},
            onNavigateToTab = {}
        )
    }
}
