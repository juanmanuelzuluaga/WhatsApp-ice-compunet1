package service;

import chat.*;
import model.User;
import model.Group;
import model.Message;
import model.AudioMessage;
import com.zeroc.Ice.Current;

import java.util.*;

/**
 * Implementación del servicio de chat usando ZeroC Ice
 * Esta clase implementa la interfaz generada por slice2java
 */
public class ChatServiceImpl implements ChatService {
    
    private final ChatManager chatManager;
    
    public ChatServiceImpl() {
        this.chatManager = new ChatManager();
    }
    
    // =====================================================================
    // AUTENTICACIÓN
    // =====================================================================
    
    @Override
    public synchronized boolean login(String username, Current current) throws UserException {
        if (username == null || username.trim().isEmpty()) {
            throw new UserException("El nombre de usuario no puede estar vacío");
        }
        
        boolean success = chatManager.loginUser(username.trim());
        if (!success) {
            throw new UserException("El usuario ya está conectado o hay un error en el login");
        }
        
        System.out.println("✅ Usuario logueado vía Ice: " + username);
        return true;
    }
    
    @Override
    public synchronized boolean logout(String username, Current current) throws UserException {
        if (username == null || username.trim().isEmpty()) {
            throw new UserException("El nombre de usuario no puede estar vacío");
        }
        
        chatManager.logoutUser(username.trim());
        System.out.println("👋 Usuario deslogueado vía Ice: " + username);
        return true;
    }
    
    // =====================================================================
    // MENSAJES PRIVADOS
    // =====================================================================
    
    @Override
    public synchronized boolean sendMessage(String from, String to, String content, Current current) throws UserException {
        if (from == null || to == null || content == null) {
            throw new UserException("Faltan parámetros requeridos (from, to, content)");
        }
        
        if (!chatManager.isUserOnline(from)) {
            throw new UserException("El usuario remitente no está conectado");
        }
        
        try {
            Message message = new Message(from, to, content, false);
            chatManager.saveTextMessage(message);
            System.out.println("💬 Mensaje privado guardado vía Ice: " + from + " -> " + to);
            return true;
        } catch (Exception e) {
            throw new UserException("Error al enviar mensaje: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized MessageInfo[] getPrivateHistory(String username, String target, Current current) throws UserException {
        if (username == null || target == null) {
            throw new UserException("Faltan parámetros requeridos (username, target)");
        }
        
        try {
            List<Message> messages = chatManager.getMessageHistory(target, false);
            return messages.stream()
                .map(msg -> new MessageInfo(
                    msg.getId(),
                    msg.getFrom(),
                    msg.getTo(),
                    msg.getContent(),
                    msg.getTimestamp().getTime(),
                    msg.isGroupMessage()
                ))
                .toArray(MessageInfo[]::new);
        } catch (Exception e) {
            throw new UserException("Error al obtener historial: " + e.getMessage());
        }
    }
    
    // =====================================================================
    // MENSAJES DE GRUPO
    // =====================================================================
    
    @Override
    public synchronized boolean sendGroupMessage(String from, String groupName, String content, Current current) throws UserException {
        if (from == null || groupName == null || content == null) {
            throw new UserException("Faltan parámetros requeridos (from, groupName, content)");
        }
        
        if (!chatManager.groupExists(groupName)) {
            throw new UserException("El grupo no existe");
        }
        
        if (!chatManager.isUserOnline(from)) {
            throw new UserException("El usuario remitente no está conectado");
        }
        
        try {
            Message message = new Message(from, groupName, content, true);
            chatManager.saveTextMessage(message);
            System.out.println("👥 Mensaje de grupo guardado vía Ice: " + from + " -> " + groupName);
            return true;
        } catch (Exception e) {
            throw new UserException("Error al enviar mensaje de grupo: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized MessageInfo[] getGroupHistory(String groupName, Current current) throws UserException {
        if (groupName == null) {
            throw new UserException("El nombre del grupo no puede estar vacío");
        }
        
        try {
            List<Message> messages = chatManager.getMessageHistory(groupName, true);
            return messages.stream()
                .map(msg -> new MessageInfo(
                    msg.getId(),
                    msg.getFrom(),
                    msg.getTo(),
                    msg.getContent(),
                    msg.getTimestamp().getTime(),
                    msg.isGroupMessage()
                ))
                .toArray(MessageInfo[]::new);
        } catch (Exception e) {
            throw new UserException("Error al obtener historial del grupo: " + e.getMessage());
        }
    }
    
    // =====================================================================
    // GESTIÓN DE GRUPOS
    // =====================================================================
    
    @Override
    public synchronized boolean createGroup(String groupName, String creator, String[] members, Current current) throws UserException {
        if (groupName == null || creator == null) {
            throw new UserException("Faltan parámetros requeridos (groupName, creator)");
        }
        
        if (!chatManager.isUserOnline(creator)) {
            throw new UserException("El creador del grupo no está conectado");
        }
        
        try {
            boolean success = chatManager.createGroup(groupName, creator);
            if (!success) {
                throw new UserException("No se pudo crear el grupo (posiblemente ya existe)");
            }
            
            // Agregar miembros adicionales si se proporcionaron
            if (members != null) {
                for (String member : members) {
                    if (member != null && !member.isEmpty() && !member.equals(creator)) {
                        chatManager.joinGroup(groupName, member.trim());
                    }
                }
            }
            
            System.out.println("✅ Grupo creado vía Ice: " + groupName + " por " + creator);
            return true;
        } catch (Exception e) {
            throw new UserException("Error al crear grupo: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized boolean joinGroup(String username, String groupName, Current current) throws UserException {
        if (username == null || groupName == null) {
            throw new UserException("Faltan parámetros requeridos (username, groupName)");
        }
        
        if (!chatManager.groupExists(groupName)) {
            throw new UserException("El grupo no existe");
        }
        
        try {
            boolean success = chatManager.joinGroup(groupName, username);
            if (success) {
                System.out.println("➕ Usuario unido al grupo vía Ice: " + username + " -> " + groupName);
            }
            return success;
        } catch (Exception e) {
            throw new UserException("Error al unirse al grupo: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized boolean leaveGroup(String username, String groupName, Current current) throws UserException {
        if (username == null || groupName == null) {
            throw new UserException("Faltan parámetros requeridos (username, groupName)");
        }
        
        try {
            boolean success = chatManager.leaveGroup(groupName, username);
            if (success) {
                System.out.println("➖ Usuario abandonó grupo vía Ice: " + username + " -> " + groupName);
            }
            return success;
        } catch (Exception e) {
            throw new UserException("Error al abandonar grupo: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized GroupInfo[] getUserGroups(String username, Current current) throws UserException {
        if (username == null) {
            throw new UserException("El nombre de usuario no puede estar vacío");
        }
        
        try {
            List<String> groupNames = new ArrayList<>();
            for (String groupName : chatManager.getAllGroups()) {
                Group group = chatManager.getGroup(groupName);
                if (group != null && group.hasMember(username)) {
                    groupNames.add(groupName);
                }
            }
            
            return groupNames.stream()
                .map(name -> {
                    Group g = chatManager.getGroup(name);
                    return new GroupInfo(
                        g.getName(),
                        g.getCreator(),
                        g.getMembers().toArray(new String[0])
                    );
                })
                .toArray(GroupInfo[]::new);
        } catch (Exception e) {
            throw new UserException("Error al obtener grupos del usuario: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized String[] getGroupMembers(String groupName, Current current) throws UserException {
        if (groupName == null) {
            throw new UserException("El nombre del grupo no puede estar vacío");
        }
        
        if (!chatManager.groupExists(groupName)) {
            throw new UserException("El grupo no existe");
        }
        
        try {
            List<String> members = chatManager.getGroupMembers(groupName);
            return members.toArray(new String[0]);
        } catch (Exception e) {
            throw new UserException("Error al obtener miembros del grupo: " + e.getMessage());
        }
    }
    
    // =====================================================================
    // AUDIO Y MULTIMEDIA
    // =====================================================================
    
    @Override
    public synchronized boolean sendAudio(String from, String to, String audioId, int size, int duration, Current current) throws UserException {
        if (from == null || to == null || audioId == null) {
            throw new UserException("Faltan parámetros requeridos (from, to, audioId)");
        }
        
        if (!chatManager.isUserOnline(from)) {
            throw new UserException("El usuario remitente no está conectado");
        }
        
        try {
            AudioMessage audioMsg = new AudioMessage(audioId, from, to, false, System.currentTimeMillis(), size, duration);
            chatManager.saveAudioMessage(audioMsg);
            System.out.println("🎵 Audio privado enviado vía Ice: " + from + " -> " + to + " (" + size + " bytes)");
            return true;
        } catch (Exception e) {
            throw new UserException("Error al enviar audio: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized boolean sendGroupAudio(String from, String groupName, String audioId, int size, int duration, Current current) throws UserException {
        if (from == null || groupName == null || audioId == null) {
            throw new UserException("Faltan parámetros requeridos (from, groupName, audioId)");
        }
        
        if (!chatManager.groupExists(groupName)) {
            throw new UserException("El grupo no existe");
        }
        
        if (!chatManager.isUserOnline(from)) {
            throw new UserException("El usuario remitente no está conectado");
        }
        
        try {
            AudioMessage audioMsg = new AudioMessage(audioId, from, groupName, true, System.currentTimeMillis(), size, duration);
            chatManager.saveAudioMessage(audioMsg);
            System.out.println("🎵 Audio de grupo enviado vía Ice: " + from + " -> " + groupName + " (" + size + " bytes)");
            return true;
        } catch (Exception e) {
            throw new UserException("Error al enviar audio de grupo: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized AudioInfo[] getPrivateAudioHistory(String username, String target, Current current) throws UserException {
        if (username == null || target == null) {
            throw new UserException("Faltan parámetros requeridos (username, target)");
        }
        
        try {
            List<AudioMessage> audioMessages = chatManager.getAudioMessageHistory(target, false);
            return audioMessages.stream()
                .map(msg -> new AudioInfo(
                    msg.getId(),
                    msg.getFrom(),
                    msg.getTo(),
                    msg.getTimestamp().getTime(),
                    msg.isGroupMessage(),
                    msg.getDuration(),
                    msg.getAudioSize()
                ))
                .toArray(AudioInfo[]::new);
        } catch (Exception e) {
            throw new UserException("Error al obtener historial de audio: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized AudioInfo[] getGroupAudioHistory(String groupName, Current current) throws UserException {
        if (groupName == null) {
            throw new UserException("El nombre del grupo no puede estar vacío");
        }
        
        try {
            List<AudioMessage> audioMessages = chatManager.getAudioMessageHistory(groupName, true);
            return audioMessages.stream()
                .map(msg -> new AudioInfo(
                    msg.getId(),
                    msg.getFrom(),
                    msg.getTo(),
                    msg.getTimestamp().getTime(),
                    msg.isGroupMessage(),
                    msg.getDuration(),
                    msg.getAudioSize()
                ))
                .toArray(AudioInfo[]::new);
        } catch (Exception e) {
            throw new UserException("Error al obtener historial de audio del grupo: " + e.getMessage());
        }
    }
    
    // =====================================================================
    // USUARIOS
    // =====================================================================
    
    @Override
    public synchronized String[] getOnlineUsers(Current current) throws UserException {
        try {
            List<String> users = chatManager.getOnlineUsers();
            return users.toArray(new String[0]);
        } catch (Exception e) {
            throw new UserException("Error al obtener usuarios en línea: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized UserInfo[] getAllUsers(Current current) throws UserException {
        try {
            List<String> onlineUsers = chatManager.getOnlineUsers();
            return onlineUsers.stream()
                .map(username -> new UserInfo(username, true))
                .toArray(UserInfo[]::new);
        } catch (Exception e) {
            throw new UserException("Error al obtener todos los usuarios: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized boolean isUserOnline(String username, Current current) throws UserException {
        if (username == null) {
            throw new UserException("El nombre de usuario no puede estar vacío");
        }
        
        try {
            return chatManager.isUserOnline(username);
        } catch (Exception e) {
            throw new UserException("Error al verificar si el usuario está en línea: " + e.getMessage());
        }
    }
    
    // =====================================================================
    // INFORMACIÓN DEL SERVIDOR
    // =====================================================================
    
    @Override
    public synchronized String getServerStatus(Current current) throws UserException {
        try {
            int onlineUsers = chatManager.getOnlineUserCount();
            int groups = chatManager.getGroupCount();
            
            return String.format(
                "Usuarios en línea: %d | Grupos activos: %d | Servidor funcionando correctamente",
                onlineUsers, groups
            );
        } catch (Exception e) {
            throw new UserException("Error al obtener estado del servidor: " + e.getMessage());
        }
    }
}
