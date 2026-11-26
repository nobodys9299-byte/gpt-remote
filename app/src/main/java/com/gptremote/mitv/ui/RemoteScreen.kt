package com.gptremote.mitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.components.DPadControl
import com.gptremote.mitv.ui.components.DeviceConnectSheet
import com.gptremote.mitv.ui.components.KeyboardSheet
import com.gptremote.mitv.ui.components.MediaVolumeBar
import com.gptremote.mitv.ui.components.TouchpadControl
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue

private enum class PadMode {
    DPAD,
    TOUCHPAD
}

@Composable
fun RemoteScreen(
    uiState: RemoteUiState,
    hasBluetoothPermissions: Boolean,
    onRequestBluetoothPermissions: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onSelectTransport: (TransportMode) -> Unit,
    onScanBluetooth: () -> Unit,
    onConnectBluetooth: (TvDevice.BluetoothTv) -> Unit,
    onScanWifi: () -> Unit,
    onConnectWifi: (TvDevice.WifiTv, Boolean) -> Unit,
    onConnectWifiManual: (String) -> Unit,
    onSubmitWifiPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onButtonPress: (RemoteButton) -> Unit,
    onSendText: (String) -> Unit,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onClearBanner: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var padMode by remember { mutableStateOf(PadMode.DPAD) }
    var showConnectSheet by remember { mutableStateOf(false) }
    var showKeyboardSheet by remember { mutableStateOf(false) }

    val statusError = (uiState.status as? ConnectionStatus.Error)?.message
    val bannerMessage = uiState.lastStatusBanner ?: statusError
    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage)
            onClearBanner()
        }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status is ConnectionStatus.AwaitingWifiPin) {
            showConnectSheet = true
        }
    }

    Scaffold(
        containerColor = RemoteBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Power + Connection Pill + Keyboard
            TopControlBar(
                uiState = uiState,
                onPowerClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onButtonPress(RemoteButton.POWER)
                },
                onConnectionClick = { showConnectSheet = true },
                onKeyboardClick = { showKeyboardSheet = true }
            )

            // Mode Switcher: D-Pad vs Touchpad
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = padMode == PadMode.DPAD,
                    onClick = { padMode = PadMode.DPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.Gamepad, contentDescription = null) },
                    label = { Text("D-Pad") }
                )
                SegmentedButton(
                    selected = padMode == PadMode.TOUCHPAD,
                    onClick = { padMode = PadMode.TOUCHPAD },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.TouchApp, contentDescription = null) },
                    label = { Text("Touchpad") }
                )
            }

            // Primary Control Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when (padMode) {
                    PadMode.DPAD -> DPadControl(onButtonPress = onButtonPress)
                    PadMode.TOUCHPAD -> TouchpadControl(
                        transportMode = (uiState.status as? ConnectionStatus.Connected)?.device?.transport
                            ?: uiState.activeTransport,
                        onMouseMove = onMouseMove,
                        onMouseClick = onMouseClick,
                        onButtonFallback = onButtonPress
                    )
                }
            }

            // Core Navigation Row: Back / Home / Menu / Keyboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavCircleButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    label = "Back",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onButtonPress(RemoteButton.BACK)
                    }
                )
                NavCircleButton(
                    icon = Icons.Rounded.Home,
                    label = "Home",
                    highlight = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onButtonPress(RemoteButton.HOME)
                    }
                )
                NavCircleButton(
                    icon = Icons.Rounded.Menu,
                    label = "Menu",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onButtonPress(RemoteButton.MENU)
                    }
                )
                NavCircleButton(
                    icon = Icons.Rounded.Keyboard,
                    label = "Type",
                    onClick = { showKeyboardSheet = true }
                )
            }

            // Volume, Channel & Media Controls
            MediaVolumeBar(onButtonPress = onButtonPress)

            // Quick Utility Shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickShortcutButton(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    onClick = { onButtonPress(RemoteButton.SEARCH) },
                    modifier = Modifier.weight(1f)
                )
                QuickShortcutButton(
                    icon = Icons.AutoMirrored.Rounded.Input,
                    label = "Input",
                    onClick = { onButtonPress(RemoteButton.TV_INPUT) },
                    modifier = Modifier.weight(1f)
                )
                QuickShortcutButton(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    onClick = { onButtonPress(RemoteButton.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showConnectSheet) {
        DeviceConnectSheet(
            uiState = uiState,
            hasBluetoothPermissions = hasBluetoothPermissions,
            onRequestBluetoothPermissions = onRequestBluetoothPermissions,
            onMakeDiscoverable = onMakeDiscoverable,
            onSelectTransport = onSelectTransport,
            onScanBluetooth = onScanBluetooth,
            onConnectBluetooth = onConnectBluetooth,
            onScanWifi = onScanWifi,
            onConnectWifi = onConnectWifi,
            onConnectWifiManual = onConnectWifiManual,
            onSubmitWifiPin = onSubmitWifiPin,
            onDisconnect = onDisconnect,
            onDismiss = { showConnectSheet = false }
        )
    }

    if (showKeyboardSheet) {
        KeyboardSheet(
            onSendText = onSendText,
            onButtonPress = onButtonPress,
            onDismiss = { showKeyboardSheet = false }
        )
    }
}

@Composable
private fun TopControlBar(
    uiState: RemoteUiState,
    onPowerClick: () -> Unit,
    onConnectionClick: () -> Unit,
    onKeyboardClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPowerClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF341A1D),
                contentColor = Color(0xFFF87171)
            )
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Power")
        }

        val (statusText, dotColor, transportIcon) = when (val st = uiState.status) {
            is ConnectionStatus.Connected -> {
                val icon = if (st.device.transport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple(st.device.name, ConnectedGreen, icon)
            }
            is ConnectionStatus.Connecting -> {
                val icon = if (st.transport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple("Connecting...", MiOrange, icon)
            }
            is ConnectionStatus.AwaitingWifiPin -> {
                Triple("Enter TV PIN", WifiBlue, Icons.Rounded.Wifi)
            }
            is ConnectionStatus.Error -> {
                Triple("Connect TV", Color(0xFFF87171), Icons.Rounded.Bluetooth)
            }
            ConnectionStatus.Disconnected -> {
                val icon = if (uiState.activeTransport == TransportMode.BLUETOOTH_HID) {
                    Icons.Rounded.Bluetooth
                } else {
                    Icons.Rounded.Wifi
                }
                Triple("Connect TV", TextSecondary, icon)
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(23.dp))
                .clickable(onClick = onConnectionClick)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = transportIcon,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = statusText,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        FilledTonalIconButton(
            onClick = onKeyboardClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = RemoteSurfaceElevated,
                contentColor = TextPrimary
            )
        ) {
            Icon(Icons.Rounded.Keyboard, contentDescription = "Keyboard")
        }
    }
}

@Composable
private fun NavCircleButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (highlight) MiOrange.copy(alpha = 0.2f) else RemoteSurfaceElevated,
                contentColor = if (highlight) MiOrange else TextPrimary
            )
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun QuickShortcutButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = label, fontSize = 12.sp)
    }
}
