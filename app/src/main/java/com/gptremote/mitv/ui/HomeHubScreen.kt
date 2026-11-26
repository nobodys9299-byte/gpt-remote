package com.gptremote.mitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.DeviceCategory
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.SavedDevice
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.components.SettingsSheet
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue

private enum class HubFilter(val label: String) {
    ALL("All"),
    TV("Android TV"),
    PC("Laptop / PC")
}

@Composable
fun HomeHubScreen(
    uiState: RemoteUiState,
    hasBluetoothPermissions: Boolean,
    onRequestBluetoothPermissions: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onScanAll: () -> Unit,
    onScanBluetooth: () -> Unit,
    onScanWifiTv: () -> Unit,
    onScanWifiPc: () -> Unit,
    onConnectBluetooth: (TvDevice.BluetoothTv) -> Unit,
    onConnectWifiTv: (TvDevice.WifiTv) -> Unit,
    onConnectWifiPc: (TvDevice.WifiPc) -> Unit,
    onConnectSaved: (SavedDevice) -> Unit,
    onAddManualWifi: (host: String, nickname: String, category: DeviceCategory) -> Unit,
    onCustomizeSaved: (id: String, nickname: String, category: DeviceCategory, iconKey: String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onSubmitWifiPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenRemote: () -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleBluetoothPcProfile: (Boolean) -> Unit,
    onClearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(HubFilter.ALL) }
    var discoveryTab by remember { mutableIntStateOf(0) } // 0 = Bluetooth, 1 = Wi-Fi TV, 2 = Wi-Fi PC
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<SavedDevice?>(null) }
    var pinInput by remember { mutableStateOf("") }

    val statusError = (uiState.status as? ConnectionStatus.Error)?.message
    val bannerMessage = uiState.lastStatusBanner ?: statusError
    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage)
            onClearBanner()
        }
    }

    val awaitingPin = uiState.status as? ConnectionStatus.AwaitingWifiPin
    if (awaitingPin != null) {
        AlertDialog(
            onDismissRequest = onDisconnect,
            title = { Text("Enter TV Pairing PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the 6-character hex code displayed on ${awaitingPin.device.name}:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.uppercase().take(6) },
                        label = { Text("PIN (e.g. A1B2C3)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    awaitingPin.message?.let { err ->
                        Text(text = err, color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitWifiPin(pinInput)
                        pinInput = ""
                    },
                    enabled = pinInput.length == 6
                ) {
                    Text("Pair")
                }
            },
            dismissButton = {
                TextButton(onClick = onDisconnect) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddDeviceDialog(
            onConfirm = { host, nickname, category ->
                onAddManualWifi(host, nickname, category)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingDevice?.let { target ->
        CustomizeDeviceDialog(
            device = target,
            onSave = { name, category, icon ->
                onCustomizeSaved(target.id, name, category, icon)
                editingDevice = null
            },
            onDelete = {
                onRemoveSaved(target.id)
                editingDevice = null
            },
            onDismiss = { editingDevice = null }
        )
    }

    if (showSettings) {
        SettingsSheet(
            hapticStrength = uiState.hapticStrength,
            bluetoothPcProfile = uiState.bluetoothPcProfile,
            onSelectHapticStrength = onSelectHapticStrength,
            onToggleBluetoothPcProfile = onToggleBluetoothPcProfile,
            onDismiss = { showSettings = false }
        )
    }

    val filteredSaved = uiState.savedDevices.filter { saved ->
        when (filter) {
            HubFilter.ALL -> true
            HubFilter.TV -> saved.category == DeviceCategory.ANDROID_TV
            HubFilter.PC -> saved.category == DeviceCategory.LAPTOP_PC
        }
    }

    Scaffold(
        containerColor = RemoteBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Device Hub",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control Android TV & Laptop via Bluetooth or Wi-Fi",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = onScanAll,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = RemoteSurfaceElevated,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Scan all")
                        }
                        FilledTonalIconButton(
                            onClick = { showAddDialog = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = RemoteSurfaceElevated,
                                contentColor = MiOrange
                            )
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add device by IP")
                        }
                        FilledTonalIconButton(
                            onClick = { showSettings = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = RemoteSurfaceElevated,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                }
            }

            // Active Connection Hero Card
            item {
                ActiveConnectionBanner(
                    status = uiState.status,
                    onOpenRemote = onOpenRemote,
                    onDisconnect = onDisconnect
                )
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubFilter.entries.forEach { itemFilter ->
                        FilterChip(
                            selected = filter == itemFilter,
                            onClick = { filter = itemFilter },
                            label = { Text(itemFilter.label) }
                        )
                    }
                }
            }

            // My Saved Electronics Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Electronics (${filteredSaved.size})",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add IP")
                    }
                }
            }

            if (filteredSaved.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RemoteSurface)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Connected devices appear here automatically so you can customize their name, icon, and 1-tap connect later.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSaved, key = { it.id }) { saved ->
                            val isConnected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == saved.id
                            SavedDeviceCard(
                                saved = saved,
                                isConnected = isConnected,
                                onClick = {
                                    if (isConnected) onOpenRemote() else onConnectSaved(saved)
                                },
                                onEdit = { editingDevice = saved }
                            )
                        }
                    }
                }
            }

            // Nearby Discovery Tabs
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Nearby & Discoverable",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = discoveryTab,
                    containerColor = RemoteSurfaceElevated
                ) {
                    Tab(
                        selected = discoveryTab == 0,
                        onClick = { discoveryTab = 0 },
                        text = { Text("Bluetooth", fontSize = 12.sp) },
                        icon = { Icon(Icons.Rounded.Bluetooth, contentDescription = null) }
                    )
                    Tab(
                        selected = discoveryTab == 1,
                        onClick = {
                            discoveryTab = 1
                            onScanWifiTv()
                        },
                        text = { Text("Wi-Fi TV", fontSize = 12.sp) },
                        icon = { Icon(Icons.Rounded.Tv, contentDescription = null) }
                    )
                    Tab(
                        selected = discoveryTab == 2,
                        onClick = {
                            discoveryTab = 2
                            onScanWifiPc()
                        },
                        text = { Text("Wi-Fi PC", fontSize = 12.sp) },
                        icon = { Icon(Icons.Rounded.Laptop, contentDescription = null) }
                    )
                }
            }

            when (discoveryTab) {
                0 -> {
                    item {
                        BluetoothDiscoveryHeader(
                            uiState = uiState,
                            hasBluetoothPermissions = hasBluetoothPermissions,
                            onRequestPermissions = onRequestBluetoothPermissions,
                            onMakeDiscoverable = onMakeDiscoverable,
                            onScan = onScanBluetooth,
                            onTogglePcProfile = onToggleBluetoothPcProfile
                        )
                    }
                    val btList = uiState.bluetoothDevices.filter {
                        when (filter) {
                            HubFilter.ALL -> true
                            HubFilter.TV -> it.category == DeviceCategory.ANDROID_TV
                            HubFilter.PC -> it.category == DeviceCategory.LAPTOP_PC
                        }
                    }
                    if (btList.isEmpty()) {
                        item {
                            EmptyDiscoveryHint("No Bluetooth devices found yet. Tap Scan or Discoverable to pair your Mi TV or Laptop.")
                        }
                    } else {
                        items(btList, key = { it.id }) { dev ->
                            val isConnected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == dev.id
                            DiscoveredRow(
                                title = dev.name,
                                subtitle = "${dev.id} • ${if (dev.isBonded) "Paired" else "Available"}",
                                icon = if (dev.category == DeviceCategory.LAPTOP_PC) Icons.Rounded.Laptop else Icons.Rounded.Tv,
                                accent = MiOrange,
                                isConnected = isConnected,
                                onClick = { if (isConnected) onOpenRemote() else onConnectBluetooth(dev) }
                            )
                        }
                    }
                }

                1 -> {
                    item {
                        WifiScanBar(
                            label = if (uiState.isScanningWifi) "Discovering Android TVs..." else "Scan Wi-Fi TVs",
                            isScanning = uiState.isScanningWifi,
                            onScan = onScanWifiTv
                        )
                    }
                    if (uiState.wifiDevices.isEmpty()) {
                        item {
                            EmptyDiscoveryHint("No Android TVs discovered yet. Ensure phone and TV are on the same Wi-Fi, or tap + to enter TV IP.")
                        }
                    } else {
                        items(uiState.wifiDevices, key = { it.id }) { dev ->
                            val isConnected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == dev.id
                            DiscoveredRow(
                                title = dev.name,
                                subtitle = "${dev.host}:${dev.port} • ${if (dev.isPaired) "Paired" else "PIN required"}",
                                icon = Icons.Rounded.Tv,
                                accent = WifiBlue,
                                isConnected = isConnected,
                                onClick = { if (isConnected) onOpenRemote() else onConnectWifiTv(dev) }
                            )
                        }
                    }
                }

                2 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WifiScanBar(
                                label = if (uiState.isScanningWifiPc) "Discovering Laptops..." else "Scan Wi-Fi Laptops / PCs",
                                isScanning = uiState.isScanningWifiPc,
                                onScan = onScanWifiPc
                            )
                            Text(
                                text = "Run `python pc-companion/gpt_remote_pc.py` on your laptop so it appears here (or tap + to enter its IP).",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (uiState.wifiPcDevices.isEmpty()) {
                        item {
                            EmptyDiscoveryHint("No Wi-Fi PCs detected yet.")
                        }
                    } else {
                        items(uiState.wifiPcDevices, key = { it.id }) { dev ->
                            val isConnected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == dev.id
                            DiscoveredRow(
                                title = dev.name,
                                subtitle = "${dev.host}:${dev.port} • ${dev.osName}",
                                icon = Icons.Rounded.Laptop,
                                accent = WifiBlue,
                                isConnected = isConnected,
                                onClick = { if (isConnected) onOpenRemote() else onConnectWifiPc(dev) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveConnectionBanner(
    status: ConnectionStatus,
    onOpenRemote: () -> Unit,
    onDisconnect: () -> Unit
) {
    when (status) {
        is ConnectionStatus.Connected -> {
            val isPc = status.device.category == DeviceCategory.LAPTOP_PC
            val accent = if (status.device.transport == TransportMode.BLUETOOTH_HID) MiOrange else WifiBlue
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(RemoteSurfaceElevated, Color(0xFF1A2636))
                        )
                    )
                    .border(1.5.dp, ConnectedGreen.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenRemote)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ConnectedGreen.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPc) Icons.Rounded.Laptop else Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = ConnectedGreen
                            )
                        }
                        Column {
                            Text(
                                text = status.device.name,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connected • ${status.device.category.label} • ${
                                    when (status.device.transport) {
                                        TransportMode.BLUETOOTH_HID -> "Bluetooth"
                                        TransportMode.WIFI_ANDROID_TV, TransportMode.WIFI_PC -> "Wi-Fi"
                                    }
                                }",
                                color = accent,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenRemote,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Open Remote", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }

        is ConnectionStatus.Connecting -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, MiOrange.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MiOrange)
                Text(
                    text = "Connecting to ${status.deviceName}...",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RemoteSurface)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ready to Connect",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Pick a saved device below or scan nearby TVs & Laptops",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = onOpenRemote,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remote UI")
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceCard(
    saved: SavedDevice,
    isConnected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val icon = iconForKey(saved.iconKey, saved.category)
    val accent = if (saved.transport == TransportMode.BLUETOOTH_HID) MiOrange else WifiBlue

    Column(
        modifier = Modifier
            .width(172.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(RemoteSurfaceElevated)
            .border(
                width = if (isConnected) 1.5.dp else 1.dp,
                color = if (isConnected) ConnectedGreen else RemoteBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Customize",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column {
            Text(
                text = saved.nickname,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${saved.category.label} • ${
                    when (saved.transport) {
                        TransportMode.BLUETOOTH_HID -> "Bluetooth"
                        TransportMode.WIFI_ANDROID_TV, TransportMode.WIFI_PC -> "Wi-Fi"
                    }
                }",
                color = if (isConnected) ConnectedGreen else TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BluetoothDiscoveryHeader(
    uiState: RemoteUiState,
    hasBluetoothPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onScan: () -> Unit,
    onTogglePcProfile: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!hasBluetoothPermissions) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
            ) {
                Text("Grant Bluetooth Permissions")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !uiState.bluetoothPcProfile,
                    onClick = { onTogglePcProfile(false) },
                    label = { Text("TV HID Profile", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = uiState.bluetoothPcProfile,
                    onClick = { onTogglePcProfile(true) },
                    label = { Text("PC / Laptop HID", fontSize = 12.sp) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isScanningBluetooth) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.isScanningBluetooth) "Scanning..." else "Scan BT")
                }
                OutlinedButton(
                    onClick = onMakeDiscoverable,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Discoverable")
                }
            }
        }
    }
}

@Composable
private fun WifiScanBar(
    label: String,
    isScanning: Boolean,
    onScan: () -> Unit
) {
    FilledTonalButton(
        onClick = onScan,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun DiscoveredRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isConnected) ConnectedGreen else RemoteBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isConnected) ConnectedGreen else accent
            )
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = if (isConnected) "Open" else "Connect",
            color = if (isConnected) ConnectedGreen else accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyDiscoveryHint(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AddDeviceDialog(
    onConfirm: (host: String, nickname: String, category: DeviceCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DeviceCategory.ANDROID_TV) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Wi-Fi Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = category == DeviceCategory.ANDROID_TV,
                        onClick = { category = DeviceCategory.ANDROID_TV },
                        label = { Text("Android TV") }
                    )
                    FilterChip(
                        selected = category == DeviceCategory.LAPTOP_PC,
                        onClick = { category = DeviceCategory.LAPTOP_PC },
                        label = { Text("Laptop / PC") }
                    )
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP Address (e.g. 192.168.1.40)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname (e.g. Living Room TV)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(host, nickname, category) },
                enabled = host.isNotBlank()
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CustomizeDeviceDialog(
    device: SavedDevice,
    onSave: (nickname: String, category: DeviceCategory, iconKey: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf(device.nickname) }
    var category by remember { mutableStateOf(device.category) }
    var iconKey by remember { mutableStateOf(device.iconKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Customize Device")
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Color(0xFFF87171))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Device Type", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = category == DeviceCategory.ANDROID_TV,
                        onClick = { category = DeviceCategory.ANDROID_TV },
                        label = { Text("Android TV") }
                    )
                    FilterChip(
                        selected = category == DeviceCategory.LAPTOP_PC,
                        onClick = { category = DeviceCategory.LAPTOP_PC },
                        label = { Text("Laptop / PC") }
                    )
                }
                Text("Card Icon", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "tv" to Icons.Rounded.Tv,
                        "laptop" to Icons.Rounded.Laptop,
                        "desktop" to Icons.Rounded.DesktopWindows,
                        "speaker" to Icons.Rounded.Speaker
                    ).forEach { (key, icon) ->
                        FilledTonalIconButton(
                            onClick = { iconKey = key },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (iconKey == key) MiOrange else RemoteSurfaceElevated,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(icon, contentDescription = key)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(nickname, category, iconKey) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun iconForKey(iconKey: String, category: DeviceCategory): ImageVector = when (iconKey) {
    "laptop" -> Icons.Rounded.Laptop
    "desktop" -> Icons.Rounded.DesktopWindows
    "speaker" -> Icons.Rounded.Speaker
    "tv" -> Icons.Rounded.Tv
    else -> if (category == DeviceCategory.LAPTOP_PC) Icons.Rounded.Computer else Icons.Rounded.Tv
}
