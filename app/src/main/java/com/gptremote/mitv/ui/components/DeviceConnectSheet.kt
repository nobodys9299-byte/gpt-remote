package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.model.TvDevice
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectSheet(
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
    onDismiss: () -> Unit
) {
    var manualIp by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    val awaitingPin = uiState.status as? ConnectionStatus.AwaitingWifiPin
    if (awaitingPin != null) {
        AlertDialog(
            onDismissRequest = onDisconnect,
            title = { Text("Enter TV Pairing PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the 6-character code displayed on ${awaitingPin.device.name}:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.uppercase().take(6) },
                        label = { Text("6-char hex PIN (e.g. A1B2C3)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    awaitingPin.message?.let { err ->
                        Text(text = err, color = androidx.compose.ui.graphics.Color(0xFFF87171), fontSize = 12.sp)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RemoteSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connect to Mi Android TV",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.isConnected) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect", color = androidx.compose.ui.graphics.Color(0xFFF87171))
                    }
                }
            }

            val selectedTab = if (uiState.activeTransport == TransportMode.BLUETOOTH_HID) 0 else 1
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = RemoteSurfaceElevated
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onSelectTransport(TransportMode.BLUETOOTH_HID) },
                    text = { Text("Bluetooth HID") },
                    icon = { Icon(Icons.Rounded.Bluetooth, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onSelectTransport(TransportMode.WIFI_ANDROID_TV) },
                    text = { Text("Wi-Fi Fallback") },
                    icon = { Icon(Icons.Rounded.Wifi, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                BluetoothSection(
                    uiState = uiState,
                    hasBluetoothPermissions = hasBluetoothPermissions,
                    onRequestPermissions = onRequestBluetoothPermissions,
                    onMakeDiscoverable = onMakeDiscoverable,
                    onScan = onScanBluetooth,
                    onConnect = {
                        onConnectBluetooth(it)
                        onDismiss()
                    }
                )
            } else {
                WifiSection(
                    uiState = uiState,
                    manualIp = manualIp,
                    onManualIpChange = { manualIp = it },
                    onScan = onScanWifi,
                    onConnect = onConnectWifi,
                    onConnectManual = onConnectWifiManual
                )
            }
        }
    }
}

@Composable
private fun BluetoothSection(
    uiState: RemoteUiState,
    hasBluetoothPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onScan: () -> Unit,
    onConnect: (TvDevice.BluetoothTv) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!hasBluetoothPermissions) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
            ) {
                Text("Grant Bluetooth Permissions")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Bluetooth HID Status: ${
                        when {
                            !uiState.bluetoothEnabled -> "Bluetooth is OFF"
                            uiState.bluetoothHidRegistered -> "Ready (HID Remote active)"
                            else -> "Initializing HID profile..."
                        }
                    }",
                    color = if (uiState.bluetoothHidRegistered) ConnectedGreen else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "First-time setup: On your Mi TV open Settings → Remotes & Accessories → Add accessory, then tap Make Discoverable below.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isScanningBluetooth) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (uiState.isScanningBluetooth) "Scanning..." else "Scan TVs")
                }

                OutlinedButton(
                    onClick = onMakeDiscoverable,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Discoverable")
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.bluetoothDevices.isEmpty()) {
                item {
                    Text(
                        text = "No Bluetooth devices found yet. Tap Scan TVs or pair from Mi TV settings.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(uiState.bluetoothDevices, key = { it.id }) { device ->
                    val isConnected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == device.id
                    DeviceRow(
                        title = device.name,
                        subtitle = "${device.id} • ${if (device.isBonded) "Paired" else "Available"}",
                        isConnected = isConnected,
                        accentColor = MiOrange,
                        onClick = { onConnect(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiSection(
    uiState: RemoteUiState,
    manualIp: String,
    onManualIpChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: (TvDevice.WifiTv, Boolean) -> Unit,
    onConnectManual: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = onManualIpChange,
                label = { Text("TV IP (optional, e.g. 192.168.1.40)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onConnectManual(manualIp) },
                enabled = manualIp.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WifiBlue)
            ) {
                Text("Go")
            }
        }

        FilledTonalButton(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isScanningWifi) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(if (uiState.isScanningWifi) "Discovering Android TVs..." else "Refresh Wi-Fi Discovery")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.wifiDevices.isEmpty()) {
                item {
                    Text(
                        text = "Make sure your phone and Mi TV are on the same Wi-Fi network.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            } else {
                items(uiState.wifiDevices, key = { it.id }) { device ->
                    val isConnected = (uiState.status as? ConnectionStatus.Connected)?.device?.id == device.id
                    DeviceRow(
                        title = device.name,
                        subtitle = "${device.host}:${device.port} • ${if (device.isPaired) "Paired" else "PIN required"}",
                        isConnected = isConnected,
                        accentColor = WifiBlue,
                        onClick = { onConnect(device, false) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    isConnected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RemoteSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isConnected) ConnectedGreen else RemoteBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = if (isConnected) ConnectedGreen else accentColor
            )
            Column {
                Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }

        if (isConnected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Connected",
                tint = ConnectedGreen
            )
        }
    }
}
