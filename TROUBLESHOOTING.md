# Guía de Resolución de Problemas

## Error: "Failed to fetch"

### Síntomas
- Al intentar login, aparece el mensaje "Error al conectar: Failed to fetch"
- La consola del navegador muestra error de CORS o conexión

### Causas Posibles

1. **El proxy no está ejecutándose**
   - Verifica que veas "Proxy stateful corriendo en http://localhost:3000"
   - Reinicia el proxy con `npm start`

2. **CORS mal configurado**
   - Abre `Proxy/src/main/server.js`
   - Verifica que la sección `cors()` permita localhost
   - Debería verse así:
     \`\`\`javascript
     origin: function (origin, callback) {
         if (!origin || origin.includes('localhost') || origin.includes('127.0.0.1')) {
             callback(null, true);
         }
     }
     \`\`\`

3. **Puerto 3000 ocupado**
   - Linux/Mac:
     \`\`\`bash
     lsof -i :3000
     kill -9 [PID]
     \`\`\`
   - Windows:
     \`\`\`bash
     netstat -ano | findstr :3000
     taskkill /PID [PID] /F
     \`\`\`

### Solución Rápida
\`\`\`bash
# 1. Termina los procesos actuales
pkill -f "node"
pkill -f "gradle run"

# 2. Reinicia desde cero
cd Proxy
npm start

# En otra terminal:
cd ServidorJava
gradle run
\`\`\`

---

## Error: "Usuario no conectado. Debe hacer login primero"

### Síntomas
- Login funciona, pero al enviar mensajes aparece este error
- El usuario se desconecta automáticamente

### Causas Posibles

1. **La conexión TCP se cierra**
   - Verifica que el servidor Java siga ejecutándose
   - Revisa los logs del Java para ver si hay excepciones

2. **Timeout en el proxy**
   - La conexión TCP puede cerrarse por inactividad
   - Aumenta el timeout en `Proxy/src/main/server.js`:
     \`\`\`javascript
     socket.setTimeout(10000); // 10 segundos
     \`\`\`

3. **El servidor Java rechazó la conexión**
   - Verifica que el puerto 5000 esté disponible
   - Revisa los logs: busca "Error de socket" o "Conexión cerrada"

### Solución
1. Reinicia ambos servidores
2. Abre la consola del navegador (F12)
3. Intenta el login de nuevo
4. Observa los logs en tiempo real

---

## Error: "Falta username válido"

### Síntomas
- El servidor rechaza nombres de usuario válidos
- Aparece error al hacer login

### Causas Posibles

1. **El formulario envía datos vacíos**
   - Verifica que escribas algo en el campo
   - La validación requiere al menos 1 carácter

2. **El servidor Java no está procesando el comando**
   - Revisa si ves en los logs: "✅ Usuario conectado"
   - Si no ves este mensaje, el servidor no recibió el comando

### Solución
\`\`\`bash
# 1. Verifica que el servidor Java esté ejecutándose
# Deberías ver: "💬 SERVIDOR DE CHAT INICIADO EN PUERTO 5000"

# 2. En el navegador, abre Console (F12)
# Debería haber un log que diga:
# "Intentando conectar a http://localhost:3000/api/login"

# 3. Si no ves ese log, el cliente no está iniciando bien
# Recarga la página (F5)
\`\`\`

---

## Error: "Grupo ya existe" o "Creador no está online"

### Síntomas
- No puedes crear un grupo
- El error aparece aunque el usuario está conectado

### Causas Posibles

1. **El usuario no está registrado en el sistema Java**
   - Aunque el login sea exitoso, el Java podría no haberlo registrado
   - Verifica que veas "✅ Usuario conectado: [name]" en los logs de Java

2. **El nombre del grupo ya existe**
   - Los nombres de grupos son únicos
   - Intenta con otro nombre

### Solución
1. Cierra el navegador completamente
2. Mata los procesos de Java y Proxy
3. Elimina archivos de historial:
   \`\`\`bash
   rm -rf ServidorJava/data/history/*
   \`\`\`
4. Reinicia todo desde cero

---

## Los mensajes no se guardan

### Síntomas
- Envías mensajes, aparecen en la interfaz
- Pero no se guardan en el historial
- Al recargar, los mensajes desaparecen

### Causas Posibles

1. **Carpeta de historial no existe**
   - Verifica: `ServidorJava/data/history/`
   - Debería crearse automáticamente

2. **Permisos de escritura insuficientes**
   - En Linux/Mac:
     \`\`\`bash
     chmod -R 755 ServidorJava/data
     \`\`\`

3. **El servidor Java no está guardando**
   - Busca en los logs: "Mensaje guardado:"
   - Si no ves este mensaje, hay un problema en ChatManager

### Solución
\`\`\`bash
# 1. Crea manualmente la carpeta
mkdir -p ServidorJava/data/history
mkdir -p ServidorJava/data/audio

# 2. Dale permisos
chmod -R 755 ServidorJava/data

# 3. Reinicia el servidor
\`\`\`

---

## La interfaz se ve muy lenta o tarda en actualizar

### Síntomas
- Hay lag al escribir mensajes
- La interfaz tarda en responder
- Los mensajes aparecen con demora

### Causas Posibles

1. **La máquina está lenta**
   - Revisa recursos: RAM, CPU
   - Cierra otras aplicaciones

2. **Hay muchos mensajes en el historial**
   - La renderización de 1000+ mensajes es lenta
   - Implementa paginación (ver ROADMAP)

3. **El servidor Java está sobrecargado**
   - Revisa si hay errores en los logs
   - Reinicia todos los servicios

### Solución
\`\`\`bash
# Limpia el historial para empezar de nuevo
rm -rf ServidorJava/data/history/*.txt

# Reinicia todo
\`\`\`

---

## Error: "Gradle no encontrado"

### Síntomas
- Al ejecutar el servidor Java: "gradle: command not found"

### Solución

**Opción 1: Usar el wrapper incluido**
\`\`\`bash
cd ServidorJava
./gradlew run  # En Mac/Linux
gradlew.bat run  # En Windows
\`\`\`

**Opción 2: Instalar Gradle globalmente**
\`\`\`bash
# Mac (con Homebrew)
brew install gradle

# Linux (descarga desde gradle.org)
wget https://services.gradle.org/distributions/gradle-8.0-bin.zip
unzip gradle-8.0-bin.zip
export PATH=$PATH:/path/to/gradle-8.0/bin

# Windows: Descarga desde gradle.org y agrega a PATH
\`\`\`

---

## Puerto 5000 ya está en uso

### Síntomas
- Error: "Address already in use"
- No puedes iniciar el servidor Java

### Solución

1. **Busca qué está usando el puerto**
   - Linux/Mac:
     \`\`\`bash
     lsof -i :5000
     \`\`\`
   - Windows:
     \`\`\`bash
     netstat -ano | findstr :5000
     \`\`\`

2. **Mata el proceso**
   - Linux/Mac:
     \`\`\`bash
     kill -9 [PID]
     \`\`\`
   - Windows:
     \`\`\`bash
     taskkill /PID [PID] /F
     \`\`\`

3. **O cambia el puerto**
   - En `ServidorJava/src/main/java/ui/MainServer.java`, línea de main:
     \`\`\`java
     int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
     // Cambia 5000 a otro puerto, ej: 5001
     \`\`\`

---

## Necesito más ayuda

Si el problema persiste:

1. **Recoge información de debug**
   - Captura de pantalla del error
   - Output completo de la consola del navegador (F12)
   - Logs del proxy y Java
   - Tu sistema operativo y versiones instaladas

2. **Revisa los archivos de log**
   - Cliente: Consola del navegador (F12 > Console)
   - Proxy: `proxy.log` en la raíz
   - Servidor: Consola donde ejecutaste `gradle run`

3. **Intenta reset completo**
   \`\`\`bash
   # Mata todos los procesos
   pkill -f node
   pkill -f java
   
   # Limpia datos
   rm -rf ServidorJava/data/*
   rm proxy.log
   
   # Reinicia todo
   # (sigue instrucciones de SETUP.md)
   \`\`\`
