#!/usr/bin/env python3
"""
GPT Remote - Wi-Fi PC/Laptop Companion
Runs silently in the Windows System Tray (no CMD window when compiled with --noconsole)
or as a CLI service on macOS/Linux.

Features:
- Zero external dependencies on Windows (ctypes SendInput + Win32 System Tray)
- UDP subnet discovery (port 58766) + TCP control & announce (port 58765)
- 1-click "Run at Windows Startup" (copies to %LOCALAPPDATA%\\GptRemotePC if run from Temp)
- 1-click "Allow Windows Firewall" helper for Private & Public/Hotspot Wi-Fi
"""

import ctypes
import json
import os
import platform
import shutil
import socket
import subprocess
import sys
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
        self.on_change = None

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


def udp_discovery_loop(stop_event: threading.Event) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.settimeout(1.0)
    sock.bind(("", UDP_DISCOVERY_PORT))
    announce = json.dumps({
        "type": "announce",
        "name": socket.gethostname(),
        "os": platform.system(),
        "port": TCP_PORT,
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
                elif t == "scroll":
                    backend.scroll(int(msg.get("dy", 0)))
                elif t == "click":
                    backend.click(msg.get("btn", "left"))
                elif t == "key":
                    backend.key(msg.get("code", ""))
                elif t == "text":
                    backend.text(msg.get("s", ""))
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
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.settimeout(1.0)
    server.bind(("0.0.0.0", TCP_PORT))
    server.listen(8)

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


# ---- Windows Startup, Install & Firewall Helpers ----

def _startup_shortcut_path() -> str:
    appdata = os.environ.get("APPDATA", os.path.expanduser("~"))
    return os.path.join(
        appdata, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "GptRemotePC.lnk"
    )


def _installed_exe_path() -> str:
    local_appdata = os.environ.get("LOCALAPPDATA", os.path.expanduser("~"))
    return os.path.join(local_appdata, "GptRemotePC", "GptRemotePC.exe")


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
        # If running out of a Temp folder/zip, copy into %LOCALAPPDATA%\GptRemotePC first
        if "temp" in target_exe.lower():
            dest_exe = _installed_exe_path()
            os.makedirs(os.path.dirname(dest_exe), exist_ok=True)
            if os.path.abspath(dest_exe) != target_exe:
                shutil.copy2(target_exe, dest_exe)
            target_exe = dest_exe
        target_path = target_exe
        args = ""
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
        creationflags=0x08000000,  # CREATE_NO_WINDOW
        check=True,
    )
    return True, f"Installed auto-start shortcut for:\n{target_path}"


def allow_windows_firewall() -> None:
    cmd = (
        'netsh advfirewall firewall delete rule name="GPT Remote PC TCP 58765" >nul 2>&1 & '
        'netsh advfirewall firewall delete rule name="GPT Remote PC UDP 58766" >nul 2>&1 & '
        'netsh advfirewall firewall add rule name="GPT Remote PC TCP 58765" dir=in action=allow protocol=TCP localport=58765 profile=any & '
        'netsh advfirewall firewall add rule name="GPT Remote PC UDP 58766" dir=in action=allow protocol=UDP localport=58766 profile=any'
    )
    ctypes.windll.shell32.ShellExecuteW(None, "runas", "cmd.exe", f"/c {cmd}", None, 0)


# ---- Windows System Tray UI (Zero Console Window) ----

def run_windows_tray(backend: InputBackend, state: RemoteServerState, stop_event: threading.Event) -> None:
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
    NIM_MODIFY = 0x00000001
    NIM_DELETE = 0x00000002
    NIF_MESSAGE = 0x00000001
    NIF_ICON = 0x00000002
    NIF_TIP = 0x00000004
    NIF_INFO = 0x00000010
    NIIF_INFO = 0x00000001

    MF_STRING = 0x00000000
    MF_CHECKED = 0x00000008
    MF_GRAYED = 0x00000001
    MF_SEPARATOR = 0x00000800
    TPM_LEFTALIGN = 0x0000
    TPM_BOTTOMALIGN = 0x0020
    TPM_RETURNCMD = 0x0100

    ID_SHOW_INFO = 1001
    ID_AUTOSTART = 1002
    ID_FIREWALL = 1003
    ID_EXIT = 1004

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
            ("uVersion", wintypes.UINT),
            ("szInfoTitle", wintypes.WCHAR * 64),
            ("dwInfoFlags", wintypes.DWORD),
            ("guidItem", GUID),
            ("hBalloonIcon", wintypes.HICON),
        ]

    user32.DefWindowProcW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    user32.DefWindowProcW.restype = LRESULT

    hinstance = kernel32.GetModuleHandleW(None)
    # Try nice computer/network icon from shell32.dll (index 15 = This PC / monitor), fallback to default app icon
    hicon = shell32.ExtractIconW(hinstance, "shell32.dll", 15)
    if not hicon or hicon == 1:
        hicon = user32.LoadIconW(None, ctypes.cast(32512, wintypes.LPCWSTR))

    hwnd_holder: dict[str, wintypes.HWND] = {}

    def build_tip() -> str:
        ips = get_local_ips()
        ip_str = ips[0] if ips else "Check Wi-Fi"
        return f"GPT Remote ({ip_str}) • Connected: {state.count()}"[:127]

    def update_tray_tip() -> None:
        hwnd = hwnd_holder.get("hwnd")
        if not hwnd:
            return
        nid = NOTIFYICONDATAW()
        nid.cbSize = ctypes.sizeof(NOTIFYICONDATAW)
        nid.hWnd = hwnd
        nid.uID = 1
        nid.uFlags = NIF_TIP
        nid.szTip = build_tip()
        shell32.Shell_NotifyIconW(NIM_MODIFY, ctypes.byref(nid))

    state.on_change = update_tray_tip

    def show_info_popup() -> None:
        ips = get_local_ips()
        ip_text = ", ".join(ips) if ips else "Not connected to Wi-Fi"
        msg = (
            f"Computer Name: {socket.gethostname()}\n"
            f"Wi-Fi IP: {ip_text}\n"
            f"Port: {TCP_PORT} (UDP Discovery: {UDP_DISCOVERY_PORT})\n"
            f"Connected Phones: {state.count()}\n\n"
            "On your phone, open GPT Remote -> Wi-Fi PC -> Scan\n"
            "or tap + and enter the Wi-Fi IP above."
        )
        user32.MessageBoxW(None, msg, "GPT Remote PC Companion", 0x00000040)

    def show_tray_menu(hwnd: wintypes.HWND) -> None:
        hmenu = user32.CreatePopupMenu()
        ips = get_local_ips()
        ip_label = ips[0] if ips else "No Wi-Fi IP"
        user32.AppendMenuW(hmenu, MF_STRING | MF_GRAYED, 0, f"IP: {ip_label}  (Port {TCP_PORT})")
        user32.AppendMenuW(hmenu, MF_STRING | MF_GRAYED, 0, f"Connected Phones: {state.count()}")
        user32.AppendMenuW(hmenu, MF_SEPARATOR, 0, None)
        user32.AppendMenuW(hmenu, MF_STRING, ID_SHOW_INFO, "Show Status && IP...")
        autostart_flags = MF_STRING | (MF_CHECKED if is_autostart_enabled() else 0)
        user32.AppendMenuW(hmenu, autostart_flags, ID_AUTOSTART, "Run at Windows Startup")
        user32.AppendMenuW(hmenu, MF_STRING, ID_FIREWALL, "Allow Windows Firewall (Admin)")
        user32.AppendMenuW(hmenu, MF_SEPARATOR, 0, None)
        user32.AppendMenuW(hmenu, MF_STRING, ID_EXIT, "Exit")

        pt = wintypes.POINT()
        user32.GetCursorPos(ctypes.byref(pt))
        user32.SetForegroundWindow(hwnd)
        cmd = user32.TrackPopupMenu(
            hmenu,
            TPM_LEFTALIGN | TPM_BOTTOMALIGN | TPM_RETURNCMD,
            pt.x,
            pt.y,
            0,
            hwnd,
            None,
        )
        user32.DestroyMenu(hmenu)

        if cmd == ID_SHOW_INFO:
            threading.Thread(target=show_info_popup, daemon=True).start()
        elif cmd == ID_AUTOSTART:
            try:
                _, info_msg = toggle_autostart()
                threading.Thread(
                    target=lambda: user32.MessageBoxW(None, info_msg, "GPT Remote Startup", 0x40),
                    daemon=True,
                ).start()
            except Exception as exc:
                err_msg = f"Failed to update startup shortcut: {exc}"
                threading.Thread(
                    target=lambda: user32.MessageBoxW(None, err_msg, "GPT Remote Error", 0x10),
                    daemon=True,
                ).start()
        elif cmd == ID_FIREWALL:
            allow_windows_firewall()
        elif cmd == ID_EXIT:
            stop_event.set()
            user32.PostQuitMessage(0)

    def wnd_proc(hwnd, msg, wparam, lparam):
        if msg == WM_TRAY:
            event = lparam & 0xFFFF
            if event in (WM_LBUTTONUP, WM_RBUTTONUP):
                show_tray_menu(hwnd)
            return 0
        if msg == WM_DESTROY:
            stop_event.set()
            user32.PostQuitMessage(0)
            return 0
        return user32.DefWindowProcW(hwnd, msg, wparam, lparam)

    wnd_proc_cb = WNDPROC(wnd_proc)
    class_name = "GptRemoteTrayWindow"

    wc = WNDCLASSEXW()
    wc.cbSize = ctypes.sizeof(WNDCLASSEXW)
    wc.lpfnWndProc = wnd_proc_cb
    wc.hInstance = hinstance
    wc.hIcon = hicon
    wc.lpszClassName = class_name
    user32.RegisterClassExW(ctypes.byref(wc))

    hwnd = user32.CreateWindowExW(
        0, class_name, "GPT Remote", 0, 0, 0, 0, 0, None, None, hinstance, None
    )
    hwnd_holder["hwnd"] = hwnd

    ips = get_local_ips()
    ip_summary = ", ".join(ips) if ips else "Check Wi-Fi"

    nid = NOTIFYICONDATAW()
    nid.cbSize = ctypes.sizeof(NOTIFYICONDATAW)
    nid.hWnd = hwnd
    nid.uID = 1
    nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_INFO
    nid.uCallbackMessage = WM_TRAY
    nid.hIcon = hicon
    nid.szTip = build_tip()
    nid.szInfoTitle = "GPT Remote Ready"
    nid.szInfo = f"Running in System Tray • Wi-Fi IP: {ip_summary}"[:255]
    nid.dwInfoFlags = NIIF_INFO
    shell32.Shell_NotifyIconW(NIM_ADD, ctypes.byref(nid))

    msg_struct = wintypes.MSG()
    while user32.GetMessageW(ctypes.byref(msg_struct), None, 0, 0) > 0:
        user32.TranslateMessage(ctypes.byref(msg_struct))
        user32.DispatchMessageW(ctypes.byref(msg_struct))

    shell32.Shell_NotifyIconW(NIM_DELETE, ctypes.byref(nid))


def main() -> None:
    backend = create_backend()
    state = RemoteServerState()
    stop_event = threading.Event()

    threading.Thread(target=udp_discovery_loop, args=(stop_event,), daemon=True).start()
    threading.Thread(target=tcp_server_loop, args=(backend, state, stop_event), daemon=True).start()

    if platform.system() == "Windows":
        run_windows_tray(backend, state, stop_event)
    else:
        ips = get_local_ips()
        print(f"GPT Remote PC Companion running on '{socket.gethostname()}' ({platform.system()})")
        print(f"Wi-Fi IP(s): {', '.join(ips) if ips else 'unknown'} | TCP {TCP_PORT} / UDP {UDP_DISCOVERY_PORT}")
        try:
            stop_event.wait()
        except KeyboardInterrupt:
            stop_event.set()


if __name__ == "__main__":
    main()
