# WhatsApp Ice CompuNet

Sistema de chat en tiempo real con mensajería de texto, grupos y notas de voz.

---

## 📋 Instrucciones claras para ejecutar el sistema

### Requisitos Previos

- **Java 11+**
- **Node.js 16+**
- **npm** (gestor de paquetes)

### Pasos para Ejecutar

**Terminal 1 - Iniciar Servidor Java (Puerto 5000):**
```bash
cd ServidorJava
gradle run
```

**Terminal 2 - Iniciar Proxy Express (Puerto 3000):**
```bash
cd Proxy
npm install
npm start
```

**Terminal 3 - Acceder a la Aplicación:**
Abre tu navegador en: `http://localhost:3000`

### Uso Básico

1. Ingresa tu nombre de usuario y haz clic en "Entrar"
2. Selecciona un contacto o crea un grupo
3. Envía mensajes de texto o notas de voz
4. Los mensajes se sincronizan en tiempo real

---

## 🌐 Descripción del flujo de comunicación entre cliente y servidor

### Arquitectura General

```
┌─────────────────────────────────┐
│   Cliente Web (Browser)          │
│   http://localhost:3000          │
│   HTML/CSS/JavaScript Vanilla    │
└──────────────┬──────────────────┘
               │
               │ HTTP REST + WebSocket
               │ - Enviar mensajes
               │ - Descargar historial
               │ - Enviar audio
               ▼
┌─────────────────────────────────┐
│   Proxy Express (Node.js)        │
│   Puerto 3000 (HTTP/WebSocket)   │
│   Puerto 5000 (TCP Client)       │
└──────────────┬──────────────────┘
               │
               │ TCP Text Protocol
               │ type:command|param:value
               ▼
┌─────────────────────────────────┐
│   Servidor Java Backend          │
│   Puerto 5000 (TCP Server)       │
│   - ChatManager (lógica)         │
│   - Persistencia en archivos     │
│   - Gestión de usuarios/grupos   │
└─────────────────────────────────┘
```

### Flujo de un Mensaje Privado

1. **Usuario escribe** un mensaje en el cliente web
2. **Cliente hace POST** a `/api/sendMessage` con `{from, to, content}`
3. **Proxy recibe** la petición HTTP y crea un comando TCP
4. **Proxy envía TCP** al servidor: `type:private_message|from:alice|to:bob|content:Hola`
5. **Servidor Java procesa** el comando y guarda el mensaje
6. **Servidor notifica** al Proxy sobre nuevos mensajes para otros usuarios
7. **Proxy reenvía** el mensaje al receptor vía WebSocket
8. **Receptor recibe** el mensaje en tiempo real

### Flujo de un Mensaje de Grupo

1. Usuario envía mensaje a un grupo desde el cliente web
2. Cliente hace POST a `/api/sendGroupMessage` con `{from, group_name, content}`
3. Proxy traduce a comando TCP: `type:group_message|from:alice|group:Amigos|content:...`
4. Servidor Java recibe y distribuye el mensaje a todos los miembros del grupo
5. Proxy reenvía a cada miembro vía WebSocket
6. Todos los miembros ven el mensaje en tiempo real

### Flujo de Notas de Voz

1. Usuario hace clic en el botón "🎤 Nota" para grabar audio
2. Browser solicita permiso de micrófono
3. Cliente captura audio usando MediaRecorder API
4. Audio se convierte a formato WAV y luego a Base64
5. Cliente hace POST a `/api/sendAudio` con el audio codificado
6. Proxy envía comando TCP con el audio al servidor
7. Servidor Java recibe y guarda el archivo de audio
8. Servidor notifica al Proxy
9. Proxy reenvía el evento al receptor vía WebSocket
10. Receptor descarga el audio y lo reproduce en un reproductor HTML5

### Capas de Comunicación

**Cliente → Proxy**: HTTP REST + WebSocket
- Login: POST `/api/login`
- Enviar mensaje: POST `/api/sendMessage`
- Enviar audio: POST `/api/sendAudio`
- Crear grupo: POST `/api/createGroup`
- Notificaciones: WebSocket bidireccional

**Proxy → Servidor Java**: TCP Text Protocol
- Formato: `type:comando|key1:value1|key2:value2`
- Conexión persistente por usuario
- Respuestas inmediatas a cada comando
- WebSocket para eventos en tiempo real

---

## 👥 Nombre de los integrantes del grupo

- **Juan Manuel Zuluaga - A00399738**

---

## 📁 Estructura del Proyecto

```
WhatsApp-ice-compunet1/
├── ServidorJava/              # Backend Java
│   ├── src/main/java/
│   │   ├── ui/                # Interfaz servidor
│   │   ├── model/             # Modelos de datos
│   │   ├── service/           # Lógica de negocio
│   │   ├── persistence/       # Almacenamiento
│   │   └── util/              # Utilidades
│   ├── data/
│   │   ├── history/           # Historial de mensajes
│   │   └── audio/             # Archivos de audio
│   └── build.gradle.kts
│
├── Proxy/                     # Backend Proxy (Node.js)
│   ├── src/main/
│   │   └── server-ice-simple.js
│   └── package.json
│
├── Web-Client/                # Frontend
│   ├── index.html
│   ├── js/app-ice.js
│   ├── src/ice-client.js
│   └── css/styles.css
│
└── README.md
```

## ✨ Funcionalidades

✅ Login con nombre de usuario
✅ Mensajes privados en tiempo real
✅ Grupos de chat
✅ Notas de voz
✅ Historial persistente
✅ Usuarios online
✅ WebSockets para actualizaciones

---

