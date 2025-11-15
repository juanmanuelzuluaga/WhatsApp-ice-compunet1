@echo off
REM Script para iniciar todos los servicios en Windows

echo.
echo ==========================================
echo    WhatsApp Pro Max 1.0 - Startup (Windows)
echo ==========================================
echo.

REM Verificar directorios
if not exist "ServidorJava" (
    echo Error: Directorio ServidorJava no encontrado
    exit /b 1
)
if not exist "Proxy" (
    echo Error: Directorio Proxy no encontrado
    exit /b 1
)
if not exist "Web-Client" (
    echo Error: Directorio Web-Client no encontrado
    exit /b 1
)

REM Iniciar Servidor Java
echo.
echo [1/2] Iniciando Servidor Java...
cd ServidorJava
start "Servidor Java" cmd /k gradle run
cd ..
timeout /t 3 /nobreak

REM Iniciar Proxy
echo.
echo [2/2] Iniciando Proxy Express...
cd Proxy
call npm install >nul 2>&1
start "Proxy Express" cmd /k npm start
cd ..
timeout /t 2 /nobreak

echo.
echo ==========================================
echo    Todos los servicios iniciados
echo ==========================================
echo.
echo Acceso:
echo   - Cliente Web: http://localhost:3000
echo   - Servidor Java: localhost:5000
echo.
echo Para detener, cierra las ventanas de terminal
echo.
pause
