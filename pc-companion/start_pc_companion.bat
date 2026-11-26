@echo off
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (
    py -3 "%~dp0gpt_remote_pc.py"
) else (
    python "%~dp0gpt_remote_pc.py"
)
if %errorlevel% neq 0 pause
