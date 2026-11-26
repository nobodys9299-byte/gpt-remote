package com.gptremote.mitv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gptremote.mitv.ui.RemoteScreen
import com.gptremote.mitv.ui.RemoteViewModel
import com.gptremote.mitv.ui.theme.MiTvRemoteTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MiTvRemoteTheme {
                val uiState by viewModel.uiState.collectAsState()
                var hasBtPermissions by remember { mutableStateOf(viewModel.hasBluetoothPermissions()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasBtPermissions = viewModel.hasBluetoothPermissions()
                    if (hasBtPermissions) {
                        viewModel.onPermissionsGranted()
                    }
                }

                val discoverableLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.refreshPairedBluetooth()
                }

                val requestBtPermissions = {
                    val perms = buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_CONNECT)
                            add(Manifest.permission.BLUETOOTH_SCAN)
                            add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        } else {
                            add(Manifest.permission.BLUETOOTH)
                            add(Manifest.permission.BLUETOOTH_ADMIN)
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    permissionLauncher.launch(perms)
                }

                LaunchedEffect(Unit) {
                    if (hasBtPermissions) {
                        viewModel.onPermissionsGranted()
                    } else {
                        requestBtPermissions()
                    }
                }

                RemoteScreen(
                    uiState = uiState,
                    hasBluetoothPermissions = hasBtPermissions,
                    onRequestBluetoothPermissions = requestBtPermissions,
                    onMakeDiscoverable = {
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        }
                        discoverableLauncher.launch(intent)
                    },
                    onSelectTransport = viewModel::selectTransport,
                    onScanBluetooth = viewModel::startBluetoothScan,
                    onConnectBluetooth = viewModel::connectBluetooth,
                    onScanWifi = viewModel::startWifiScan,
                    onConnectWifi = viewModel::connectWifi,
                    onConnectWifiManual = viewModel::connectWifiManual,
                    onSubmitWifiPin = viewModel::submitWifiPin,
                    onDisconnect = viewModel::disconnect,
                    onButtonPress = viewModel::pressButton,
                    onSendText = viewModel::sendText,
                    onMouseMove = viewModel::moveMouse,
                    onMouseClick = viewModel::clickMouse,
                    onClearBanner = viewModel::clearBanner
                )
            }
        }
    }
}
