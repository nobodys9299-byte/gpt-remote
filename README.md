# GPT Remote (`gpt-remote`)

Universal Android remote hub for **Xiaomi Mi Android TV** (and any Android TV / Google TV) **plus Windows, macOS, and Linux Laptops/PCs** over **Bluetooth HID** or **Wi-Fi**, featuring hardware **vibration haptics** and a customizable **Device Hub** home screen.

## Features

- **Customizable Device Hub Home Screen**
  - Filter devices by **All**, **Android TV**, or **Laptop / PC**.
  - Pin & customize saved electronics (custom nicknames, device categories, custom icons, 1-tap reconnect, or manual IP addition).
  - Live nearby discovery across **Bluetooth HID**, **Wi-Fi Android TVs**, and **Wi-Fi PCs**.

- **Bluetooth HID Mode (TV & Laptop/PC — no companion app required)**
  - Emulates a Bluetooth HID Keyboard + Consumer Control + Mouse (`BluetoothHidDevice`, Android 9+ / API 28+).
  - Switchable **TV Remote Profile** (`SUBCLASS2_REMOTE_CONTROL`) and **PC / Laptop Combo Profile** (`SUBCLASS1_COMBO`).
  - Smooth ~80Hz background-throttled **Touchpad** (sub-pixel cursor, instant click, 2-finger right-click, 2-finger & edge scroll, `1x` / `1.5x` / `2.2x` sensitivity).

- **Wi-Fi Modes**
  - **Android TV Remote v2**: Auto-discovers `_androidtvremote2._tcp.`, pairs via TLS (`port 6467`) with 6-char hex PIN, and controls via `port 6466`.
  - **Wi-Fi PC Companion**: Run [`pc-companion/gpt_remote_pc.py`](pc-companion/gpt_remote_pc.py) on your laptop (zero extra dependencies on Windows!) for Wi-Fi mouse, keyboard, and PC shortcuts (`Alt+Tab`, `Copy`, `Paste`, `Desktop`, `Win`).

- **Hardware Vibration Haptics**
  - Configurable strength (**Off**, **Subtle**, **Crisp**, **Strong**) pulsing on D-pad presses, clicks, volume, and scroll ticks.

## Build & Test

```bash
./gradlew testDebugUnitTest assembleDebug
```
