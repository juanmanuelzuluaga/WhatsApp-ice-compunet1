#!/bin/bash

# Script para iniciar todos los servicios del proyecto WhatsApp Pro Max 1.0

echo "=========================================="
echo "   WhatsApp Pro Max 1.0 - Startup Script"
echo "=========================================="
echo ""

# Colores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Verificar que estamos en el directorio correcto
if [ ! -d "ServidorJava" ] || [ ! -d "Proxy" ] || [ ! -d "Web-Client" ]; then
    echo -e "${RED}Error: Este script debe ejecutarse desde la raíz del proyecto${NC}"
    echo "Directorios esperados: ServidorJava/, Proxy/, Web-Client/"
    exit 1
fi

# Iniciar Servidor Java en background
echo -e "${BLUE}1. Iniciando Servidor Java...${NC}"
cd ServidorJava
if command -v gradle &> /dev/null; then
    gradle run &
else
    ./gradlew run &
fi
JAVA_PID=$!
echo -e "${GREEN}   Servidor Java iniciado (PID: $JAVA_PID)${NC}"
sleep 3
cd ..

# Iniciar Proxy Express en background
echo ""
echo -e "${BLUE}2. Iniciando Proxy Express...${NC}"
cd Proxy
npm install > /dev/null 2>&1
npm start &
PROXY_PID=$!
echo -e "${GREEN}   Proxy Express iniciado (PID: $PROXY_PID)${NC}"
sleep 2
cd ..

echo ""
echo -e "${GREEN}=========================================="
echo "   ✅ Todos los servicios iniciados"
echo "==========================================${NC}"
echo ""
echo -e "${BLUE}Acceso:${NC}"
echo "  - Cliente Web: http://localhost:3000"
echo "  - Servidor Java: localhost:5000"
echo "  - Proxy: http://localhost:3000"
echo ""
echo -e "${BLUE}Para detener los servicios:${NC}"
echo "  - Presiona Ctrl+C en cada terminal"
echo "  - O ejecuta: kill $JAVA_PID $PROXY_PID"
echo ""
echo -e "${BLUE}Logs:${NC}"
echo "  - Proxy: proxy.log"
echo "  - Historia: ServidorJava/data/history/"
echo ""

# Mantener el script activo
wait
