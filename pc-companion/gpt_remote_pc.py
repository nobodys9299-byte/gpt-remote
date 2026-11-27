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

def capture_pc_jpeg(max_width: int = 1080, quality: int = 58) -> bytes | None:
    """Captures the primary PC screen as JPEG bytes. Uses mss + Pillow if available, or Pillow ImageGrab."""
    try:
        from PIL import Image
    except ImportError:
        return None

    img = None
    try:
        import mss  # type: ignore
        with mss.mss() as sct:
            mon = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
            raw = sct.grab(mon)
            img = Image.frombytes("RGB", raw.size, raw.bgra, "raw", "BGRX")
    except Exception:
        try:
            from PIL import ImageGrab
            img = ImageGrab.grab()
        except Exception:
            img = None

    if img is None:
        return None

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
        self.on_change = None
        self.on_files_change = None
        self.on_phone_frame = None
        self.on_transfer = None

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
    ) -> None:
        with self._lock:
            self.transfer_info = {
                "direction": direction,
                "filename": filename,
                "done": done_bytes,
                "total": total_bytes,
                "active": active,
                "speed_bps": speed_bps,
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
                msg = json.loads(line)
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
        while not stop_event.is_set():
            if not state.allow_pc_screen_share:
                time.sleep(0.25)
                continue
            t0 = time.time()
            jpeg = capture_pc_jpeg(max_width=1080, quality=58)
            if jpeg:
                header = struct.pack(">I", len(jpeg))
                conn.sendall(header + jpeg)
            elapsed = time.time() - t0
            sleep_for = max(0.02, (1.0 / 14.0) - elapsed)
            time.sleep(sleep_for)
    except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError, OSError):
        pass
    finally:
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


class ThreadingHttpServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def file_http_server_loop(state: RemoteServerState, stop_event: threading.Event) -> None:
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            return

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path == "/files":
                folder = state.shared_dir
                os.makedirs(folder, exist_ok=True)
                items = []
                for entry in sorted(os.listdir(folder)):
                    full = os.path.join(folder, entry)
                    if os.path.isfile(full):
                        st = os.stat(full)
                        items.append({
                            "name": entry,
                            "size": st.st_size,
                            "modifiedMs": int(st.st_mtime * 1000),
                        })
                body = json.dumps({"folder": folder, "files": items}).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            elif parsed.path == "/download":
                params = urllib.parse.parse_qs(parsed.query)
                raw_name = params.get("name", [""])[0]
                safe_name = sanitize_filename(raw_name)
                full = os.path.join(state.shared_dir, safe_name)
                if not os.path.isfile(full):
                    self.send_error(404, "File not found")
                    return
                size = os.path.getsize(full)
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", f'attachment; filename="{safe_name}"')
                self.send_header("Content-Length", str(size))
                self.end_headers()
                sent = 0
                last_notify = 0
                start_ts = time.monotonic()
                state.update_transfer("Sending to Phone", safe_name, 0, size, True, 0.0)
                try:
                    with open(full, "rb") as fh:
                        while True:
                            chunk = fh.read(65536)
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                            sent += len(chunk)
                            if sent - last_notify >= 131072 or sent == size:
                                last_notify = sent
                                spd = sent / max(0.001, time.monotonic() - start_ts)
                                state.update_transfer("Sending to Phone", safe_name, sent, size, True, spd)
                finally:
                    final_spd = sent / max(0.001, time.monotonic() - start_ts)
                    state.update_transfer("Sent to Phone", safe_name, sent, size, False, final_spd)
                    if sent > 0:
                        state.record_history("sent", safe_name, sent, full, final_spd)
            else:
                self.send_error(404)

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            length = int(self.headers.get("Content-Length", "0"))
            if parsed.path == "/upload":
                raw_name = urllib.parse.unquote(self.headers.get("X-Filename", "shared_file.bin"))
                safe_name = sanitize_filename(raw_name)
                dest = unique_target_path(state.shared_dir, safe_name)
                remaining = length
                received = 0
                last_notify = 0
                start_ts = time.monotonic()
                state.update_transfer("Receiving from Phone", safe_name, 0, length, True, 0.0)
                try:
                    with open(dest, "wb") as out:
                        while remaining > 0:
                            chunk = self.rfile.read(min(65536, remaining))
                            if not chunk:
                                break
                            out.write(chunk)
                            remaining -= len(chunk)
                            received += len(chunk)
                            if received - last_notify >= 131072 or remaining == 0:
                                last_notify = received
                                spd = received / max(0.001, time.monotonic() - start_ts)
                                state.update_transfer("Receiving from Phone", safe_name, received, length, True, spd)
                finally:
                    final_spd = received / max(0.001, time.monotonic() - start_ts)
                    saved_name = os.path.basename(dest)
                    state.update_transfer("Received from Phone", saved_name, received, length, False, final_spd)
                    if received > 0:
                        state.record_history("received", saved_name, received, dest, final_spd)
                state.notify_files_changed()
                resp = json.dumps({"ok": True, "savedAs": os.path.basename(dest)}).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(resp)))
                self.end_headers()
                self.wfile.write(resp)
            elif parsed.path == "/phone-frame":
                if length <= 0 or length > 5 * 1024 * 1024:
                    self.send_error(400)
                    return
                data = self.rfile.read(length)
                state.update_phone_frame(data)
                self.send_response(200)
                self.send_header("Content-Length", "2")
                self.end_headers()
                self.wfile.write(b"OK")
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
    qr_toggle_btn.pack(side="left", padx=(0, 8))

    tk.Button(
        btn_hdr,
        text="Allow Firewall",
        command=allow_windows_firewall,
        bg=ELEV,
        fg=BLUE,
        activebackground=BORDER,
        activeforeground=BLUE,
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=12,
        pady=7,
        cursor="hand2",
    ).pack(side="left", padx=(0, 8))

    tk.Button(
        btn_hdr,
        text="Exit App",
        command=quit_completely,
        bg="#341A1D",
        fg="#F87171",
        activebackground="#4C2226",
        activeforeground="#FCA5A5",
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=12,
        pady=7,
        cursor="hand2",
    ).pack(side="left")

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
        text="Allow phone app to view & touch PC screen",
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
    transfer_pct_lbl = tk.Label(
        prog_hdr, textvariable=transfer_pct_var, bg=CARD, fg=ORANGE, font=("Segoe UI", 10, "bold")
    )
    transfer_pct_lbl.pack(side="right")

    prog_canvas = tk.Canvas(prog_card, height=14, bg=ELEV, highlightthickness=0)
    prog_canvas.pack(fill="x", padx=16, pady=(0, 6))
    transfer_detail_var = tk.StringVar(value="Select or send any file between Phone and PC")
    tk.Label(prog_card, textvariable=transfer_detail_var, bg=CARD, fg=SUB, font=("Segoe UI", 8)).pack(
        anchor="w", padx=16, pady=(0, 12)
    )

    def draw_progress_bar(fraction: float, active: bool):
        prog_canvas.delete("all")
        w = prog_canvas.winfo_width()
        if w < 10:
            w = 430
        h = 14
        fill_w = max(0, min(w, int(w * fraction)))
        prog_canvas.create_rectangle(0, 0, w, h, fill=ELEV, outline="")
        if fill_w > 0:
            color = ORANGE if active else GREEN
            prog_canvas.create_rectangle(0, 0, fill_w, h, fill=color, outline="")

    def refresh_transfer():
        info = state.transfer_info
        if not info:
            transfer_title_var.set("File Transfer Ready")
            transfer_pct_var.set("")
            transfer_detail_var.set("Progress appears here when sending or receiving files")
            draw_progress_bar(0.0, False)
            return
        total = max(1, info["total"])
        done = info["done"]
        frac = min(1.0, done / total) if info["total"] > 0 else (1.0 if not info["active"] else 0.5)
        pct = int(round(frac * 100))
        done_mb = done / (1024.0 * 1024.0)
        tot_mb = total / (1024.0 * 1024.0)
        speed_txt = format_speed(info.get("speed_bps", 0.0))
        transfer_title_var.set(f"{info['direction']}: {info['filename']}")
        transfer_pct_var.set(f"{pct}%")
        transfer_pct_lbl.configure(fg=ORANGE if info["active"] else GREEN)
        transfer_detail_var.set(f"{done_mb:.2f} MB / {tot_mb:.2f} MB   •   {speed_txt}")
        draw_progress_bar(frac, info["active"])

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

    files_listbox = tk.Listbox(
        file_card,
        bg=ELEV,
        fg=TXT,
        selectbackground=ORANGE,
        selectforeground="#FFFFFF",
        borderwidth=0,
        highlightthickness=0,
        font=("Segoe UI", 9),
    )

    def format_size_short(sz: int) -> str:
        if sz >= 1024 * 1024:
            return f"{sz / (1024.0 * 1024.0):.1f} MB"
        return f"{max(1, sz // 1024)} KB"

    def refresh_files():
        files_listbox.delete(0, tk.END)
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
                e for e in os.listdir(state.shared_dir) if os.path.isfile(os.path.join(state.shared_dir, e))
            )
            if not entries:
                files_listbox.insert(tk.END, "  (Folder empty — click + Share File or send from phone)")
            else:
                for e in entries:
                    full = os.path.join(state.shared_dir, e)
                    sz = os.path.getsize(full)
                    visible_paths.append(full)
                    files_listbox.insert(tk.END, f"  {e}    ({format_size_short(sz)})")
        else:
            items = [h for h in state.transfer_history if h["kind"] == mode]
            if not items:
                label = "sent to phone" if mode == "sent" else "received from phone"
                files_listbox.insert(tk.END, f"  (No files {label} in this session yet)")
            else:
                for h in items:
                    spd = format_speed(h.get("speed_bps", 0.0))
                    visible_paths.append(h["path"])
                    files_listbox.insert(
                        tk.END,
                        f"  [{h['ts']}]  {h['filename']}    ({format_size_short(h['size'])} • {spd})",
                    )

    def set_history_mode(mode: str):
        history_mode.set(mode)
        refresh_files()

    tab_all_btn.configure(command=lambda: set_history_mode("all"))
    tab_sent_btn.configure(command=lambda: set_history_mode("sent"))
    tab_recv_btn.configure(command=lambda: set_history_mode("received"))

    def open_selected_file(_event=None):
        sel = files_listbox.curselection()
        if not sel:
            return
        idx = sel[0]
        if 0 <= idx < len(visible_paths):
            path = visible_paths[idx]
            if os.path.exists(path):
                try:
                    os.startfile(path)
                except Exception as exc:
                    messagebox.showerror("Open File", f"Could not open file: {exc}")
            else:
                messagebox.showwarning("File Missing", f"File no longer exists:\n{path}")

    files_listbox.bind("<Double-Button-1>", open_selected_file)
    files_listbox.bind("<Return>", open_selected_file)

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
        padx=10,
        pady=5,
        cursor="hand2",
    ).pack(side="left", padx=(0, 6))
    tk.Button(
        btn_row,
        text="Open File",
        command=open_selected_file,
        bg=ELEV,
        fg=BLUE,
        activebackground=BORDER,
        activeforeground=BLUE,
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=10,
        pady=5,
        cursor="hand2",
    ).pack(side="left", padx=(0, 6))
    tk.Button(
        btn_row,
        text="Open Folder",
        command=open_shared_folder,
        bg=ELEV,
        fg=TXT,
        activebackground=BORDER,
        activeforeground=TXT,
        relief="flat",
        font=("Segoe UI", 9),
        padx=10,
        pady=5,
        cursor="hand2",
    ).pack(side="left")

    tk.Label(
        file_card,
        text=f"Path: {state.shared_dir}   •   Double-click any file to open",
        bg=CARD,
        fg=SUB,
        font=("Segoe UI", 8),
    ).pack(anchor="w", padx=16, pady=(0, 8))

    files_listbox.pack(fill="both", expand=True, padx=16, pady=(0, 14))

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
