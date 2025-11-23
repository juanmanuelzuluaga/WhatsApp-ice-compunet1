// Definiciones de interfaces Ice para el sistema de chat
// Este archivo define los servicios RPC disponibles

module chat {
    // Secuencias de strings para listas
    sequence<string> StringSeq;
    
    // Información de un mensaje
    struct MessageInfo {
        string id;
        string from;
        string to;
        string content;
        long timestamp;
        bool isGroup;
    };
    
    // Información de un audio
    struct AudioInfo {
        string id;
        string from;
        string to;
        long timestamp;
        bool isGroup;
        int duration;
        int size;
    };
    
    // Secuencias de mensajes
    sequence<MessageInfo> MessageSeq;
    sequence<AudioInfo> AudioSeq;
    
    // Información de usuario
    struct UserInfo {
        string name;
        bool online;
    };
    
    sequence<UserInfo> UserSeq;
    
    // Información de grupo
    struct GroupInfo {
        string name;
        string creator;
        StringSeq members;
    };
    
    sequence<GroupInfo> GroupSeq;
    
    // Excepción personalizada
    exception UserException {
        string message;
    };
    
    // Interfaz principal del servicio de chat
    interface ChatService {
        // Autenticación
        bool login(string username) throws UserException;
        bool logout(string username) throws UserException;
        
        // Mensajes privados
        bool sendMessage(string from, string to, string content) throws UserException;
        MessageSeq getPrivateHistory(string username, string target) throws UserException;
        
        // Mensajes de grupo
        bool sendGroupMessage(string from, string groupName, string content) throws UserException;
        MessageSeq getGroupHistory(string groupName) throws UserException;
        
        // Gestión de grupos
        bool createGroup(string groupName, string creator, StringSeq members) throws UserException;
        bool joinGroup(string username, string groupName) throws UserException;
        bool leaveGroup(string username, string groupName) throws UserException;
        GroupSeq getUserGroups(string username) throws UserException;
        StringSeq getGroupMembers(string groupName) throws UserException;
        
        // Audio y multimedia
        bool sendAudio(string from, string to, string audioId, int size, int duration) throws UserException;
        bool sendGroupAudio(string from, string groupName, string audioId, int size, int duration) throws UserException;
        AudioSeq getPrivateAudioHistory(string username, string target) throws UserException;
        AudioSeq getGroupAudioHistory(string groupName) throws UserException;
        
        // Usuarios
        StringSeq getOnlineUsers() throws UserException;
        UserSeq getAllUsers() throws UserException;
        bool isUserOnline(string username) throws UserException;
        
        // Información del servidor
        idempotent string getServerStatus() throws UserException;
    };
};
