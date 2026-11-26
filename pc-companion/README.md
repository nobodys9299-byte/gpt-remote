# Wi-Fi PC / Laptop Companion

Run `gpt_remote_pc.py` on your laptop or desktop to control its mouse, keyboard, and media over Wi-Fi from the **GPT Remote** Android app.

## Windows (Zero Extra Installs)

```powershell
python pc-companion/gpt_remote_pc.py
```

## macOS / Linux

```bash
pip install pynput
python3 pc-companion/gpt_remote_pc.py
```

Make sure your phone and laptop are on the same Wi-Fi network (and allow Python through your firewall on TCP port `58765` / UDP port `58766` if prompted).
