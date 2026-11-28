package com.gptremote.mitv.wifi

import android.content.Context
import androidx.activity.ComponentActivity
import com.google.home.ConsentScreenOptions
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.FactoryRegistry
import com.google.home.ForcePermissionFlow
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import com.google.home.Trait
import com.google.home.TraitFactory
import com.google.home.google.ExtendedColorControl
import com.google.home.google.ExtendedLevelControl
import com.google.home.google.LightEffects
import com.google.home.matter.standard.BasicInformation
import com.google.home.matter.standard.ColorControl
import com.google.home.matter.standard.ColorControlTrait
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import android.util.Log

/**
 * Integrates Google Home Android APIs SDK (play-services-home 17.12.0) to discover and control
 * smart bulbs and lights linked in the user's Google Home (such as Wipro Next, Philips Hue, TP-Link, Matter bulbs).
 */
class GoogleHomeClient(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _statusText = MutableStateFlow("Not connected")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _accountHint = MutableStateFlow<String?>(null)
    val accountHint: StateFlow<String?> = _accountHint.asStateFlow()

    private val _homeLights = MutableStateFlow<List<DiscoveredSmartBulb>>(emptyList())
    val homeLights: StateFlow<List<DiscoveredSmartBulb>> = _homeLights.asStateFlow()

    private data class LightHandle(
        val device: HomeDevice,
        val onOff: OnOff?,
        val levelControl: LevelControl?,
        val colorControl: ColorControl?,
        val extendedColorControl: ExtendedColorControl?
    )

    private val handles = ConcurrentHashMap<String, LightHandle>()
    private var permissionObserverJob: Job? = null

    private val client: HomeClient? by lazy {
        runCatching {
            val types: List<DeviceTypeFactory<out DeviceType>> = listOf(
                ExtendedColorLightDevice,
                ColorTemperatureLightDevice,
                DimmableLightDevice,
                OnOffLightDevice,
                OnOffPluginUnitDevice
            )
            val traits: List<TraitFactory<out Trait>> = listOf(
                OnOff,
                LevelControl,
                ColorControl,
                BasicInformation,
                ExtendedColorControl,
                ExtendedLevelControl,
                LightEffects
            )
            val registry = FactoryRegistry(
                traits = traits,
                types = types
            )
            val handler = CoroutineExceptionHandler { _, err ->
                Log.w("GoogleHomeClient", "Home SDK background exception suppressed: ${err.message}")
            }
            val config = HomeConfig(
                coroutineContext = SupervisorJob() + Dispatchers.IO + handler,
                factoryRegistry = registry,
                homePlatformScope = HomeConfig.HomePlatformScope.HOME_PLATFORM_SCOPE_VERSION_1
            )
            Home.getClient(appContext, homeConfig = config)
        }.getOrElse { err ->
            Log.w("GoogleHomeClient", "Google Home SDK unavailable: ${err.message}")
            null
        }
    }

    fun registerActivityResultCaller(activity: ComponentActivity) {
        runCatching {
            val homeClient = client ?: run {
                _statusText.value = "Google Play services for Home unavailable"
                return
            }
            homeClient.registerActivityResultCallerForPermissions(activity)
        }.onFailure { err ->
            Log.w("GoogleHomeClient", "Failed to register activity result caller: ${err.message}")
            _statusText.value = "Google Home unavailable"
        }
    }

    private fun startPermissionObserverIfNeeded() {
        if (permissionObserverJob?.isActive == true) return
        val homeClient = client ?: return
        permissionObserverJob = scope.launch {
            runCatching {
                homeClient.hasPermissions().collectLatest { state ->
                    when (state) {
                        PermissionsState.GRANTED -> {
                            _isSignedIn.value = true
                            _statusText.value = "Connected • Syncing lights..."
                            refreshLightsInternal()
                        }
                        PermissionsState.NOT_GRANTED -> {
                            _isSignedIn.value = false
                            _statusText.value = "Not signed in"
                            _accountHint.value = null
                            handles.clear()
                            _homeLights.value = emptyList()
                        }
                        PermissionsState.PERMISSIONS_STATE_UNAVAILABLE -> {
                            _isSignedIn.value = false
                            _statusText.value = "Google Home unavailable on device"
                        }
                        PermissionsState.PERMISSIONS_STATE_UNINITIALIZED -> Unit
                    }
                }
            }.onFailure { err ->
                _statusText.value = err.message ?: "Google Home check failed"
            }
        }
    }

    fun signInAndRequestPermissions(onComplete: (String) -> Unit) {
        startPermissionObserverIfNeeded()
        val homeClient = client ?: run {
            onComplete("Google Home SDK is unavailable on this device (update Google Play services).")
            return
        }
        scope.launch {
            _statusText.value = "Opening Google Home permission sheet..."
            runCatching {
                val res = homeClient.requestPermissions(
                    ForcePermissionFlow.FORCE_LAUNCH,
                    ConsentScreenOptions()
                )
                if (res.status == PermissionsResultStatus.SUCCESS) {
                    _isSignedIn.value = true
                    runCatching { homeClient.syncLinkedDevices() }
                    val count = refreshLightsInternal()
                    onComplete(
                        if (count > 0) "Google Home connected ($count light/switch device(s) synced)!"
                        else "Google Home connected! Make sure bulbs are linked in the Google Home app."
                    )
                } else {
                    val reason = res.errorMessage?.takeIf { it.isNotBlank() } ?: res.status.name
                    _statusText.value = if (_isSignedIn.value) "Connected" else "Sign-in cancelled ($reason)"
                    onComplete("Google Home sign-in: $reason")
                }
            }.onFailure { err ->
                val msg = err.message ?: "Permission flow failed"
                _statusText.value = msg
                onComplete("Google Home sign-in failed: $msg")
            }
        }
    }

    fun refreshLights(onComplete: ((String) -> Unit)? = null) {
        scope.launch {
            if (!_isSignedIn.value) {
                onComplete?.invoke("Sign in with Google Home in Settings first.")
                return@launch
            }
            val count = refreshLightsInternal()
            onComplete?.invoke(
                if (count > 0) "Found $count Google Home light(s)."
                else "No light devices found in Google Home. Link Wipro Next in the Google Home app first."
            )
        }
    }

    fun disconnectSession() {
        _isSignedIn.value = false
        _accountHint.value = null
        _statusText.value = "Disconnected in app (tap Sign In to reconnect)"
        handles.clear()
        _homeLights.value = emptyList()
    }

    private suspend fun refreshLightsInternal(): Int {
        val homeClient = client ?: return 0
        return runCatching {
            runCatching { withTimeoutOrNull(4000L) { homeClient.syncLinkedDevices() } }
            val structures = withTimeoutOrNull(4000L) { homeClient.structures().list() }.orEmpty()
            if (structures.isNotEmpty()) {
                _accountHint.value = structures.joinToString(", ") { it.name.ifBlank { "Home" } }
            }
            // filterToSupported = false so partner cloud-to-cloud bulbs (like Wipro, Tuya) are not filtered out
            val devices = withTimeoutOrNull(7000L) { homeClient.devices(false).list() }.orEmpty()
            val foundList = mutableListOf<DiscoveredSmartBulb>()
            handles.clear()
            for (dev in devices) {
                val typesSet = withTimeoutOrNull(3500L) { dev.types().firstOrNull() }.orEmpty()
                val match = extractLightHandle(dev, typesSet)
                if (match != null) {
                    val devId = dev.id.id
                    handles[devId] = match
                    val label = dev.name.ifBlank { "Google Home Light" }
                    foundList += DiscoveredSmartBulb(
                        host = "ghome://$devId",
                        name = "$label (Google Home)",
                        protocol = BulbProtocol.GOOGLE_HOME,
                        deviceId = devId
                    )
                }
            }
            _homeLights.value = foundList
            _statusText.value = if (foundList.isNotEmpty()) {
                "Connected • ${foundList.size} light(s) synced"
            } else {
                "Connected • 0 lights in home"
            }
            foundList.size
        }.getOrElse { err ->
            _statusText.value = "Sync error: ${err.message ?: "retry"}"
            0
        }
    }

    private fun extractLightHandle(device: HomeDevice, types: Set<DeviceType>): LightHandle? {
        var matchedType: DeviceType? = null
        for (t in types) {
            val f = t.factory
            if (f == ExtendedColorLightDevice ||
                f == ColorTemperatureLightDevice ||
                f == DimmableLightDevice ||
                f == OnOffLightDevice
            ) {
                matchedType = t
                break
            }
            if (f == OnOffPluginUnitDevice && matchedType == null) {
                matchedType = t
            }
        }
        val t = matchedType ?: types.firstOrNull { it.trait(OnOff) != null } ?: types.firstOrNull()
        if (t != null) {
            val onOff = t.trait(OnOff)
            val level = t.trait(LevelControl)
            val color = t.trait(ColorControl)
            val extColor = t.trait(ExtendedColorControl)
            if (onOff != null || level != null || color != null || extColor != null) {
                return LightHandle(
                    device = device,
                    onOff = onOff,
                    levelControl = level,
                    colorControl = color,
                    extendedColorControl = extColor
                )
            }
        }
        val nameLower = device.name.lowercase()
        if (nameLower.contains("light") || nameLower.contains("bulb") || nameLower.contains("lamp") || nameLower.contains("wipro")) {
            val firstType = types.firstOrNull()
            return LightHandle(
                device = device,
                onOff = firstType?.trait(OnOff),
                levelControl = firstType?.trait(LevelControl),
                colorControl = firstType?.trait(ColorControl),
                extendedColorControl = firstType?.trait(ExtendedColorControl)
            )
        }
        return null
    }

    suspend fun applyLightState(
        deviceId: String,
        power: Boolean? = null,
        brightness: Int? = null,
        rgb: Triple<Int, Int, Int>? = null,
        tempKelvin: Int? = null
    ): Result<Unit> = runCatching {
        var handle = handles[deviceId]
        if (handle == null) {
            refreshLightsInternal()
            handle = handles[deviceId]
        }
        var target = handle ?: error("Google Home device not ready. Tap Sync in Settings.")
        if (target.onOff == null) {
            val freshTypes = runCatching { withTimeoutOrNull(2500L) { target.device.types().firstOrNull() } }.getOrNull().orEmpty()
            val freshOnOff = freshTypes.firstNotNullOfOrNull { it.trait(OnOff) }
            val freshLevel = freshTypes.firstNotNullOfOrNull { it.trait(LevelControl) }
            val freshColor = freshTypes.firstNotNullOfOrNull { it.trait(ColorControl) }
            if (freshOnOff != null || freshLevel != null || freshColor != null) {
                target = target.copy(
                    onOff = freshOnOff ?: target.onOff,
                    levelControl = freshLevel ?: target.levelControl,
                    colorControl = freshColor ?: target.colorControl
                )
                handles[deviceId] = target
            }
        }
        if (power == true) {
            target.onOff?.on()
        } else if (power == false) {
            target.onOff?.off()
            return@runCatching
        }
        if (brightness != null) {
            val lvl = ((brightness.coerceIn(1, 100) * 254) / 100).coerceIn(1, 254).toUByte()
            target.levelControl?.moveToLevelWithOnOff(
                level = lvl,
                transitionTime = 2u,
                optionsMask = LevelControlTrait.OptionsBitmap(),
                optionsOverride = LevelControlTrait.OptionsBitmap()
            )
        }
        if (rgb != null) {
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                rgb.first.coerceIn(0, 255),
                rgb.second.coerceIn(0, 255),
                rgb.third.coerceIn(0, 255),
                hsv
            )
            val colorTrait = target.colorControl
            if (colorTrait != null) {
                val hue254 = ((hsv[0] / 360f) * 254f).roundToInt().coerceIn(0, 254).toUByte()
                val sat254 = (hsv[1] * 254f).roundToInt().coerceIn(0, 254).toUByte()
                colorTrait.moveToHueAndSaturation(
                    hue = hue254,
                    saturation = sat254,
                    transitionTime = 2u,
                    optionsMask = ColorControlTrait.OptionsBitmap(),
                    optionsOverride = ColorControlTrait.OptionsBitmap()
                )
            } else {
                target.extendedColorControl?.moveToColorHsv(hsv[0], hsv[1], hsv[2].coerceAtLeast(0.2f))
            }
        }
        if (tempKelvin != null) {
            val mireds = (1_000_000 / tempKelvin.coerceIn(2000, 6500)).coerceIn(153, 500).toUShort()
            target.colorControl?.moveToColorTemperature(
                colorTemperatureMireds = mireds,
                transitionTime = 2u,
                optionsMask = ColorControlTrait.OptionsBitmap(),
                optionsOverride = ColorControlTrait.OptionsBitmap()
            )
        }
    }
}
