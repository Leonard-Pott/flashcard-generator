@echo off
REM Startet Postgres, Backend und Frontend im Hintergrund und oeffnet den Browser.
REM Beenden mit stop.bat - die Server laufen ohne Fenster weiter.
cd /d "%~dp0"
if not exist logs mkdir logs

docker info >nul 2>&1 && goto :db
REM Standardpfad von Docker Desktop. Liegt es woanders oder ist es eine andere
REM Docker-Variante, wird nicht geraten - dann startet es der Mensch selbst.
set "DOCKER_EXE=C:\Program Files\Docker\Docker\Docker Desktop.exe"
if not exist "%DOCKER_EXE%" (
  echo Docker laeuft nicht und ist nicht unter "%DOCKER_EXE%" zu finden.
  echo Bitte Docker von Hand starten und start.bat erneut ausfuehren.
  pause
  exit /b 1
)
echo Docker Desktop wird gestartet...
start "" "%DOCKER_EXE%"
set DOCKERTRIES=0
:dockerwait
set /a DOCKERTRIES+=1
if %DOCKERTRIES% gtr 40 (
  echo.
  echo Docker antwortet auch nach zwei Minuten nicht - bitte von Hand nachsehen.
  pause
  exit /b 1
)
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

REM Leerer Titel, damit start die URL nicht als Fenstertitel missversteht. Oeffnet
REM den Standardbrowser - kein fest verdrahteter Chrome.
start "" http://localhost:4200
exit /b 0

:fail
echo.
echo Postgres konnte nicht gestartet werden.
pause
