package com.gptremote.mitv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.ui.BrandPickerScreen
import com.gptremote.mitv.ui.HomeHubScreen
import com.gptremote.mitv.ui.RemoteScreen
import com.gptremote.mitv.ui.RemoteViewModel
import com.gptremote.mitv.ui.components.InstalledAppsPickerDialog
import com.gptremote.mitv.ui.theme.MiTvRemoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AppRoute {
    data object Home : AppRoute
    data class BrandList(val product: ProductType) : AppRoute
    data class Remote(val brand: BrandOption) : AppRoute
}

private enum class FilePickTarget {
    PC_UPLOAD,
    PHONE_HOST_ADD,
    PHONE_PEER_SEND,
    TV_COMPANION_UPLOAD
}

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            MiTvRemoteTheme(accentTheme = uiState.accentTheme) {
                var hasBtPermissions by remember { mutableStateOf(viewModel.hasBluetoothPermissions()) }
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }
                var filePickTarget by remember { mutableStateOf(FilePickTarget.PC_UPLOAD) }
                var appPickerTarget by remember { mutableStateOf<FilePickTarget?>(null) }
                var installedAppsList by remember { mutableStateOf<List<RemoteRepository.ShareableAppInfo>>(emptyList()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasBtPermissions = viewModel.hasBluetoothPermissions()
                    if (hasBtPermissions) {
                        viewModel.onPermissionsGranted()
                    }
                }

                val hotspotPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.startLocalHotspot()
                }

                val discoverableLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.refreshPairedBluetooth()
                }

                val screenCastLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    if (result.resultCode == RESULT_OK && data != null) {
                        viewModel.startPhoneCast(result.resultCode, data)
                    }
                }

                val tvCastLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    if (result.resultCode == RESULT_OK && data != null) {
                        viewModel.startPhoneCastToTv(result.resultCode, data)
                    }
                }

                val pickFilesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        when (filePickTarget) {
                            FilePickTarget.PC_UPLOAD -> viewModel.uploadFilesToPc(uris)
                            FilePickTarget.PHONE_HOST_ADD -> viewModel.addLocalFilesToPhoneShare(uris)
                            FilePickTarget.PHONE_PEER_SEND -> viewModel.sendFilesToPeerPhone(uris)
                            FilePickTarget.TV_COMPANION_UPLOAD -> viewModel.uploadFilesToTv(uris)
                        }
                    }
                }

                val openAppsPicker: (FilePickTarget) -> Unit = { target ->
                    appPickerTarget = target
                    if (installedAppsList.isEmpty()) {
                        coroutineScope.launch {
                            installedAppsList = withContext(Dispatchers.IO) {
                                viewModel.queryInstalledAppsForShare()
                            }
                        }
                    }
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
                            add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // LocalOnlyHotspot requires ACCESS_FINE_LOCATION on Android 12
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
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

                val currentAppTarget = appPickerTarget
                if (currentAppTarget != null) {
                    InstalledAppsPickerDialog(
                        apps = installedAppsList,
                        onConfirm = { chosen ->
                            appPickerTarget = null
                            coroutineScope.launch {
                                val stagedUris = withContext(Dispatchers.IO) {
                                    viewModel.stageAppsAsUris(chosen)
                                }
                                if (stagedUris.isNotEmpty()) {
                                    when (currentAppTarget) {
                                        FilePickTarget.PC_UPLOAD -> viewModel.uploadFilesToPc(stagedUris)
                                        FilePickTarget.PHONE_HOST_ADD -> viewModel.addLocalFilesToPhoneShare(stagedUris)
                                        FilePickTarget.PHONE_PEER_SEND -> viewModel.sendFilesToPeerPhone(stagedUris)
                                        FilePickTarget.TV_COMPANION_UPLOAD -> viewModel.uploadFilesToTv(stagedUris)
                                    }
                                }
                            }
                        },
                        onDismiss = { appPickerTarget = null }
                    )
                }

                when (val current = route) {
                    AppRoute.Home -> {
                        HomeHubScreen(
                            uiState = uiState,
                            onSelectProduct = { product ->
                                if (product == ProductType.PHONE_TO_PHONE) {
                                    val phoneBrand = ProductCatalog.brandsFor(product).first()
                                    viewModel.selectProductBrand(phoneBrand)
                                    route = AppRoute.Remote(phoneBrand)
                                } else {
                                    route = AppRoute.BrandList(product)
                                }
                            },
                            onOpenActiveRemote = {
                                val activeBrand = uiState.connectedBrandOption
                                    ?: ProductCatalog.brandsFor(ProductType.SMART_TV).first()
                                viewModel.selectProductBrand(activeBrand)
                                route = AppRoute.Remote(activeBrand)
                            },
                            onDisconnect = viewModel::disconnect,
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onClearBanner = viewModel::clearBanner
                        )
                    }

                    is AppRoute.BrandList -> {
                        BackHandler { route = AppRoute.Home }
                        BrandPickerScreen(
                            product = current.product,
                            uiState = uiState,
                            onBack = { route = AppRoute.Home },
                            onSelectBrand = { brand ->
                                viewModel.selectProductBrand(brand)
                                route = AppRoute.Remote(brand)
                            },
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme
                        )
                    }

                    is AppRoute.Remote -> {
                        val backTarget = if (current.brand.productType == ProductType.PHONE_TO_PHONE) {
                            AppRoute.Home
                        } else {
                            AppRoute.BrandList(current.brand.productType)
                        }
                        BackHandler { route = backTarget }
                        RemoteScreen(
                            brand = current.brand,
                            uiState = uiState,
                            hasBluetoothPermissions = hasBtPermissions,
                            onNavigateBack = { route = backTarget },
                            onRequestBluetoothPermissions = requestBtPermissions,
                            onMakeDiscoverable = {
                                viewModel.allowBluetoothIncomingPairing()
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                                }
                                discoverableLauncher.launch(intent)
                            },
                            onScanBluetooth = viewModel::startBluetoothScan,
                            onConnectBluetooth = viewModel::connectBluetooth,
                            onScanWifiTv = viewModel::startWifiScan,
                            onConnectWifiTv = viewModel::connectWifi,
                            onScanWifiPc = viewModel::startWifiPcScan,
                            onConnectWifiPc = viewModel::connectWifiPc,
                            onConnectManualIp = { host, category ->
                                viewModel.addAndConnectManualWifi(host, current.brand.name, category)
                            },
                            onQrScanned = { rawQr ->
                                viewModel.connectFromQr(rawQr)
                            },
                            onScanBulbs = viewModel::startBulbScan,
                            onSelectBulb = viewModel::selectBulb,
                            onConnectManualBulb = viewModel::connectManualBulb,
                            onBulbAction = { power, bright, rgb, kelvin, irAction ->
                                viewModel.setBulbState(power, bright, rgb, kelvin, irAction)
                            },
                            onEnableIr = { viewModel.setIrMode(true) },
                            onSubmitWifiPin = viewModel::submitWifiPin,
                            onDisconnect = viewModel::disconnect,
                            onStartPcMirror = viewModel::startPcScreenMirror,
                            onStopPcMirror = viewModel::stopPcScreenMirror,
                            onTapMirroredScreen = viewModel::sendPcScreenTap,
                            onMoveMirroredScreen = viewModel::sendPcScreenMove,
                            onStartPhoneCast = {
                                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCastLauncher.launch(mgr.createScreenCaptureIntent())
                            },
                            onStopPhoneCast = viewModel::stopPhoneCast,
                            onRefreshPcFiles = viewModel::refreshPcFiles,
                            onPickFileToSend = {
                                filePickTarget = FilePickTarget.PC_UPLOAD
                                pickFilesLauncher.launch(arrayOf("*/*"))
                            },
                            onShareAppsToPc = { openAppsPicker(FilePickTarget.PC_UPLOAD) },
                            onDownloadPcFile = { file -> viewModel.downloadFileFromPc(file.name) },
                            onStartPhoneShareHost = viewModel::startPhoneShareHost,
                            onStartLocalHotspot = {
                                val hotspotPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                                } else {
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                                val granted = hotspotPerms.all {
                                    ContextCompat.checkSelfPermission(this@MainActivity, it) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (granted) {
                                    viewModel.startLocalHotspot()
                                } else {
                                    hotspotPermissionLauncher.launch(hotspotPerms)
                                }
                            },
                            onStopLocalHotspot = viewModel::stopLocalHotspot,
                            onOpenHotspotSettings = {
                                runCatching {
                                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                }.onFailure {
                                    runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }
                            },
                            onPickHostFileToShare = {
                                filePickTarget = FilePickTarget.PHONE_HOST_ADD
                                pickFilesLauncher.launch(arrayOf("*/*"))
                            },
                            onShareHostApps = { openAppsPicker(FilePickTarget.PHONE_HOST_ADD) },
                            onRefreshPeerPhoneFiles = viewModel::refreshPeerPhoneFiles,
                            onPickFileForPeerPhone = {
                                filePickTarget = FilePickTarget.PHONE_PEER_SEND
                                pickFilesLauncher.launch(arrayOf("*/*"))
                            },
                            onShareAppsToPeerPhone = { openAppsPicker(FilePickTarget.PHONE_PEER_SEND) },
                            onDownloadFromPeerPhone = { file -> viewModel.downloadFileFromPeerPhone(file.name) },
                            onOpenHistoryFile = { item ->
                                viewModel.openTransferredFile(this@MainActivity, item.localUriString, item.fileName)
                            },
                            onScanTvCompanion = viewModel::startTvCompanionScan,
                            onConnectTvManual = { host, token ->
                                viewModel.connectTvCompanion(host, "Android TV ($host)", token)
                            },
                            onSelectTvCompanion = viewModel::connectTvCompanion,
                            onDisconnectTv = viewModel::disconnectTvCompanion,
                            onRefreshTvFiles = viewModel::refreshTvCompanionFiles,
                            onPickFilesForTv = {
                                filePickTarget = FilePickTarget.TV_COMPANION_UPLOAD
                                pickFilesLauncher.launch(arrayOf("*/*"))
                            },
                            onShareAppsToTv = { openAppsPicker(FilePickTarget.TV_COMPANION_UPLOAD) },
                            onDownloadTvFile = { file -> viewModel.downloadFileFromTv(file.name) },
                            onStartPhoneCastToTv = {
                                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                tvCastLauncher.launch(mgr.createScreenCaptureIntent())
                            },
                            onOpenSystemCastSettings = {
                                runCatching {
                                    startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                                }.onFailure {
                                    runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                                }
                            },
                            onStartTvBrowserHost = viewModel::startTvBrowserShareHost,
                            onAddBrowserDlnaFiles = {
                                filePickTarget = FilePickTarget.PHONE_HOST_ADD
                                pickFilesLauncher.launch(arrayOf("*/*"))
                            },
                            onOpenInTvBrowser = viewModel::openSharedPortalInSmartTvBrowser,
                            onScanDlna = viewModel::startDlnaScan,
                            onSelectDlnaRenderer = viewModel::selectDlnaRenderer,
                            onCastMediaDlna = viewModel::castHostedItemToDlnaTv,
                            onDlnaPlaybackAction = viewModel::dlnaPlaybackCommand,
                            onButtonPress = viewModel::pressButton,
                            onApplianceAction = viewModel::sendApplianceAction,
                            onLaunchApp = viewModel::launchApp,
                            onSendText = viewModel::sendText,
                            onMouseMove = viewModel::moveMouse,
                            onMouseClick = viewModel::clickMouse,
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onClearBanner = viewModel::clearBanner
                        )
                    }
                }
            }
        }
    }
}
