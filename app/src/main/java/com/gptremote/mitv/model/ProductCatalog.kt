package com.gptremote.mitv.model

import androidx.compose.ui.graphics.Color

/**
 * Supported product categories shown on the clean Home Screen.
 */
enum class ProductType(
    val title: String,
    val subtitle: String,
    val supportsBluetoothHid: Boolean,
    val supportsBluetoothGeneric: Boolean,
    val supportsWifiTv: Boolean,
    val supportsWifiPc: Boolean,
    val supportsWifiSmart: Boolean,
    val supportsIr: Boolean,
    val supportsAppLaunch: Boolean
) {
    SMART_TV(
        title = "Smart TV",
        subtitle = "Mi, Samsung, LG, Sony",
        supportsBluetoothHid = true,
        supportsBluetoothGeneric = true,
        supportsWifiTv = true,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = true
    ),
    PC_LAPTOP(
        title = "PC / Laptop",
        subtitle = "Windows, Mac, Linux",
        supportsBluetoothHid = true,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = true,
        supportsWifiSmart = false,
        supportsIr = false,
        supportsAppLaunch = true
    ),
    AC(
        title = "Air Conditioner",
        subtitle = "Split, Window & Inverter AC",
        supportsBluetoothHid = false,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    FRIDGE(
        title = "Refrigerator",
        subtitle = "Smart Wi-Fi & BT Fridges",
        supportsBluetoothHid = false,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    WASHING_MACHINE(
        title = "Washing Machine",
        subtitle = "Wi-Fi & Bluetooth Washers",
        supportsBluetoothHid = false,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    SET_TOP_BOX(
        title = "Set-Top Box",
        subtitle = "DTH, Cable & Streaming",
        supportsBluetoothHid = true,
        supportsBluetoothGeneric = true,
        supportsWifiTv = true,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = true
    ),
    SOUNDBAR(
        title = "Soundbar / Audio",
        subtitle = "Home Theater & Speakers",
        supportsBluetoothHid = true,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    PROJECTOR(
        title = "Projector",
        subtitle = "Smart & Home Projectors",
        supportsBluetoothHid = true,
        supportsBluetoothGeneric = true,
        supportsWifiTv = true,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = true
    ),
    CEILING_FAN(
        title = "Ceiling Fan",
        subtitle = "Smart & IR Fans",
        supportsBluetoothHid = false,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    SMART_BULB(
        title = "Smart Bulb",
        subtitle = "Wipro, Philips WiZ, Mi, Hue",
        supportsBluetoothHid = false,
        supportsBluetoothGeneric = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsWifiSmart = true,
        supportsIr = true,
        supportsAppLaunch = false
    )
}

data class BrandOption(
    val id: String,
    val name: String,
    val productType: ProductType,
    val note: String,
    val supportsAppLaunch: Boolean = productType.supportsAppLaunch,
    val supportsWifiTv: Boolean = productType.supportsWifiTv
)

data class LaunchableApp(
    val id: String,
    val name: String,
    val shortBadge: String,
    val badgeColor: Color,
    val tvAppLink: String? = null,
    val pcAppKey: String? = null,
    val pcFallbackCommand: String? = null
)

object ProductCatalog {

    fun brandsFor(product: ProductType): List<BrandOption> = when (product) {
        ProductType.SMART_TV -> listOf(
            BrandOption("tv_xiaomi", "Xiaomi / Mi TV", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_samsung", "Samsung", product, "Bluetooth • Wi-Fi Tizen • IR"),
            BrandOption("tv_lg", "LG", product, "Bluetooth • Wi-Fi webOS • IR"),
            BrandOption("tv_sony", "Sony Bravia", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_tcl", "TCL", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_hisense", "Hisense", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_oneplus", "OnePlus TV", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_realme", "Realme TV", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_vu", "Vu", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_panasonic", "Panasonic", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_philips", "Philips", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("tv_other", "Other Android TV", product, "Bluetooth • Wi-Fi • IR")
        )

        ProductType.PC_LAPTOP -> listOf(
            BrandOption("pc_windows", "Windows PC / Laptop", product, "Bluetooth HID • Wi-Fi PC App"),
            BrandOption("pc_mac", "MacBook / iMac", product, "Bluetooth HID • Wi-Fi PC App"),
            BrandOption("pc_linux", "Linux Desktop", product, "Bluetooth HID • Wi-Fi PC App")
        )

        ProductType.AC -> listOf(
            BrandOption("ac_daikin", "Daikin", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_lg", "LG", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_samsung", "Samsung", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_voltas", "Voltas", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_bluestar", "Blue Star", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_lloyd", "Lloyd", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_ogeneral", "O General", product, "IR AC Remote"),
            BrandOption("ac_mitsubishi", "Mitsubishi", product, "IR AC Remote"),
            BrandOption("ac_hitachi", "Hitachi", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_panasonic", "Panasonic", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_carrier", "Carrier", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_haier", "Haier", product, "Wi-Fi • IR AC Remote"),
            BrandOption("ac_whirlpool", "Whirlpool", product, "IR AC Remote"),
            BrandOption("ac_godrej", "Godrej", product, "IR AC Remote"),
            BrandOption("ac_ifb", "IFB", product, "IR AC Remote"),
            BrandOption("ac_midea", "Midea / Gree", product, "Wi-Fi • IR AC Remote")
        )

        ProductType.FRIDGE -> listOf(
            BrandOption("fr_lg", "LG", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("fr_samsung", "Samsung", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("fr_whirlpool", "Whirlpool", product, "Wi-Fi • IR Smart Fridge"),
            BrandOption("fr_haier", "Haier", product, "Wi-Fi • IR Smart Fridge"),
            BrandOption("fr_godrej", "Godrej", product, "Wi-Fi • IR Smart Fridge"),
            BrandOption("fr_bosch", "Bosch", product, "Wi-Fi Home Connect"),
            BrandOption("fr_panasonic", "Panasonic", product, "Wi-Fi • IR Smart Fridge"),
            BrandOption("fr_hitachi", "Hitachi", product, "Wi-Fi • IR Smart Fridge")
        )

        ProductType.WASHING_MACHINE -> listOf(
            BrandOption("wm_lg", "LG", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("wm_samsung", "Samsung", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("wm_ifb", "IFB", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("wm_bosch", "Bosch", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("wm_whirlpool", "Whirlpool", product, "Wi-Fi • IR Washer"),
            BrandOption("wm_haier", "Haier", product, "Wi-Fi • IR Washer"),
            BrandOption("wm_siemens", "Siemens", product, "Wi-Fi • Home Connect"),
            BrandOption("wm_panasonic", "Panasonic", product, "Wi-Fi • IR Washer")
        )

        ProductType.SET_TOP_BOX -> listOf(
            BrandOption("stb_tataplay", "Tata Play", product, "IR DTH Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_airtel", "Airtel Xstream", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("stb_jio", "Jio Set-Top Box", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("stb_dishtv", "Dish TV", product, "IR DTH Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_sundirect", "Sun Direct", product, "IR DTH Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_firetv", "Amazon Fire TV", product, "Bluetooth HID", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_appletv", "Apple TV", product, "Bluetooth HID", supportsAppLaunch = false, supportsWifiTv = false)
        )

        ProductType.SOUNDBAR -> listOf(
            BrandOption("sb_sony", "Sony", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("sb_jbl", "JBL", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("sb_samsung", "Samsung", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("sb_lg", "LG", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("sb_bose", "Bose", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("sb_boat", "boAt", product, "Bluetooth • IR"),
            BrandOption("sb_yamaha", "Yamaha", product, "Bluetooth • Wi-Fi • IR")
        )

        ProductType.PROJECTOR -> listOf(
            BrandOption("pj_xgimi", "XGIMI", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("pj_wanbo", "Wanbo", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("pj_nebula", "Anker Nebula", product, "Bluetooth • Wi-Fi • IR"),
            BrandOption("pj_epson", "Epson", product, "Wi-Fi • IR Projector", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("pj_benq", "BenQ", product, "Wi-Fi • IR Projector", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("pj_viewsonic", "ViewSonic", product, "Wi-Fi • IR Projector", supportsAppLaunch = false, supportsWifiTv = false)
        )

        ProductType.CEILING_FAN -> listOf(
            BrandOption("fan_atomberg", "Atomberg", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("fan_havells", "Havells", product, "Wi-Fi • IR Fan"),
            BrandOption("fan_crompton", "Crompton", product, "Wi-Fi • IR Fan"),
            BrandOption("fan_orient", "Orient", product, "Wi-Fi • IR Fan"),
            BrandOption("fan_dyson", "Dyson", product, "Wi-Fi • IR Fan"),
            BrandOption("fan_xiaomi", "Xiaomi", product, "Wi-Fi • IR Fan")
        )

        ProductType.SMART_BULB -> listOf(
            BrandOption("bulb_wipro", "Wipro Next Smart", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("bulb_wiz", "Philips WiZ / Hue", product, "Wi-Fi LAN • Bluetooth"),
            BrandOption("bulb_yeelight", "Mi / Yeelight", product, "Wi-Fi LAN • Bluetooth"),
            BrandOption("bulb_syska", "Syska Smart", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("bulb_tapo", "TP-Link Tapo", product, "Wi-Fi • Smart Light"),
            BrandOption("bulb_havells", "Havells Smart", product, "Wi-Fi • Bluetooth • IR"),
            BrandOption("bulb_generic", "Other Smart / IR Bulb", product, "Wi-Fi • IR RGB Bulb")
        )
    }

    val tvApps: List<LaunchableApp> = listOf(
        LaunchableApp("youtube", "YouTube", "YT", Color(0xFFFF0000), tvAppLink = "https://www.youtube.com"),
        LaunchableApp("netflix", "Netflix", "N", Color(0xFFE50914), tvAppLink = "https://www.netflix.com"),
        LaunchableApp("prime", "Prime Video", "P", Color(0xFF00A8E1), tvAppLink = "https://app.primevideo.com"),
        LaunchableApp("hotstar", "Hotstar", "H★", Color(0xFF0F79AF), tvAppLink = "https://www.hotstar.com"),
        LaunchableApp("sonyliv", "SonyLIV", "LIV", Color(0xFF7C3AED), tvAppLink = "https://www.sonyliv.com"),
        LaunchableApp("zee5", "ZEE5", "Z5", Color(0xFF9333EA), tvAppLink = "https://www.zee5.com"),
        LaunchableApp("jiocinema", "JioCinema", "JC", Color(0xFFD9008D), tvAppLink = "https://www.jiocinema.com"),
        LaunchableApp("spotify", "Spotify", "♫", Color(0xFF1DB954), tvAppLink = "https://open.spotify.com"),
        LaunchableApp("mxplayer", "MX Player", "MX", Color(0xFF2563EB), tvAppLink = "https://www.mxplayer.in"),
        LaunchableApp("playstore", "Play Store", "▶", Color(0xFF10B981), tvAppLink = "https://play.google.com/store")
    )

    val pcApps: List<LaunchableApp> = listOf(
        LaunchableApp("browser", "Browser", "🌐", Color(0xFF3B82F6), pcAppKey = "browser", pcFallbackCommand = "https://www.google.com"),
        LaunchableApp("youtube", "YouTube", "YT", Color(0xFFFF0000), pcAppKey = "youtube", pcFallbackCommand = "https://www.youtube.com"),
        LaunchableApp("netflix", "Netflix", "N", Color(0xFFE50914), pcAppKey = "netflix", pcFallbackCommand = "https://www.netflix.com"),
        LaunchableApp("files", "File Explorer", "📁", Color(0xFFF59E0B), pcAppKey = "files", pcFallbackCommand = "explorer"),
        LaunchableApp("calc", "Calculator", "=", Color(0xFF10B981), pcAppKey = "calc", pcFallbackCommand = "calc"),
        LaunchableApp("terminal", "Terminal", ">_", Color(0xFF64748B), pcAppKey = "terminal", pcFallbackCommand = "cmd"),
        LaunchableApp("taskmgr", "Task Manager", "⚡", Color(0xFF8B5CF6), pcAppKey = "taskmgr", pcFallbackCommand = "taskmgr"),
        LaunchableApp("settings", "Settings", "⚙", Color(0xFF06B6D4), pcAppKey = "settings", pcFallbackCommand = "ms-settings:")
    )
}
