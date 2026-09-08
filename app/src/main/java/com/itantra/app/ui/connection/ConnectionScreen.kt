package com.itantra.app.ui.connection

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.transport.WifiDirectDevice
import com.itantra.app.ui.state.AppUiState
import com.itantra.app.ui.theme.ITantraTheme
import com.itantra.app.ui.theme.PrimaryRed
import com.itantra.app.ui.theme.ScanningPurple
import com.itantra.app.ui.theme.SecondaryLavender
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    discoveredDevices: List<WifiDirectDevice> = emptyList(),
    onStartScan: () -> Unit,
    onDeviceClick: (WifiDirectDevice) -> Unit,
    onBackClick: () -> Unit,
    onStartCommunication: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SecondaryLavender),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = ScanningPurple,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Connection Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Radar Animation
            RadarIllustration(isScanning = connectionState is ConnectionState.Discovering)

            Spacer(modifier = Modifier.height(32.dp))

            // Status Pill
            ScanningStatusPill(
                text = if (connectionState is ConnectionState.Discovering) "SCANNING FOR DEVICES" else "NO DEVICE CONNECTED",
                isScanning = connectionState is ConnectionState.Discovering
            )

            Spacer(modifier = Modifier.weight(1f))

            // State-based content
            when (connectionState) {
                is ConnectionState.Connected -> {
                    Text(
                        text = "Connected to: ${connectionState.deviceName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartCommunication,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("START COMMUNICATION", fontWeight = FontWeight.Bold)
                    }
                }
                is ConnectionState.Discovering -> {
                    if (discoveredDevices.isNotEmpty()) {
                        DeviceList(devices = discoveredDevices, onDeviceClick = onDeviceClick)
                    } else {
                        var showHint by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(2000)
                            showHint = true
                        }
                        if (showHint) {
                            Text(
                                text = "Searching for nearby devices...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
                is ConnectionState.Disconnected -> {
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONNECT DEVICE", fontWeight = FontWeight.Bold)
                    }
                }
                is ConnectionState.Connecting -> {
                    Text(
                        text = "Connecting to device...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ScanningPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
                is ConnectionState.Error -> {
                    Text(
                        text = "Error: ${connectionState.reason}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("TRY AGAIN", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecondaryLavender)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = ScanningPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Home", color = ScanningPurple)
            }
        }
    }
}

@Composable
fun RadarIllustration(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        // Concentric Squares
        repeat(3) { index ->
            val size = 80.dp + (index * 40).dp
            Box(
                modifier = Modifier
                    .size(if (isScanning) size * scale else size)
                    .border(
                        width = 1.dp,
                        color = ScanningPurple.copy(alpha = if (isScanning) 0.2f else 0.4f),
                        shape = RoundedCornerShape(24.dp)
                    )
            )
        }
        
        // Inner square with icon
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SecondaryLavender),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = ScanningPurple, modifier = Modifier.size(32.dp))
        }

        // Green dot moving along the path
        if (isScanning) {
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "angle"
            )
            
            val radius = 90.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = radius)
                        .clip(CircleShape)
                        .background(Color.Green)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
fun ScanningStatusPill(text: String, isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        color = SecondaryLavender,
        shape = CircleShape,
        modifier = Modifier.border(1.dp, Color.LightGray.copy(alpha = 0.2f), CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ScanningPurple.copy(alpha = if (isScanning) alpha else 1f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = ScanningPurple,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
fun DeviceList(devices: List<WifiDirectDevice>, onDeviceClick: (WifiDirectDevice) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            items(devices) { device ->
                ListItem(
                    headlineContent = { Text(device.name, fontWeight = FontWeight.Medium) },
                    trailingContent = {
                        TextButton(onClick = { onDeviceClick(device) }) {
                            Text("Connect", color = PrimaryRed)
                        }
                    },
                    modifier = Modifier.clickable { onDeviceClick(device) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionScreenPreview() {
    ITantraTheme {
        ConnectionScreen(
            connectionState = ConnectionState.Disconnected,
            onStartScan = {},
            onDeviceClick = {},
            onBackClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionScreenScanningPreview() {
    ITantraTheme {
        ConnectionScreen(
            connectionState = ConnectionState.Discovering,
            onStartScan = {},
            onDeviceClick = {},
            onBackClick = {}
        )
    }
}
