package ui;

import model.AudioMessage;
import model.Message;
import network.TCPConnection;
import network.TCPConnectionListener;
import service.ChatManager;
import service.ChatServiceImpl;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.Util;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor de chat principal - Versión híbrida con Ice y TCP
 * Maneja conexiones tanto por Ice (RPC) como por TCP (backward compatibility)
 */
public class MainServer implements TCPConnectionListener {

    private final ChatManager chatManager;
    private final Map<String, TCPConnection> userConnections = new ConcurrentHashMap<>();
    private Communicator iceComm = null;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    /**
     * Inicializa el servidor con soporte Ice y TCP
     */
    private MainServer(int tcpPort, int icePort) {
        this.chatManager = new ChatManager();
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║    SERVIDOR DE CHAT - VERSIÓN ICE     ║");
        System.out.println("╚════════════════════════════════════════╝");
        
        // Iniciar servidor Ice en thread separado
        threadPool.execute(() -> initializeIceServer(icePort));
        
        // Iniciar servidor TCP (backward compatibility)
        threadPool.execute(() -> initializeTCPServer(tcpPort));
        
        // Mantener el servidor activo
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("❌ Servidor interrumpido");
        }
    }

    public static void main(String[] args) {
        int tcpPort = 5000;
        int icePort = 5001;
        
        // Parsear argumentos
        for (int i = 0; i < args.length; i++) {
            if ("--tcp-port".equals(args[i]) && i + 1 < args.length) {
                tcpPort = Integer.parseInt(args[++i]);
            } else if ("--ice-port".equals(args[i]) && i + 1 < args.length) {
                icePort = Integer.parseInt(args[++i]);
            }
        }
        
        // Crear instancia
        new MainServer(tcpPort, icePort);
    }

    // =====================================================================
    // INICIALIZACIÓN DE SERVIDORES
    // =====================================================================

    /**
     * Inicializa el servidor Ice
     */
    private void initializeIceServer(int port) {
        try {
            String[] args = new String[]{
                "--Ice.Default.Host=0.0.0.0",
                "--Ice.Default.Port=" + port,
                "--Ice.Warn.Connections=1"
            };
            
            iceComm = Util.initialize(args);
            ObjectAdapter adapter = iceComm.createObjectAdapterWithEndpoints(
                "ChatAdapter",
                "default -p " + port
            );
            
            // Crear instancia del servicio
            ChatServiceImpl chatService = new ChatServiceImpl();
            
            // Registrar objeto Ice como tipo genérico Object
            Identity id = new Identity("ChatService", "chat");
            adapter.add((com.zeroc.Ice.Object)chatService, id);
            
            adapter.activate();
            
            System.out.println("✅ Servidor Ice iniciado en puerto " + port);
            System.out.println("   Identidad: ChatService");
            System.out.println("   Endpoint: tcp -p " + port);
            
        } catch (java.lang.Exception e) {
            System.err.println("❌ Error iniciando servidor Ice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inicializa el servidor TCP (para backward compatibility con proxy antiguo)
     */
    private void initializeTCPServer(int port) {
        System.out.println("✅ Servidor TCP iniciado en puerto " + port);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("💬 Esperando conexiones TCP en puerto " + port);
            while (true) {
                try {
                    new TCPConnection(serverSocket.accept(), this);
                } catch (IOException e) {
                    System.err.println("⚠️ Error al aceptar conexión: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ No se pudo iniciar el servidor TCP en el puerto " + port, e);
        }
    }

    // =====================================================================
    // EVENTOS DE CONEXIÓN TCP
    // =====================================================================

    @Override
    public synchronized void onConnectionReady(TCPConnection connection) {
        System.out.println("🔗 Nueva conexión TCP desde: " + connection.getRemoteAddress());
    }

    @Override
    public synchronized void onDisconnect(TCPConnection connection) {
        String user = findUserByConnection(connection);
        if (user != null) {
            userConnections.remove(user);
            chatManager.logoutUser(user);
            broadcastObject("type:system_message|content:El usuario " + user + " se ha desconectado.");
            System.out.println("👋 Usuario desconectado (TCP): " + user);
        }
    }

    @Override
    public synchronized void onReceiveObject(TCPConnection connection, java.lang.Object object) {
        if (object instanceof String command) {
            processCommand(connection, command.toString().trim());
        } else if (object instanceof AudioMessage audioMessage) {
            handleAudioMessage(audioMessage);
        } else {
            System.out.println("⚠️ Objeto recibido desconocido: " + object);
        }
    }

    @Override
    public synchronized void onException(TCPConnection connection, java.lang.Exception e) {
        System.err.println("💥 Excepción en " + connection.getRemoteAddress() + ": " + e.getMessage());
        onDisconnect(connection);
    }

    // =====================================================================
    // PROCESAMIENTO DE COMANDOS TCP
    // =====================================================================

    private void processCommand(TCPConnection connection, String command) {
        Map<String, String> data = parseCommand(command);
        String type = data.get("type");
        if (type == null) return;

        switch (type) {
            case "login" -> handleLogin(connection, data);
            case "logout" -> onDisconnect(connection);
            case "private_message" -> handlePrivateMessage(connection, data);
            case "audio" -> handleAudioMessage(connection, data);
            case "group_audio" -> handleGroupAudioMessage(connection, data);
            case "group_message" -> handleGroupMessage(data);
            case "create_group" -> handleCreateGroup(connection, data);
            case "join_group" -> handleJoinGroup(connection, data);
            case "get_online_users" -> handleGetOnlineUsers(connection, data);
            case "get_groups" -> handleGetGroups(connection, data);
            case "get_history" -> handleGetHistory(connection, data);
            case "call_start" -> handleCallStart(data);
            case "call_accept" -> handleCallAccept(data);
            case "call_end" -> handleCallEnd(data);
            default -> System.out.println("❓ Comando desconocido (TCP): " + type);
        }
    }

    // =====================================================================
    // MANEJO DE LOGIN / LOGOUT
    // =====================================================================

    private void handleLogin(TCPConnection connection, Map<String, String> data) {
        String username = data.get("username");
        if (username != null && chatManager.loginUser(username)) {
            userConnections.put(username, connection);
            sendObjectToUser(username, "type:login_success|message:Bienvenido " + username);
            broadcastObject("type:system_message|content:" + username + " se ha conectado.");
            System.out.println("✅ Usuario conectado (TCP): " + username);
        } else {
            connection.sendObject("type:login_error|message:Nombre de usuario inválido o en uso.");
            connection.disconnect();
        }
    }

    // =====================================================================
    // MENSAJERÍA PRIVADA
    // =====================================================================

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

    // =====================================================================
    // AUDIO PRIVADO
    // =====================================================================

    private void handleAudioMessage(TCPConnection connection, Map<String, String> data) {
        String from = data.get("from");
        String to = data.get("to");
        String audioId = data.get("audio_id");

        if (from == null || to == null || audioId == null) {
            connection.sendObject("type:error|message:Datos de audio incompletos");
            return;
        }

        try {
            String audioDir = "data/audio";
            String audioFile = audioDir + "/" + audioId + ".audio";
            
            java.io.File file = new java.io.File(audioFile);
            if (!file.exists()) {
                System.err.println("❌ Archivo de audio no encontrado: " + audioFile);
                connection.sendObject("type:error|message:Archivo de audio no encontrado");
                return;
            }
            
            System.out.println("🎵 Audio privado procesado: " + from + " -> " + to);
            
            String notification = String.format("type:audio|from:%s|to:%s|audio_id:%s", from, to, audioId);
            sendObjectToUser(to, notification);
            
            String response = String.format("type:audio|from:%s|to:%s|audio_id:%s|status:sent", from, to, audioId);
            connection.sendObject(response);
            
            System.out.println("✅ Nota de voz enviada (TCP): " + from + " → " + to);
            
        } catch (java.lang.Exception e) {
            System.err.println("❌ Error procesando audio: " + e.getMessage());
            connection.sendObject("type:error|message:Error procesando audio: " + e.getMessage());
        }
    }

    // =====================================================================
    // AUDIO DE GRUPO
    // =====================================================================

    private void handleGroupAudioMessage(TCPConnection connection, Map<String, String> data) {
        String from = data.get("from");
        String groupName = data.get("group_name");
        String audioId = data.get("audio_id");

        if (from == null || groupName == null || audioId == null) {
            connection.sendObject("type:error|message:Datos de audio de grupo incompletos");
            return;
        }

        if (!chatManager.groupExists(groupName)) {
            connection.sendObject("type:error|message:Grupo no existe");
            return;
        }

        try {
            String audioDir = "data/audio";
            String audioFile = audioDir + "/" + audioId + ".audio";
            
            java.io.File file = new java.io.File(audioFile);
            if (!file.exists()) {
                System.err.println("❌ Archivo de audio de grupo no encontrado: " + audioFile);
                connection.sendObject("type:error|message:Archivo de audio no encontrado");
                return;
            }
            
            java.util.List<String> members = chatManager.getGroupMembers(groupName);
            
            String notification = String.format("type:group_audio|from:%s|group:%s|audio_id:%s", from, groupName, audioId);
            for (String member : members) {
                if (!member.equals(from)) {
                    sendObjectToUser(member, notification);
                }
            }
            
            String membersList = String.join(",", members);
            String response = String.format("type:group_audio|from:%s|group:%s|audio_id:%s|members:%s|status:sent", 
                    from, groupName, audioId, membersList);
            connection.sendObject(response);
            
            System.out.println("✅ Audio de grupo enviado (TCP): " + from + " → " + groupName);
            
        } catch (java.lang.Exception e) {
            System.err.println("❌ Error procesando audio de grupo: " + e.getMessage());
            connection.sendObject("type:error|message:Error procesando audio de grupo");
        }
    }

    // =====================================================================
    // GESTIÓN DE GRUPOS
    // =====================================================================

    private void handleCreateGroup(TCPConnection connection, Map<String, String> data) {
        String groupName = data.get("group_name");
        String creator = data.get("creator");
        String membersStr = data.get("members");

        if (groupName == null || creator == null) return;

        if (chatManager.createGroup(groupName, creator)) {
            java.util.List<String> allMembers = new java.util.ArrayList<>();
            allMembers.add(creator);

            if (membersStr != null && !membersStr.isEmpty()) {
                String[] members = membersStr.split(",");
                for (String member : members) {
                    member = member.trim();
                    if (!member.isEmpty() && !member.equals(creator)) {
                        chatManager.joinGroup(groupName, member);
                        allMembers.add(member);
                    }
                }
            }

            String membersList = String.join(",", allMembers);
            String notificationMsg = String.format(
                "type:group_created|group_name:%s|creator:%s|members:%s",
                groupName, creator, membersList
            );
            
            for (String member : allMembers) {
                sendObjectToUser(member, notificationMsg);
            }
            
            System.out.println("✅ Grupo creado (TCP): " + groupName);
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

    // =====================================================================
    // LLAMADAS
    // =====================================================================

    private void handleCallStart(Map<String, String> data) {
        String from = data.get("from");
        String to = data.get("to");
        String isGroupStr = data.get("isGroup");
        String udpPortStr = data.get("udpPort");

        if (from == null || to == null || udpPortStr == null) return;

        boolean isGroup = "true".equalsIgnoreCase(isGroupStr);
        int callerUdpPort = java.lang.Integer.parseInt(udpPortStr);

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

        int receiverUdpPort = java.lang.Integer.parseInt(udpPortStr);
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

    private void handleAudioMessage(AudioMessage audioMessage) {
        if (audioMessage == null) return;
        String from = audioMessage.getFrom();
        String to = audioMessage.getTo();
        
        chatManager.saveAudioMessage(audioMessage);
        sendObjectToUser(to, audioMessage);
        System.out.println("🎵 Mensaje de audio (TCP): " + from + " → " + to);
    }

    // =====================================================================
    // MENSAJES DE GRUPO
    // =====================================================================

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

    // =====================================================================
    // UTILIDADES
    // =====================================================================

    private void sendObjectToUser(String username, java.lang.Object object) {
        TCPConnection conn = userConnections.get(username);
        if (conn != null && conn.isConnected()) {
            conn.sendObject((Serializable) object);
        }
    }

    private void broadcastObject(java.lang.Object object) {
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
