# Script para iniciar el servidor Ice de forma completa
# Uso: .\start-ice-server.ps1

Write-Host "╔════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   INICIANDO SISTEMA DE CHAT CON ICE           ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════╝" -ForegroundColor Cyan

# Crear directorios si no existen
New-Item -Path "ServidorJava\data\audio" -ItemType Directory -Force | Out-Null
New-Item -Path "ServidorJava\data\history" -ItemType Directory -Force | Out-Null

Write-Host ""
Write-Host "Paso 1: Compilando servidor Java..." -ForegroundColor Yellow
Push-Location ServidorJava
gradle build
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error compilando servidor Java" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

Write-Host ""
Write-Host "Paso 2: Iniciando servidor Java en nueva terminal..." -ForegroundColor Yellow
$javaProcessStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$javaProcessStartInfo.FileName = "powershell.exe"
$javaProcessStartInfo.Arguments = "-NoExit -Command cd `'$PWD\ServidorJava`' ; gradle run"
$javaProcessStartInfo.WorkingDirectory = $PWD
[System.Diagnostics.Process]::Start($javaProcessStartInfo) | Out-Null
Write-Host "✅ Servidor Java iniciado (Terminal nueva)" -ForegroundColor Green

Start-Sleep -Seconds 3

Write-Host ""
Write-Host "Paso 3: Instalando dependencias del Proxy..." -ForegroundColor Yellow
Push-Location Proxy
npm install
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error instalando dependencias" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

Write-Host ""
Write-Host "Paso 4: Iniciando Proxy Ice en nueva terminal..." -ForegroundColor Yellow
$proxyProcessStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$proxyProcessStartInfo.FileName = "powershell.exe"
$proxyProcessStartInfo.Arguments = "-NoExit -Command cd `'$PWD\Proxy`' ; npm run start:ice"
$proxyProcessStartInfo.WorkingDirectory = $PWD
[System.Diagnostics.Process]::Start($proxyProcessStartInfo) | Out-Null
Write-Host "✅ Proxy Ice iniciado (Terminal nueva)" -ForegroundColor Green

Write-Host ""
Write-Host "╔════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║          SISTEMA LISTO PARA USAR             ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════╝" -ForegroundColor Green

Write-Host ""
Write-Host "📍 Accede a: http://localhost:3000" -ForegroundColor Cyan
Write-Host "📊 Servidor Java: localhost:5001 (Ice)" -ForegroundColor Cyan
Write-Host "🔌 Servidor Proxy: localhost:3000 (HTTP) + localhost:8080 (WebSocket)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Presiona Ctrl+C para detener..." -ForegroundColor Yellow
