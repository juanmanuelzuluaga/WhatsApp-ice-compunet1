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
┌─────────────────────────────────┐
│   Cliente Web (Navegador)       │
│   HTML/CSS/JavaScript Vanilla   │
│   Puerto: 3000                   │
└──────────────┬──────────────────┘
               │
               │ HTTP REST + WebSocket
               │
               ▼
┌─────────────────────────────────┐
│   Proxy Node.js (Express)        │
│   HTTP: Puerto 3000              │
│   WebSocket: Puerto 8080         │
└──────────────┬──────────────────┘
               │
               │ ZeroC Ice RPC
               │ (Invocación remota de métodos)
               ▼
┌─────────────────────────────────┐
│   Servidor Java (Ice Server)     │
│   Ice RPC: Puerto 5001           │
│   ChatServiceImpl                │
│   Persistencia en archivos       │
└─────────────────────────────────┘
```

### Flujo Detallado de Comunicación

**1. Envío de Mensaje Privado:**

- El usuario escribe un mensaje en el navegador y presiona enviar
- El cliente JavaScript hace una petición HTTP POST a `/api/sendMessage`
- El Proxy Node.js recibe la petición y llama al método Ice RPC `sendMessage(from, to, content)`
- El Servidor Java ejecuta el método, guarda el mensaje en persistencia
- El servidor retorna confirmación al Proxy vía Ice RPC
- El Proxy envía una notificación al destinatario mediante WebSocket
- El destinatario recibe el mensaje en tiempo real sin necesidad de recargar la página

**2. Creación de Grupos:**

- El usuario crea un grupo desde la interfaz web
- Cliente POST a `/api/createGroup` con nombre y lista de miembros
- Proxy invoca método Ice RPC `createGroup(groupName, creator, members)`
- Servidor Java crea el grupo y agrega a todos los miembros
- Proxy envía notificaciones WebSocket a cada miembro agregado
- Todos los miembros ven el nuevo grupo instantáneamente

**3. Envío de Notas de Voz:**

- Usuario presiona botón de micrófono y graba audio (MediaRecorder API del navegador)
- Audio se codifica en Base64 y se envía vía POST a `/api/sendAudio`
- Proxy guarda el archivo de audio en el servidor
- Proxy invoca Ice RPC `sendAudio(from, to, audioId, size, duration)`
- Servidor registra el audio en la base de datos
- Destinatario recibe notificación WebSocket con el ID del audio
- Cliente descarga y reproduce el audio automáticamente

**4. Sincronización en Tiempo Real:**

- Al hacer login, el cliente establece conexión WebSocket con el Proxy
- El Proxy mantiene un mapa de usuarios conectados y sus sockets
- Cuando ocurre un evento (mensaje, audio, grupo), el servidor notifica al Proxy
- El Proxy identifica al usuario destinatario y envía la notificación vía WebSocket
- Si WebSocket falla, existe un sistema de fallback con polling HTTP

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

