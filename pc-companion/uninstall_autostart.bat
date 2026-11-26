@echo off
set "SHORTCUT=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\GptRemotePC.lnk"
if exist "%SHORTCUT%" (
    del "%SHORTCUT%"
    echo Removed auto-start shortcut.
) else (
    echo Auto-start shortcut was not installed.
)
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='pythonw.exe'\" | Where-Object { $_.CommandLine -like '*gpt_remote_pc.py*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
echo Stopped GPT Remote PC Companion background process.
pause
