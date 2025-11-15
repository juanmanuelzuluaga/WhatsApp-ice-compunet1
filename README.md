# WhatsApp Pro Max 1.0 - Chat en Tiempo Real via Proxy HTTP

Un sistema de chat completo que utiliza un servidor Java con TCP, un proxy Express, y un cliente web para permitir mensajería, gestión de grupos e historial de conversaciones.

## 📝 Resumen Ejecutivo

**WhatsApp Pro Max** es una aplicación de chat en tiempo real que demuestra la integración entre múltiples tecnologías:

- **Backend:** Servidor Java que gestiona la lógica de negocio y persistencia
- **Proxy:** Express.js que actúa como intermediario HTTP ↔ TCP
- **Frontend:** Cliente web moderno basado en WhatsApp

**Características principales:**
✅ Autenticación de usuarios
✅ Mensajes privados en tiempo real
✅ Creación y gestión de grupos
✅ Historial de conversaciones persistente
✅ Interfaz intuitiva y responsive

---

## Integrantes del Equipo

👤 **Juan Manuel Zuluaga**
👤 **Miguel Perez**
👤 **Alejandro Rendon**

## Arquitectura del Proyecto

\`\`\`
┌─────────────────────────────────────────────┐
│        Web Client (HTML/CSS/JavaScript)     │
│         http://localhost:3000                │
└─────────────┬───────────────────────────────┘
              │ HTTP (fetch)
              ▼
┌─────────────────────────────────────────────┐
│      Express Proxy Server (Node.js)          │
│      http://localhost:3000                   │
│  - Maneja CORS                               │
│  - Traduce HTTP a comandos TCP               │
│  - Gestiona conexiones persistentes          │
└─────────────┬───────────────────────────────┘
              │ TCP Socket
              ▼
┌─────────────────────────────────────────────┐
│        Java Server (MainServer)              │
│     tcp://localhost:5000                     │
│  - Autenticación de usuarios                 │
│  - Gestión de mensajes                       │
│  - Creación y gestión de grupos              │
│  - Persistencia de historial                 │
└─────────────────────────────────────────────┘
\`\`\`

---

## 🚀 Instrucciones para Ejecutar el Sistema

### Requisitos Previos
- **Java 11+** (para el servidor backend)
- **Node.js 14+** (para el proxy)
- **Gradle** (para compilar el proyecto Java)
- **npm** (gerenciador de paquetes de Node.js)

### Paso 1: Iniciar el Servidor Java (Backend)

\`\`\`bash
cd ServidorJava
gradle clean build
gradle run
\`\`\`

El servidor Java escuchará en **localhost:5000**. Deberías ver en la consola:
\`\`\`
💬 SERVIDOR DE CHAT INICIADO EN PUERTO 5000
\`\`\`

### Paso 2: Iniciar el Proxy Express (Intermediario)

Abre una **nueva terminal** y ejecuta:

\`\`\`bash
cd Proxy
npm install
npm start
\`\`\`

El proxy escuchará en **http://localhost:3000**. Deberías ver:
\`\`\`
Proxy stateful corriendo en http://localhost:3000
\`\`\`

### Paso 3: Acceder al Cliente Web

Abre tu navegador y ve a:

\`\`\`
http://localhost:3000
\`\`\`

¡Listo! Ya puedes empezar a usar el sistema de chat.

### Pruebas Simultáneas

Para probar la comunicación entre usuarios:

1. **Abre dos navegadores (o pestañas incógnito)** en `http://localhost:3000`
2. En la **primera ventana**: Ingresa usuario `alice`
3. En la **segunda ventana**: Ingresa usuario `bob`
4. Ambos pueden enviarse mensajes privados y crear grupos

---

## 📋 Componentes del Sistema

### 1. Servidor Java (`ServidorJava/`)
- **MainServer.java**: Servidor principal que maneja todas las conexiones TCP
- **ChatManager.java**: Lógica de negocio (usuarios, grupos, mensajes)
- **TCPConnection.java**: Gestión de conexiones TCP (modo texto para compatibilidad)
- **Message.java**: Modelo de mensajes de texto
- **AudioMessage.java**: Modelo de mensajes de audio
- **Group.java**: Modelo de grupos
- **User.java**: Modelo de usuarios
- **ChatHistory.java**: Persistencia de mensajes en archivo

**Comando para iniciar:**
\`\`\`bash
cd ServidorJava
gradle run
\`\`\`

### 2. Proxy Express (`Proxy/`)
- **server.js**: Servidor proxy que:
  - Maneja CORS correctamente
  - Sirve archivos estáticos del cliente web
  - Traduce peticiones HTTP a comandos TCP
  - Gestiona conexiones persistentes por usuario
  - Implementa timeouts para evitar cuelgues

**Comando para iniciar:**
\`\`\`bash
cd Proxy
npm install
npm start
\`\`\`

### 3. Cliente Web (`Web-Client/`)
- **index.html**: Interfaz de usuario
- **css/styles.css**: Estilos inspirados en WhatsApp
- **js/app.js**: Lógica del cliente, manejo de eventos, llamadas API

**Cómo acceder:**
\`\`\`
http://localhost:3000
\`\`\`

## 📡 Flujo de Comunicación Detallado

### 1️⃣ Flujo de Login

\`\`\`
┌─────────┐                                    ┌───────┐                          ┌─────────────┐
│ Cliente │                                    │ Proxy │                          │ Java Server │
│   Web   │                                    │Express│                          │   Backend   │
└────┬────┘                                    └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │─── 1. POST /api/login ──────────────────→ │                                     │
     │       {username: 'alice'}                 │                                     │
     │                                            │─ 2. Crear Socket TCP ────────────→ │
     │                                            │     Conectar a :5000               │
     │                                            │                                     │
     │                                            │←─ 3. Enviar Login (TCP) ──────────│
     │                                            │     type:login|username:alice     │
     │                                            │                                     │
     │                                            │←─ 4. Respuesta Login ─────────────│
     │                                            │     type:login_success|message:.. │
     │                                            │     [Almacenar conexión]          │
     │                                            │                                     │
     │←─ 5. JSON Response (200 OK) ─────────────│                                     │
     │    {ok: true, message: "..."}             │                                     │
     │                                            │                                     │
```

**Resultado**: 
- ✅ Usuario autenticado
- ✅ Conexión TCP persistente abierta
- ✅ Proxy almacena la conexión para futuras peticiones

---

### 2️⃣ Flujo de Mensaje Privado

\`\`\`
┌─────────┐                                    ┌───────┐                          ┌─────────────┐
│ Cliente │                                    │ Proxy │                          │ Java Server │
│  alice  │                                    │Express│                          │  Backend    │
└────┬────┘                                    └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │─── 1. POST /api/sendMessage ──────────→ │                                     │
     │       {from:'alice',to:'bob',content}   │                                     │
     │                                            │─ 2. Usar conexión TCP ────────→ │
     │                                            │     (Ya existe)                   │
     │                                            │                                     │
     │                                            │     Enviar TCP:                   │
     │                                            │     type:private_message|...      │
     │                                            │                                     │
     │                                            │←─ 3. Mensaje guardado ────────────│
     │                                            │     Entregar a 'bob'               │
     │                                            │                                     │
     │←─ 4. Confirmación (200 OK) ────────────│                                     │
     │    {ok: true, message: "Enviado"}      │                                     │
     │                                            │                                     │

┌──────────┐                                   ┌───────┐                          ┌─────────────┐
│ Cliente  │                                   │ Proxy │                          │ Java Server │
│   bob    │                                   │Express│                          │   Backend   │
└────┬─────┘                                   └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │                                            │←─ 5. Notificación TCP ────────────│
     │                                            │     type:private_message|...      │
     │                                            │     [Guardar en cola]             │
     │                                            │                                     │
     │─── 6. GET /api/notifications/bob ─────→ │                                     │
     │        (Polling cada 1 seg)               │                                     │
     │                                            │                                     │
     │←─ 7. JSON Array ──────────────────────│                                     │
     │    {notifications: [                      │                                     │
     │       "type:private_message|..."         │                                     │
     │    ]}                                      │                                     │
     │                                            │                                     │
     │ [Procesar notificación]                   │                                     │
     │ [Mostrar en pantalla]                     │                                     │
     │                                            │                                     │
```

**Resultado**:
- ✅ Mensaje enviado de alice a bob
- ✅ Bob recibe el mensaje mediante polling
- ✅ Mensaje se muestra en tiempo real

---

### 3️⃣ Flujo de Creación de Grupo

\`\`\`
┌─────────┐                                    ┌───────┐                          ┌─────────────┐
│ Cliente │                                    │ Proxy │                          │ Java Server │
│  alice  │                                    │Express│                          │  Backend    │
└────┬────┘                                    └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │─── 1. POST /api/createGroup ───────────→ │                                     │
     │       {group_name:'Amigos',creator:...} │                                     │
     │                                            │─ 2. Enviar TCP ────────────────→ │
     │                                            │     type:create_group|...        │
     │                                            │                                     │
     │                                            │←─ 3. Grupo Creado ────────────────│
     │                                            │     Notificación: "Grupo creado"  │
     │                                            │                                     │
     │←─ 4. Confirmación ─────────────────────│                                     │
     │    {ok: true}                             │                                     │
     │                                            │                                     │
     │─── 5. POST /api/joinGroup ────────────→ │                                     │
     │       {username:'bob',group_name:...}   │                                     │
     │       [Para cada miembro]                 │                                     │
     │                                            │─ 6. Enviar TCP ────────────────→ │
     │                                            │     type:join_group|...          │
     │                                            │                                     │
     │                                            │←─ 7. Bob Unido ─────────────────│
     │                                            │                                     │
     │←─ 8. Confirmación ─────────────────────│                                     │
     │    {ok: true}                             │                                     │
     │                                            │                                     │

┌──────────┐                                   ┌───────┐                          ┌─────────────┐
│ Cliente  │                                   │ Proxy │                          │ Java Server │
│   bob    │                                   │Express│                          │   Backend   │
└────┬─────┘                                   └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │                                            │←─ 9. Notificación ────────────────│
     │                                            │     type:system_message|...      │
     │                                            │     type:join_group_success|...   │
     │                                            │     [Guardar en cola]             │
     │                                            │                                     │
     │─── 10. GET /api/notifications/bob ────→ │                                     │
     │        [Polling]                          │                                     │
     │                                            │                                     │
     │←─ 11. Array de Notificaciones ────────│                                     │
     │    {notifications: [...]}                 │                                     │
     │                                            │                                     │
     │ [Procesar: Crear grupo localmente]       │                                     │
     │ [Mostrar grupo en lista]                  │                                     │
     │                                            │                                     │
```

**Resultado**:
- ✅ Grupo creado en el servidor
- ✅ Todos los miembros se unen automáticamente
- ✅ Todos ven el grupo en su lista de contactos

---

### 4️⃣ Flujo de Mensaje de Grupo

\`\`\`
┌─────────┐                                    ┌───────┐                          ┌─────────────┐
│ Cliente │                                    │ Proxy │                          │ Java Server │
│  alice  │                                    │Express│                          │  Backend    │
└────┬────┘                                    └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │─── 1. POST /api/sendGroupMessage ──────→ │                                     │
     │       {from:'alice',group_name:'Amigos'} │                                     │
     │                                            │─ 2. Enviar TCP ────────────────→ │
     │                                            │     type:group_message|...       │
     │                                            │                                     │
     │                                            │←─ 3. Guardar y Distribuir ───────│
     │                                            │     - Guardar en BD               │
     │                                            │     - Enviar a cada miembro      │
     │                                            │       (excepto al remitente)     │
     │                                            │                                     │
     │←─ 4. Confirmación ─────────────────────│                                     │
     │    {ok: true}                             │                                     │

┌──────────┐                                   ┌───────┐                          ┌─────────────┐
│ Cliente  │                                   │ Proxy │                          │ Java Server │
│   bob    │                                   │Express│                          │   Backend   │
└────┬─────┘                                   └───┬───┘                          └──────┬──────┘
     │                                            │                                     │
     │                                            │←─ 5. Mensaje del Grupo ───────────│
     │                                            │     type:group_message|...       │
     │                                            │     [Guardar en cola]             │
     │                                            │                                     │
     │─── 6. GET /api/notifications/bob ─────→ │                                     │
     │        [Polling cada 1 segundo]          │                                     │
     │                                            │                                     │
     │←─ 7. Notificación ─────────────────────│                                     │
     │    {notifications: [                      │                                     │
     │       "type:group_message|from:alice|..." │                                     │
     │    ]}                                      │                                     │
     │                                            │                                     │
     │ [Procesar: Mensaje de grupo]              │                                     │
     │ [Mostrar en el chat del grupo]            │                                     │
     │                                            │                                     │
```

**Resultado**:
- ✅ Mensaje enviado a todos los miembros del grupo
- ✅ Cada miembro recibe vía polling
- ✅ Se muestra en tiempo real en el chat grupal

---

## Flujo de Funcionamiento

### Login
1. Usuario ingresa su nombre en el cliente web
2. El cliente hace POST a `/api/login` con el username
3. El proxy crea una conexión TCP con el servidor Java
4. El servidor Java autentica al usuario y responde `type:login_success`
5. El proxy mantiene la conexión abierta para futuras peticiones

### Enviar Mensaje Privado
1. Usuario escribe y envía un mensaje en el chat privado
2. Cliente hace POST a `/api/sendMessage` con `{from, to, content}`
3. Proxy envía comando TCP: `type:private_message|from:...|to:...|content:...`
4. Servidor Java guarda el mensaje y lo entrega al destinatario
5. Proxy responde al cliente con confirmación

### Crear Grupo
1. Usuario ingresa nombre del grupo y miembros
2. Cliente hace POST a `/api/createGroup` con `{group_name, creator}`
3. Proxy traduce a comando TCP y envía al servidor
4. Servidor Java crea el grupo en memoria
5. El grupo aparece en la lista de contactos

### Obtener Historial
1. Al seleccionar un chat, el cliente solicita el historial
2. Cliente hace GET a `/api/history/:target?username=...&isGroup=...`
3. Servidor Java carga mensajes del archivo de historial
4. Proxy devuelve los mensajes al cliente

## Endpoints del Proxy

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/login` | Autenticar usuario |
| POST | `/api/sendMessage` | Enviar mensaje privado |
| POST | `/api/createGroup` | Crear grupo |
| GET | `/api/groups/:username` | Obtener grupos del usuario |
| POST | `/api/joinGroup` | Unirse a un grupo |
| GET | `/api/history/:target` | Obtener historial de mensajes |
| GET | `/api/onlineUsers/:username` | Obtener usuarios online |
| GET | `/api/test` | Verificar estado del proxy |

## Formato de Comandos TCP

Los comandos TCP utilizan formato `key:value|key:value`:

\`\`\`
type:login|username:carlos
type:private_message|from:carlos|to:sofia|content:Hola!
type:create_group|group_name:Amigos|creator:carlos
type:get_groups|username:carlos
type:join_group|username:carlos|group_name:Amigos
type:get_history|target:sofia|username:carlos|isGroup:false
\`\`\`

## Características Implementadas

✅ **Autenticación**: Login con nombre de usuario
✅ **Mensajes Privados**: Comunicación uno a uno
✅ **Grupos**: Crear y unirse a grupos de chat
✅ **Historial**: Persistencia de mensajes en archivos
✅ **Usuarios Online**: Ver quién está conectado
✅ **Interfaz Web**: Cliente moderno basado en WhatsApp
✅ **CORS**: Configuración correcta para desarrollo
✅ **Manejo de Errores**: Validación y mensajes de error claros

## Limitaciones (Según Especificación)

- ❌ Notas de voz: No implementadas en esta etapa
- ❌ Llamadas: No implementadas en esta etapa
- ❌ WebSockets: No implementados (solo HTTP polling)
- ❌ Persistencia en BD: Historial en archivos solamente

## Troubleshooting

### "Error al conectar: Failed to fetch"
1. Verificar que el proxy esté corriendo en `http://localhost:3000`
2. Verificar CORS en `Proxy/src/main/server.js`
3. Revisar la consola del navegador (F12) para más detalles

### El servidor Java no responde
1. Verificar que el servidor esté en `localhost:5000`
2. Revisar logs en la consola de Java
3. Verificar que no haya un cortafuegos bloqueando el puerto

### Mensajes no se guardan
1. Verificar que la carpeta `ServidorJava/data/history/` existe
2. Verificar permisos de escritura en el sistema de archivos

## Próximas Mejoras

- [ ] WebSockets para comunicación en tiempo real
- [ ] Notas de voz con UDP
- [ ] Llamadas de voz
- [ ] Base de datos relacional
- [ ] Autenticación segura con tokens
- [ ] Encriptación de mensajes
- [ ] Búsqueda en historial
- [ ] Notificaciones del sistema

---

**Desarrollado por:**
- 👨‍💻 Juan Manuel Zuluaga
- 👨‍💻 Miguel Perez
- 👨‍💻 Alejandro Rendon

**Proyecto:** Tarea 2 - Sistemas de Chat en Tiempo Real
**Última actualización:** Noviembre 2025
