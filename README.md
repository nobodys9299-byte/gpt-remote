# Mi TV Remote (`gpt-remote`)

Android remote control app for **Xiaomi Mi Android TV** (and other Android TV / Google TV devices) using **Bluetooth HID** as the primary transport and **Wi-Fi Android TV Remote v2** as a built-in fallback.

## Features

- **Bluetooth HID Remote (Primary — no TV app or Wi-Fi required)**
  - Emulates a composite Bluetooth HID Keyboard + Consumer Control + Mouse (`BluetoothHidDevice`, Android 9+ / API 28+).
  - **D-Pad & Navigation**: Up, Down, Left, Right (with hold-to-repeat), OK, Back, Home, Menu, Power.
  - **Volume, Channel & Media**: Volume +/-, Mute, Channel +/-, Play/Pause, Next, Previous, Stop.
  - **HID Mouse Touchpad**: Drag cursor, tap to click, double-tap for right-click, vertical scroll strip, adjustable sensitivity (`1x`, `1.5x`, `2.2x`).
  - **TV Keyboard & Numpad**: Full text typing (including live typing mode), Enter, Backspace, Search, and `0–9` numpad.
  - **Foreground Service**: Keeps the Bluetooth HID connection alive cleanly while using the remote.

- **Wi-Fi Android TV Remote v2 (Fallback)**
  - Auto-discovers TVs via mDNS (`_androidtvremote2._tcp.`) or manual IP entry.
  - TLS pairing (`port 6467`) with 6-character hex PIN verification and persistent client RSA-2048 credentials in `AndroidKeyStore`.
  - Encrypted remote control (`port 6466`) for navigation, volume, media, and IME text input (plus swipe-to-D-pad gesture fallback on the touchpad).

## How to Pair with Mi Android TV (Bluetooth)

1. Open **Mi TV Remote** on your Android phone and grant Bluetooth permissions.
2. Tap **Connect TV** at the top of the screen.
3. On your Mi Android TV, open **Settings → Remotes & Accessories → Add accessory**.
4. In the app, tap **Discoverable** (or **Scan TVs**), select your Mi TV, and accept the pairing prompt on both devices.

## Build & Test

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
