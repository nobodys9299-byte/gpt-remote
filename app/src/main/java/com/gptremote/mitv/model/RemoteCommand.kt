package com.gptremote.mitv.model

/**
 * Unified remote control commands supported across Bluetooth HID and Wi-Fi Android TV Remote v2.
 */
enum class RemoteButton {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_CENTER,
    BACK,
    HOME,
    MENU,
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    MEDIA_PLAY_PAUSE,
    MEDIA_PREVIOUS,
    MEDIA_NEXT,
    MEDIA_STOP,
    SEARCH,
    SETTINGS,
    TV_INPUT,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    BACKSPACE,
    ENTER,
    ESCAPE
}

enum class MouseButton {
    LEFT,
    RIGHT,
    MIDDLE
}

enum class TransportMode {
    BLUETOOTH_HID,
    WIFI_ANDROID_TV
}
