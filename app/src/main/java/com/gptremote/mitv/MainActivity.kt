package com.gptremote.mitv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ProductCatalog
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.ui.BrandPickerScreen
import com.gptremote.mitv.ui.HomeHubScreen
import com.gptremote.mitv.ui.RemoteScreen
import com.gptremote.mitv.ui.RemoteViewModel
import com.gptremote.mitv.ui.components.InstalledAppsPickerDialog
import com.gptremote.mitv.ui.components.InteractiveMirrorSurface
import com.gptremote.mitv.ui.components.QrScannerDialog
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.MiTvRemoteTheme
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val incomingShareUris = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GPT_REMOTE", "Fatal crash on thread ${thread.name}", throwable)
            runCatching {
                val text = "Crash on ${thread.name} at ${System.currentTimeMillis()}:\n${throwable.stackTraceToString()}"
                File(cacheDir, "gpt_crash.txt").writeText(text)
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GptRemote")
                if (dlDir.exists()) {
                    File(dlDir, "gpt_crash.txt").writeText(text)
                }
            }
            prevHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate(savedInstanceState)
        runCatching {
            viewModel.registerActivityResultCaller(this)
        }.onFailure { err ->
            Log.w("MainActivity", "Google Home registration skipped: ${err.message}")
        }
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val pendingShareUris by incomingShareUris.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            MiTvRemoteTheme(accentTheme = uiState.accentTheme) {
                var hasBtPermissions by remember { mutableStateOf(viewModel.hasBluetoothPermissions()) }
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }
                var filePickTarget by remember { mutableStateOf(FilePickTarget.PC_UPLOAD) }
                var appPickerTarget by remember { mutableStateOf<FilePickTarget?>(null) }
                var installedAppsList by remember { mutableStateOf<List<RemoteRepository.ShareableAppInfo>>(emptyList()) }
                var showGlobalQrScanner by remember { mutableStateOf(false) }
                var queuedShareAfterQr by remember { mutableStateOf<List<Uri>>(emptyList()) }
                var isFullscreenMirrorOpen by remember { mutableStateOf(false) }

                val handleQrAndNavigate: (String) -> String = { rawQr ->
                    val kind = viewModel.connectFromQr(rawQr)
                    val targetProduct = when (kind) {
                        "phone" -> ProductType.PHONE_TO_PHONE
                        "tv" -> ProductType.SMART_TV
                        "bulb" -> ProductType.SMART_BULB
                        "pc" -> ProductType.PC_LAPTOP
                        else -> null
                    }
                    if (targetProduct != null) {
                        val currentSameBrand = (route as? AppRoute.Remote)?.brand
                            ?.takeIf { it.productType == targetProduct }
                        val brand = currentSameBrand ?: ProductCatalog.brandsFor(targetProduct).first()
                        viewModel.selectProductBrand(brand)
                        route = AppRoute.Remote(brand)

                        val queued = queuedShareAfterQr
                        if (queued.isNotEmpty()) {
                            queuedShareAfterQr = emptyList()
                            coroutineScope.launch {
                                delay(650L)
                                when (kind) {
                                    "pc" -> viewModel.uploadFilesToPc(queued)
                                    "phone" -> viewModel.sendFilesToPeerPhone(queued)
                                    "tv" -> viewModel.uploadFilesToTv(queued)
                                }
                            }
                        }
                    }
                    kind
                }

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

                if (showGlobalQrScanner) {
                    QrScannerDialog(
                        title = "Scan Any Device QR",
                        subtitle = "Scan PC Companion, Android TV App, Phone Share, or Smart Bulb QR",
                        onScanned = { rawQr ->
                            showGlobalQrScanner = false
                            handleQrAndNavigate(rawQr)
                        },
                        onDismiss = {
                            showGlobalQrScanner = false
                            queuedShareAfterQr = emptyList()
                        }
                    )
                }

                if (pendingShareUris.isNotEmpty()) {
                    ShareIncomingFilesDialog(
                        uris = pendingShareUris,
                        pcName = uiState.connectedWifiPcDevice?.name,
                        pcHost = uiState.connectedWifiPcDevice?.host ?: viewModel.getConnectedPcHost(),
                        peerPhoneName = uiState.peerPhoneName,
                        tvCompanionName = uiState.connectedTvCompanion?.name,
                        onSendToPc = {
                            val uris = pendingShareUris
                            incomingShareUris.value = emptyList()
                            viewModel.uploadFilesToPc(uris)
                            val pcBrand = ProductCatalog.brandsFor(ProductType.PC_LAPTOP).first()
                            viewModel.selectProductBrand(pcBrand)
                            route = AppRoute.Remote(pcBrand)
                        },
                        onSendToPhone = {
                            val uris = pendingShareUris
                            incomingShareUris.value = emptyList()
                            val phoneBrand = ProductCatalog.brandsFor(ProductType.PHONE_TO_PHONE).first()
                            viewModel.selectProductBrand(phoneBrand)
                            viewModel.sendFilesToPeerPhone(uris)
                            route = AppRoute.Remote(phoneBrand)
                        },
                        onSendToTvCompanion = {
                            val uris = pendingShareUris
                            incomingShareUris.value = emptyList()
                            val tvBrand = ProductCatalog.brandsFor(ProductType.SMART_TV).first()
                            viewModel.selectProductBrand(tvBrand)
                            viewModel.uploadFilesToTv(uris)
                            route = AppRoute.Remote(tvBrand)
                        },
                        onScanQrAndSend = {
                            queuedShareAfterQr = pendingShareUris
                            incomingShareUris.value = emptyList()
                            showGlobalQrScanner = true
                        },
                        onDismiss = { incomingShareUris.value = emptyList() }
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
                            onOpenActiveSession = { session ->
                                val brand = viewModel.openActiveSession(session)
                                route = AppRoute.Remote(brand)
                            },
                            onDisconnectSession = viewModel::disconnectSession,
                            onScanQrCode = { showGlobalQrScanner = true },
                            onRequestHostQr = viewModel::startPhoneShareHost,
                            onDisconnect = viewModel::disconnect,
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onClearBanner = viewModel::clearBanner,
                            onSignInGoogleHome = { viewModel.signInGoogleHome() },
                            onRefreshGoogleHome = viewModel::refreshGoogleHomeLights,
                            onDisconnectGoogleHome = viewModel::disconnectGoogleHome
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
                                handleQrAndNavigate(rawQr)
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
                            onOpenFullscreenMirror = { isFullscreenMirrorOpen = true },
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
                            onPausePcTransfer = viewModel::pausePcTransfer,
                            onResumePcTransfer = viewModel::resumePcTransfer,
                            onCancelPcTransfer = viewModel::cancelPcTransfer,
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
                            onDisconnectPhonePeer = viewModel::disconnectPhonePeer,
                            onRefreshPeerPhoneFiles = viewModel::refreshPeerPhoneFiles,
                            onPickFileForPeerPhone = {
                                filePickTarget = FilePickTarget.PHONE_PEER_SEND
                                pickFilesLauncher.launch(arrayOf("*/*"))
                            },
                            onShareAppsToPeerPhone = { openAppsPicker(FilePickTarget.PHONE_PEER_SEND) },
                            onDownloadFromPeerPhone = { file -> viewModel.downloadFileFromPeerPhone(file.name) },
                            onPausePhoneTransfer = viewModel::pausePhoneTransfer,
                            onResumePhoneTransfer = viewModel::resumePhoneTransfer,
                            onCancelPhoneTransfer = viewModel::cancelPhoneTransfer,
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
                            onPauseTvTransfer = viewModel::pauseTvTransfer,
                            onResumeTvTransfer = viewModel::resumeTvTransfer,
                            onCancelTvTransfer = viewModel::cancelTvTransfer,
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
                            onCloseApp = viewModel::closeApp,
                            onSendText = viewModel::sendText,
                            onMouseMove = viewModel::moveMouse,
                            onMouseClick = viewModel::clickMouse,
                            onSelectHapticStrength = viewModel::setHapticStrength,
                            onToggleSound = viewModel::setSoundEnabled,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onClearBanner = viewModel::clearBanner,
                            onOpenActiveSession = { session ->
                                val brand = viewModel.openActiveSession(session)
                                route = AppRoute.Remote(brand)
                            },
                            onDisconnectSession = viewModel::disconnectSession,
                            onSignInGoogleHome = { viewModel.signInGoogleHome() },
                            onRefreshGoogleHome = viewModel::refreshGoogleHomeLights,
                            onDisconnectGoogleHome = viewModel::disconnectGoogleHome
                        )
                    }
                }

                // True edge-to-edge fullscreen landscape overlay (replaces cramped Dialog)
                val fullBmp = uiState.pcScreenBitmap
                if (isFullscreenMirrorOpen && fullBmp != null) {
                    BackHandler { isFullscreenMirrorOpen = false }
                    DisposableEffect(Unit) {
                        val prevOrientation = requestedOrientation
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        onDispose {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                            requestedOrientation = prevOrientation
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val aspect = (fullBmp.width.toFloat() / fullBmp.height.coerceAtLeast(1)).coerceIn(0.6f, 2.5f)
                        InteractiveMirrorSurface(
                            bitmap = fullBmp,
                            aspect = aspect,
                            onTap = viewModel::sendPcScreenTap,
                            onMove = viewModel::sendPcScreenMove,
                            modifier = Modifier.fillMaxSize()
                        )

                        FilledTonalIconButton(
                            onClick = { isFullscreenMirrorOpen = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.72f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Exit Fullscreen")
                        }

                        var showFullscreenHint by remember { mutableStateOf(true) }
                        LaunchedEffect(Unit) {
                            delay(3000L)
                            showFullscreenHint = false
                        }
                        if (showFullscreenHint) {
                            Text(
                                text = "Tap = Click • Drag = Move Cursor • Back = Exit",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.58f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                } else if (isFullscreenMirrorOpen && fullBmp == null) {
                    isFullscreenMirrorOpen = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconnectPcIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val extracted = linkedSetOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (streamUri != null) extracted.add(streamUri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list?.forEach { extracted.add(it) }
            }
        }
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { extracted.add(it) }
            }
        }
        if (extracted.isNotEmpty()) {
            incomingShareUris.value = extracted.toList()
        }
    }
}

@Composable
private fun ShareIncomingFilesDialog(
    uris: List<Uri>,
    pcName: String?,
    pcHost: String?,
    peerPhoneName: String?,
    tvCompanionName: String?,
    onSendToPc: () -> Unit,
    onSendToPhone: () -> Unit,
    onSendToTvCompanion: () -> Unit,
    onScanQrAndSend: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = MiOrange
    val hasAnyConnected = pcHost != null || tvCompanionName != null || peerPhoneName != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RemoteSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Share ${uris.size} File${if (uris.size > 1) "s" else ""}",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (hasAnyConnected) {
                        "Tap a connected device or scan a QR code"
                    } else {
                        "Scan any PC, TV, or Phone QR code to send"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pcHost != null) {
                    ShareTargetRow(
                        icon = Icons.Rounded.Laptop,
                        title = pcName?.takeIf { it.isNotBlank() } ?: "PC ($pcHost)",
                        subtitle = "Connected • Upload to PC ($pcHost)",
                        tint = WifiBlue,
                        onClick = onSendToPc
                    )
                }
                if (tvCompanionName != null) {
                    ShareTargetRow(
                        icon = Icons.Rounded.Tv,
                        title = tvCompanionName,
                        subtitle = "Connected • Upload to Android TV",
                        tint = ConnectedGreen,
                        onClick = onSendToTvCompanion
                    )
                }
                if (peerPhoneName != null) {
                    ShareTargetRow(
                        icon = Icons.Rounded.PhonelinkSetup,
                        title = peerPhoneName,
                        subtitle = "Connected • Send to Peer Phone",
                        tint = ConnectedGreen,
                        onClick = onSendToPhone
                    )
                }
                ShareTargetRow(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = "Scan QR Code & Send",
                    subtitle = "Scan PC, TV, or Phone QR to connect & send",
                    tint = accent,
                    onClick = onScanQrAndSend
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun ShareTargetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}
