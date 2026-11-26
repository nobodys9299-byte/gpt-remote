# GPT Remote PC (Windows / macOS / Linux)

Installable PC companion app for **GPT Remote** with remote mouse/keyboard, live **Screen Sharing**, and **Two-Way File Transfer** over Wi-Fi.

## Option A: Windows Installer (Recommended)

Download **`GptRemotePC-Setup.exe`** from [GitHub Releases](https://github.com/nobodys9299-byte/gpt-remote/releases) and run the installer.
- Installs **GPT Remote PC** into Program Files with Desktop/Start Menu shortcuts and clean Uninstall support.
- Automatically configures Windows Firewall ports (`58765`, `58766`, `58767`, `58768`).
- Closing the window minimizes it to the **Windows System Tray** so your phone can connect anytime.

## Option B: Run from Python Source

On Windows (install `mss` & `pillow` for screen sharing):
```powershell
pip install mss pillow
python pc-companion/gpt_remote_pc.py
```

On macOS / Linux:
```bash
pip install pynput mss pillow
python3 pc-companion/gpt_remote_pc.py
```
