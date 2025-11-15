package service;

import model.User;
import model.Group;
import model.Message;
import model.AudioMessage;
import persistence.ChatHistory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {
    private Map<String, User> onlineUsers;
    private Map<String, Group> groups;
    private ChatHistory chatHistory;

    public ChatManager() {
        this.onlineUsers = new ConcurrentHashMap<>();
        this.groups = new ConcurrentHashMap<>();
        this.chatHistory = new ChatHistory();
    }

    public boolean loginUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String cleanUsername = username.trim();

        if (onlineUsers.containsKey(cleanUsername)) {
            return false;
        }

        User user = new User(cleanUsername);
        onlineUsers.put(cleanUsername, user);
        System.out.println("Usuario conectado: " + cleanUsername);
        return true;
    }

    public void logoutUser(String username) {
        if (username != null) {
            onlineUsers.remove(username);
            System.out.println("Usuario desconectado: " + username);
        }
    }

    public boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    public List<String> getOnlineUsers() {
        return new ArrayList<>(onlineUsers.keySet());
    }

    public User getUser(String username) {
        return onlineUsers.get(username);
    }

    public boolean createGroup(String groupName, String creator) {
        if (groupName == null || groupName.trim().isEmpty() || creator == null) {
            return false;
        }

        String cleanGroupName = groupName.trim();

        if (groups.containsKey(cleanGroupName)) {
            System.out.println("Grupo ya existe: " + cleanGroupName);
            return false;
        }

        if (!isUserOnline(creator)) {
            System.out.println("Creador no está online: " + creator);
            return false;
        }

        Group group = new Group(cleanGroupName, creator);
        groups.put(cleanGroupName, group);
        System.out.println("Grupo creado: " + cleanGroupName + " por " + creator);
        return true;
    }

    public boolean joinGroup(String groupName, String username) {
        if (groupName == null || username == null) {
            return false;
        }

        Group group = groups.get(groupName);
        if (group == null) {
            System.out.println("Grupo no existe: " + groupName);
            return false;
        }

        if (!isUserOnline(username)) {
            System.out.println("Usuario no está online: " + username);
            return false;
        }

        boolean success = group.addMember(username);
        if (success) {
            System.out.println("Usuario " + username + " se unió al grupo " + groupName);
        } else {
            System.out.println("Usuario " + username + " ya está en el grupo " + groupName);
        }
        return success;
    }

    public boolean leaveGroup(String groupName, String username) {
        if (groupName == null || username == null) {
            return false;
        }

        Group group = groups.get(groupName);
        if (group == null) {
            return false;
        }

        boolean success = group.removeMember(username);
        if (success) {
            System.out.println("Usuario " + username + " abandonó el grupo " + groupName);

            if (group.getMemberCount() == 0) {
                groups.remove(groupName);
                System.out.println("Grupo eliminado por estar vacío: " + groupName);
            }
        }
        return success;
    }

    public List<String> getGroupMembers(String groupName) {
        Group group = groups.get(groupName);
        if (group != null) {
            return new ArrayList<>(group.getMembers());
        }
        return new ArrayList<>();
    }

    public List<String> getAllGroups() {
        return new ArrayList<>(groups.keySet());
    }

    public boolean groupExists(String groupName) {
        return groups.containsKey(groupName);
    }

    public Group getGroup(String groupName) {
        return groups.get(groupName);
    }

    public void saveTextMessage(Message message) {
        if (message != null) {
            chatHistory.saveMessage(message);
            System.out.println("Mensaje guardado: " + message.getFrom() + " -> " + message.getTo());
        }
    }

    public void saveAudioMessage(AudioMessage audioMessage) {
        if (audioMessage != null) {
            chatHistory.saveAudioMessage(audioMessage);
            System.out.println("Audio guardado: " + audioMessage.getFrom() + " -> " + audioMessage.getTo() +
                    " (" + audioMessage.getAudioSize() + " bytes)");
        }
    }

    public List<Message> getMessageHistory(String target, boolean isGroup) {
        if (target == null) {
            return new ArrayList<>();
        }
        return chatHistory.loadMessages(target, isGroup);
    }

    public List<AudioMessage> getAudioMessageHistory(String target, boolean isGroup) {
        if (target == null) {
            return new ArrayList<>();
        }
        return chatHistory.loadAudioMessages(target, isGroup);
    }

    public byte[] getAudioData(String audioId) {
        return chatHistory.loadAudioData(audioId);
    }

    public int getOnlineUserCount() {
        return onlineUsers.size();
    }

    public int getGroupCount() {
        return groups.size();
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("onlineUsers", getOnlineUserCount());
        status.put("activeGroups", getGroupCount());
        status.put("totalUsers", onlineUsers.size());
        status.put("totalGroups", groups.size());
        return status;
    }

    public void clearAllData() {
        onlineUsers.clear();
        groups.clear();
        System.out.println("Todos los datos han sido limpiados");
    }

    public boolean isUserInGroup(String username, String groupName) {
        Group group = groups.get(groupName);
        return group != null && group.hasMember(username);
    }

    public List<String> getUserGroups(String username) {
        List<String> userGroups = new ArrayList<>();
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            if (entry.getValue().hasMember(username)) {
                userGroups.add(entry.getKey());
            }
        }
        return userGroups;
    }

    public void showGroups() {
        System.out.println("\n=== GRUPOS DISPONIBLES ===");
        if (groups.isEmpty()) {
            System.out.println("No hay grupos creados");
        } else {
            for (Map.Entry<String, Group> entry : groups.entrySet()) {
                String groupName = entry.getKey();
                Group group = entry.getValue();
                System.out.println(" " + groupName + " (" + group.getMemberCount() + " miembros)");
            }
        }
        System.out.println("========================\n");
    }

    public String createUser(String username) {
        if (loginUser(username)) {
            return username;
        }
        return null;
    }

    public boolean addUserToGroup(String groupName, String username) {
        return joinGroup(groupName, username);
    }

    public void sendMessage(String groupName, String username, String content) {
        if (groupExists(groupName) && isUserInGroup(username, groupName)) {
            Message msg = new Message(username, groupName, content, true);
            saveTextMessage(msg);
            System.out.println("Mensaje enviado a grupo " + groupName);
        } else {
            System.out.println("Error: Usuario no pertenece al grupo o grupo no existe");
        }
    }

    public void showHistory(String groupName) {
        if (groupExists(groupName)) {
            List<Message> messages = getMessageHistory(groupName, true);
            System.out.println("\n=== HISTORIAL DE " + groupName + " ===");
            if (messages.isEmpty()) {
                System.out.println("No hay mensajes");
            } else {
                for (Message msg : messages) {
                    System.out.println(msg);
                }
            }
            System.out.println("================================\n");
        } else {
            System.out.println("Grupo no existe");
        }
    }
}
