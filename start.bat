@echo off
REM Startet Postgres, Backend und Frontend im Hintergrund und oeffnet Chrome.
REM Beenden mit stop.bat - die Server laufen ohne Fenster weiter.
cd /d "%~dp0"
if not exist logs mkdir logs

docker info >nul 2>&1 && goto :db
echo Docker Desktop wird gestartet...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
:dockerwait
timeout /t 3 >nul
docker info >nul 2>&1 || goto :dockerwait

:db
echo Postgres wird gestartet...
docker compose up -d --wait
if errorlevel 1 goto :fail

REM Ohne Fenster, Ausgabe in logs\. Start-Process haengt die Prozesse von dieser
REM Konsole ab - sie ueberleben, wenn sich dieses Fenster am Ende schliesst.
echo Backend und Frontend werden gestartet...
powershell -NoProfile -Command "Start-Process cmd '/c mvn spring-boot:run > ..\logs\backend.log 2>&1' -WorkingDirectory '%~dp0backend' -WindowStyle Hidden"
powershell -NoProfile -Command "Start-Process cmd '/c npm start > ..\logs\frontend.log 2>&1' -WorkingDirectory '%~dp0frontend' -WindowStyle Hidden"

REM Auf beide warten: nur auf das Frontend zu warten zeigt "Backend nicht erreichbar".
echo Warte auf das Backend...
:backendwait
timeout /t 2 >nul
curl -s -o nul http://localhost:8080/api/health || goto :backendwait

echo Warte auf das Frontend...
:frontendwait
timeout /t 2 >nul
curl -s -o nul http://localhost:4200 || goto :frontendwait

start chrome http://localhost:4200
exit /b 0

:fail
echo.
echo Postgres konnte nicht gestartet werden.
pause
