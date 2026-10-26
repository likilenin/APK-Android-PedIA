@echo off
chcp 65001 >nul
title PedIA Móvil - tablet de pruebas
rem Abre la tablet de pruebas (emulador Android) con PedIA Móvil conectada a un PedIA
rem simulado de este PC (puerto 4100), para probar la app sin tocar la PedIA real.

set "SDK=%USERPROFILE%\Android\sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
cd /d "%~dp0"

echo [1/4] PedIA simulado...
netstat -ano | findstr ":4100 " | findstr "LISTENING" >nul
if errorlevel 1 (
  start "PedIA simulado - no cerrar mientras pruebas" cmd /k node "%~dp0herramientas\pedia-simulada\servidor.js"
) else (
  echo       ya estaba en marcha
)

echo [2/4] Tablet de pruebas...
"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
  start "" "%SDK%\emulator\emulator.exe" -avd pedia_tablet
) else (
  echo       ya estaba abierta
)

echo [3/4] Esperando a que arranque Android (la primera vez tarda un par de minutos)...
"%ADB%" wait-for-device
:esperar
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr /b "1" >nul
if errorlevel 1 (
  timeout /t 2 /nobreak >nul
  goto esperar
)

echo [4/4] Instalando la última versión compilada y abriendo PedIA Móvil...
set "APK="
for %%f in (PedIA-Movil-*.apk) do set "APK=%%f"
if exist "app\build\outputs\apk\release\app-release.apk" set "APK=app\build\outputs\apk\release\app-release.apk"
if defined APK "%ADB%" install -r -d "%APK%" >nul
"%ADB%" shell am start -n com.pedia.movil/.MainActivity >nul

echo.
echo Listo. Atajos dentro de la tablet: Ctrl+Retroceso = atrás, Ctrl+H = inicio.
echo Para terminar, cierra la ventana de la tablet y la del PedIA simulado.
timeout /t 10 >nul
