import express from "express"
import net from "net"
import bodyParser from "body-parser"
import cors from "cors"
import path from "path"
import { fileURLToPath } from "url"
import fs from "fs"

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
console.log("=== PROXY STARTUP ===")
console.log("PID:", process.pid)
console.log("__filename:", __filename)
console.log("__dirname:", __dirname)
console.log("Timestamp:", new Date().toISOString())

const LOG_PATH = path.resolve(__dirname, "..", "..", "proxy.log")
function appendLog(...args) {
  try {
    const line = args.map((a) => (typeof a === "string" ? a : JSON.stringify(a))).join(" ") + "\n"
    fs.appendFileSync(LOG_PATH, new Date().toISOString() + " " + line)
  } catch (e) {}
}
appendLog("PROXY STARTUP", "PID:", process.pid, "__filename:", __filename)
process.on("uncaughtException", (err) => {
  console.error("Uncaught exception:", err)
  appendLog("Uncaught exception:", err && err.stack ? err.stack : String(err))
})
process.on("unhandledRejection", (reason) => {
  console.error("Unhandled rejection:", reason)
  appendLog("Unhandled rejection:", reason && reason.stack ? reason.stack : String(reason))
})

const app = express()

app.use(
  cors({
    origin: (origin, callback) => {
      if (!origin || origin.includes("localhost") || origin.includes("127.0.0.1")) {
        callback(null, true)
      } else {
        callback(new Error("Not allowed by CORS"))
      }
    },
    methods: ["GET", "POST", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Accept"],
    credentials: true,
    optionsSuccessStatus: 200,
  }),
)

app.use(bodyParser.json({ limit: "50mb" }))
app.use(bodyParser.urlencoded({ limit: "50mb", extended: true }))

app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`, req.body)
  appendLog(`${req.method} ${req.path}`, req.body)
  next()
})

const TCP_HOST = "localhost"
const TCP_PORT = 5000

const activeConnections = new Map()
const pendingNotifications = new Map() // Guardar notificaciones para cada usuario

function connectUser(username) {
  return new Promise((resolve, reject) => {
    if (activeConnections.has(username)) {
      console.log(`[${username}] Ya existe conexión activa`)
      resolve(activeConnections.get(username))
      return
    }

    const socket = new net.Socket()
    const userSession = { socket: socket, responseQueue: [], username: username, connected: false, isConnecting: false, notifications: [] }

    socket.setTimeout(300000)
    socket.on("timeout", () => {
      console.error(`[${username}] Socket timeout`)
      socket.destroy()
      activeConnections.delete(username)
      reject(new Error("Connection timeout"))
    })

    socket.on("connect", () => {
      console.log(`[${username}] Conectado al servidor Java en ${TCP_HOST}:${TCP_PORT}`)
      const loginCmd = `type:login|username:${username}`
      console.log(`[${username}] Enviando comando de login: ${loginCmd}`)
      socket.write(loginCmd + "\n")
    })
    
    socket.connect(TCP_PORT, TCP_HOST)

    let loginResponseReceived = false

    socket.on("data", (data) => {
      const message = data.toString().trim()
      console.log(`[${username}] Recibido de Java: ${message}`)
      appendLog(`[${username}] Recibido de Java:`, message)

      if (message.includes("type:login_success")) {
        loginResponseReceived = true
        userSession.connected = true
        activeConnections.set(username, userSession)
        console.log(`[${username}] Login exitoso, conexión persistente establecida`)
        resolve(userSession)
      } else if (message.includes("type:login_error")) {
        console.error(`[${username}] Error de login: ${message}`)
        socket.end()
        reject(new Error(message))
      } else {
        // Respuesta a un comando HTTP o notificación
        if (userSession.responseQueue.length > 0) {
          const { resolve: httpResolve } = userSession.responseQueue.shift()
          httpResolve(message)
        } else {
          // Notificación del servidor sin comando HTTP asociado
          // Guardar en cola de notificaciones para que el web client las reciba después
          userSession.notifications.push(message)
          console.log(`[${username}] Notificación guardada: ${message}`)
        }
      }
    })

    socket.on("error", (err) => {
      console.error(`[${username}] Error de socket: ${err.message}`)
      appendLog(`[${username}] Error de socket:`, err.message)
      activeConnections.delete(username)
      if (!loginResponseReceived) {
        reject(err)
      }
      userSession.responseQueue.forEach(({ reject: httpReject }) => httpReject(err))
      userSession.responseQueue = []
    })

    socket.on("close", () => {
      console.log(`[${username}] Conexión cerrada`)
      activeConnections.delete(username)
    })
  })
}

function sendCommand(username, command) {
  return new Promise((resolve, reject) => {
    const session = activeConnections.get(username)
    if (!session || !session.connected) {
      reject(new Error("Usuario no conectado. Debe hacer login primero."))
      return
    }

    const timeout = setTimeout(() => {
      reject(new Error("Timeout esperando respuesta del servidor"))
    }, 30000)

    session.responseQueue.push({
      resolve: (msg) => {
        clearTimeout(timeout)
        resolve(msg)
      },
      reject: (err) => {
        clearTimeout(timeout)
        reject(err)
      },
    })

    try {
      session.socket.write(command + "\n")
    } catch (err) {
      session.responseQueue.pop()
      reject(err)
    }
  })
}

// === ENDPOINTS ===

app.get("/api/test", (req, res) => {
  console.log("GET /api/test - Test endpoint")
  res.json({ message: "Servidor proxy funcionando correctamente", timestamp: new Date().toISOString() })
})

app.post("/api/login", async (req, res) => {
  const { username } = req.body
  console.log(`POST /api/login - username: ${username}`)

  if (!username || username.trim() === "") {
    return res.status(400).json({ ok: false, error: "Falta username válido" })
  }

  try {
    const session = await connectUser(username.trim())
    console.log(`Login successful for ${username}`)
    res.json({ ok: true, message: "Login exitoso", username: username })
  } catch (err) {
    console.error(`Login error for ${username}:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.post("/api/sendMessage", async (req, res) => {
  const { from, to, content } = req.body
  console.log(`POST /api/sendMessage - from: ${from}, to: ${to}`)

  if (!from || !to || !content) {
    return res.status(400).json({ ok: false, error: "Faltan parámetros" })
  }

  const escapedContent = String(content).replace(/\n/g, " ").replace(/\|/g, "_")
  const cmd = `type:private_message|from:${from}|to:${to}|content:${escapedContent}`

  try {
    const response = await sendCommand(from, cmd)
    console.log(`Message sent from ${from} to ${to}, response: ${response}`)
    res.json({ ok: true, java_response: response, message: "Mensaje enviado" })
  } catch (err) {
    console.error(`Error sending message:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

// Nuevo endpoint para mensajes de grupo
app.post("/api/sendGroupMessage", async (req, res) => {
  const { from, group_name, content } = req.body
  console.log(`POST /api/sendGroupMessage - from: ${from}, group: ${group_name}`)

  if (!from || !group_name || !content) {
    return res.status(400).json({ ok: false, error: "Faltan parámetros" })
  }

  const escapedContent = String(content).replace(/\n/g, " ").replace(/\|/g, "_")
  const cmd = `type:group_message|from:${from}|group_name:${group_name}|content:${escapedContent}`

  try {
    const response = await sendCommand(from, cmd)
    console.log(`Group message sent from ${from} to ${group_name}, response: ${response}`)
    res.json({ ok: true, java_response: response, message: "Mensaje de grupo enviado" })
  } catch (err) {
    console.error(`Error sending group message:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.post("/api/sendAudio", async (req, res) => {
  const { from, to, group_name, audio_data } = req.body
  console.log(`🎵 POST /api/sendAudio - from: ${from}, to: ${to}, group: ${group_name}, audio size: ${audio_data ? audio_data.length : 0} bytes`)

  if (!from || !audio_data) {
    return res.status(400).json({ ok: false, error: "Faltan parámetros (from, audio_data requeridos)" })
  }

  // Guardar el archivo directamente en el servidor ServidorJava
  const audioId = Math.random().toString(36).substring(7)
  const audioDir = path.join(__dirname, "..", "..", "..", "ServidorJava", "data", "audio")
  
  if (!fs.existsSync(audioDir)) {
    fs.mkdirSync(audioDir, { recursive: true })
  }
  
  const audioFile = path.join(audioDir, `${audioId}.audio`)
  
  try {
    // Guardar el audio Base64 en archivo
    fs.writeFileSync(audioFile, audio_data)
    console.log(`💾 Audio guardado: ${audioFile} (${audio_data.length} bytes)`)
    
    // Determinar si es audio privado o de grupo
    if (group_name) {
      // Audio de grupo
      const cmd = `type:group_audio|from:${from}|group_name:${group_name}|audio_id:${audioId}`
      try {
        const response = await sendCommand(from, cmd)
        console.log(`📤 Audio de grupo enviado desde ${from} al grupo ${group_name}, respuesta: ${response.substring(0, 80)}...`)
        res.json({ ok: true, java_response: response, message: "Audio de grupo enviado", audio_id: audioId })
      } catch (err) {
        console.error(`❌ Error sending group audio:`, err.message)
        res.status(500).json({ ok: false, error: err.message })
      }
    } else if (to) {
      // Audio privado
      const cmd = `type:audio|from:${from}|to:${to}|audio_id:${audioId}`
      try {
        const response = await sendCommand(from, cmd)
        console.log(`📤 Audio privado enviado desde ${from} a ${to}, respuesta: ${response.substring(0, 80)}...`)
        res.json({ ok: true, java_response: response, message: "Audio enviado", audio_id: audioId })
      } catch (err) {
        console.error(`❌ Error sending audio:`, err.message)
        res.status(500).json({ ok: false, error: err.message })
      }
    } else {
      res.status(400).json({ ok: false, error: "Debe especificar 'to' para audio privado o 'group_name' para audio de grupo" })
    }
  } catch (err) {
    console.error(`❌ Error guardando audio: ${err.message}`)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.post("/api/createGroup", async (req, res) => {
  const { group_name, creator, members = [] } = req.body
  console.log(`POST /api/createGroup - group: ${group_name}, creator: ${creator}, members: ${members.join(", ")}`)

  if (!group_name || !creator) {
    return res.status(400).json({ ok: false, error: "Faltan parámetros" })
  }

  const membersStr = members.join(",")
  const cmd = `type:create_group|group_name:${group_name}|creator:${creator}|members:${membersStr}`

  try {
    const response = await sendCommand(creator, cmd)
    console.log(`Group created: ${group_name}, response: ${response}`)
    res.json({ ok: true, java_response: response, message: "Grupo creado" })
  } catch (err) {
    console.error(`Error creating group:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.get("/api/groups/:username", async (req, res) => {
  const { username } = req.params
  console.log(`GET /api/groups/${username}`)

  if (!username) {
    return res.status(400).json({ ok: false, error: "Falta username" })
  }

  const cmd = `type:get_groups|username:${username}`

  try {
    const response = await sendCommand(username, cmd)
    console.log(`Groups for ${username}: ${response}`)
    res.json({ ok: true, java_response: response })
  } catch (err) {
    console.error(`Error getting groups:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.get("/api/history/:target", async (req, res) => {
  const { target } = req.params
  const { username, isGroup } = req.query
  console.log(`GET /api/history/${target} - username: ${username}, isGroup: ${isGroup}`)

  if (!username || !target) {
    return res.status(400).json({ ok: false, error: "Faltan parámetros" })
  }

  const isGroupBool = isGroup === "true"
  const cmd = `type:get_history|target:${target}|username:${username}|isGroup:${isGroupBool}`

  try {
    const response = await sendCommand(username, cmd)
    res.json({ ok: true, java_response: response })
  } catch (err) {
    console.error(`Error getting history:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.post("/api/joinGroup", async (req, res) => {
  const { username, group_name } = req.body
  console.log(`POST /api/joinGroup - user: ${username}, group: ${group_name}`)

  if (!username || !group_name) {
    return res.status(400).json({ ok: false, error: "Faltan parámetros" })
  }

  const cmd = `type:join_group|username:${username}|group_name:${group_name}`

  try {
    const response = await sendCommand(username, cmd)
    res.json({ ok: true, java_response: response })
  } catch (err) {
    console.error(`Error joining group:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

app.get("/api/onlineUsers/:username", async (req, res) => {
  const { username } = req.params
  console.log(`GET /api/onlineUsers/${username}`)

  if (!username) {
    return res.status(400).json({ ok: false, error: "Falta username" })
  }

  const cmd = `type:get_online_users|username:${username}`

  try {
    const response = await sendCommand(username, cmd)
    res.json({ ok: true, java_response: response })
  } catch (err) {
    console.error(`Error getting online users:`, err.message)
    res.status(500).json({ ok: false, error: err.message })
  }
})

// Endpoint para obtener notificaciones pendientes (polling)
app.get("/api/notifications/:username", (req, res) => {
  const { username } = req.params
  console.log(`GET /api/notifications/${username}`)

  if (!username) {
    return res.status(400).json({ ok: false, error: "Falta username" })
  }

  const session = activeConnections.get(username)
  if (!session) {
    return res.status(401).json({ ok: false, error: "Usuario no conectado" })
  }

  // Obtener y limpiar las notificaciones
  let notifications = session.notifications || []
  session.notifications = []

  // Desescapar caracteres especiales en audio
  const processedNotifications = notifications.map((notif) => {
    return notif.replace(/_PIPE_/g, "|").replace(/_NEWLINE_/g, "\n")
  })

  console.log(`[${username}] Enviando ${processedNotifications.length} notificaciones`)
  res.json({ ok: true, notifications: processedNotifications })
})

// Endpoint para descargar audio por ID
app.get("/api/audio/:audioId", (req, res) => {
  const { audioId } = req.params
  console.log(`GET /api/audio/${audioId}`)

  if (!audioId) {
    return res.status(400).json({ ok: false, error: "Audio ID requerido" })
  }

  try {
    const audioFile = path.join(__dirname, "..", "..", "..", "ServidorJava", "data", "audio", `${audioId}.audio`)
    
    // Verificar que el archivo existe
    if (!fs.existsSync(audioFile)) {
      console.error(`❌ Archivo de audio no encontrado: ${audioFile}`)
      return res.status(404).json({ ok: false, error: "Audio no encontrado" })
    }

    // Leer el archivo de audio (Base64)
    const audioBase64 = fs.readFileSync(audioFile, "utf8")
    console.log(`✅ Audio enviado: ${audioId} (${audioBase64.length} bytes)`)
    
    res.json({ ok: true, audio_data: audioBase64 })
  } catch (err) {
    console.error(`❌ Error obteniendo audio: ${err.message}`)
    res.status(500).json({ ok: false, error: err.message })
  }
})

const webClientPath = path.resolve(__dirname, "..", "..", "..", "Web-Client")
console.log("Serving static files from:", webClientPath)
app.use(express.static(webClientPath))

app.get("/", (req, res) => {
  res.sendFile(path.join(webClientPath, "index.html"))
})

const PORT = 3000
app.listen(PORT, () => {
  console.log(`Proxy stateful corriendo en http://localhost:${PORT}`)
  console.log(`Sirviendo Web-Client desde: ${webClientPath}`)
  console.log(`Servidor Java esperado en: ${TCP_HOST}:${TCP_PORT}`)
})
