#!/usr/bin/env python3
"""
GPT Remote - Wi-Fi PC/Laptop Companion Server
Lets the GPT Remote Android app control your Windows, macOS, or Linux computer
as a wireless mouse, touchpad, keyboard, and media remote over Wi-Fi.

On Windows: Zero external dependencies required (uses built-in ctypes SendInput).
On macOS/Linux: Uses pynput (`pip install pynput`).
"""

import ctypes
import json
import platform
import socket
import threading

TCP_PORT = 58765
UDP_DISCOVERY_PORT = 58766
DISCOVER_MAGIC = b"GPT_REMOTE_DISCOVER_V1"


class InputBackend:
    def move(self, dx: int, dy: int) -> None:
        raise NotImplementedError

    def scroll(self, dy: int) -> None:
        raise NotImplementedError

    def click(self, btn: str) -> None:
        raise NotImplementedError

    def key(self, code: str) -> None:
        raise NotImplementedError

    def text(self, s: str) -> None:
        raise NotImplementedError


def create_backend() -> InputBackend:
    system = platform.system()
    if system == "Windows":
        return WindowsBackend()
    return PynputBackend()


class WindowsBackend(InputBackend):
    """Zero-dependency Windows mouse/keyboard backend via user32.SendInput."""

    INPUT_MOUSE = 0
    INPUT_KEYBOARD = 1
    MOUSEEVENTF_MOVE = 0x0001
    MOUSEEVENTF_LEFTDOWN = 0x0002
    MOUSEEVENTF_LEFTUP = 0x0004
    MOUSEEVENTF_RIGHTDOWN = 0x0008
    MOUSEEVENTF_RIGHTUP = 0x0010
    MOUSEEVENTF_MIDDLEDOWN = 0x0020
    MOUSEEVENTF_MIDDLEUP = 0x0040
    MOUSEEVENTF_WHEEL = 0x0800
    KEYEVENTF_KEYUP = 0x0002
    KEYEVENTF_UNICODE = 0x0004

    VK_MAP = {
        "backspace": 0x08,
        "tab": 0x09,
        "enter": 0x0D,
        "esc": 0x1B,
        "page_up": 0x21,
        "page_down": 0x22,
        "left": 0x25,
        "up": 0x26,
        "right": 0x27,
        "down": 0x28,
        "win": 0x5B,
        "menu": 0x5D,
        "mute": 0xAD,
        "vol_down": 0xAE,
        "vol_up": 0xAF,
        "next": 0xB0,
        "prev": 0xB1,
        "stop": 0xB2,
        "play_pause": 0xB3,
        "search": 0xAA,
        **{str(i): 0x30 + i for i in range(10)},
    }

    def __init__(self) -> None:
        from ctypes import wintypes

        class MOUSEINPUT(ctypes.Structure):
            _fields_ = [
                ("dx", wintypes.LONG),
                ("dy", wintypes.LONG),
                ("mouseData", wintypes.DWORD),
                ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
            ]

        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [
                ("wVk", wintypes.WORD),
                ("wScan", wintypes.WORD),
                ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
            ]

        class HARDWAREINPUT(ctypes.Structure):
            _fields_ = [
                ("uMsg", wintypes.DWORD),
                ("wParamL", wintypes.WORD),
                ("wParamH", wintypes.WORD),
            ]

        class _INPUT_UNION(ctypes.Union):
            _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]

        class INPUT(ctypes.Structure):
            _fields_ = [("type", wintypes.DWORD), ("union", _INPUT_UNION)]

        self._INPUT = INPUT
        self._MOUSEINPUT = MOUSEINPUT
        self._KEYBDINPUT = KEYBDINPUT
        self._user32 = ctypes.windll.user32

    def _send(self, *inputs) -> None:
        arr = (self._INPUT * len(inputs))(*inputs)
        self._user32.SendInput(len(inputs), arr, ctypes.sizeof(self._INPUT))

    def _mouse(self, flags: int, dx: int = 0, dy: int = 0, data: int = 0) -> None:
        inp = self._INPUT(type=self.INPUT_MOUSE)
        inp.union.mi = self._MOUSEINPUT(dx=dx, dy=dy, mouseData=data, dwFlags=flags, time=0, dwExtraInfo=None)
        self._send(inp)

    def _vk(self, vk: int, key_up: bool = False) -> object:
        inp = self._INPUT(type=self.INPUT_KEYBOARD)
        flags = self.KEYEVENTF_KEYUP if key_up else 0
        inp.union.ki = self._KEYBDINPUT(wVk=vk, wScan=0, dwFlags=flags, time=0, dwExtraInfo=None)
        return inp

    def _combo(self, mod_vk: int, key_vk: int) -> None:
        self._send(
            self._vk(mod_vk, False),
            self._vk(key_vk, False),
            self._vk(key_vk, True),
            self._vk(mod_vk, True),
        )

    def move(self, dx: int, dy: int) -> None:
        self._mouse(self.MOUSEEVENTF_MOVE, dx=dx, dy=dy)

    def scroll(self, dy: int) -> None:
        self._mouse(self.MOUSEEVENTF_WHEEL, data=int(dy * 120) & 0xFFFFFFFF)

    def click(self, btn: str) -> None:
        if btn == "right":
            self._mouse(self.MOUSEEVENTF_RIGHTDOWN)
            self._mouse(self.MOUSEEVENTF_RIGHTUP)
        elif btn == "middle":
            self._mouse(self.MOUSEEVENTF_MIDDLEDOWN)
            self._mouse(self.MOUSEEVENTF_MIDDLEUP)
        else:
            self._mouse(self.MOUSEEVENTF_LEFTDOWN)
            self._mouse(self.MOUSEEVENTF_LEFTUP)

    def key(self, code: str) -> None:
        if code == "alt_tab":
            self._combo(0x12, 0x09)  # VK_MENU + VK_TAB
        elif code == "copy":
            self._combo(0x11, 0x43)  # VK_CONTROL + C
        elif code == "paste":
            self._combo(0x11, 0x56)  # VK_CONTROL + V
        elif code == "desktop":
            self._combo(0x5B, 0x44)  # LWIN + D
        elif code == "settings":
            self._combo(0x5B, 0x49)  # LWIN + I
        elif code == "lock":
            self._user32.LockWorkStation()
        elif code in self.VK_MAP:
            vk = self.VK_MAP[code]
            self._send(self._vk(vk, False), self._vk(vk, True))

    def text(self, s: str) -> None:
        events = []
        for ch in s:
            if ch == "\n":
                events.extend([self._vk(0x0D, False), self._vk(0x0D, True)])
            else:
                code_unit = ord(ch)
                down = self._INPUT(type=self.INPUT_KEYBOARD)
                down.union.ki = self._KEYBDINPUT(0, code_unit, self.KEYEVENTF_UNICODE, 0, None)
                up = self._INPUT(type=self.INPUT_KEYBOARD)
                up.union.ki = self._KEYBDINPUT(0, code_unit, self.KEYEVENTF_UNICODE | self.KEYEVENTF_KEYUP, 0, None)
                events.extend([down, up])
        if events:
            self._send(*events)


class PynputBackend(InputBackend):
    """Cross-platform backend for macOS / Linux using pynput."""

    def __init__(self) -> None:
        try:
            from pynput.keyboard import Controller as KbController, Key
            from pynput.mouse import Button, Controller as MouseController
        except ImportError as exc:
            raise SystemExit("Install pynput on macOS/Linux: pip install pynput") from exc

        self.mouse = MouseController()
        self.kb = KbController()
        self.Button = Button
        self.Key = Key
        self.key_map = {
            "enter": Key.enter,
            "esc": Key.esc,
            "backspace": Key.backspace,
            "tab": Key.tab,
            "up": Key.up,
            "down": Key.down,
            "left": Key.left,
            "right": Key.right,
            "win": Key.cmd,
            "menu": Key.menu,
            "vol_up": Key.media_volume_up,
            "vol_down": Key.media_volume_down,
            "mute": Key.media_volume_mute,
            "play_pause": Key.media_play_pause,
            "next": Key.media_next,
            "prev": Key.media_previous,
            "page_up": Key.page_up,
            "page_down": Key.page_down,
        }

    def move(self, dx: int, dy: int) -> None:
        self.mouse.move(dx, dy)

    def scroll(self, dy: int) -> None:
        self.mouse.scroll(0, dy)

    def click(self, btn: str) -> None:
        target = {
            "right": self.Button.right,
            "middle": self.Button.middle,
        }.get(btn, self.Button.left)
        self.mouse.click(target)

    def key(self, code: str) -> None:
        if code in self.key_map:
            k = self.key_map[code]
            self.kb.press(k)
            self.kb.release(k)
        elif len(code) == 1:
            self.kb.type(code)

    def text(self, s: str) -> None:
        self.kb.type(s)


def udp_discovery_loop() -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", UDP_DISCOVERY_PORT))
    announce = json.dumps({
        "type": "announce",
        "name": socket.gethostname(),
        "os": platform.system(),
        "port": TCP_PORT,
    }).encode("utf-8")

    while True:
        data, addr = sock.recvfrom(1024)
        if data.strip() == DISCOVER_MAGIC:
            sock.sendto(announce, addr)


def handle_client(conn: socket.socket, addr: tuple, backend: InputBackend) -> None:
    print(f"[+] Connected: {addr[0]}:{addr[1]}")
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    buf = ""
    try:
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            buf += chunk.decode("utf-8", errors="ignore")
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                if not line.strip():
                    continue
                msg = json.loads(line)
                t = msg.get("t")
                if t == "move":
                    backend.move(int(msg.get("dx", 0)), int(msg.get("dy", 0)))
                elif t == "scroll":
                    backend.scroll(int(msg.get("dy", 0)))
                elif t == "click":
                    backend.click(msg.get("btn", "left"))
                elif t == "key":
                    backend.key(msg.get("code", ""))
                elif t == "text":
                    backend.text(msg.get("s", ""))
    except Exception as exc:
        print(f"[!] Client error ({addr[0]}): {exc}")
    finally:
        conn.close()
        print(f"[-] Disconnected: {addr[0]}:{addr[1]}")


def get_local_ips() -> list[str]:
    ips: list[str] = []
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        probe.connect(("8.8.8.8", 80))
        primary = probe.getsockname()[0]
        probe.close()
        if primary and not primary.startswith("127."):
            ips.append(primary)
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip and not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except OSError:
        pass
    return ips


def main() -> None:
    backend = create_backend()
    threading.Thread(target=udp_discovery_loop, daemon=True).start()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", TCP_PORT))
    server.listen(4)

    hostname = socket.gethostname()
    local_ips = get_local_ips()
    print("==========================================")
    print(f" GPT Remote PC Companion ({platform.system()})")
    print(f" Computer Name : {hostname}")
    print(f" Wi-Fi IP(s)   : {', '.join(local_ips) if local_ips else 'Check ipconfig'}")
    print(f" Listening on  : TCP {TCP_PORT} / UDP {UDP_DISCOVERY_PORT}")
    print("==========================================")
    print("Open GPT Remote on your phone -> Wi-Fi PC -> Scan (or enter IP above).")
    print("Keep this window open while controlling your PC (Ctrl+C to stop).\n")

    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle_client, args=(conn, addr, backend), daemon=True).start()


if __name__ == "__main__":
    main()
