@echo off
REM Beendet Backend und Frontend ueber ihre Ports und faehrt Postgres herunter.
REM Ueber den Port statt ueber den Prozessnamen: sonst wuerde es auch java- und
REM node-Prozesse anderer Projekte treffen.
cd /d "%~dp0"

powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080,4200 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"

REM Lernstand sichern, solange Postgres noch laeuft. Eine Datei pro Tag; mehrere
REM Stopps am selben Tag ueberschreiben sich, das ist der jeweils letzte Stand.
REM
REM Der Dump laeuft ueber die cmd-Umleitung, damit die Bytes von pg_dump unveraendert
REM ankommen - PowerShell wuerde UTF-8 mit BOM daraus machen, was psql beim
REM Zurueckspielen nicht mag. Das Umbenennen macht dann PowerShell: erst in eine .tmp,
REM damit ein fehlgeschlagener Dump kein gutes Backup ueberschreibt.
if not exist backups mkdir backups
docker exec flashcards-postgres pg_dump -U flashcards -d flashcards > "backups\neu.tmp" 2>nul
powershell -NoProfile -Command "$t = 'backups\neu.tmp'; if ((Test-Path $t) -and (Get-Item $t).Length -gt 0) { $ziel = 'backups\{0:yyyy-MM-dd}.sql' -f (Get-Date); Move-Item $t $ziel -Force; Write-Host \"Lernstand gesichert: $ziel\" } else { Remove-Item $t -ErrorAction SilentlyContinue; Write-Host 'Postgres laeuft nicht - kein Backup angelegt.' }"

docker compose down
echo.
echo Beendet.
timeout /t 2 >nul
