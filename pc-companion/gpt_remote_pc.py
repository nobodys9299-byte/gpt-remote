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


class RemoteServerState:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.active_clients: set[str] = set()
        self.shared_dir: str = default_shared_dir()
        self.allow_pc_screen_share: bool = True
        self.latest_phone_frame: bytes | None = None
        self.latest_phone_frame_ts: float = 0.0
        self.bind_errors: list[str] = []
        self.on_change = None
        self.on_files_change = None
        self.on_phone_frame = None

    def report_bind_error(self, msg: str) -> None:
        with self._lock:
            self.bind_errors.append(msg)
            cb = self.on_change
        if cb:
            cb()

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
                with open(full, "rb") as fh:
                    while True:
                        chunk = fh.read(65536)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
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
                with open(dest, "wb") as out:
                    while remaining > 0:
                        chunk = self.rfile.read(min(65536, remaining))
                        if not chunk:
                            break
                        out.write(chunk)
                        remaining -= len(chunk)
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
    root.geometry("460x600")
    root.configure(bg="#0E1015")
    root.resizable(False, False)

    BG = "#0E1015"
    CARD = "#181B23"
    ELEV = "#232834"
    ORANGE = "#FF6900"
    BLUE = "#38BDF8"
    GREEN = "#34D399"
    TXT = "#F3F4F6"
    SUB = "#9CA3AF"

    # Header
    hdr = tk.Frame(root, bg=BG)
    hdr.pack(fill="x", padx=20, pady=(18, 10))
    tk.Label(hdr, text="GPT Remote PC", bg=BG, fg=TXT, font=("Segoe UI", 18, "bold")).pack(anchor="w")
    tk.Label(
        hdr,
        text="Control PC, share screen, and transfer files with your phone",
        bg=BG,
        fg=SUB,
        font=("Segoe UI", 9),
    ).pack(anchor="w")

    # Connection Card
    conn_card = tk.Frame(root, bg=CARD, highlightbackground="#2F3545", highlightthickness=1)
    conn_card.pack(fill="x", padx=20, pady=6)

    ip_var = tk.StringVar()
    clients_var = tk.StringVar()

    def refresh_status():
        ips = get_local_ips()
        ip_str = ", ".join(ips) if ips else "Not connected to Wi-Fi"
        ip_var.set(f"PC Name: {socket.gethostname()}   •   Wi-Fi IP: {ip_str}")
        if state.bind_errors:
            clients_var.set("Port conflict: " + "; ".join(state.bind_errors))
        else:
            c = state.count()
            clients_var.set(
                f"Connected Phones: {c}   •   Ports: {TCP_PORT} / {SCREEN_PORT} / {FILE_HTTP_PORT}"
            )

    tk.Label(conn_card, textvariable=ip_var, bg=CARD, fg=BLUE, font=("Segoe UI", 10, "bold")).pack(
        anchor="w", padx=14, pady=(12, 2)
    )
    tk.Label(conn_card, textvariable=clients_var, bg=CARD, fg=GREEN, font=("Segoe UI", 9)).pack(
        anchor="w", padx=14, pady=(0, 12)
    )

    # Screen Share Card
    scr_card = tk.Frame(root, bg=CARD, highlightbackground="#2F3545", highlightthickness=1)
    scr_card.pack(fill="x", padx=20, pady=6)
    tk.Label(scr_card, text="Screen Sharing", bg=CARD, fg=TXT, font=("Segoe UI", 11, "bold")).pack(
        anchor="w", padx=14, pady=(12, 4)
    )
    allow_screen_var = tk.BooleanVar(value=state.allow_pc_screen_share)

    def on_toggle_screen():
        state.allow_pc_screen_share = allow_screen_var.get()

    tk.Checkbutton(
        scr_card,
        text="Allow phone app to view & touch-control this PC screen",
        variable=allow_screen_var,
        command=on_toggle_screen,
        bg=CARD,
        fg=TXT,
        selectcolor=ELEV,
        activebackground=CARD,
        activeforeground=TXT,
        font=("Segoe UI", 9),
    ).pack(anchor="w", padx=12, pady=(0, 8))

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
            text="Waiting for phone screen cast...\n\nOn your phone, open:\nPC Remote -> Screen -> Cast Phone Screen",
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
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=12,
        pady=6,
    ).pack(anchor="w", padx=14, pady=(0, 12))

    # File Sharing Card
    file_card = tk.Frame(root, bg=CARD, highlightbackground="#2F3545", highlightthickness=1)
    file_card.pack(fill="both", expand=True, padx=20, pady=6)
    tk.Label(file_card, text="Two-Way File Share", bg=CARD, fg=TXT, font=("Segoe UI", 11, "bold")).pack(
        anchor="w", padx=14, pady=(12, 2)
    )
    folder_lbl = tk.Label(
        file_card,
        text=f"Folder: {state.shared_dir}",
        bg=CARD,
        fg=SUB,
        font=("Segoe UI", 8),
    )
    folder_lbl.pack(anchor="w", padx=14, pady=(0, 6))

    files_listbox = tk.Listbox(
        file_card,
        bg=ELEV,
        fg=TXT,
        borderwidth=0,
        highlightthickness=0,
        font=("Segoe UI", 9),
        height=7,
    )
    files_listbox.pack(fill="both", expand=True, padx=14, pady=(0, 8))

    def refresh_files():
        files_listbox.delete(0, tk.END)
        os.makedirs(state.shared_dir, exist_ok=True)
        entries = sorted(
            e for e in os.listdir(state.shared_dir) if os.path.isfile(os.path.join(state.shared_dir, e))
        )
        if not entries:
            files_listbox.insert(tk.END, "(No files yet — add a file or send from phone)")
        else:
            for e in entries:
                sz = os.path.getsize(os.path.join(state.shared_dir, e))
                kb = max(1, sz // 1024)
                files_listbox.insert(tk.END, f"{e}  ({kb} KB)")

    def add_files_to_share():
        picked = filedialog.askopenfilenames(title="Select file(s) to share with phone")
        for src in picked:
            dest = unique_target_path(state.shared_dir, sanitize_filename(src))
            shutil.copy2(src, dest)
        refresh_files()

    def open_shared_folder():
        os.makedirs(state.shared_dir, exist_ok=True)
        os.startfile(state.shared_dir)

    btn_row = tk.Frame(file_card, bg=CARD)
    btn_row.pack(fill="x", padx=14, pady=(0, 12))
    tk.Button(
        btn_row,
        text="+ Share File to Phone",
        command=add_files_to_share,
        bg=ORANGE,
        fg="#FFFFFF",
        relief="flat",
        font=("Segoe UI", 9, "bold"),
        padx=10,
        pady=6,
    ).pack(side="left")
    tk.Button(
        btn_row,
        text="Open Folder",
        command=open_shared_folder,
        bg=ELEV,
        fg=TXT,
        relief="flat",
        font=("Segoe UI", 9),
        padx=10,
        pady=6,
    ).pack(side="left", padx=8)

    # Bottom Controls Row
    bot = tk.Frame(root, bg=BG)
    bot.pack(fill="x", padx=20, pady=(6, 16))

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
        bot,
        text="Run at Windows Startup",
        variable=autostart_var,
        command=on_toggle_autostart,
        bg=BG,
        fg=TXT,
        selectcolor=ELEV,
        activebackground=BG,
        activeforeground=TXT,
        font=("Segoe UI", 9),
    ).pack(side="left")

    tk.Button(
        bot,
        text="Allow Firewall",
        command=allow_windows_firewall,
        bg=ELEV,
        fg=BLUE,
        relief="flat",
        font=("Segoe UI", 9),
        padx=8,
        pady=4,
    ).pack(side="right")

    tk.Button(
        bot,
        text="Exit App",
        command=lambda: quit_completely(),
        bg="#341A1D",
        fg="#F87171",
        relief="flat",
        font=("Segoe UI", 9),
        padx=8,
        pady=4,
    ).pack(side="right", padx=6)

    def quit_completely():
        stop_event.set()
        root.quit()
        root.destroy()

    def minimize_to_tray():
        root.withdraw()

    root.protocol("WM_DELETE_WINDOW", minimize_to_tray)

    # Wire live background state updates safely onto Tk thread
    state.on_change = lambda: root.after(0, refresh_status)
    state.on_files_change = lambda: root.after(0, refresh_files)
    state.on_phone_frame = lambda data: root.after(0, lambda d=data: (
        open_phone_mirror_window() if not (phone_win_ref["win"] and phone_win_ref["win"].winfo_exists()) else None,
        render_phone_frame(d),
    ))

    refresh_status()
    refresh_files()

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
