package com.gptremote.mitv.model

/**
 * Supported product categories shown on the clean Home Screen.
 */
enum class ProductType(
    val title: String,
    val subtitle: String,
    val supportsBluetoothHid: Boolean,
    val supportsWifiTv: Boolean,
    val supportsWifiPc: Boolean,
    val supportsIr: Boolean,
    val supportsAppLaunch: Boolean
) {
    SMART_TV(
        title = "Smart TV",
        subtitle = "Android TV, Mi, Samsung, LG",
        supportsBluetoothHid = true,
        supportsWifiTv = true,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = true
    ),
    PC_LAPTOP(
        title = "PC / Laptop",
        subtitle = "Windows, Mac, Linux",
        supportsBluetoothHid = true,
        supportsWifiTv = false,
        supportsWifiPc = true,
        supportsIr = false,
        supportsAppLaunch = true
    ),
    AC(
        title = "Air Conditioner",
        subtitle = "Split & Window AC",
        supportsBluetoothHid = false,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    FRIDGE(
        title = "Refrigerator",
        subtitle = "Smart Fridge Controls",
        supportsBluetoothHid = false,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    WASHING_MACHINE(
        title = "Washing Machine",
        subtitle = "Wash Cycles & Spin",
        supportsBluetoothHid = false,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    SET_TOP_BOX(
        title = "Set-Top Box",
        subtitle = "DTH, Cable & Streaming",
        supportsBluetoothHid = true,
        supportsWifiTv = true,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = true
    ),
    SOUNDBAR(
        title = "Soundbar / Audio",
        subtitle = "Home Theater & Speakers",
        supportsBluetoothHid = true,
        supportsWifiTv = false,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = false
    ),
    PROJECTOR(
        title = "Projector",
        subtitle = "Smart & Home Projectors",
        supportsBluetoothHid = true,
        supportsWifiTv = true,
        supportsWifiPc = false,
        supportsIr = true,
        supportsAppLaunch = true
    ),
    CEILING_FAN(
        title = "Ceiling Fan",
        subtitle = "Smart & IR Fans",
        supportsBluetoothHid = false,
        supportsWifiTv = false,
        supportsWifiPc = false,
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
    val tvAppLink: String? = null,
    val pcAppKey: String? = null,
    val pcFallbackCommand: String? = null
)

object ProductCatalog {

    fun brandsFor(product: ProductType): List<BrandOption> = when (product) {
        ProductType.SMART_TV -> listOf(
            BrandOption("tv_xiaomi", "Xiaomi / Mi TV", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_samsung", "Samsung", product, "Bluetooth HID • IR Blaster", supportsWifiTv = false),
            BrandOption("tv_lg", "LG", product, "Bluetooth HID • IR Blaster", supportsWifiTv = false),
            BrandOption("tv_sony", "Sony Bravia", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_tcl", "TCL", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_hisense", "Hisense", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_oneplus", "OnePlus TV", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_realme", "Realme TV", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_vu", "Vu", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_panasonic", "Panasonic", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_philips", "Philips", product, "Bluetooth HID • Wi-Fi Android TV • IR"),
            BrandOption("tv_other", "Other Android TV", product, "Bluetooth HID • Wi-Fi Android TV • IR")
        )

        ProductType.PC_LAPTOP -> listOf(
            BrandOption("pc_windows", "Windows PC / Laptop", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_mac", "MacBook / iMac", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_linux", "Linux Desktop", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_dell", "Dell", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_hp", "HP", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_lenovo", "Lenovo", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_asus", "Asus", product, "Bluetooth HID • Wi-Fi Receiver"),
            BrandOption("pc_acer", "Acer", product, "Bluetooth HID • Wi-Fi Receiver")
        )

        ProductType.AC -> listOf(
            BrandOption("ac_daikin", "Daikin", product, "IR AC Remote"),
            BrandOption("ac_lg", "LG", product, "IR AC Remote"),
            BrandOption("ac_samsung", "Samsung", product, "IR AC Remote"),
            BrandOption("ac_voltas", "Voltas", product, "IR AC Remote"),
            BrandOption("ac_bluestar", "Blue Star", product, "IR AC Remote"),
            BrandOption("ac_lloyd", "Lloyd", product, "IR AC Remote"),
            BrandOption("ac_hitachi", "Hitachi", product, "IR AC Remote"),
            BrandOption("ac_panasonic", "Panasonic", product, "IR AC Remote"),
            BrandOption("ac_carrier", "Carrier", product, "IR AC Remote"),
            BrandOption("ac_haier", "Haier", product, "IR AC Remote"),
            BrandOption("ac_whirlpool", "Whirlpool", product, "IR AC Remote"),
            BrandOption("ac_midea", "Midea / Gree", product, "IR AC Remote")
        )

        ProductType.FRIDGE -> listOf(
            BrandOption("fr_lg", "LG", product, "Smart Fridge Layout"),
            BrandOption("fr_samsung", "Samsung", product, "Smart Fridge Layout"),
            BrandOption("fr_whirlpool", "Whirlpool", product, "Smart Fridge Layout"),
            BrandOption("fr_haier", "Haier", product, "Smart Fridge Layout"),
            BrandOption("fr_godrej", "Godrej", product, "Smart Fridge Layout"),
            BrandOption("fr_bosch", "Bosch", product, "Smart Fridge Layout"),
            BrandOption("fr_panasonic", "Panasonic", product, "Smart Fridge Layout"),
            BrandOption("fr_hitachi", "Hitachi", product, "Smart Fridge Layout")
        )

        ProductType.WASHING_MACHINE -> listOf(
            BrandOption("wm_lg", "LG", product, "Washer Control Layout"),
            BrandOption("wm_samsung", "Samsung", product, "Washer Control Layout"),
            BrandOption("wm_ifb", "IFB", product, "Washer Control Layout"),
            BrandOption("wm_bosch", "Bosch", product, "Washer Control Layout"),
            BrandOption("wm_whirlpool", "Whirlpool", product, "Washer Control Layout"),
            BrandOption("wm_haier", "Haier", product, "Washer Control Layout"),
            BrandOption("wm_siemens", "Siemens", product, "Washer Control Layout"),
            BrandOption("wm_panasonic", "Panasonic", product, "Washer Control Layout")
        )

        ProductType.SET_TOP_BOX -> listOf(
            BrandOption("stb_tataplay", "Tata Play", product, "IR DTH Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_airtel", "Airtel Xstream", product, "Bluetooth • Wi-Fi Android TV • IR"),
            BrandOption("stb_jio", "Jio Set-Top Box", product, "Bluetooth • Wi-Fi Android TV • IR"),
            BrandOption("stb_dishtv", "Dish TV", product, "IR DTH Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_sundirect", "Sun Direct", product, "IR DTH Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_firetv", "Amazon Fire TV", product, "Bluetooth HID", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("stb_appletv", "Apple TV", product, "Bluetooth HID", supportsAppLaunch = false, supportsWifiTv = false)
        )

        ProductType.SOUNDBAR -> listOf(
            BrandOption("sb_sony", "Sony", product, "Bluetooth Audio • IR"),
            BrandOption("sb_jbl", "JBL", product, "Bluetooth Audio • IR"),
            BrandOption("sb_samsung", "Samsung", product, "Bluetooth Audio • IR"),
            BrandOption("sb_lg", "LG", product, "Bluetooth Audio • IR"),
            BrandOption("sb_bose", "Bose", product, "Bluetooth Audio • IR"),
            BrandOption("sb_boat", "boAt", product, "Bluetooth Audio • IR"),
            BrandOption("sb_yamaha", "Yamaha", product, "Bluetooth Audio • IR")
        )

        ProductType.PROJECTOR -> listOf(
            BrandOption("pj_xgimi", "XGIMI", product, "Bluetooth • Wi-Fi Android TV • IR"),
            BrandOption("pj_wanbo", "Wanbo", product, "Bluetooth • Wi-Fi Android TV • IR"),
            BrandOption("pj_nebula", "Anker Nebula", product, "Bluetooth • Wi-Fi Android TV • IR"),
            BrandOption("pj_epson", "Epson", product, "IR Projector Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("pj_benq", "BenQ", product, "IR Projector Remote", supportsAppLaunch = false, supportsWifiTv = false),
            BrandOption("pj_viewsonic", "ViewSonic", product, "IR Projector Remote", supportsAppLaunch = false, supportsWifiTv = false)
        )

        ProductType.CEILING_FAN -> listOf(
            BrandOption("fan_atomberg", "Atomberg", product, "IR Fan Remote"),
            BrandOption("fan_havells", "Havells", product, "IR Fan Remote"),
            BrandOption("fan_crompton", "Crompton", product, "IR Fan Remote"),
            BrandOption("fan_orient", "Orient", product, "IR Fan Remote"),
            BrandOption("fan_dyson", "Dyson", product, "IR Fan Remote"),
            BrandOption("fan_xiaomi", "Xiaomi", product, "IR Fan Remote")
        )
    }

    val tvApps: List<LaunchableApp> = listOf(
        LaunchableApp("youtube", "YouTube", tvAppLink = "https://www.youtube.com"),
        LaunchableApp("netflix", "Netflix", tvAppLink = "https://www.netflix.com"),
        LaunchableApp("prime", "Prime Video", tvAppLink = "https://app.primevideo.com"),
        LaunchableApp("hotstar", "Hotstar / Disney+", tvAppLink = "https://www.hotstar.com"),
        LaunchableApp("spotify", "Spotify", tvAppLink = "https://open.spotify.com"),
        LaunchableApp("playstore", "Play Store", tvAppLink = "https://play.google.com/store")
    )

    val pcApps: List<LaunchableApp> = listOf(
        LaunchableApp("browser", "Browser", pcAppKey = "browser", pcFallbackCommand = "https://www.google.com"),
        LaunchableApp("youtube", "YouTube", pcAppKey = "youtube", pcFallbackCommand = "https://www.youtube.com"),
        LaunchableApp("netflix", "Netflix", pcAppKey = "netflix", pcFallbackCommand = "https://www.netflix.com"),
        LaunchableApp("files", "File Explorer", pcAppKey = "files", pcFallbackCommand = "explorer"),
        LaunchableApp("calc", "Calculator", pcAppKey = "calc", pcFallbackCommand = "calc"),
        LaunchableApp("terminal", "Terminal", pcAppKey = "terminal", pcFallbackCommand = "cmd"),
        LaunchableApp("taskmgr", "Task Manager", pcAppKey = "taskmgr", pcFallbackCommand = "taskmgr"),
        LaunchableApp("settings", "Settings", pcAppKey = "settings", pcFallbackCommand = "ms-settings:")
    )
}
