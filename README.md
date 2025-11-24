# WhatsApp Ice CompuNet

Sistema de chat en tiempo real con mensajería de texto, grupos y notas de voz utilizando ZeroC Ice RPC y WebSockets.

---

## Instrucciones para ejecutar el sistema

### Requisitos Previos

- **Java 11 o superior**
- **Node.js 16 o superior**
- **npm** (gestor de paquetes de Node.js)
- **ZeroC Ice 3.7** (las librerías ya están incluidas en el proyecto)

### Pasos para Ejecutar

Siga estos pasos en orden, abriendo una terminal diferente para cada comando:

**Terminal 1 - Iniciar Servidor Java (Ice RPC):**
```bash
cd ServidorJava
gradle run
```

**Terminal 2 - Iniciar Proxy Node.js:**
```bash
cd Proxy
npm install
npm run start:ice
```

**Terminal 3 - Acceder a la Aplicación:**

Abra su navegador web en: `http://localhost:3000`

### Uso Básico

1. Ingrese su nombre de usuario y haga clic en "Entrar"
2. Seleccione un contacto de la lista de usuarios conectados o cree un grupo
3. Envíe mensajes de texto o grabe notas de voz presionando el botón de micrófono
4. Los mensajes se reciben en tiempo real gracias a WebSockets

---

## Descripción del flujo de comunicación entre cliente y servidor

### Arquitectura del Sistema

El sistema utiliza una arquitectura de tres capas con ZeroC Ice como mecanismo principal de comunicación RPC:

```
┌──────────────────────────────────────────┐
│   Cliente Web (Navegador)                │
│   HTML/CSS/JavaScript Vanilla            │
│   Accede a: http://localhost:3000        │
└──────────────┬───────────────────────────┘
               │
               │ HTTP REST (puerto 3000)
               │ WebSocket (puerto 8080)
               │
               ▼
┌──────────────────────────────────────────┐
│   Proxy Node.js (Express + WebSocket)    │
│   - Servidor HTTP: Puerto 3000           │
│   - WebSocket Server: Puerto 8080        │
│   - Sirve archivos estáticos (HTML/CSS/JS)│
│   - Mantiene mapa de usuarios conectados │
└──────────────┬───────────────────────────┘
               │
               │ ZeroC Ice RPC (puerto 5001)
               │ (Invocación remota de métodos)
               │
               ▼
┌──────────────────────────────────────────┐
│   Servidor Java (Ice RPC Server)         │
│   - Ice Adapter: Puerto 5001             │
│   - ChatServiceImpl (lógica de negocio)  │
│   - ChatManager (gestión de usuarios)    │
│   - ChatHistory (persistencia en archivos)│
└──────────────────────────────────────────┘
```

### Flujo Detallado de Comunicación

**1. Envío de Mensaje Privado:**

- El usuario escribe un mensaje en el navegador y presiona enviar
- El cliente JavaScript hace una petición HTTP POST a `/api/sendMessage` (puerto 3000)
- El Proxy Node.js recibe la petición y llama al método Ice RPC `sendMessage(from, to, content)`
- El Servidor Java ejecuta el método:
  - Guarda el mensaje en archivo de texto en `data/history/user_{destinatario}.txt`
  - Retorna `true` si fue exitoso
- El Proxy recibe confirmación del servidor Java vía Ice RPC
- El Proxy busca al destinatario en su mapa de conexiones WebSocket activas
- Si el destinatario está conectado, envía notificación WebSocket (puerto 8080) con el mensaje
- El destinatario recibe el mensaje en tiempo real sin necesidad de recargar la página
- Si el destinatario NO está conectado, el mensaje queda guardado y lo verá al hacer login

**2. Creación de Grupos:**

- El usuario crea un grupo desde la interfaz web (nombre + miembros separados por comas)
- Cliente POST a `/api/createGroup` con `groupName`, `creator`, `members[]`
- Proxy invoca método Ice RPC `createGroup(groupName, creator, members)`
- Servidor Java:
  - Crea el objeto `Group` en memoria
  - Agrega al creador como miembro vía `joinGroup(groupName, creator)`
  - Agrega a cada miembro de la lista vía `joinGroup(groupName, member)`
  - Guarda el grupo en `data/history/group_{nombre}.txt`
- Proxy recibe confirmación Ice RPC
- Proxy llama a `getGroupMembers(groupName)` para obtener lista completa de miembros
- Proxy envía notificación WebSocket `group_created` a CADA miembro del grupo
- Todos los miembros ven el nuevo grupo instantáneamente en su lista de grupos

**3. Envío de Notas de Voz:**

- Usuario presiona botón de micrófono 🎤 y graba audio (MediaRecorder API del navegador)
- Audio se captura en formato WebM/Opus y se codifica en Base64
- Cliente envía vía POST a `/api/sendAudio` con `from`, `to` (o `group_name`), `audio_data` (Base64)
- Proxy Node.js:
  - Decodifica Base64 a Buffer binario
  - Genera ID único: `audio_{timestamp}_{random}.audio`
  - Guarda archivo en `Proxy/ServidorJava/data/audio/{audioId}.audio`
  - Determina si es mensaje privado (tiene `to`) o grupal (tiene `group_name`)
- Para mensaje privado:
  - Proxy invoca Ice RPC `sendAudio(from, to, audioId, size, duration)`
  - Servidor Java guarda SOLO metadatos en `data/history/user_{to}.txt_audio`
  - Proxy envía notificación WebSocket al destinatario con `audioId`
- Para mensaje grupal:
  - Proxy invoca Ice RPC `sendGroupAudio(from, groupName, audioId, size, duration)`
  - Servidor Java guarda SOLO metadatos en `data/history/group_{nombre}.txt_audio`
  - Proxy envía notificación WebSocket a todos los miembros del grupo
- Cliente destinatario:
  - Recibe notificación WebSocket con `audioId`
  - Hace GET a `/api/audio/{audioId}` para descargar el archivo
  - Reproduce automáticamente usando `<audio>` HTML5

**4. Sincronización en Tiempo Real:**

- Al hacer login exitoso, el cliente establece conexión WebSocket con `ws://localhost:8080`
- Cliente envía mensaje de autenticación con su username
- El Proxy mantiene un mapa `Map<username, WebSocket>` de usuarios conectados
- Cuando ocurre un evento (mensaje, audio, grupo creado):
  - El Proxy (NO el servidor Java) identifica a los destinatarios
  - Busca cada destinatario en el mapa de conexiones WebSocket
  - Si está conectado, envía notificación JSON vía WebSocket
  - Tipos de notificaciones: `message`, `group_message`, `audio`, `group_audio`, `group_created`, `group_joined`
- Si WebSocket falla o usuario no está conectado:
  - El mensaje/audio se guarda en persistencia (archivo de texto)
  - Usuario lo verá al cargar historial en su próximo login
- Fallback: Existe polling HTTP cada 1 segundo a `/api/notifications/{username}` (solo si WebSocket falla)

### Tecnologías de Comunicación

**Capa Cliente-Proxy:**
- HTTP REST para operaciones síncronas (login, enviar mensaje, crear grupo)
- WebSocket para notificaciones asíncronas en tiempo real
- JSON como formato de intercambio de datos

**Capa Proxy-Servidor:**
- ZeroC Ice RPC para invocación remota de métodos
- Archivo de definición Ice (`chat.ice`) que especifica la interfaz del servicio
- Serialización binaria eficiente de Ice para transferencia de datos
- Métodos síncronos con manejo de excepciones

---

## Nombre de los integrantes del grupo

- **Juan Manuel Zuluaga - A00399738**

---

