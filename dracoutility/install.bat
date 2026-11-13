REM Draco Utility - Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)
REM Licensed under the GNU General Public License v3. See the LICENSE file.
@echo off
cd /d "%~dp0"
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Send the red error text to get it fixed.
  pause
  exit /b 1
)
if not exist "D:\.minecraft\mods" mkdir "D:\.minecraft\mods"
del /Q "D:\.minecraft\mods\dracoutility-*.jar" 2>nul
copy /Y "build\libs\dracoutility-1.1.0.jar" "D:\.minecraft\mods\"
echo.
echo Done. Start Minecraft with Fabric 1.21.1.
pause
