# Wi-Fi PC / Laptop Companion

Run the companion on your laptop or desktop to control its mouse, keyboard, and media over Wi-Fi from the **GPT Remote** Android app.

## Option A: Standalone Windows `.exe` (No Python Needed)

Download **`GptRemotePC.exe`** from the [GitHub Releases](https://github.com/nobodys9299-byte/gpt-remote/releases) page and double-click it.

## Option B: One-Click Scripts (Windows with Python)

- **`start_pc_companion.bat`**: Double-click to start immediately in a status window (shows your Wi-Fi IP and connection logs).
- **`install_autostart.bat`**: Adds a silent Windows Startup shortcut (`GptRemotePC.lnk`) and starts the companion in the background automatically whenever you log in.
- **`uninstall_autostart.bat`**: Stops the background companion and removes the Startup shortcut.

Or run from terminal:
```powershell
python pc-companion/gpt_remote_pc.py
```

## macOS / Linux

```bash
pip install pynput
python3 pc-companion/gpt_remote_pc.py
```

Make sure your phone and laptop are on the same Wi-Fi network, and allow Private network access in Windows Firewall if prompted (TCP port `58765` / UDP port `58766`).
