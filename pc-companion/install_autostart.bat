@echo off
set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "TARGET_VBS=%~dp0start_hidden.vbs"
powershell -NoProfile -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%STARTUP_DIR%\GptRemotePC.lnk'); $s.TargetPath = 'wscript.exe'; $s.Arguments = '\"%TARGET_VBS%\"'; $s.WorkingDirectory = '%~dp0'; $s.Save()"
echo Installed auto-start shortcut at: %STARTUP_DIR%\GptRemotePC.lnk
echo Starting GPT Remote PC Companion in the background now...
start "" wscript.exe "%TARGET_VBS%"
pause
