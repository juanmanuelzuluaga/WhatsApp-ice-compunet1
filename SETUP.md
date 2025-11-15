# Guía de Instalación y Ejecución

## Requisitos Previos

- **Java 11+** (para el servidor Java)
- **Node.js 16+** (para el proxy Express)
- **Gradle** (para compilar el servidor Java)
- **npm** (gestor de paquetes de Node)

## Instalación Step by Step

### 1. Preparar el Servidor Java

\`\`\`bash
# Navegar al directorio
cd ServidorJava

# Compilar y ejecutar
gradle build
gradle run
\`\`\`

Si Gradle no está instalado globalmente, puedes usar el wrapper incluido:
\`\`\`bash
./gradlew build
./gradlew run
\`\`\`

El servidor debería mostrar:
\`\`\`
💬 SERVIDOR DE CHAT INICIADO EN PUERTO 5000
\`\`\`

### 2. Preparar el Proxy Express

En otra terminal:

\`\`\`bash
# Navegar al directorio
cd Proxy

# Instalar dependencias
npm install

# Iniciar el proxy
npm start
\`\`\`

El proxy debería mostrar:
\`\`\`
Proxy stateful corriendo en http://localhost:3000
Sirviendo Web-Client desde: /path/to/Web-Client
Servidor Java esperado en: localhost:5000
\`\`\`

### 3. Acceder al Cliente Web

Abre tu navegador en:
\`\`\`
http://localhost:3000
\`\`\`

## Verificación

### Verificar Servidor Java
\`\`\`bash
# Debería mostrar mensajes de conexión y comandos procesados
# Busca logs como: "✅ Usuario conectado: [username]"
\`\`\`

### Verificar Proxy
\`\`\`bash
# En la consola del proxy deberías ver:
# - Logs de peticiones HTTP
# - Conexiones TCP al servidor Java
# - Respuestas del servidor
\`\`\`

### Verificar Cliente Web
\`\`\`bash
# En la consola del navegador (F12 > Console)
# Debería ver logs de conexión exitosa:
# - "Conexión con el proxy: ..."
# - "Login exitoso: ..."
\`\`\`

## Estructura de Archivos de Datos

\`\`\`
ServidorJava/data/
├── history/                    # Historial de mensajes
│   ├── user_[username].txt     # Mensajes privados
│   └── group_[groupname].txt   # Mensajes grupales
└── audio/                      # Archivos de audio (si se implementan)
\`\`\`

## Pruebas Manuales

### Test 1: Login
1. Abre `http://localhost:3000`
2. Ingresa tu nombre (ej: "carlos")
3. Deberías ver "Bienvenido, carlos" en verde

### Test 2: Crear Contacto
1. Haz clic en "+ Contacto"
2. Ingresa un nombre de contacto (ej: "sofia")
3. El contacto debería aparecer en la lista

### Test 3: Enviar Mensaje
1. Haz clic en un contacto
2. Escribe un mensaje
3. Haz clic en "Enviar"
4. El mensaje debería aparecer en la conversación

### Test 4: Crear Grupo
1. Haz clic en "Crear" en la sección de grupos
2. Nombre del grupo: "Amigos"
3. Miembros: "sofia,juan,maria"
4. El grupo debería aparecer en la lista

### Test 5: Verificar Historial
1. Cierra el navegador
2. Vuelve a iniciar sesión con el mismo nombre
3. Los mensajes anteriores deberían estar en el historial
4. (Los archivos se guardan en `ServidorJava/data/history/`)

## Debugging

### Activar Logs Detallados

**En Proxy:**
- Los logs se guardan en `proxy.log`
- Revisa la consola para ver peticiones en tiempo real

**En Java:**
- La consola muestra todos los eventos del servidor
- Busca símbolos como ✅, ❌, 💬, 🔗 para identificar eventos

**En Cliente Web:**
- Abre F12 > Console para ver logs de JavaScript
- Revisa la pestaña Network para ver peticiones HTTP

### Ports Ocupados

Si los puertos están ocupados:

\`\`\`bash
# Linux/Mac - Ver qué usa el puerto 3000
lsof -i :3000

# Windows
netstat -ano | findstr :3000

# Matar el proceso (Linux/Mac)
kill -9 [PID]

# O cambiar el puerto en Proxy/src/main/server.js línea: app.listen(3000, ...)
\`\`\`

## Variables de Entorno

El proyecto actualmente no utiliza variables de entorno, pero puedes agregar:

\`\`\`javascript
// En Proxy/src/main/server.js
const PORT = process.env.PORT || 3000;
const TCP_HOST = process.env.TCP_HOST || "localhost";
const TCP_PORT = process.env.TCP_PORT || 5000;
\`\`\`

## Deployment a Vercel

El cliente web puede deployarse a Vercel, pero requiere un proxy backend. Opciones:

1. **Mantener Proxy local**: No es práctico para producción
2. **Deployar Proxy a Vercel/Railway**: Node.js soporta Express
3. **Deployar Servidor Java a un VPS**: Para la lógica principal

---

¿Necesitas ayuda? Revisa los logs en la consola y consulta el archivo README.md
