@echo off
rem Saves everything in this folder to GitHub (main). Optional: push.bat "what I changed"
cd /d "%~dp0"
set MSG=%~1
if "%MSG%"=="" set MSG=Update from PC
git add -A
git commit -m "%MSG%"
git pull --rebase origin main
if errorlevel 1 (
  echo.
  echo Pull failed. Nothing was pushed. Send the message above to Claude.
  pause
  exit /b 1
)
git push origin main
echo.
echo Done. GitHub is now building the APK: open the repo's Actions tab.
pause
