@echo off
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting Administrator permission to allow GPT Remote ports in Windows Firewall...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

netsh advfirewall firewall delete rule name="GPT Remote PC TCP 58765" >nul 2>&1
netsh advfirewall firewall delete rule name="GPT Remote PC UDP 58766" >nul 2>&1

netsh advfirewall firewall add rule name="GPT Remote PC TCP 58765" dir=in action=allow protocol=TCP localport=58765 profile=any
netsh advfirewall firewall add rule name="GPT Remote PC UDP 58766" dir=in action=allow protocol=UDP localport=58766 profile=any

echo.
echo Windows Firewall rules added for TCP 58765 and UDP 58766 (Private + Public).
echo You can now tap Scan Wi-Fi PC in the phone app.
pause
