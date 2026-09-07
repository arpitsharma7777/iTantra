package com.itantra.app.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import com.itantra.app.ui.communication.CommunicationScreen
import com.itantra.app.ui.connection.ConnectionScreen
import com.itantra.app.ui.developer.DeveloperScreen
import com.itantra.app.ui.home.HomeScreen
import com.itantra.app.ui.settings.SettingsScreen
import com.itantra.app.ui.state.AppViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: AppViewModel? = null
) {
    val context = LocalContext.current
    val actualViewModel = viewModel ?: composeViewModel(
        factory = AppViewModel.Factory(context.applicationContext)
    )
    val uiState by actualViewModel.uiState.collectAsState()
    var pendingPermissionAction by remember { mutableStateOf<PendingPermissionAction?>(null) }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAllPermissions(context, requiredWifiPermissions())) {
            when (pendingPermissionAction) {
                PendingPermissionAction.DISCOVER -> {
                    if (isLocationEnabled(context)) {
                        actualViewModel.startDiscovering()
                    } else {
                        actualViewModel.showError("Turn on Location services to discover Wi-Fi Direct devices.")
                    }
                }
                null -> Unit
            }
        } else {
            actualViewModel.showError("Wi-Fi Direct permission was denied.")
        }
        pendingPermissionAction = null
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            actualViewModel.startSpeaking()
        } else {
            actualViewModel.showError("Microphone permission was denied.")
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                uiState = uiState,
                onLanguageSelect = { actualViewModel.setLanguage(it) },
                onConnectClick = { navController.navigate(Screen.Connection.route) },
                onCommunicateClick = {
                    if (uiState.connectionState is ConnectionState.Connected) {
                        navController.navigate(Screen.Communication.route)
                    }
                },
                onNavigateToTab = { tab ->
                    when (tab) {
                        "Settings" -> navController.navigate(Screen.Settings.route)
                    }
                }
            )
        }
        composable(Screen.Connection.route) {
            ConnectionScreen(
                connectionState = uiState.connectionState,
                discoveredDevices = uiState.discoveredDevices,
                onStartScan = {
                    if (!hasAllPermissions(context, requiredWifiPermissions())) {
                        pendingPermissionAction = PendingPermissionAction.DISCOVER
                        wifiPermissionLauncher.launch(requiredWifiPermissions())
                    } else if (!isLocationEnabled(context)) {
                        actualViewModel.showError("Turn on Location services to discover Wi-Fi Direct devices.")
                    } else {
                        actualViewModel.startDiscovering()
                    }
                },
                onDeviceClick = { device ->
                    actualViewModel.connectToDevice(device)
                },
                onBackClick = { navController.popBackStack() },
                onStartCommunication = {
                    navController.navigate(Screen.Communication.route)
                }
            )
        }
        composable(Screen.Communication.route) {
            CommunicationScreen(
                messages = uiState.messages,
                selectedLanguage = uiState.selectedLanguage,
                connectedDeviceName = uiState.connectedDeviceName,
                onBackClick = { navController.popBackStack() },
                onMicClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        actualViewModel.startSpeaking()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSendClick = { actualViewModel.sendCurrentMessage() },
                onClearClick = { actualViewModel.clearMessages() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = actualViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDeveloper = { navController.navigate(Screen.Developer.route) }
            )
        }
        composable(Screen.Developer.route) {
            DeveloperScreen()
        }
    }
}

private enum class PendingPermissionAction {
    DISCOVER
}

private fun requiredWifiPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
