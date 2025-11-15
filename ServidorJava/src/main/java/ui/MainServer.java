package ui;

import model.AudioMessage;
import model.Message;
import network.TCPConnection;
import network.TCPConnectionListener;
import service.ChatManager;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor de chat principal.
 * Maneja conexiones TCP, autenticación, mensajes privados y grupales,
 * así como gestión de llamadas (simuladas) y audio.
 */
public class MainServer implements TCPConnectionListener {

    private final ChatManager chatManager;
    private final Map<String, TCPConnection> userConnections = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        new MainServer(port);
    }

    /**
     * Inicializa el servidor y escucha conexiones entrantes.
     */
    private MainServer(int port) {
        this.chatManager = new ChatManager();
        System.out.println("💬 SERVIDOR DE CHAT INICIADO EN PUERTO " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    new TCPConnection(serverSocket.accept(), this);
                } catch (IOException e) {
                    System.err.println("⚠️ Error al aceptar conexión: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ No se pudo iniciar el servidor en el puerto " + port, e);
        }
    }

    // =====================================================
    // 🔌 Eventos de conexión
    // =====================================================

    @Override
    public synchronized void onConnectionReady(TCPConnection connection) {
        System.out.println("🔗 Nueva conexión desde: " + connection.getRemoteAddress());
    }

    @Override
    public synchronized void onDisconnect(TCPConnection connection) {
        String user = findUserByConnection(connection);
        if (user != null) {
            userConnections.remove(user);
            chatManager.logoutUser(user);
            broadcastObject("type:system_message|content:El usuario " + user + " se ha desconectado.");
            System.out.println("👋 Usuario desconectado: " + user);
        }
    }

    @Override
    public synchronized void onReceiveObject(TCPConnection connection, Object object) {
        if (object instanceof String command) {
            processCommand(connection, command.trim());
        } else if (object instanceof AudioMessage audioMessage) {
            handleAudioMessage(audioMessage);
        } else {
            System.out.println("⚠️ Objeto recibido desconocido: " + object);
        }
    }

    @Override
    public synchronized void onException(TCPConnection connection, Exception e) {
        System.err.println("💥 Excepción en " + connection.getRemoteAddress() + ": " + e.getMessage());
        onDisconnect(connection);
    }

    // =====================================================
    // 🧩 Procesamiento de comandos
    // =====================================================

    private void processCommand(TCPConnection connection, String command) {
        Map<String, String> data = parseCommand(command);
        String type = data.get("type");
        if (type == null) return;

        switch (type) {
            case "login" -> handleLogin(connection, data);
            case "logout" -> onDisconnect(connection);
            case "private_message" -> handlePrivateMessage(connection, data);
            case "group_message" -> handleGroupMessage(data);
            case "create_group" -> handleCreateGroup(connection, data);
            case "join_group" -> handleJoinGroup(connection, data);
            case "get_online_users" -> handleGetOnlineUsers(connection, data);
            case "get_groups" -> handleGetGroups(connection, data);
            case "get_history" -> handleGetHistory(connection, data);
            case "call_start" -> handleCallStart(data);
            case "call_accept" -> handleCallAccept(data);
            case "call_end" -> handleCallEnd(data);
            default -> System.out.println("❓ Comando desconocido: " + type);
        }
    }

    // =====================================================
    // 👤 Manejo de login / logout
    // =====================================================

    private void handleLogin(TCPConnection connection, Map<String, String> data) {
        String username = data.get("username");
        if (username != null && chatManager.loginUser(username)) {
            userConnections.put(username, connection);
            sendObjectToUser(username, "type:login_success|message:Bienvenido " + username);
            broadcastObject("type:system_message|content:" + username + " se ha conectado.");
            System.out.println("✅ Usuario conectado: " + username);
        } else {
            connection.sendObject("type:login_error|message:Nombre de usuario inválido o en uso.");
            connection.disconnect();
        }
    }

    // =====================================================
    // 💬 Mensajería
    // =====================================================

    private void handlePrivateMessage(TCPConnection connection, Map<String, String> data) {
        String from = data.get("from");
        String to = data.get("to");
        String content = data.get("content");

        if (from == null || to == null || content == null) return;

        chatManager.saveTextMessage(new Message(from, to, content, false));

        String msg = String.format("type:private_message|from:%s|to:%s|content:%s", from, to, content);
        sendObjectToUser(to, msg);
        sendObjectToUser(from, "type:message_sent|to:" + to + "|status:ok|content:" + content);
    }

    // =====================================================
    // 👥 Grupos
    // =====================================================

    private void handleCreateGroup(TCPConnection connection, Map<String, String> data) {
        String groupName = data.get("group_name");
        String creator = data.get("creator");

        if (groupName != null && creator != null && chatManager.createGroup(groupName, creator)) {
            sendObjectToUser(creator, "type:group_created|group_name:" + groupName + "|status:ok");
            broadcastObject("type:system_message|content:Grupo '" + groupName + "' creado por " + creator);
        } else {
            sendObjectToUser(creator, "type:error|message:No se pudo crear el grupo '" + groupName + "'.");
        }
    }

    private void handleJoinGroup(TCPConnection connection, Map<String, String> data) {
        String groupName = data.get("group_name");
        String username = data.get("username");

        if (groupName != null && username != null && chatManager.joinGroup(groupName, username)) {
            sendObjectToUser(username, "type:join_group_success|group:" + groupName + "|status:ok");
            broadcastObject("type:system_message|content:" + username + " se unió al grupo " + groupName);
        } else {
            sendObjectToUser(username, "type:error|message:No se pudo unir al grupo '" + groupName + "'.");
        }
    }

    private void handleGetOnlineUsers(TCPConnection connection, Map<String, String> data) {
        String user = data.get("username");
        if (user != null) {
            String list = String.join(",", chatManager.getOnlineUsers());
            sendObjectToUser(user, "type:online_users|users:" + list);
        }
    }

    private void handleGetGroups(TCPConnection connection, Map<String, String> data) {
        String user = data.get("username");
        if (user != null) {
            String list = String.join(",", chatManager.getAllGroups());
            sendObjectToUser(user, "type:groups_list|groups:" + list);
        }
    }

    private void handleGetHistory(TCPConnection connection, Map<String, String> data) {
        String target = data.get("target");
        String username = data.get("username");
        String isGroupStr = data.get("isGroup");
        
        if (target == null || username == null) return;
        
        boolean isGroup = "true".equalsIgnoreCase(isGroupStr);
        var messages = chatManager.getMessageHistory(target, isGroup);
        
        StringBuilder historyStr = new StringBuilder();
        for (Message msg : messages) {
            if (historyStr.length() > 0) historyStr.append("|");
            historyStr.append(msg.getFrom()).append(":").append(msg.getContent());
        }
        
        sendObjectToUser(username, "type:history|target:" + target + "|messages:" + historyStr.toString());
    }

    // =====================================================
    // 📞 Llamadas simuladas
    // =====================================================

    private void handleCallStart(Map<String, String> data) {
        String from = data.get("from");
        String to = data.get("to");
        String isGroupStr = data.get("isGroup");
        String udpPortStr = data.get("udpPort");

        if (from == null || to == null || udpPortStr == null) return;

        boolean isGroup = "true".equalsIgnoreCase(isGroupStr);
        int callerUdpPort = Integer.parseInt(udpPortStr);

        TCPConnection callerConn = userConnections.get(from);
        if (callerConn == null) return;
        String callerIp = callerConn.getSocket().getInetAddress().getHostAddress();

        System.out.println("📞 Llamada de " + from + " a " + to + " | UDP: " + callerIp + ":" + callerUdpPort);

        String incomingCallMsg = String.format(
                "type:incoming_call|from:%s|to:%s|isGroup:%s|callerIp:%s|callerUdpPort:%d",
                from, to, isGroup, callerIp, callerUdpPort
        );

        if (isGroup && chatManager.groupExists(to)) {
            for (String member : chatManager.getGroupMembers(to)) {
                if (!member.equals(from)) sendObjectToUser(member, incomingCallMsg);
            }
        } else {
            sendObjectToUser(to, incomingCallMsg);
        }

        sendObjectToUser(from, "type:call_waiting|to:" + to);
    }

    private void handleCallAccept(Map<String, String> data) {
        String from = data.get("from");
        String to = data.get("to");
        String udpPortStr = data.get("udpPort");

        if (from == null || to == null || udpPortStr == null) return;

        int receiverUdpPort = Integer.parseInt(udpPortStr);
        TCPConnection receiverConn = userConnections.get(from);
        if (receiverConn == null) return;

        String receiverIp = receiverConn.getSocket().getInetAddress().getHostAddress();

        System.out.println("📲 Llamada aceptada: " + from + " ↔ " + to);

        sendObjectToUser(to, String.format(
                "type:call_accepted|from:%s|receiverIp:%s|receiverUdpPort:%d",
                from, receiverIp, receiverUdpPort
        ));
    }

    private void handleCallEnd(Map<String, String> data) {
        String from = data.get("from");
        String callId = data.get("callId");
        System.out.println("🛑 Llamada finalizada por " + from + " (ID: " + callId + ")");
    }

    // =====================================================
    // 🎵 Manejo de audio
    // =====================================================

    private void handleAudioMessage(AudioMessage audioMessage) {
        if (audioMessage == null) return;
        String from = audioMessage.getFrom();
        String to = audioMessage.getTo();
        
        chatManager.saveAudioMessage(audioMessage);
        sendObjectToUser(to, audioMessage);
        System.out.println("🎵 Mensaje de audio de " + from + " a " + to);
    }

    // =====================================================
    // 👥 Mensajes de grupo
    // =====================================================

    private void handleGroupMessage(Map<String, String> data) {
        String from = data.get("from");
        String groupName = data.get("group_name");
        String content = data.get("content");

        if (from == null || groupName == null || content == null) return;

        if (chatManager.groupExists(groupName)) {
            chatManager.saveTextMessage(new Message(from, groupName, content, true));
            
            String msg = String.format("type:group_message|from:%s|group:%s|content:%s", from, groupName, content);
            for (String member : chatManager.getGroupMembers(groupName)) {
                if (!member.equals(from)) {
                    sendObjectToUser(member, msg);
                }
            }
            sendObjectToUser(from, "type:message_sent|group:" + groupName + "|status:ok|content:" + content);
        }
    }

    // =====================================================
    // 🧱 Utilidades
    // =====================================================

    private void sendObjectToUser(String username, Object object) {
        TCPConnection conn = userConnections.get(username);
        if (conn != null && conn.isConnected()) {
            conn.sendObject((Serializable) object);
        }
    }

    private void broadcastObject(Object object) {
        for (TCPConnection conn : userConnections.values()) {
            if (conn.isConnected()) {
                conn.sendObject((Serializable) object);
            }
        }
    }

    private String findUserByConnection(TCPConnection connection) {
        for (Map.Entry<String, TCPConnection> entry : userConnections.entrySet()) {
            if (entry.getValue().equals(connection)) return entry.getKey();
        }
        return null;
    }

    private Map<String, String> parseCommand(String command) {
        Map<String, String> result = new HashMap<>();
        for (String part : command.split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) result.put(kv[0], kv[1]);
        }
        return result;
    }
}
