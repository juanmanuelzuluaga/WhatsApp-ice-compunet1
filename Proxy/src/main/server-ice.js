/**
 * Proxy Ice mejorado para sistema de chat
 * Usa ZeroC Ice para comunicación RPC con el servidor Java
 * Mantiene HTTP REST y agrega WebSocket para tiempo real
 */

import express from "express";
import bodyParser from "body-parser";
import cors from "cors";
import path from "path";
import { fileURLToPath } from "url";
import fs from "fs";
import { WebSocketServer } from "ws";
import IceClient from "./ice-client.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log("╔════════════════════════════════════════╗");
console.log("║ PROXY ICE - SERVIDOR DE CHAT ║");
console.log("╚════════════════════════════════════════╝");
console.log("PID:", process.pid);
console.log("Timestamp:", new Date().toISOString());

const LOG_PATH = path.resolve(__dirname, "..", "..", "proxy.log");
function appendLog(...args) {
    try {
        const line = args.map((a) => (typeof a === "string" ? a : JSON.stringify(a))).join(" ") + "\n";
        fs.appendFileSync(LOG_PATH, new Date().toISOString() + " " + line);
    } catch (e) {}
}

process.on("uncaughtException", (err) => {
 console.error("Uncaught exception:", err);
    appendLog("Uncaught exception:", err && err.stack ? err.stack : String(err));
});

process.on("unhandledRejection", (reason) => {
 console.error("Unhandled rejection:", reason);
    appendLog("Unhandled rejection:", reason && reason.stack ? reason.stack : String(reason));
});

const app = express();

// Configurar CORS
app.use(
    cors({
        origin: (origin, callback) => {
            if (!origin || origin.includes("localhost") || origin.includes("127.0.0.1")) {
                callback(null, true);
            } else {
                callback(new Error("Not allowed by CORS"));
            }
        },
        methods: ["GET", "POST", "OPTIONS"],
        allowedHeaders: ["Content-Type", "Accept"],
        credentials: true,
        optionsSuccessStatus: 200,
    }),
);

app.use(bodyParser.json({ limit: "50mb" }));
app.use(bodyParser.urlencoded({ limit: "50mb", extended: true }));

app.use((req, res, next) => {
 console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
    appendLog(`${req.method} ${req.path}`, req.body);
    next();
});

// =====================================================================
// CLIENTE ICE Y CONEXIONES WEBSOCKET
// =====================================================================

const ICE_HOST = "localhost";
const ICE_PORT = 5001;
const HTTP_PORT = 3000;
const WS_PORT = 8080;

let iceClient = null;
const connectedUsers = new Map(); // username -> WebSocket
const userSessions = new Map(); // username -> session data

/**
 * Inicializa el cliente Ice
 */
async function initializeIceClient() {
    try {
        iceClient = new IceClient(ICE_HOST, ICE_PORT);
        await iceClient.initialize();
 console.log(" Cliente Ice conectado al servidor");
        return true;
    } catch (err) {
 console.error(" Error al conectar con Ice:", err.message);
        appendLog("Error conectando Ice:", err.message);
        // Reintentar en 5 segundos
        setTimeout(initializeIceClient, 5000);
        return false;
    }
}

/**
 * Limpia la conexión Ice
 */
function cleanupIceClient() {
    if (iceClient) {
        iceClient.destroy();
        iceClient = null;
    }
}

// =====================================================================
// ENDPOINTS HTTP
// =====================================================================

app.get("/api/test", (req, res) => {
 console.log("GET /api/test");
    res.json({
        message: "Proxy Ice funcionando correctamente",
        timestamp: new Date().toISOString(),
        ice_connected: iceClient !== null && iceClient.initialized,
    });
});

app.post("/api/login", async (req, res) => {
    const { username } = req.body;
 console.log(`POST /api/login - username: ${username}`);

    if (!username || username.trim() === "") {
        return res.status(400).json({ ok: false, error: "Falta username válido" });
    }

    try {
        if (!iceClient || !iceClient.initialized) {
            return res.status(503).json({ ok: false, error: "Servidor no disponible, reconectando..." });
        }

        const success = await iceClient.login(username.trim());
        userSessions.set(username.trim(), {
            loginTime: Date.now(),
            username: username.trim(),
        });

        res.json({ ok: true, message: "Login exitoso", username: username.trim() });
    } catch (err) {
 console.error(`Login error for ${username}:`, err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.post("/api/sendMessage", async (req, res) => {
    const { from, to, content } = req.body;
 console.log(`POST /api/sendMessage - from: ${from}, to: ${to}`);

    if (!from || !to || !content) {
        return res.status(400).json({ ok: false, error: "Faltan parámetros" });
    }

    try {
        const success = await iceClient.sendMessage(from, to, content);
        
        // Notificar al receptor vía WebSocket si está conectado
        const receiverWs = connectedUsers.get(to);
        if (receiverWs && receiverWs.readyState === 1) {
            receiverWs.send(JSON.stringify({
                type: "private_message",
                from: from,
                to: to,
                content: content,
                timestamp: Date.now(),
            }));
        }

        res.json({ ok: true, message: "Mensaje enviado" });
    } catch (err) {
 console.error("Error sending message:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.post("/api/sendGroupMessage", async (req, res) => {
    const { from, group_name, content } = req.body;
 console.log(`POST /api/sendGroupMessage - from: ${from}, group: ${group_name}`);

    if (!from || !group_name || !content) {
        return res.status(400).json({ ok: false, error: "Faltan parámetros" });
    }

    try {
        const success = await iceClient.sendGroupMessage(from, group_name, content);
        
        // Obtener miembros del grupo
        const members = await iceClient.getGroupMembers(group_name);
        
        // Notificar a todos los miembros vía WebSocket
        for (const member of members) {
            if (member !== from) {
                const memberWs = connectedUsers.get(member);
                if (memberWs && memberWs.readyState === 1) {
                    memberWs.send(JSON.stringify({
                        type: "group_message",
                        from: from,
                        group: group_name,
                        content: content,
                        timestamp: Date.now(),
                    }));
                }
            }
        }

        res.json({ ok: true, message: "Mensaje de grupo enviado" });
    } catch (err) {
 console.error("Error sending group message:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.post("/api/createGroup", async (req, res) => {
    const { group_name, creator, members = [] } = req.body;
    const membersList = Array.isArray(members) ? members : [];
 console.log(`POST /api/createGroup - group: ${group_name}, creator: ${creator}, members: ${membersList.join(",")}`);

    if (!group_name || !creator) {
        return res.status(400).json({ ok: false, error: "Faltan parámetros" });
    }

    try {
        await iceClient.createGroup(group_name, creator, membersList);
        
        // Obtener la lista completa de miembros del grupo desde el servidor
        const groupMembers = await iceClient.getGroupMembers(group_name);
 console.log(`📋 Miembros del grupo ${group_name}: ${groupMembers.join(", ")}`);
        
        res.json({ ok: true, message: "Grupo creado", members: groupMembers });
        
        // Notificar a todos los miembros del nuevo grupo vía WebSocket
 console.log(`📢 Enviando notificación de grupo_created a ${groupMembers.length} miembros: ${groupMembers.join(",")}`);
        for (const member of groupMembers) {
            const memberWs = connectedUsers.get(member);
            if (memberWs && memberWs.readyState === 1) {
 console.log(` Enviando a ${member}`);
                memberWs.send(JSON.stringify({
                    type: "group_created",
                    group_name: group_name,
                    creator: creator,
                    members: groupMembers,
                    timestamp: Date.now(),
                }));
            } else {
 console.log(` ${member} no está conectado (ws=${memberWs ? 'exist' : 'null'}, state=${memberWs?.readyState})`);
            }
        }
    } catch (err) {
 console.error("Error creating group:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.post("/api/joinGroup", async (req, res) => {
    const { username, group_name } = req.body;
 console.log(`POST /api/joinGroup - user: ${username}, group: ${group_name}`);

    if (!username || !group_name) {
        return res.status(400).json({ ok: false, error: "Faltan parámetros" });
    }

    try {
        const success = await iceClient.joinGroup(username, group_name);
        res.json({ ok: true, message: "Usuario agregado al grupo" });
        
        // Notificar al usuario vía WebSocket que fue agregado a un grupo
 console.log(`📢 Enviando notificación group_joined a ${username}`);
        const userWs = connectedUsers.get(username);
        if (userWs && userWs.readyState === 1) {
 console.log(` Conectado, enviando`);
            userWs.send(JSON.stringify({
                type: "group_joined",
                group_name: group_name,
                timestamp: Date.now(),
            }));
        } else {
 console.log(` No está conectado (ws=${userWs ? 'exist' : 'null'}, state=${userWs?.readyState})`);
        }
    } catch (err) {
 console.error("Error joining group:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.post("/api/sendAudio", async (req, res) => {
    const { from, to, group_name, audio_data } = req.body;
 console.log(`POST /api/sendAudio - from: ${from}, to: ${to}, group: ${group_name}, audio_data length: ${audio_data ? audio_data.length : 0}`);

    if (!from || !audio_data) {
 console.error(` Faltan parámetros: from=${from}, audio_data=${audio_data ? 'present' : 'missing'}`);
        return res.status(400).json({ ok: false, error: "Faltan parámetros (from, audio_data requeridos)" });
    }

    try {
        // Generar ID único para el audio
        const audioId = "audio_" + Date.now() + "_" + Math.random().toString(36).substring(7);
        
        // Guardar archivo de audio
        const audioDir = path.join(__dirname, "..", "..", "ServidorJava", "data", "audio");
        if (!fs.existsSync(audioDir)) {
            fs.mkdirSync(audioDir, { recursive: true });
        }

        // Decodificar Base64 si es necesario
        let audioBuffer;
        if (typeof audio_data === "string") {
            // Si es Base64, decodificar
            audioBuffer = Buffer.from(audio_data.replace(/^data:audio\/[^;]+;base64,/, ""), "base64");
        } else {
            audioBuffer = audio_data;
        }

        const audioFile = path.join(audioDir, `${audioId}.audio`);
        fs.writeFileSync(audioFile, audioBuffer);
        
 console.log(`💾 Audio guardado: ${audioFile} (${audioBuffer.length} bytes)`);

        // ✅ Determinar si es grupo o privado y registrar en Ice
        const isGroup = !!group_name;
        const duration = 0; // Por ahora no calculamos la duración
        const size = audioBuffer.length;

        if (isGroup) {
            // ✅ Enviar audio de grupo vía Ice
            await iceClient.sendGroupAudio(from, group_name, audioId, size, duration);
 console.log(`✅ Audio de grupo registrado en Ice: ${from} -> ${group_name}`);
            
            // ✅ Notificar a todos los miembros del grupo vía WebSocket
            const members = await iceClient.getGroupMembers(group_name);
            for (const member of members) {
                if (member !== from) {
                    const memberWs = connectedUsers.get(member);
                    if (memberWs && memberWs.readyState === 1) {
                        memberWs.send(JSON.stringify({
                            type: "group_audio",
                            from: from,
                            group: group_name,
                            audio_id: audioId,
                            timestamp: Date.now(),
                        }));
 console.log(` Notificado audio de grupo a ${member}`);
                    }
                }
            }
        } else {
            // ✅ Enviar audio privado vía Ice
            // IMPORTANTE: Ice no acepta null, usar el valor de 'to' que ya debe estar definido
            const targetUser = to || "";
            if (!targetUser) {
                throw new Error("Destinatario no especificado para audio privado");
            }
            await iceClient.sendAudio(from, targetUser, audioId, size, duration);
 console.log(`✅ Audio privado registrado en Ice: ${from} -> ${targetUser}`);
            
            // ✅ Notificar al receptor vía WebSocket
            const receiverWs = connectedUsers.get(targetUser);
            if (receiverWs && receiverWs.readyState === 1) {
                receiverWs.send(JSON.stringify({
                    type: "audio",
                    from: from,
                    to: targetUser,
                    audio_id: audioId,
                    timestamp: Date.now(),
                }));
 console.log(` Notificado audio privado a ${targetUser}`);
            }
        }

        res.json({ ok: true, message: "Audio enviado", audio_id: audioId });
    } catch (err) {
 console.error("Error sending audio:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.get("/api/history/:target", async (req, res) => {
    const { target } = req.params;
    const { username, isGroup } = req.query;

    if (!username || !target) {
        return res.status(400).json({ ok: false, error: "Faltan parámetros" });
    }

    try {
        const isGroupBool = isGroup === "true";
        let messages = [];
        
        if (isGroupBool) {
            messages = await iceClient.getGroupHistory(target);
        } else {
            messages = await iceClient.getPrivateHistory(username, target);
        }

        res.json({ ok: true, messages: messages });
    } catch (err) {
 console.error("Error getting history:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.get("/api/groups/:username", async (req, res) => {
    const { username } = req.params;

    if (!username) {
        return res.status(400).json({ ok: false, error: "Falta username" });
    }

    try {
        const groups = await iceClient.getUserGroups(username);
        res.json({ ok: true, groups: groups });
    } catch (err) {
 console.error("Error getting groups:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

app.get("/api/onlineUsers", async (req, res) => {
    try {
        // Ignorar el parámetro username si viene en query o en la URL
        const users = await iceClient.getOnlineUsers();
        res.json({ ok: true, users: users });
    } catch (err) {
 console.error("Error getting online users:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

// Ruta con parámetro para compatibilidad con cliente anterior
app.get("/api/onlineUsers/:username", async (req, res) => {
    try {
        const users = await iceClient.getOnlineUsers();
        res.json({ ok: true, users: users });
    } catch (err) {
 console.error("Error getting online users:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

// Endpoint de notificaciones (polling fallback - WebSocket es preferido)
app.get("/api/notifications/:username", (req, res) => {
    const { username } = req.params;
    // Con WebSocket, este endpoint retorna una lista vacía
    // Ya que todas las notificaciones se envían vía WebSocket
    res.json({ ok: true, notifications: [] });
});

app.get("/api/audio/:audioId", (req, res) => {
    const { audioId } = req.params;

    try {
        const audioFile = path.join(__dirname, "..", "..", "ServidorJava", "data", "audio", `${audioId}.audio`);
        
        if (!fs.existsSync(audioFile)) {
            return res.status(404).json({ ok: false, error: "Audio no encontrado" });
        }

        // ✅ Leer el archivo como buffer binario y convertir a Base64
        const audioBuffer = fs.readFileSync(audioFile);
        const audioBase64 = audioBuffer.toString('base64');
        
 console.log(`📥 Audio ${audioId} enviado (${audioBase64.length} bytes en Base64)`);
        res.json({ ok: true, audio_data: audioBase64 });
    } catch (err) {
 console.error("Error getting audio:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

// Servir archivos estáticos
const webClientPath = path.resolve(__dirname, "..", "..", "..", "Web-Client");
console.log(" Sirviendo archivos desde:", webClientPath);
app.use(express.static(webClientPath));

app.get("/", (req, res) => {
    res.sendFile(path.join(webClientPath, "index.html"));
});

// =====================================================================
// WEBSOCKET PARA TIEMPO REAL
// =====================================================================

const wss = new WebSocketServer({ port: WS_PORT, host: "0.0.0.0" });

console.log(`📡 Iniciando WebSocket Server en puerto ${WS_PORT}...`);

wss.on("listening", () => {
 console.log(` WebSocket Server está escuchando en puerto ${WS_PORT}`);
});

wss.on("error", (err) => {
 console.error(` Error en WebSocket Server: ${err.message}`);
});

wss.on("connection", (ws, req) => {
 console.log(" Cliente WebSocket conectado");

    let username = null;

    ws.on("message", (message) => {
        try {
            const data = JSON.parse(message);

            if (data.type === "auth") {
                username = data.username;
                connectedUsers.set(username, ws);
 console.log(` Usuario autenticado en WebSocket: ${username}`);
                
                ws.send(JSON.stringify({
                    type: "auth_response",
                    ok: true,
                    username: username,
                }));
            }
        } catch (err) {
 console.error("Error procesando WebSocket:", err.message);
        }
    });

    ws.on("close", () => {
        if (username) {
            connectedUsers.delete(username);
 console.log(` Usuario desconectado de WebSocket: ${username}`);
        }
    });

    ws.on("error", (err) => {
 console.error("Error en WebSocket:", err.message);
    });
});

// =====================================================================
// INICIO DEL SERVIDOR
// =====================================================================

app.listen(HTTP_PORT, () => {
 console.log(`\n Proxy HTTP escuchando en puerto ${HTTP_PORT}`);
 console.log(`📡 WebSocket escuchando en puerto ${WS_PORT}`);
 console.log(` Conectando con servidor Ice: ${ICE_HOST}:${ICE_PORT}\n`);
});

// Inicializar cliente Ice
initializeIceClient();

// Manejar shutdown
process.on("SIGINT", () => {
 console.log("\n👋 Cerrando proxy...");
    cleanupIceClient();
    process.exit(0);
});
