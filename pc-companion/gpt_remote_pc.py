#!/usr/bin/env python3
"""
GPT Remote PC - Windows/macOS/Linux Desktop Companion App
Provides:
1. Dark Desktop Dashboard GUI + System Tray Icon
2. Remote Mouse, Touchpad, Absolute Tap, Keyboard, Media & App Launch (TCP 58765)
3. UDP Auto-Discovery (UDP 58766)
4. Live PC-to-Phone Screen Streaming (TCP 58767)
5. Two-Way File Transfer & Phone-to-PC Screen Viewer (HTTP 58768)
"""

import ctypes
import http.server
import io
import json
import os
import platform
import shutil
import socket
import socketserver
import struct
import subprocess
import sys
import threading
import time
import urllib.parse
import webbrowser

TCP_PORT = 58765
UDP_DISCOVERY_PORT = 58766
SCREEN_PORT = 58767
FILE_HTTP_PORT = 58768
DISCOVER_MAGIC = b"GPT_REMOTE_DISCOVER_V1"


def default_shared_dir() -> str:
    downloads = os.path.join(os.path.expanduser("~"), "Downloads", "GptRemote")
    os.makedirs(downloads, exist_ok=True)
    return downloads


# ---- Input Backends ----

class InputBackend:
    def move(self, dx: int, dy: int) -> None:
        raise NotImplementedError

    def move_abs(self, nx: float, ny: float, click: bool = False) -> None:
        raise NotImplementedError

    def scroll(self, dy: int) -> None:
        raise NotImplementedError

    def click(self, btn: str) -> None:
        raise NotImplementedError

    def key(self, code: str) -> None:
        raise NotImplementedError

    def text(self, s: str) -> None:
        raise NotImplementedError

    def launch(self, app: str) -> None:
        urls = {
            "browser": "https://www.google.com",
            "youtube": "https://www.youtube.com",
            "netflix": "https://www.netflix.com",
        }
        if app in urls:
            webbrowser.open(urls[app])
            return
        system = platform.system()
        if system == "Windows":
            cmds = {
                "files": ["explorer.exe"],
                "calc": ["calc.exe"],
                "terminal": ["cmd.exe"],
                "taskmgr": ["taskmgr.exe"],
                "settings": ["cmd.exe", "/c", "start", "ms-settings:"],
            }
            if app in cmds:
                subprocess.Popen(cmds[app])
        elif system == "Darwin":
            mac_apps = {
                "files": ["open", "."],
                "calc": ["open", "-a", "Calculator"],
                "terminal": ["open", "-a", "Terminal"],
                "settings": ["open", "x-apple.systempreferences:"],
            }
            if app in mac_apps:
                subprocess.Popen(mac_apps[app])
        else:
            linux_apps = {
                "files": ["xdg-open", "."],
                "calc": ["gnome-calculator"],
                "terminal": ["x-terminal-emulator"],
            }
            if app in linux_apps:
                subprocess.Popen(linux_apps[app])

    def close_app(self, app: str) -> None:
        system = platform.system()
        if system == "Windows":
            proc_map = {
                "calc": ["CalculatorApp.exe", "calc.exe"],
                "terminal": ["cmd.exe", "WindowsTerminal.exe"],
                "taskmgr": ["Taskmgr.exe"],
                "settings": ["SystemSettings.exe"],
                "browser": ["chrome.exe", "msedge.exe", "firefox.exe", "brave.exe"],
                "youtube": ["chrome.exe", "msedge.exe"],
                "netflix": ["chrome.exe", "msedge.exe"],
            }
            targets = proc_map.get(app, [])
            killed = False
            for exe in targets:
                try:
                    res = subprocess.run(
                        ["taskkill", "/F", "/IM", exe],
                        capture_output=True,
                        creationflags=0x08000000,
                    )
                    if res.returncode == 0:
                        killed = True
                except Exception:
                    pass
            if not killed:
                try:
                    # Fallback: close foreground window cleanly via Alt+F4
                    user32 = ctypes.windll.user32
                    user32.keybd_event(0x12, 0, 0, 0)
                    user32.keybd_event(0x73, 0, 0, 0)
                    user32.keybd_event(0x73, 0, 2, 0)
                    user32.keybd_event(0x12, 0, 2, 0)
                except Exception:
                    pass
        elif system == "Darwin":
            mac_procs = {"calc": "Calculator", "terminal": "Terminal", "browser": "Safari"}
            name = mac_procs.get(app)
            if name:
                subprocess.Popen(["pkill", "-x", name])
        else:
            linux_procs = {"calc": "gnome-calculator", "terminal": "x-terminal-emulator"}
            name = linux_procs.get(app)
            if name:
                subprocess.Popen(["pkill", "-f", name])


def create_backend() -> InputBackend:
    if platform.system() == "Windows":
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
    MOUSEEVENTF_ABSOLUTE = 0x8000
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

    def move_abs(self, nx: float, ny: float, click: bool = False) -> None:
        nx = max(0.0, min(1.0, nx))
        ny = max(0.0, min(1.0, ny))
        ax = int(nx * 65535)
        ay = int(ny * 65535)
        self._mouse(self.MOUSEEVENTF_MOVE | self.MOUSEEVENTF_ABSOLUTE, dx=ax, dy=ay)
        if click:
            self.click("left")

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
            self._combo(0x12, 0x09)
        elif code == "copy":
            self._combo(0x11, 0x43)
        elif code == "paste":
            self._combo(0x11, 0x56)
        elif code == "desktop":
            self._combo(0x5B, 0x44)
        elif code == "settings":
            self._combo(0x5B, 0x49)
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

    def move_abs(self, nx: float, ny: float, click: bool = False) -> None:
        # Basic fallback without screen dimensions
        if click:
            self.click("left")

    def scroll(self, dy: int) -> None:
        self.mouse.scroll(0, dy)

    def click(self, btn: str) -> None:
        target = {"right": self.Button.right, "middle": self.Button.middle}.get(btn, self.Button.left)
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


# ---- Screen Capture Helper ----

def _overlay_windows_cursor(
    img,
    mon_left: int = 0,
    mon_top: int = 0,
    mon_w: int = 0,
    mon_h: int = 0,
):
    """Draws the live Windows mouse cursor arrow onto the captured screen image."""
    if platform.system() != "Windows":
        return img
    try:
        from ctypes import wintypes
        from PIL import ImageDraw

        class _CURSORINFO(ctypes.Structure):
            _fields_ = [
                ("cbSize", wintypes.DWORD),
                ("flags", wintypes.DWORD),
                ("hCursor", wintypes.HANDLE),
                ("ptScreenPos", wintypes.POINT),
            ]

        user32 = ctypes.windll.user32
        user32.GetCursorInfo.argtypes = [ctypes.c_void_p]
        user32.GetCursorInfo.restype = wintypes.BOOL
        ci = _CURSORINFO()
        ci.cbSize = ctypes.sizeof(_CURSORINFO)
        if not user32.GetCursorInfo(ctypes.byref(ci)):
            return img
        if (ci.flags & 0x00000001) == 0:
            return img

        px = ci.ptScreenPos.x - mon_left
        py = ci.ptScreenPos.y - mon_top
        if mon_w > 0 and mon_h > 0:
            cx = int(round(px * (img.width / float(mon_w))))
            cy = int(round(py * (img.height / float(mon_h))))
        else:
            cx, cy = int(px), int(py)
        if not (0 <= cx < img.width and 0 <= cy < img.height):
            return img

        draw = ImageDraw.Draw(img)
        sc = max(1.0, img.height / 760.0)
        pts = [
            (cx, cy),
            (cx, cy + int(round(19 * sc))),
            (cx + int(round(4.5 * sc)), cy + int(round(14.5 * sc))),
            (cx + int(round(7.8 * sc)), cy + int(round(21.5 * sc))),
            (cx + int(round(10.5 * sc)), cy + int(round(20.2 * sc))),
            (cx + int(round(7.2 * sc)), cy + int(round(13.2 * sc))),
            (cx + int(round(13.5 * sc)), cy + int(round(13.2 * sc))),
        ]
        draw.polygon(pts, fill=(255, 255, 255), outline=(15, 23, 42))
    except Exception:
        pass
    return img


def capture_pc_jpeg(
    max_width: int = 1920,
    quality: int = 76,
    sct_ctx=None,
) -> bytes | None:
    """Captures the primary PC screen as JPEG bytes with live cursor overlay."""
    try:
        from PIL import Image
    except ImportError:
        return None

    img = None
    mon_left, mon_top, mon_w, mon_h = 0, 0, 0, 0
    try:
        if sct_ctx is not None:
            mon = sct_ctx.monitors[1] if len(sct_ctx.monitors) > 1 else sct_ctx.monitors[0]
            raw = sct_ctx.grab(mon)
            mon_left = int(mon.get("left", 0))
            mon_top = int(mon.get("top", 0))
            mon_w = int(mon.get("width", raw.size[0]))
            mon_h = int(mon.get("height", raw.size[1]))
            img = Image.frombytes("RGB", raw.size, raw.bgra, "raw", "BGRX")
        else:
            import mss  # type: ignore
            with mss.mss() as sct:
                mon = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
                raw = sct.grab(mon)
                mon_left = int(mon.get("left", 0))
                mon_top = int(mon.get("top", 0))
                mon_w = int(mon.get("width", raw.size[0]))
                mon_h = int(mon.get("height", raw.size[1]))
                img = Image.frombytes("RGB", raw.size, raw.bgra, "raw", "BGRX")
    except Exception:
        try:
            from PIL import ImageGrab
            img = ImageGrab.grab()
            mon_w, mon_h = img.size
        except Exception:
            img = None

    if img is None:
        return None

    img = _overlay_windows_cursor(img, mon_left, mon_top, mon_w, mon_h)

    w, h = img.size
    if w > max_width:
        new_h = max(1, int(h * (max_width / float(w))))
        img = img.resize((max_width, new_h), Image.Resampling.BILINEAR)

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality, optimize=False)
    return buf.getvalue()


# ---- Networking Helpers & State ----

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


def format_speed(bps: float) -> str:
    if bps <= 0:
        return "0 KB/s"
    kbps = bps / 1024.0
    if kbps < 1024.0:
        return f"{kbps:.0f} KB/s"
    return f"{kbps / 1024.0:.2f} MB/s"


class RemoteServerState:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.active_clients: set[str] = set()
        self.shared_dir: str = default_shared_dir()
        self.allow_pc_screen_share: bool = True
        self.latest_phone_frame: bytes | None = None
        self.latest_phone_frame_ts: float = 0.0
        self.bind_errors: list[str] = []
        self.transfer_info: dict | None = None
        self.transfer_history: list[dict] = []
        self.transfer_cancel_event = threading.Event()
        self.cancelled_sessions: set[str] = set()
        self.paused_sessions: set[str] = set()
        self.session_control_commands: dict[str, str] = {}
        self.active_transfer_session: str | None = None
        self.upload_parts: dict[str, tuple[str, str]] = {}
        self.on_change = None
        self.on_files_change = None
        self.on_phone_frame = None
        self.on_transfer = None

    def request_pause_or_resume_transfer(self, session_id: str = "") -> None:
        with self._lock:
            target_sid = session_id or self.active_transfer_session or ""
            info = self.transfer_info
            if not info:
                return
            was_paused = bool(info.get("paused")) or (bool(target_sid) and target_sid in self.paused_sessions)
            info_name = info.get("filename", "transfer")
            info_done = int(info.get("done", 0))
            info_total = int(info.get("total", max(1, info_done)))
            if was_paused:
                if target_sid:
                    self.paused_sessions.discard(target_sid)
                    self.session_control_commands[target_sid] = "resume"
                new_dir = "Receiving from Phone"
                new_paused = False
            else:
                if target_sid:
                    self.paused_sessions.add(target_sid)
                    self.session_control_commands[target_sid] = "pause"
                new_dir = "Paused"
                new_paused = True
        self.update_transfer(new_dir, info_name, info_done, info_total, True, 0.0, paused=new_paused)

    def set_remote_paused(self, session_id: str, paused: bool) -> None:
        with self._lock:
            target_sid = session_id or self.active_transfer_session or ""
            if target_sid:
                if paused:
                    self.paused_sessions.add(target_sid)
                else:
                    self.paused_sessions.discard(target_sid)
            info = self.transfer_info
            if not info:
                return
            info_name = info.get("filename", "transfer")
            info_done = int(info.get("done", 0))
            info_total = int(info.get("total", max(1, info_done)))
        if paused:
            self.update_transfer("Paused", info_name, info_done, info_total, True, 0.0, paused=True)
        else:
            self.update_transfer("Receiving from Phone", info_name, info_done, info_total, True, 0.0, paused=False)

    def pop_control_command(self, session_id: str) -> tuple[str, bool]:
        sid = session_id.strip()
        with self._lock:
            if not sid:
                return "none", False
            action = self.session_control_commands.pop(sid, "none")
            is_paused = sid in self.paused_sessions
            return action, is_paused

    def request_cancel_transfer(self, session_id: str = "") -> None:
        target_sid = session_id or self.active_transfer_session or ""
        info_name = "transfer"
        info_done = 0
        with self._lock:
            if target_sid:
                self.cancelled_sessions.add(target_sid)
                self.paused_sessions.discard(target_sid)
                self.session_control_commands[target_sid] = "cancel"
            else:
                self.transfer_cancel_event.set()
            if self.transfer_info:
                info_name = self.transfer_info.get("filename", "transfer")
                info_done = int(self.transfer_info.get("done", 0))
            part_entry = self.upload_parts.pop(target_sid, None) if target_sid else None
        if part_entry and os.path.isfile(part_entry[0]):
            try:
                os.remove(part_entry[0])
            except OSError:
                pass
        if info_name:
            self.update_transfer("Cancelled", info_name, info_done, max(1, info_done), False, 0.0, paused=False)
            self.record_history("cancelled", info_name, info_done, "", 0.0)

    def report_bind_error(self, msg: str) -> None:
        with self._lock:
            self.bind_errors.append(msg)
            cb = self.on_change
        if cb:
            cb()

    def update_transfer(
        self,
        direction: str,
        filename: str,
        done_bytes: int,
        total_bytes: int,
        active: bool = True,
        speed_bps: float = 0.0,
        paused: bool = False,
    ) -> None:
        with self._lock:
            self.transfer_info = {
                "direction": direction,
                "filename": filename,
                "done": done_bytes,
                "total": total_bytes,
                "active": active,
                "speed_bps": speed_bps,
                "paused": paused,
            }
            cb = self.on_transfer
        if cb:
            cb()

    def record_history(
        self,
        kind: str,
        filename: str,
        size_bytes: int,
        path: str,
        speed_bps: float = 0.0,
    ) -> None:
        with self._lock:
            self.transfer_history.insert(
                0,
                {
                    "kind": kind,
                    "filename": filename,
                    "size": size_bytes,
                    "path": path,
                    "speed_bps": speed_bps,
                    "ts": time.strftime("%H:%M"),
                },
            )
            if len(self.transfer_history) > 100:
                del self.transfer_history[100:]
            cb_files = self.on_files_change
        if cb_files:
            cb_files()

    def add_client(self, addr_str: str) -> None:
        with self._lock:
            self.active_clients.add(addr_str)
            cb = self.on_change
        if cb:
            cb()

    def remove_client(self, addr_str: str) -> None:
        with self._lock:
            self.active_clients.discard(addr_str)
            cb = self.on_change
        if cb:
            cb()

    def count(self) -> int:
        with self._lock:
            return len(self.active_clients)

    def notify_files_changed(self) -> None:
        cb = self.on_files_change
        if cb:
            cb()

    def update_phone_frame(self, jpeg_bytes: bytes) -> None:
        with self._lock:
            self.latest_phone_frame = jpeg_bytes
            self.latest_phone_frame_ts = time.time()
            cb = self.on_phone_frame
        if cb:
            cb(jpeg_bytes)


def _bind_server_sock(sock: socket.socket) -> None:
    if platform.system() == "Windows" and hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
    else:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)


def udp_discovery_loop(state: RemoteServerState, stop_event: threading.Event) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    _bind_server_sock(sock)
    sock.settimeout(1.0)
    try:
        sock.bind(("", UDP_DISCOVERY_PORT))
    except OSError as exc:
        state.report_bind_error(f"UDP {UDP_DISCOVERY_PORT}: {exc}")
        sock.close()
        return
    announce = json.dumps({
        "type": "announce",
        "name": socket.gethostname(),
        "os": platform.system(),
        "port": TCP_PORT,
        "screenPort": SCREEN_PORT,
        "filePort": FILE_HTTP_PORT,
    }).encode("utf-8")

    try:
        while not stop_event.is_set():
            try:
                data, addr = sock.recvfrom(1024)
                if data.strip() == DISCOVER_MAGIC:
                    sock.sendto(announce, addr)
            except socket.timeout:
                continue
            except OSError:
                break
    finally:
        sock.close()


def handle_client(
    conn: socket.socket,
    addr: tuple,
    backend: InputBackend,
    state: RemoteServerState,
) -> None:
    tag = f"{addr[0]}:{addr[1]}"
    state.add_client(tag)
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    try:
        conn.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
    except OSError:
        pass
    try:
        hello = json.dumps({
            "type": "announce",
            "name": socket.gethostname(),
            "os": platform.system(),
            "port": TCP_PORT,
            "screenPort": SCREEN_PORT,
            "filePort": FILE_HTTP_PORT,
        }) + "\n"
        conn.sendall(hello.encode("utf-8"))
    except OSError:
        pass

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
                try:
                    msg = json.loads(line)
                except Exception:
                    continue
                t = msg.get("t")
                if t == "move":
                    backend.move(int(msg.get("dx", 0)), int(msg.get("dy", 0)))
                elif t == "tap_abs":
                    backend.move_abs(float(msg.get("nx", 0.5)), float(msg.get("ny", 0.5)), click=True)
                elif t == "move_abs":
                    backend.move_abs(float(msg.get("nx", 0.5)), float(msg.get("ny", 0.5)), click=False)
                elif t == "scroll":
                    backend.scroll(int(msg.get("dy", 0)))
                elif t == "click":
                    backend.click(msg.get("btn", "left"))
                elif t == "key":
                    backend.key(msg.get("code", ""))
                elif t == "text":
                    backend.text(msg.get("s", ""))
                elif t == "launch":
                    backend.launch(msg.get("app", ""))
                elif t == "close_app":
                    backend.close_app(msg.get("app", ""))
                elif t == "ping":
                    conn.sendall(b'{"type":"pong"}\n')
    except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError, OSError):
        pass
    finally:
        conn.close()
        state.remove_client(tag)


def tcp_server_loop(
    backend: InputBackend,
    state: RemoteServerState,
    stop_event: threading.Event,
) -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    _bind_server_sock(server)
    server.settimeout(1.0)
    try:
        server.bind(("0.0.0.0", TCP_PORT))
        server.listen(8)
    except OSError as exc:
        state.report_bind_error(f"TCP {TCP_PORT}: {exc}")
        server.close()
        return

    try:
        while not stop_event.is_set():
            try:
                conn, addr = server.accept()
                threading.Thread(
                    target=handle_client,
                    args=(conn, addr, backend, state),
                    daemon=True,
                ).start()
            except socket.timeout:
                continue
            except OSError:
                break
    finally:
        server.close()


def handle_screen_viewer(
    conn: socket.socket,
    state: RemoteServerState,
    stop_event: threading.Event,
) -> None:
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    try:
        conn.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 1024 * 1024)
    except OSError:
        pass
    sct_ctx = None
    try:
        import mss  # type: ignore
        sct_ctx = mss.mss()
    except Exception:
        sct_ctx = None
    try:
        while not stop_event.is_set():
            if not state.allow_pc_screen_share:
                time.sleep(0.2)
                continue
            t0 = time.monotonic()
            jpeg = capture_pc_jpeg(max_width=1920, quality=76, sct_ctx=sct_ctx)
            if jpeg:
                header = struct.pack(">I", len(jpeg))
                conn.sendall(header + jpeg)
            elapsed = time.monotonic() - t0
            sleep_for = max(0.002, (1.0 / 26.0) - elapsed)
            time.sleep(sleep_for)
    except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError, OSError):
        pass
    finally:
        if sct_ctx is not None:
            try:
                sct_ctx.close()
            except Exception:
                pass
        conn.close()


def screen_server_loop(state: RemoteServerState, stop_event: threading.Event) -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    _bind_server_sock(server)
    server.settimeout(1.0)
    try:
        server.bind(("0.0.0.0", SCREEN_PORT))
        server.listen(4)
    except OSError as exc:
        state.report_bind_error(f"Screen {SCREEN_PORT}: {exc}")
        server.close()
        return

    try:
        while not stop_event.is_set():
            try:
                conn, _ = server.accept()
                threading.Thread(
                    target=handle_screen_viewer,
                    args=(conn, state, stop_event),
                    daemon=True,
                ).start()
            except socket.timeout:
                continue
            except OSError:
                break
    finally:
        server.close()


def sanitize_filename(raw: str) -> str:
    base = os.path.basename(raw or "received_file.bin").strip()
    if not base or base in (".", ".."):
        base = "received_file.bin"
    return "".join(ch if ch not in '<>:"/\\|?*' else "_" for ch in base)


def unique_target_path(folder: str, filename: str) -> str:
    os.makedirs(folder, exist_ok=True)
    candidate = os.path.join(folder, filename)
    if not os.path.exists(candidate):
        return candidate
    stem, ext = os.path.splitext(filename)
    idx = 1
    while os.path.exists(candidate):
        candidate = os.path.join(folder, f"{stem} ({idx}){ext}")
        idx += 1
    return candidate


TRANSFER_CHUNK_SIZE = 4 * 1024 * 1024


class ThreadingHttpServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    request_queue_size = 32
    allow_reuse_address = True


def _extract_windows_shell_image(path: str, size: int = 48):
    """Uses IShellItemImageFactory::GetImage on Windows to extract native Explorer thumbnails (videos/APKs/docs)."""
    if platform.system() != "Windows" or not os.path.exists(path):
        return None
    try:
        from ctypes import wintypes
        from PIL import Image

        class _SIZE(ctypes.Structure):
            _fields_ = [("cx", ctypes.c_long), ("cy", ctypes.c_long)]

        class _GUID(ctypes.Structure):
            _fields_ = [
                ("Data1", wintypes.DWORD),
                ("Data2", wintypes.WORD),
                ("Data3", wintypes.WORD),
                ("Data4", ctypes.c_ubyte * 8),
            ]

        class _BITMAP(ctypes.Structure):
            _fields_ = [
                ("bmType", ctypes.c_long),
                ("bmWidth", ctypes.c_long),
                ("bmHeight", ctypes.c_long),
                ("bmWidthBytes", ctypes.c_long),
                ("bmPlanes", wintypes.WORD),
                ("bmBitsPixel", wintypes.WORD),
                ("bmBits", ctypes.c_void_p),
            ]

        class _BITMAPINFOHEADER(ctypes.Structure):
            _fields_ = [
                ("biSize", wintypes.DWORD),
                ("biWidth", ctypes.c_long),
                ("biHeight", ctypes.c_long),
                ("biPlanes", wintypes.WORD),
                ("biBitCount", wintypes.WORD),
                ("biCompression", wintypes.DWORD),
                ("biSizeImage", wintypes.DWORD),
                ("biXPelsPerMeter", ctypes.c_long),
                ("biYPelsPerMeter", ctypes.c_long),
                ("biClrUsed", wintypes.DWORD),
                ("biClrImportant", wintypes.DWORD),
            ]

        iid = _GUID(0xBCC18B79, 0xBA16, 0x442F, (ctypes.c_ubyte * 8)(0x80, 0xC4, 0x8A, 0x59, 0xC3, 0x0C, 0x46, 0x3B))
        ole32 = ctypes.windll.ole32
        shell32 = ctypes.windll.shell32
        gdi32 = ctypes.windll.gdi32
        user32 = ctypes.windll.user32

        ole32.CoInitializeEx.argtypes = [ctypes.c_void_p, wintypes.DWORD]
        ole32.CoInitializeEx.restype = ctypes.c_long
        shell32.SHCreateItemFromParsingName.argtypes = [
            wintypes.LPCWSTR,
            ctypes.c_void_p,
            ctypes.POINTER(_GUID),
            ctypes.POINTER(ctypes.c_void_p),
        ]
        shell32.SHCreateItemFromParsingName.restype = ctypes.c_long
        gdi32.GetObjectW.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
        gdi32.GetObjectW.restype = ctypes.c_int
        gdi32.GetDIBits.argtypes = [
            ctypes.c_void_p,
            ctypes.c_void_p,
            wintypes.UINT,
            wintypes.UINT,
            ctypes.c_void_p,
            ctypes.c_void_p,
            wintypes.UINT,
        ]
        gdi32.GetDIBits.restype = ctypes.c_int
        gdi32.DeleteObject.argtypes = [ctypes.c_void_p]
        gdi32.DeleteObject.restype = wintypes.BOOL
        user32.GetDC.argtypes = [ctypes.c_void_p]
        user32.GetDC.restype = ctypes.c_void_p
        user32.ReleaseDC.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        user32.ReleaseDC.restype = ctypes.c_int

        ole32.CoInitializeEx(None, 0x2)
        item_ptr = ctypes.c_void_p()
        hr = shell32.SHCreateItemFromParsingName(
            os.path.abspath(path), None, ctypes.byref(iid), ctypes.byref(item_ptr)
        )
        if hr != 0 or not item_ptr.value:
            return None

        try:
            vtable = ctypes.cast(item_ptr, ctypes.POINTER(ctypes.c_void_p))[0]
            fn_ptr = ctypes.cast(vtable, ctypes.POINTER(ctypes.c_void_p))[3]  # GetImage
            GET_IMAGE = ctypes.WINFUNCTYPE(ctypes.c_long, ctypes.c_void_p, _SIZE, ctypes.c_int, ctypes.POINTER(wintypes.HBITMAP))
            get_image = GET_IMAGE(fn_ptr)

            hbmp = wintypes.HBITMAP()
            hr_img = get_image(item_ptr, _SIZE(size, size), 0x00000000, ctypes.byref(hbmp))
            if hr_img != 0 or not hbmp.value:
                return None

            try:
                bm = _BITMAP()
                if not gdi32.GetObjectW(hbmp, ctypes.sizeof(_BITMAP), ctypes.byref(bm)):
                    return None
                w, h = bm.bmWidth, bm.bmHeight
                if w <= 0 or h <= 0:
                    return None

                bmi = _BITMAPINFOHEADER()
                bmi.biSize = ctypes.sizeof(_BITMAPINFOHEADER)
                bmi.biWidth = w
                bmi.biHeight = -h
                bmi.biPlanes = 1
                bmi.biBitCount = 32
                bmi.biCompression = 0  # BI_RGB

                buf = (ctypes.c_ubyte * (w * h * 4))()
                hdc = user32.GetDC(None)
                try:
                    lines = gdi32.GetDIBits(hdc, hbmp, 0, h, buf, ctypes.byref(bmi), 0)
                finally:
                    user32.ReleaseDC(None, hdc)
                if lines == 0:
                    return None
                return Image.frombuffer("RGBA", (w, h), bytes(buf), "raw", "BGRA", 0, 1).convert("RGB")
            finally:
                gdi32.DeleteObject(hbmp)
        finally:
            vtable = ctypes.cast(item_ptr, ctypes.POINTER(ctypes.c_void_p))[0]
            rel_ptr = ctypes.cast(vtable, ctypes.POINTER(ctypes.c_void_p))[2]  # IUnknown::Release
            RELEASE = ctypes.WINFUNCTYPE(ctypes.c_ulong, ctypes.c_void_p)
            RELEASE(rel_ptr)(item_ptr)
    except Exception:
        return None


def generate_file_preview_pil(path: str, size: int = 44):
    """Returns a PIL RGB/RGBA thumbnail for image/video/file with rounded stylized fallback."""
    try:
        from PIL import Image, ImageDraw, ImageOps
    except ImportError:
        return None

    ext = os.path.splitext(path)[1].lower()
    is_img = ext in (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
    img = None
    if is_img and os.path.isfile(path):
        try:
            with Image.open(path) as im:
                im = ImageOps.exif_transpose(im)
                img = ImageOps.fit(im.convert("RGB"), (size, size), Image.Resampling.BILINEAR)
        except Exception:
            img = None

    if img is None and os.path.isfile(path):
        shell_im = _extract_windows_shell_image(path, max(64, size * 2))
        if shell_im is not None:
            try:
                img = ImageOps.fit(shell_im, (size, size), Image.Resampling.BILINEAR)
            except Exception:
                img = None

    if img is None:
        bg_hex, label = ("#17324F", "FILE")
        if ext in (".mp4", ".mkv", ".mov", ".webm", ".avi", ".m4v"):
            bg_hex, label = ("#0F3B5C", "VID")
        elif is_img:
            bg_hex, label = ("#4A1C3C", "IMG")
        elif ext == ".apk":
            bg_hex, label = ("#123D2B", "APK")
        elif ext == ".pdf":
            bg_hex, label = ("#4B1B22", "PDF")
        elif ext in (".zip", ".rar", ".7z", ".tar", ".gz"):
            bg_hex, label = ("#473311", "ZIP")
        elif ext in (".mp3", ".wav", ".m4a", ".aac", ".flac"):
            bg_hex, label = ("#12423D", "AUD")
        elif ext in (".exe", ".msi"):
            bg_hex, label = ("#322354", "APP")

        img = Image.new("RGB", (size, size), bg_hex)
        draw = ImageDraw.Draw(img)
        draw.rectangle([1, 1, size - 2, size - 2], outline="#3B4963", width=1)
        tx = max(4, (size - len(label) * 7) // 2)
        ty = max(6, (size - 10) // 2)
        draw.text((tx, ty), label, fill="#F8FAFC")

    return img


def file_http_server_loop(state: RemoteServerState, stop_event: threading.Event) -> None:
    class Handler(http.server.BaseHTTPRequestHandler):
        rbufsize = TRANSFER_CHUNK_SIZE
        wbufsize = 0
        protocol_version = "HTTP/1.1"

        def setup(self):
            # Initialize self.connection / rfile / wfile BEFORE touching socket options
            super().setup()
            try:
                self.connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4 * 1024 * 1024)
                self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4 * 1024 * 1024)
            except OSError:
                pass

        def log_message(self, format, *args):
            return

        def _send_json(self, code: int, payload: dict) -> None:
            body = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            self.wfile.flush()

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path == "/files":
                folder = state.shared_dir
                os.makedirs(folder, exist_ok=True)
                items = []
                for entry in sorted(os.listdir(folder)):
                    if entry.endswith(".gptpart"):
                        continue
                    full = os.path.join(folder, entry)
                    if os.path.isfile(full):
                        st = os.stat(full)
                        items.append({
                            "name": entry,
                            "size": st.st_size,
                            "modifiedMs": int(st.st_mtime * 1000),
                        })
                self._send_json(200, {"folder": folder, "files": items})
            elif parsed.path == "/upload-status":
                params = urllib.parse.parse_qs(parsed.query)
                sid = params.get("session", [""])[0].strip()
                existing = 0
                if sid:
                    part_entry = state.upload_parts.get(sid)
                    if part_entry and os.path.isfile(part_entry[0]):
                        existing = os.path.getsize(part_entry[0])
                self._send_json(200, {"bytes": existing})
            elif parsed.path == "/transfer-control":
                params = urllib.parse.parse_qs(parsed.query)
                sid = params.get("session", [""])[0].strip()
                action, is_paused = state.pop_control_command(sid)
                self._send_json(200, {"action": action, "paused": is_paused})
            elif parsed.path == "/thumb":
                params = urllib.parse.parse_qs(parsed.query)
                safe_name = sanitize_filename(params.get("name", [""])[0])
                full = os.path.join(state.shared_dir, safe_name)
                pil_img = generate_file_preview_pil(full, 96)
                if pil_img is None:
                    self.send_error(404)
                    return
                buf = io.BytesIO()
                pil_img.save(buf, format="JPEG", quality=82)
                raw = buf.getvalue()
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.send_header("Content-Length", str(len(raw)))
                self.send_header("Connection", "close")
                self.end_headers()
                self.wfile.write(raw)
                self.wfile.flush()
            elif parsed.path == "/download":
                params = urllib.parse.parse_qs(parsed.query)
                raw_name = params.get("name", [""])[0]
                safe_name = sanitize_filename(raw_name)
                full = os.path.join(state.shared_dir, safe_name)
                if not os.path.isfile(full):
                    self.send_error(404, "File not found")
                    return
                size = os.path.getsize(full)
                range_hdr = self.headers.get("Range", "")
                start_offset = 0
                end_offset = max(0, size - 1)
                is_partial = False
                if range_hdr.startswith("bytes="):
                    spec = range_hdr[6:].split(",")[0].strip()
                    p_start, _, p_end = spec.partition("-")
                    if p_start.isdigit():
                        start_offset = min(int(p_start), size)
                        if p_end.isdigit():
                            end_offset = min(int(p_end), max(0, size - 1))
                        is_partial = True

                to_send = max(0, end_offset - start_offset + 1) if size > 0 else 0
                state.transfer_cancel_event.clear()
                self.send_response(206 if is_partial else 200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Accept-Ranges", "bytes")
                if is_partial and size > 0:
                    self.send_header("Content-Range", f"bytes {start_offset}-{end_offset}/{size}")
                self.send_header("Content-Disposition", f'attachment; filename="{safe_name}"')
                self.send_header("Content-Length", str(to_send))
                self.send_header("Connection", "close")
                self.end_headers()

                sent_this_conn = 0
                last_emit_ts = time.monotonic()
                start_ts = last_emit_ts
                state.update_transfer("Sending to Phone", safe_name, start_offset, size, True, 0.0)
                cancelled = False
                try:
                    with open(full, "rb") as fh:
                        if start_offset > 0:
                            fh.seek(start_offset)
                        remaining = to_send
                        while remaining > 0:
                            if state.transfer_cancel_event.is_set():
                                cancelled = True
                                break
                            chunk = fh.read(min(TRANSFER_CHUNK_SIZE, remaining))
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                            sent_this_conn += len(chunk)
                            remaining -= len(chunk)
                            total_done = start_offset + sent_this_conn
                            now_ts = time.monotonic()
                            if now_ts - last_emit_ts >= 0.22 or remaining == 0:
                                last_emit_ts = now_ts
                                spd = sent_this_conn / max(0.001, now_ts - start_ts)
                                state.update_transfer("Sending to Phone", safe_name, total_done, size, True, spd)
                    self.wfile.flush()
                except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError, OSError):
                    cancelled = state.transfer_cancel_event.is_set()
                finally:
                    total_done = start_offset + sent_this_conn
                    final_spd = sent_this_conn / max(0.001, time.monotonic() - start_ts)
                    if cancelled:
                        state.update_transfer("Cancelled", safe_name, total_done, size, False, 0.0)
                        state.record_history("cancelled", safe_name, total_done, full, 0.0)
                    elif total_done >= size:
                        state.update_transfer("Sent to Phone", safe_name, total_done, size, False, final_spd)
                        state.record_history("sent", safe_name, total_done, full, final_spd)
                    else:
                        state.update_transfer("Paused / Incomplete", safe_name, total_done, size, False, 0.0)
            else:
                self.send_error(404)

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            params = urllib.parse.parse_qs(parsed.query)
            length = int(self.headers.get("Content-Length", "0"))
            if parsed.path == "/transfer-cancel":
                sid = params.get("session", [""])[0].strip()
                if not sid and 0 < length < 65536:
                    body_bytes = self.rfile.read(length)
                    try:
                        sid = str(json.loads(body_bytes.decode("utf-8")).get("session", "")).strip()
                    except Exception:
                        pass
                state.request_cancel_transfer(sid)
                self._send_json(200, {"ok": True, "cancelled": True})
                return

            if parsed.path == "/transfer-pause":
                sid = params.get("session", [""])[0].strip()
                if 0 < length < 65536:
                    self.rfile.read(length)
                state.set_remote_paused(sid, True)
                self._send_json(200, {"ok": True, "paused": True})
                return

            if parsed.path == "/transfer-resume":
                sid = params.get("session", [""])[0].strip()
                if 0 < length < 65536:
                    self.rfile.read(length)
                state.set_remote_paused(sid, False)
                self._send_json(200, {"ok": True, "paused": False})
                return

            if parsed.path == "/upload":
                sid = params.get("session", [""])[0].strip()
                offset = max(0, int(params.get("offset", ["0"])[0] or "0"))
                total_param = max(0, int(params.get("total", [str(length)])[0] or str(length)))
                total_expected = max(total_param, offset + length)

                raw_name = urllib.parse.unquote(self.headers.get("X-Filename", "shared_file.bin"))
                safe_name = sanitize_filename(raw_name)
                os.makedirs(state.shared_dir, exist_ok=True)
                if sid:
                    state.cancelled_sessions.discard(sid)
                    state.paused_sessions.discard(sid)
                    state.session_control_commands.pop(sid, None)
                    part_entry = state.upload_parts.get(sid)
                    part_path = part_entry[0] if part_entry else os.path.join(state.shared_dir, f".upload_{sanitize_filename(sid)}.gptpart")
                    state.upload_parts[sid] = (part_path, safe_name)
                    state.active_transfer_session = sid
                else:
                    state.transfer_cancel_event.clear()
                    part_path = unique_target_path(state.shared_dir, safe_name)

                remaining = length
                received = 0
                start_ts = time.monotonic()
                last_emit_ts = start_ts
                state.update_transfer("Receiving from Phone", safe_name, offset, total_expected, True, 0.0, paused=False)
                cancelled = False
                completed = False
                saved_path = part_path
                try:
                    mode = "r+b" if (offset > 0 and os.path.exists(part_path)) else "wb"
                    with open(part_path, mode) as out:
                        if offset > 0 and mode == "r+b":
                            out.seek(offset)
                        while remaining > 0:
                            is_cancelled = (sid in state.cancelled_sessions) if sid else state.transfer_cancel_event.is_set()
                            if is_cancelled:
                                cancelled = True
                                break
                            chunk = self.rfile.read(min(TRANSFER_CHUNK_SIZE, remaining))
                            if not chunk:
                                break
                            out.write(chunk)
                            remaining -= len(chunk)
                            received += len(chunk)
                            overall_done = offset + received
                            now_ts = time.monotonic()
                            if (now_ts - last_emit_ts >= 0.22 or remaining == 0) and not (sid and sid in state.paused_sessions):
                                last_emit_ts = now_ts
                                spd = received / max(0.001, now_ts - start_ts)
                                state.update_transfer("Receiving from Phone", safe_name, overall_done, total_expected, True, spd, paused=False)
                    overall_done = offset + received
                    if cancelled or (sid and sid in state.cancelled_sessions):
                        if sid:
                            state.cancelled_sessions.discard(sid)
                        try:
                            if sid:
                                state.upload_parts.pop(sid, None)
                            if os.path.exists(part_path):
                                os.remove(part_path)
                        except OSError:
                            pass
                        state.update_transfer("Cancelled", safe_name, overall_done, total_expected, False, 0.0)
                        state.record_history("cancelled", safe_name, overall_done, "", 0.0)
                        self._send_json(200, {"ok": False, "cancelled": True})
                        return

                    final_spd = received / max(0.001, time.monotonic() - start_ts)
                    if sid and (total_expected <= 0 or overall_done >= total_expected):
                        final_dest = unique_target_path(state.shared_dir, safe_name)
                        os.replace(part_path, final_dest)
                        state.upload_parts.pop(sid, None)
                        saved_path = final_dest
                        completed = True
                    elif not sid and remaining == 0:
                        completed = True

                    saved_name = os.path.basename(saved_path if completed else safe_name)
                    if completed:
                        state.update_transfer("Received from Phone", saved_name, overall_done, total_expected, False, final_spd)
                        state.record_history("received", saved_name, overall_done, saved_path, final_spd)
                        state.notify_files_changed()
                        self._send_json(200, {"ok": True, "savedAs": saved_name, "received": overall_done})
                    else:
                        state.update_transfer("Paused", safe_name, overall_done, total_expected, False, 0.0)
                        self._send_json(200, {"ok": True, "paused": True, "received": overall_done})
                except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError, OSError):
                    overall_done = offset + received
                    was_cancelled = (sid in state.cancelled_sessions) if sid else state.transfer_cancel_event.is_set()
                    if was_cancelled and os.path.exists(part_path):
                        if sid:
                            state.cancelled_sessions.discard(sid)
                        try:
                            os.remove(part_path)
                        except OSError:
                            pass
                        state.update_transfer("Cancelled", safe_name, overall_done, total_expected, False, 0.0)
                    elif sid:
                        state.update_transfer("Paused", safe_name, overall_done, total_expected, False, 0.0)
                    else:
                        state.update_transfer("Failed", safe_name, overall_done, total_expected, False, 0.0)
            elif parsed.path == "/phone-frame":
                if length <= 0 or length > 5 * 1024 * 1024:
                    self.send_error(400)
                    return
                data = self.rfile.read(length)
                state.update_phone_frame(data)
                self.send_response(200)
                self.send_header("Content-Length", "2")
                self.send_header("Connection", "close")
                self.end_headers()
                self.wfile.write(b"OK")
                self.wfile.flush()
            else:
                self.send_error(404)

    try:
        httpd = ThreadingHttpServer(("0.0.0.0", FILE_HTTP_PORT), Handler)
    except OSError as exc:
        state.report_bind_error(f"HTTP {FILE_HTTP_PORT}: {exc}")
        return
    httpd.timeout = 0.5
    try:
        while not stop_event.is_set():
            httpd.handle_request()
    finally:
        httpd.server_close()


# ---- Windows Startup, Install & Firewall Helpers ----

def _startup_shortcut_path() -> str:
    appdata = os.environ.get("APPDATA", os.path.expanduser("~"))
    return os.path.join(
        appdata, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "GptRemotePC.lnk"
    )


def _installed_exe_path() -> str:
    local_appdata = os.environ.get("LOCALAPPDATA", os.path.expanduser("~"))
    return os.path.join(local_appdata, "Programs", "GPT Remote PC", "GptRemotePC.exe")


def is_autostart_enabled() -> bool:
    return os.path.exists(_startup_shortcut_path())


def toggle_autostart() -> tuple[bool, str]:
    shortcut = _startup_shortcut_path()
    if os.path.exists(shortcut):
        os.remove(shortcut)
        return False, "Auto-start at Windows login removed."

    is_frozen = getattr(sys, "frozen", False)
    if is_frozen:
        target_exe = os.path.abspath(sys.executable)
        if "temp" in target_exe.lower():
            dest_exe = _installed_exe_path()
            os.makedirs(os.path.dirname(dest_exe), exist_ok=True)
            if os.path.abspath(dest_exe) != target_exe:
                shutil.copy2(target_exe, dest_exe)
            target_exe = dest_exe
        target_path = target_exe
        args = "--tray"
        workdir = os.path.dirname(target_exe)
    else:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        vbs = os.path.join(script_dir, "start_hidden.vbs")
        target_path = "wscript.exe"
        args = f'"{vbs}"'
        workdir = script_dir

    ps_cmd = (
        "$ws = New-Object -ComObject WScript.Shell; "
        f"$s = $ws.CreateShortcut('{shortcut}'); "
        f"$s.TargetPath = '{target_path}'; "
        f"$s.Arguments = '{args}'; "
        f"$s.WorkingDirectory = '{workdir}'; "
        "$s.Save()"
    )
    subprocess.run(
        ["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps_cmd],
        creationflags=0x08000000,
        check=True,
    )
    return True, f"Auto-start enabled for:\n{target_path}"


def allow_windows_firewall() -> None:
    cmd = (
        'netsh advfirewall firewall delete rule name="gptremotepc.exe" >nul 2>&1 & '
        'netsh advfirewall firewall delete rule name="GPT Remote PC TCP 58765" >nul 2>&1 & '
        'netsh advfirewall firewall delete rule name="GPT Remote PC UDP 58766" >nul 2>&1 & '
        'netsh advfirewall firewall delete rule name="GPT Remote PC Screen 58767" >nul 2>&1 & '
        'netsh advfirewall firewall delete rule name="GPT Remote PC Files 58768" >nul 2>&1 & '
        'netsh advfirewall firewall add rule name="GPT Remote PC TCP 58765" dir=in action=allow protocol=TCP localport=58765 profile=any & '
        'netsh advfirewall firewall add rule name="GPT Remote PC UDP 58766" dir=in action=allow protocol=UDP localport=58766 profile=any & '
        'netsh advfirewall firewall add rule name="GPT Remote PC Screen 58767" dir=in action=allow protocol=TCP localport=58767 profile=any & '
        'netsh advfirewall firewall add rule name="GPT Remote PC Files 58768" dir=in action=allow protocol=TCP localport=58768 profile=any'
    )
    ctypes.windll.shell32.ShellExecuteW(None, "runas", "cmd.exe", f"/c {cmd}", None, 0)


def stop_stale_windows_instances() -> None:
    """Terminate older GptRemotePC.exe instances so one clean instance owns all ports."""
    if platform.system() != "Windows":
        return
    my_pid = os.getpid()
    parent_pid = os.getppid()
    try:
        subprocess.run(
            [
                "taskkill",
                "/F",
                "/FI",
                f"PID ne {my_pid}",
                "/FI",
                f"PID ne {parent_pid}",
                "/IM",
                "GptRemotePC.exe",
            ],
            creationflags=0x08000000,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=4,
        )
        time.sleep(0.35)
    except Exception:
        pass


# ---- Windows Desktop GUI + System Tray ----

def run_windows_desktop_app(
    state: RemoteServerState,
    stop_event: threading.Event,
    start_minimized: bool = False,
) -> None:
    import tkinter as tk
    from tkinter import filedialog, messagebox

    root = tk.Tk()
    root.title("GPT Remote PC")
    root.geometry("940x560")
    root.minsize(880, 520)
    root.configure(bg="#0B0E14")

    def resolve_ico_path() -> str | None:
        base_dir = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
        for cand in [
            os.path.join(base_dir, "gpt_remote.ico"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "gpt_remote.ico"),
        ]:
            if os.path.isfile(cand):
                return cand
        return None

    ico_file = resolve_ico_path()
    if ico_file:
        try:
            root.iconbitmap(ico_file)
        except Exception:
            pass

    BG = "#0B0E14"
    CARD = "#151A24"
    ELEV = "#1F2735"
    BORDER = "#2B3548"
    ORANGE = "#FF6900"
    BLUE = "#38BDF8"
    GREEN = "#34D399"
    TXT = "#F8FAFC"
    SUB = "#94A3B8"

    def quit_completely():
        stop_event.set()
        root.quit()
        root.destroy()

    def minimize_to_tray():
        root.withdraw()

    root.protocol("WM_DELETE_WINDOW", minimize_to_tray)

    # Top Landscape Header Bar
    hdr = tk.Frame(root, bg=BG)
    hdr.pack(fill="x", padx=22, pady=(16, 10))

    title_col = tk.Frame(hdr, bg=BG)
    title_col.pack(side="left")
    tk.Label(
        title_col,
        text="GPT Remote PC",
        bg=BG,
        fg=TXT,
        font=("Segoe UI", 20, "bold"),
    ).pack(anchor="w")
    tk.Label(
        title_col,
        text="Mouse, Touchpad, Screen Mirror, QR Connect & Two-Way File Share",
        bg=BG,
        fg=SUB,
        font=("Segoe UI", 9),
    ).pack(anchor="w")

    qr_visible = tk.BooleanVar(value=False)

    btn_hdr = tk.Frame(hdr, bg=BG)
    btn_hdr.pack(side="right", pady=4)

    qr_toggle_btn = tk.Button(
        btn_hdr,
        text="Show QR Code",
        bg=ORANGE,
        fg="#FFFFFF",
        activebackground="#E05D00",
        activeforeground="#FFFFFF",
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=14,
        pady=7,
        cursor="hand2",
    )
    qr_toggle_btn.pack(side="left")

    # Main 2-Column Landscape Grid
    main_row = tk.Frame(root, bg=BG)
    main_row.pack(fill="both", expand=True, padx=22, pady=(4, 18))

    left_col = tk.Frame(main_row, bg=BG, width=410)
    left_col.pack(side="left", fill="y", padx=(0, 14))
    left_col.pack_propagate(False)

    right_col = tk.Frame(main_row, bg=BG)
    right_col.pack(side="left", fill="both", expand=True)

    # ---- Left Top: Connection Hub Card (with toggleable QR Code) ----
    conn_card = tk.Frame(left_col, bg=CARD, highlightbackground=BORDER, highlightthickness=1)
    conn_card.pack(fill="x", pady=(0, 12))

    conn_top = tk.Frame(conn_card, bg=CARD)
    conn_top.pack(fill="x", padx=16, pady=(14, 6))
    tk.Label(conn_top, text="Connection Hub", bg=CARD, fg=TXT, font=("Segoe UI", 12, "bold")).pack(
        side="left"
    )
    status_badge_lbl = tk.Label(
        conn_top,
        text="Waiting for phone",
        bg=ELEV,
        fg=SUB,
        font=("Segoe UI", 8, "bold"),
        padx=10,
        pady=3,
    )
    status_badge_lbl.pack(side="right")

    info_panel = tk.Frame(conn_card, bg=CARD)
    info_panel.pack(fill="x", padx=16, pady=(0, 14))

    ip_var = tk.StringVar()
    ports_var = tk.StringVar()
    selected_ip_idx = {"i": 0}

    def current_primary_ip() -> str:
        ips = get_local_ips()
        if not ips:
            return ""
        idx = selected_ip_idx["i"] % len(ips)
        return ips[idx]

    def copy_ip():
        ip = current_primary_ip()
        if ip:
            root.clipboard_clear()
            root.clipboard_append(ip)
            ip_var.set(f"Copied Wi-Fi IP: {ip}")
            root.after(1600, refresh_status)

    def cycle_ip():
        selected_ip_idx["i"] += 1
        refresh_status()
        if qr_visible.get():
            draw_qr_code()

    tk.Label(info_panel, textvariable=ip_var, bg=CARD, fg=BLUE, font=("Segoe UI", 11, "bold")).pack(
        anchor="w", pady=(2, 2)
    )
    tk.Label(info_panel, textvariable=ports_var, bg=CARD, fg=SUB, font=("Segoe UI", 9)).pack(
        anchor="w", pady=(0, 10)
    )

    ip_btn_row = tk.Frame(info_panel, bg=CARD)
    ip_btn_row.pack(fill="x")
    tk.Button(
        ip_btn_row,
        text="Copy IP",
        command=copy_ip,
        bg=ELEV,
        fg=TXT,
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=10,
        pady=5,
        cursor="hand2",
    ).pack(side="left", padx=(0, 8))
    tk.Button(
        ip_btn_row,
        text="Switch Network IP",
        command=cycle_ip,
        bg=ELEV,
        fg=SUB,
        relief="flat",
        font=("Segoe UI", 9),
        padx=10,
        pady=5,
        cursor="hand2",
    ).pack(side="left")

    # Inline QR Panel (hidden until Show QR Code clicked)
    qr_panel = tk.Frame(conn_card, bg=CARD)
    qr_canvas = tk.Canvas(qr_panel, width=196, height=196, bg="#FFFFFF", highlightthickness=0)
    qr_canvas.pack(pady=(4, 6))
    qr_caption = tk.Label(qr_panel, text="", bg=CARD, fg=SUB, font=("Segoe UI", 8))
    qr_caption.pack(pady=(0, 12))

    def draw_qr_code():
        qr_canvas.delete("all")
        ip = current_primary_ip()
        if not ip:
            qr_caption.configure(text="Connect PC to Wi-Fi first to generate QR code")
            return
        host_name = urllib.parse.quote(socket.gethostname())
        payload = f"gptremote://pc?host={ip}&port={TCP_PORT}&name={host_name}"
        try:
            import qrcode
            qr = qrcode.QRCode(
                version=None,
                error_correction=qrcode.constants.ERROR_CORRECT_M,
                box_size=1,
                border=2,
            )
            qr.add_data(payload)
            qr.make(fit=True)
            matrix = qr.get_matrix()
            n = len(matrix)
            size = 196
            cell = size / n
            qr_canvas.create_rectangle(0, 0, size, size, fill="#FFFFFF", outline="")
            for r, row_vals in enumerate(matrix):
                for c, val in enumerate(row_vals):
                    if val:
                        x0 = c * cell
                        y0 = r * cell
                        qr_canvas.create_rectangle(
                            x0, y0, x0 + cell + 0.5, y0 + cell + 0.5, fill="#0B0E14", outline=""
                        )
            qr_caption.configure(text=f"Scan in phone app -> Connect PC -> Scan QR  ({ip})")
        except Exception as exc:
            qr_canvas.create_text(
                98,
                98,
                text=f"QR requires 'qrcode'\n{exc}\nUse IP: {ip}",
                fill="#0B0E14",
                font=("Segoe UI", 9),
                justify="center",
            )
            qr_caption.configure(text=f"Enter IP manually on phone: {ip}")

    def toggle_qr():
        show = not qr_visible.get()
        qr_visible.set(show)
        if show:
            info_panel.pack_forget()
            qr_panel.pack(fill="x", padx=16, pady=(0, 6))
            draw_qr_code()
            qr_toggle_btn.configure(text="Hide QR Code", bg=ELEV, fg=TXT)
        else:
            qr_panel.pack_forget()
            info_panel.pack(fill="x", padx=16, pady=(0, 14))
            qr_toggle_btn.configure(text="Show QR Code", bg=ORANGE, fg="#FFFFFF")

    qr_toggle_btn.configure(command=toggle_qr)

    def refresh_status():
        ips = get_local_ips()
        primary = current_primary_ip()
        host = socket.gethostname()
        ip_var.set(f"{host}   •   {primary if primary else 'No Wi-Fi'}")
        if len(ips) > 1:
            ports_var.set(f"All IPs: {', '.join(ips)}   •   Ports {TCP_PORT}/{SCREEN_PORT}/{FILE_HTTP_PORT}")
        else:
            ports_var.set(f"Ports: Control {TCP_PORT} • Screen {SCREEN_PORT} • Files {FILE_HTTP_PORT}")

        if state.bind_errors:
            status_badge_lbl.configure(text="Port Conflict!", bg="#3B1A1E", fg="#F87171")
            ports_var.set("Port error: " + "; ".join(state.bind_errors))
        else:
            c = state.count()
            if c > 0:
                status_badge_lbl.configure(text=f"Connected ({c})", bg="#123829", fg=GREEN)
            else:
                status_badge_lbl.configure(text="Ready on Wi-Fi", bg=ELEV, fg=BLUE)

    # ---- Left Bottom: Screen Share & Startup Card ----
    scr_card = tk.Frame(left_col, bg=CARD, highlightbackground=BORDER, highlightthickness=1)
    scr_card.pack(fill="both", expand=True)
    tk.Label(scr_card, text="Screen & Startup", bg=CARD, fg=TXT, font=("Segoe UI", 12, "bold")).pack(
        anchor="w", padx=16, pady=(14, 6)
    )

    allow_screen_var = tk.BooleanVar(value=state.allow_pc_screen_share)

    def on_toggle_screen():
        state.allow_pc_screen_share = allow_screen_var.get()

    tk.Checkbutton(
        scr_card,
        text="Share PC Screen with Phone",
        variable=allow_screen_var,
        command=on_toggle_screen,
        bg=CARD,
        fg=TXT,
        selectcolor=ELEV,
        activebackground=CARD,
        activeforeground=TXT,
        font=("Segoe UI", 9),
    ).pack(anchor="w", padx=12, pady=(0, 6))

    autostart_var = tk.BooleanVar(value=is_autostart_enabled())

    def on_toggle_autostart():
        try:
            enabled, msg = toggle_autostart()
            autostart_var.set(enabled)
            messagebox.showinfo("GPT Remote Startup", msg)
        except Exception as exc:
            autostart_var.set(is_autostart_enabled())
            messagebox.showerror("Error", f"Could not update startup: {exc}")

    tk.Checkbutton(
        scr_card,
        text="Launch minimized to Tray on Windows login",
        variable=autostart_var,
        command=on_toggle_autostart,
        bg=CARD,
        fg=TXT,
        selectcolor=ELEV,
        activebackground=CARD,
        activeforeground=TXT,
        font=("Segoe UI", 9),
    ).pack(anchor="w", padx=12, pady=(0, 12))

    phone_win_ref: dict[str, tk.Toplevel | None] = {"win": None}
    phone_label_ref: dict[str, tk.Label | None] = {"lbl": None}
    phone_photo_ref: dict[str, object | None] = {"img": None}

    def open_phone_mirror_window():
        existing = phone_win_ref["win"]
        if existing and existing.winfo_exists():
            existing.lift()
            return
        win = tk.Toplevel(root)
        win.title("Phone Screen Mirror")
        win.geometry("400x720")
        win.configure(bg="#000000")
        lbl = tk.Label(
            win,
            text="Waiting for phone screen cast...\n\nOn your phone, open:\nPC Remote -> Screen -> Cast Screen",
            bg="#000000",
            fg=SUB,
            font=("Segoe UI", 10),
        )
        lbl.pack(fill="both", expand=True)
        phone_win_ref["win"] = win
        phone_label_ref["lbl"] = lbl
        if state.latest_phone_frame:
            render_phone_frame(state.latest_phone_frame)

    def render_phone_frame(jpeg_bytes: bytes):
        win = phone_win_ref.get("win")
        lbl = phone_label_ref.get("lbl")
        if not win or not win.winfo_exists() or not lbl:
            return
        try:
            from PIL import Image, ImageTk
            img = Image.open(io.BytesIO(jpeg_bytes))
            w = max(240, win.winfo_width() - 12)
            h = max(360, win.winfo_height() - 12)
            img.thumbnail((w, h), Image.Resampling.BILINEAR)
            photo = ImageTk.PhotoImage(img)
            phone_photo_ref["img"] = photo
            lbl.configure(image=photo, text="")
        except Exception:
            pass

    tk.Button(
        scr_card,
        text="Open Phone Screen Viewer",
        command=open_phone_mirror_window,
        bg=ELEV,
        fg=TXT,
        activebackground=BORDER,
        activeforeground=TXT,
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=14,
        pady=8,
        cursor="hand2",
    ).pack(anchor="w", padx=16, pady=(0, 14))

    # ---- Right Top: Live File Transfer Progress Card ----
    prog_card = tk.Frame(right_col, bg=CARD, highlightbackground=BORDER, highlightthickness=1)
    prog_card.pack(fill="x", pady=(0, 12))

    prog_hdr = tk.Frame(prog_card, bg=CARD)
    prog_hdr.pack(fill="x", padx=16, pady=(12, 6))
    transfer_title_var = tk.StringVar(value="File Transfer Idle")
    transfer_pct_var = tk.StringVar(value="0%")
    tk.Label(prog_hdr, textvariable=transfer_title_var, bg=CARD, fg=TXT, font=("Segoe UI", 10, "bold")).pack(
        side="left"
    )

    transfer_cancel_btn = tk.Button(
        prog_hdr,
        text="Cancel",
        command=lambda: state.request_cancel_transfer(),
        bg="#3B1A1E",
        fg="#F87171",
        activebackground="#5A2229",
        activeforeground="#FFFFFF",
        relief="flat",
        font=("Segoe UI", 8, "bold"),
        padx=8,
        pady=2,
        cursor="hand2",
    )

    transfer_pause_btn = tk.Button(
        prog_hdr,
        text="Pause",
        command=lambda: state.request_pause_or_resume_transfer(),
        bg=ELEV,
        fg=BLUE,
        activebackground=BORDER,
        activeforeground=TXT,
        relief="flat",
        font=("Segoe UI", 8, "bold"),
        padx=8,
        pady=2,
        cursor="hand2",
    )

    transfer_pct_lbl = tk.Label(
        prog_hdr, textvariable=transfer_pct_var, bg=CARD, fg=ORANGE, font=("Segoe UI", 13, "bold")
    )
    transfer_pct_lbl.pack(side="right")

    prog_canvas = tk.Canvas(prog_card, height=14, bg=ELEV, highlightthickness=0)
    prog_canvas.pack(fill="x", padx=16, pady=(0, 6))
    transfer_detail_var = tk.StringVar(value="Select or send any file between Phone and PC")
    tk.Label(prog_card, textvariable=transfer_detail_var, bg=CARD, fg=SUB, font=("Segoe UI", 8)).pack(
        anchor="w", padx=16, pady=(0, 12)
    )

    def draw_progress_bar(fraction: float, active: bool, paused: bool = False):
        prog_canvas.delete("all")
        w = prog_canvas.winfo_width()
        if w < 10:
            w = 430
        h = 14
        fill_w = max(0, min(w, int(w * fraction)))
        prog_canvas.create_rectangle(0, 0, w, h, fill=ELEV, outline="")
        if fill_w > 0:
            color = "#FBBF24" if paused else (ORANGE if active else GREEN)
            prog_canvas.create_rectangle(0, 0, fill_w, h, fill=color, outline="")

    def refresh_transfer():
        info = state.transfer_info
        if not info:
            transfer_pause_btn.pack_forget()
            transfer_cancel_btn.pack_forget()
            transfer_title_var.set("File Transfer Ready")
            transfer_pct_var.set("")
            transfer_detail_var.set("Progress appears here when sending or receiving files")
            draw_progress_bar(0.0, False)
            return
        is_paused = bool(info.get("paused"))
        if info.get("active"):
            transfer_cancel_btn.pack(side="right", padx=(0, 6))
            transfer_pause_btn.configure(
                text="Resume" if is_paused else "Pause",
                fg=GREEN if is_paused else BLUE,
            )
            transfer_pause_btn.pack(side="right", padx=(0, 6))
        else:
            transfer_pause_btn.pack_forget()
            transfer_cancel_btn.pack_forget()
        total = max(1, info["total"])
        done = info["done"]
        frac = min(1.0, done / total) if info["total"] > 0 else (1.0 if not info["active"] else 0.5)
        pct = int(round(frac * 100))
        done_mb = done / (1024.0 * 1024.0)
        tot_mb = total / (1024.0 * 1024.0)
        speed_txt = "Paused" if is_paused else format_speed(info.get("speed_bps", 0.0))
        transfer_title_var.set(f"{info['direction']}: {info['filename']}")
        transfer_pct_var.set(f"{pct}%")
        d_lower = str(info.get("direction", "")).lower()
        pct_color = "#FBBF24" if (is_paused or "pause" in d_lower or "cancel" in d_lower) else (ORANGE if info["active"] else GREEN)
        transfer_pct_lbl.configure(fg=pct_color)
        transfer_detail_var.set(f"{done_mb:.2f} MB / {tot_mb:.2f} MB   •   {speed_txt}")
        draw_progress_bar(frac, info["active"], paused=is_paused)

    prog_canvas.bind("<Configure>", lambda _e: refresh_transfer())

    # ---- Right Bottom: Two-Way File Share & History Hub ----
    file_card = tk.Frame(right_col, bg=CARD, highlightbackground=BORDER, highlightthickness=1)
    file_card.pack(fill="both", expand=True)

    file_hdr = tk.Frame(file_card, bg=CARD)
    file_hdr.pack(fill="x", padx=16, pady=(14, 6))
    tk.Label(file_hdr, text="Shared Files & History", bg=CARD, fg=TXT, font=("Segoe UI", 12, "bold")).pack(
        side="left"
    )
    btn_row = tk.Frame(file_hdr, bg=CARD)
    btn_row.pack(side="right")

    history_mode = tk.StringVar(value="all")
    visible_paths: list[str] = []

    tab_row = tk.Frame(file_card, bg=CARD)
    tab_row.pack(fill="x", padx=16, pady=(0, 8))

    tab_all_btn = tk.Button(tab_row, text="All Files", relief="flat", font=("Segoe UI", 8, "bold"), padx=10, pady=4, cursor="hand2")
    tab_sent_btn = tk.Button(tab_row, text="Sent", relief="flat", font=("Segoe UI", 8, "bold"), padx=10, pady=4, cursor="hand2")
    tab_recv_btn = tk.Button(tab_row, text="Received", relief="flat", font=("Segoe UI", 8, "bold"), padx=10, pady=4, cursor="hand2")
    tab_all_btn.pack(side="left", padx=(0, 6))
    tab_sent_btn.pack(side="left", padx=(0, 6))
    tab_recv_btn.pack(side="left")

    files_list_wrap = tk.Frame(file_card, bg=ELEV)
    files_canvas = tk.Canvas(files_list_wrap, bg=ELEV, highlightthickness=0, borderwidth=0)
    files_scroll = tk.Scrollbar(files_list_wrap, orient="vertical", command=files_canvas.yview)
    files_inner = tk.Frame(files_canvas, bg=ELEV)
    files_canvas_win = files_canvas.create_window((0, 0), window=files_inner, anchor="nw")
    files_canvas.configure(yscrollcommand=files_scroll.set)
    files_canvas.pack(side="left", fill="both", expand=True)
    files_scroll.pack(side="right", fill="y")

    def _on_inner_cfg(_e=None):
        files_canvas.configure(scrollregion=files_canvas.bbox("all"))

    def _on_canvas_cfg(event):
        files_canvas.itemconfigure(files_canvas_win, width=event.width)

    files_inner.bind("<Configure>", _on_inner_cfg)
    files_canvas.bind("<Configure>", _on_canvas_cfg)

    def _on_wheel(event):
        try:
            files_canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")
        except Exception:
            pass

    files_canvas.bind("<MouseWheel>", _on_wheel)

    thumb_photo_cache: dict[str, object] = {}

    def format_size_short(sz: int) -> str:
        if sz >= 1024 * 1024:
            return f"{sz / (1024.0 * 1024.0):.1f} MB"
        return f"{max(1, sz // 1024)} KB"

    def file_badge_and_color(filename: str) -> tuple[str, str]:
        ext = os.path.splitext(filename)[1].lower()
        if ext in (".mp4", ".mkv", ".mov", ".webm", ".avi", ".m4v"):
            return "VID", "#38BDF8"
        if ext in (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"):
            return "IMG", "#F472B6"
        if ext == ".apk":
            return "APK", "#34D399"
        if ext == ".pdf":
            return "PDF", "#F87171"
        if ext in (".zip", ".rar", ".7z", ".tar", ".gz"):
            return "ZIP", "#FBBF24"
        if ext in (".mp3", ".wav", ".m4a", ".aac", ".flac"):
            return "AUD", "#2DD4BF"
        if ext in (".exe", ".msi"):
            return "APP", "#A78BFA"
        return "FILE", TXT

    def open_path_directly(path: str):
        if path and os.path.exists(path):
            try:
                os.startfile(path)
            except Exception as exc:
                messagebox.showerror("Open File", f"Could not open file: {exc}")
        else:
            messagebox.showwarning("File Missing", f"File no longer exists:\n{path}")

    def attach_thumb_async(lbl_widget: tk.Label, full_path: str):
        cached = thumb_photo_cache.get(full_path)
        if cached is not None:
            lbl_widget.configure(image=cached, text="", width=40, height=40)
            return

        def worker():
            pil_im = generate_file_preview_pil(full_path, 40)
            if pil_im is None:
                return

            def apply_ui():
                if not lbl_widget.winfo_exists():
                    return
                try:
                    from PIL import ImageTk
                    photo = ImageTk.PhotoImage(pil_im)
                    if len(thumb_photo_cache) > 120:
                        thumb_photo_cache.clear()
                    thumb_photo_cache[full_path] = photo
                    lbl_widget.configure(image=photo, text="", width=40, height=40)
                except Exception:
                    pass

            try:
                root.after(0, apply_ui)
            except Exception:
                pass

        threading.Thread(target=worker, daemon=True).start()

    def build_file_row(name: str, sub_txt: str, full_path: str):
        badge, color = file_badge_and_color(name)
        row_card = tk.Frame(files_inner, bg=CARD, highlightbackground=BORDER, highlightthickness=1, cursor="hand2")
        row_card.pack(fill="x", padx=8, pady=4)

        thumb_lbl = tk.Label(
            row_card,
            text=badge,
            bg=ELEV,
            fg=color,
            font=("Segoe UI", 8, "bold"),
            width=5,
            height=2,
        )
        thumb_lbl.pack(side="left", padx=(8, 10), pady=6)
        if full_path and os.path.isfile(full_path):
            attach_thumb_async(thumb_lbl, full_path)

        text_col = tk.Frame(row_card, bg=CARD)
        text_col.pack(side="left", fill="both", expand=True, pady=6)
        name_lbl = tk.Label(text_col, text=name, bg=CARD, fg=TXT, font=("Segoe UI", 9, "bold"), anchor="w")
        name_lbl.pack(fill="x")
        sub_lbl = tk.Label(text_col, text=sub_txt, bg=CARD, fg=SUB, font=("Segoe UI", 8), anchor="w")
        sub_lbl.pack(fill="x")

        open_btn = tk.Button(
            row_card,
            text="Open",
            command=lambda p=full_path: open_path_directly(p),
            bg=ELEV,
            fg=BLUE,
            activebackground=BORDER,
            activeforeground=TXT,
            relief="flat",
            font=("Segoe UI", 8, "bold"),
            padx=10,
            pady=4,
            cursor="hand2",
        )
        open_btn.pack(side="right", padx=(6, 10))

        for w_item in (row_card, thumb_lbl, text_col, name_lbl, sub_lbl):
            w_item.bind("<Double-Button-1>", lambda _e, p=full_path: open_path_directly(p))
            w_item.bind("<MouseWheel>", _on_wheel)

    def refresh_files():
        for child in files_inner.winfo_children():
            child.destroy()
        visible_paths.clear()
        os.makedirs(state.shared_dir, exist_ok=True)
        mode = history_mode.get()

        sent_count = sum(1 for h in state.transfer_history if h["kind"] == "sent")
        recv_count = sum(1 for h in state.transfer_history if h["kind"] == "received")
        tab_all_btn.configure(
            bg=ORANGE if mode == "all" else ELEV,
            fg="#FFFFFF" if mode == "all" else SUB,
        )
        tab_sent_btn.configure(
            text=f"Sent ({sent_count})",
            bg=ORANGE if mode == "sent" else ELEV,
            fg="#FFFFFF" if mode == "sent" else SUB,
        )
        tab_recv_btn.configure(
            text=f"Received ({recv_count})",
            bg=ORANGE if mode == "received" else ELEV,
            fg="#FFFFFF" if mode == "received" else SUB,
        )

        if mode == "all":
            entries = sorted(
                e for e in os.listdir(state.shared_dir)
                if not e.endswith(".gptpart") and os.path.isfile(os.path.join(state.shared_dir, e))
            )
            if not entries:
                tk.Label(
                    files_inner,
                    text="Folder empty — click + Share File or send from phone",
                    bg=ELEV,
                    fg=SUB,
                    font=("Segoe UI", 9),
                    pady=24,
                ).pack(fill="x")
            else:
                for e in entries:
                    full = os.path.join(state.shared_dir, e)
                    sz = os.path.getsize(full)
                    visible_paths.append(full)
                    build_file_row(e, format_size_short(sz), full)
        else:
            items = [h for h in state.transfer_history if h["kind"] == mode]
            if not items:
                label = "sent to phone" if mode == "sent" else "received from phone"
                tk.Label(
                    files_inner,
                    text=f"No files {label} in this session yet",
                    bg=ELEV,
                    fg=SUB,
                    font=("Segoe UI", 9),
                    pady=24,
                ).pack(fill="x")
            else:
                for h in items:
                    spd = format_speed(h.get("speed_bps", 0.0))
                    visible_paths.append(h["path"])
                    build_file_row(
                        h["filename"],
                        f"{h['ts']}  •  {format_size_short(h['size'])}  •  {spd}",
                        h["path"],
                    )
        files_canvas.yview_moveto(0.0)

    def set_history_mode(mode: str):
        history_mode.set(mode)
        refresh_files()

    tab_all_btn.configure(command=lambda: set_history_mode("all"))
    tab_sent_btn.configure(command=lambda: set_history_mode("sent"))
    tab_recv_btn.configure(command=lambda: set_history_mode("received"))

    def add_files_to_share():
        picked = filedialog.askopenfilenames(title="Select file(s) to share with phone")
        for src in picked:
            name = os.path.basename(src)
            total_sz = max(1, os.path.getsize(src))
            dest = unique_target_path(state.shared_dir, sanitize_filename(name))
            t0 = time.monotonic()
            state.update_transfer("Adding to Share", name, 0, total_sz, True, 0.0)
            shutil.copy2(src, dest)
            spd = total_sz / max(0.001, time.monotonic() - t0)
            saved = os.path.basename(dest)
            state.update_transfer("Ready for Phone", saved, total_sz, total_sz, False, spd)
        refresh_files()

    def open_shared_folder():
        os.makedirs(state.shared_dir, exist_ok=True)
        os.startfile(state.shared_dir)

    tk.Button(
        btn_row,
        text="+ Share File",
        command=add_files_to_share,
        bg=ORANGE,
        fg="#FFFFFF",
        activebackground="#E05D00",
        activeforeground="#FFFFFF",
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=12,
        pady=5,
        cursor="hand2",
    ).pack(side="left")

    tk.Label(
        file_card,
        text="Downloads/GptRemote   •   Double-click any file to open",
        bg=CARD,
        fg=SUB,
        font=("Segoe UI", 8),
    ).pack(anchor="w", padx=16, pady=(0, 8))

    files_list_wrap.pack(fill="both", expand=True, padx=16, pady=(0, 14))

    # Wire live background state updates safely onto Tk thread
    state.on_change = lambda: root.after(0, refresh_status)
    state.on_files_change = lambda: root.after(0, refresh_files)
    state.on_transfer = lambda: root.after(0, refresh_transfer)
    state.on_phone_frame = lambda data: root.after(0, lambda d=data: (
        open_phone_mirror_window() if not (phone_win_ref["win"] and phone_win_ref["win"].winfo_exists()) else None,
        render_phone_frame(d),
    ))

    refresh_status()
    refresh_files()
    refresh_transfer()

    # Background Win32 Tray Icon so closing window keeps service alive in System Tray
    def tray_thread():
        run_windows_tray_bridge(
            state=state,
            stop_event=stop_event,
            on_show_window=lambda: root.after(0, lambda: (root.deiconify(), root.lift())),
            on_exit=lambda: root.after(0, quit_completely),
        )

    threading.Thread(target=tray_thread, daemon=True).start()

    if start_minimized:
        root.withdraw()

    root.mainloop()


def run_windows_tray_bridge(
    state: RemoteServerState,
    stop_event: threading.Event,
    on_show_window,
    on_exit,
) -> None:
    from ctypes import wintypes

    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    shell32 = ctypes.windll.shell32

    LRESULT = ctypes.c_ssize_t
    WNDPROC = ctypes.WINFUNCTYPE(LRESULT, wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM)

    WM_DESTROY = 0x0002
    WM_LBUTTONUP = 0x0202
    WM_RBUTTONUP = 0x0205
    WM_TRAY = 0x8001

    NIM_ADD = 0x00000000
    NIM_DELETE = 0x00000002
    NIF_MESSAGE = 0x00000001
    NIF_ICON = 0x00000002
    NIF_TIP = 0x00000004

    MF_STRING = 0x00000000
    MF_SEPARATOR = 0x00000800
    TPM_LEFTALIGN = 0x0000
    TPM_BOTTOMALIGN = 0x0020
    TPM_RETURNCMD = 0x0100

    ID_OPEN = 1001
    ID_EXIT = 1002

    class WNDCLASSEXW(ctypes.Structure):
        _fields_ = [
            ("cbSize", wintypes.UINT),
            ("style", wintypes.UINT),
            ("lpfnWndProc", WNDPROC),
            ("cbClsExtra", ctypes.c_int),
            ("cbWndExtra", ctypes.c_int),
            ("hInstance", wintypes.HINSTANCE),
            ("hIcon", wintypes.HICON),
            ("hCursor", wintypes.HANDLE),
            ("hbrBackground", wintypes.HBRUSH),
            ("lpszMenuName", wintypes.LPCWSTR),
            ("lpszClassName", wintypes.LPCWSTR),
            ("hIconSm", wintypes.HICON),
        ]

    class GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", wintypes.DWORD),
            ("Data2", wintypes.WORD),
            ("Data3", wintypes.WORD),
            ("Data4", ctypes.c_ubyte * 8),
        ]

    class NOTIFYICONDATAW(ctypes.Structure):
        _fields_ = [
            ("cbSize", wintypes.DWORD),
            ("hWnd", wintypes.HWND),
            ("uID", wintypes.UINT),
            ("uFlags", wintypes.UINT),
            ("uCallbackMessage", wintypes.UINT),
            ("hIcon", wintypes.HICON),
            ("szTip", wintypes.WCHAR * 128),
            ("dwState", wintypes.DWORD),
            ("dwStateMask", wintypes.DWORD),
            ("szInfo", wintypes.WCHAR * 256),
            ("uTimeoutOrVersion", wintypes.UINT),
            ("szInfoTitle", wintypes.WCHAR * 64),
            ("dwInfoFlags", wintypes.DWORD),
            ("guidItem", GUID),
            ("hBalloonIcon", wintypes.HICON),
        ]

    user32.DefWindowProcW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    user32.DefWindowProcW.restype = LRESULT

    hinstance = kernel32.GetModuleHandleW(None)
    base_dir = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    ico_candidates = [
        os.path.join(base_dir, "gpt_remote.ico"),
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "gpt_remote.ico"),
    ]
    hicon = None
    for ico_path in ico_candidates:
        if os.path.isfile(ico_path):
            loaded = user32.LoadImageW(None, ico_path, 1, 0, 0, 0x0010 | 0x0040)
            if loaded:
                hicon = loaded
                break
    if not hicon:
        hicon = shell32.ExtractIconW(hinstance, "shell32.dll", 15)
    if not hicon or hicon == 1:
        hicon = user32.LoadIconW(None, ctypes.cast(32512, wintypes.LPCWSTR))

    def show_menu(hwnd):
        hmenu = user32.CreatePopupMenu()
        user32.AppendMenuW(hmenu, MF_STRING, ID_OPEN, "Open GPT Remote PC")
        user32.AppendMenuW(hmenu, MF_SEPARATOR, 0, None)
        user32.AppendMenuW(hmenu, MF_STRING, ID_EXIT, "Exit")
        pt = wintypes.POINT()
        user32.GetCursorPos(ctypes.byref(pt))
        user32.SetForegroundWindow(hwnd)
        cmd = user32.TrackPopupMenu(
            hmenu, TPM_LEFTALIGN | TPM_BOTTOMALIGN | TPM_RETURNCMD, pt.x, pt.y, 0, hwnd, None
        )
        user32.DestroyMenu(hmenu)
        if cmd == ID_OPEN:
            on_show_window()
        elif cmd == ID_EXIT:
            on_exit()

    def wnd_proc(hwnd, msg, wparam, lparam):
        if msg == WM_TRAY:
            ev = lparam & 0xFFFF
            if ev == WM_LBUTTONUP:
                on_show_window()
            elif ev == WM_RBUTTONUP:
                show_menu(hwnd)
            return 0
        if msg == WM_DESTROY:
            user32.PostQuitMessage(0)
            return 0
        return user32.DefWindowProcW(hwnd, msg, wparam, lparam)

    cb = WNDPROC(wnd_proc)
    cls_name = "GptRemotePcTrayBridge"
    wc = WNDCLASSEXW()
    wc.cbSize = ctypes.sizeof(WNDCLASSEXW)
    wc.lpfnWndProc = cb
    wc.hInstance = hinstance
    wc.hIcon = hicon
    wc.lpszClassName = cls_name
    user32.RegisterClassExW(ctypes.byref(wc))

    hwnd = user32.CreateWindowExW(0, cls_name, "GPT Remote Tray", 0, 0, 0, 0, 0, None, None, hinstance, None)
    nid = NOTIFYICONDATAW()
    nid.cbSize = ctypes.sizeof(NOTIFYICONDATAW)
    nid.hWnd = hwnd
    nid.uID = 1
    nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP
    nid.uCallbackMessage = WM_TRAY
    nid.hIcon = hicon
    nid.szTip = "GPT Remote PC"
    shell32.Shell_NotifyIconW(NIM_ADD, ctypes.byref(nid))

    def watch_stop():
        stop_event.wait()
        user32.PostMessageW(hwnd, WM_DESTROY, 0, 0)

    threading.Thread(target=watch_stop, daemon=True).start()

    msg_struct = wintypes.MSG()
    while user32.GetMessageW(ctypes.byref(msg_struct), None, 0, 0) > 0:
        user32.TranslateMessage(ctypes.byref(msg_struct))
        user32.DispatchMessageW(ctypes.byref(msg_struct))

    shell32.Shell_NotifyIconW(NIM_DELETE, ctypes.byref(nid))


def main() -> None:
    stop_stale_windows_instances()
    backend = create_backend()
    state = RemoteServerState()
    stop_event = threading.Event()

    threading.Thread(target=udp_discovery_loop, args=(state, stop_event), daemon=True).start()
    threading.Thread(target=tcp_server_loop, args=(backend, state, stop_event), daemon=True).start()
    threading.Thread(target=screen_server_loop, args=(state, stop_event), daemon=True).start()
    threading.Thread(target=file_http_server_loop, args=(state, stop_event), daemon=True).start()

    if platform.system() == "Windows":
        start_minimized = "--tray" in sys.argv
        run_windows_desktop_app(state, stop_event, start_minimized=start_minimized)
    else:
        ips = get_local_ips()
        print(f"GPT Remote PC running on '{socket.gethostname()}' ({platform.system()})")
        print(f"Wi-Fi IP(s): {', '.join(ips) if ips else 'unknown'} | Ports: {TCP_PORT}/{SCREEN_PORT}/{FILE_HTTP_PORT}")
        try:
            stop_event.wait()
        except KeyboardInterrupt:
            stop_event.set()


if __name__ == "__main__":
    main()
