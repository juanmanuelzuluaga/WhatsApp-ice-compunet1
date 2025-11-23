/**
 * Cliente Ice para conectarse al servidor de chat Java
 * Usa ZeroC Ice para comunicación RPC
 */
import Ice_module from "ice";
const Ice = Ice_module.Ice;

class IceClient {
  constructor(host = "localhost", port = 5001) {
    this.host = host;
    this.port = port;
    this.communicator = null;
    this.proxy = null;
    this.initialized = false;
    this.chat = null;
  }

  /**
   * Inicializa la comunicación Ice con el servidor
   */
  async initialize() {
    try {
      const chatModule = await import("./chat.cjs");
      this.chat = chatModule.chat;

      if (!this.chat) {
        throw new Error("No se pudo cargar el módulo chat");
      }

      const initData = new Ice.InitializationData();
      initData.properties = Ice.createProperties();
      initData.properties.setProperty("Ice.Default.Host", this.host);
      initData.properties.setProperty("Ice.Default.Port", this.port.toString());

      this.communicator = Ice.initialize(initData);

      const proxyString = `chat/ChatService:tcp -h ${this.host} -p ${this.port}`;
      const baseProxy = this.communicator.stringToProxy(proxyString);

      this.proxy = this.chat.ChatServicePrx.uncheckedCast(baseProxy);

      if (!this.proxy) {
        throw new Error("Invalid proxy after uncheckedCast");
      }

      this.initialized = true;
 console.log("Cliente Ice inicializado correctamente");
 console.log(`Conectado a: ${this.host}:${this.port}`);
      return true;
    } catch (err) {
 console.error("Error inicializando Ice:", err.message);
      throw err;
    }
  }

  async login(username) {
    try {
      const result = await this.proxy.login(username);
 console.log(`Login exitoso: ${username}`);
      return result;
    } catch (err) {
 console.error(`Error en login: ${err.message}`);
      throw err;
    }
  }

  async logout(username) {
    try {
      const result = await this.proxy.logout(username);
 console.log(`Logout exitoso: ${username}`);
      return result;
    } catch (err) {
 console.error(`Error en logout: ${err.message}`);
      throw err;
    }
  }

  async sendMessage(from, to, content) {
    try {
      const result = await this.proxy.sendMessage(from, to, content);
 console.log(`Mensaje privado enviado: ${from} -> ${to}`);
      return result;
    } catch (err) {
 console.error(`Error al enviar mensaje: ${err.message}`);
      throw err;
    }
  }

  async sendGroupMessage(from, groupName, content) {
    try {
      const result = await this.proxy.sendGroupMessage(from, groupName, content);
 console.log(`Mensaje de grupo enviado: ${from} -> ${groupName}`);
      return result;
    } catch (err) {
 console.error(`Error al enviar mensaje de grupo: ${err.message}`);
      throw err;
    }
  }

  async getPrivateHistory(username, target) {
    try {
      const messages = await this.proxy.getPrivateHistory(username, target);
 console.log(`Historial privado obtenido: ${target} (${messages.length} mensajes)`);
      return messages;
    } catch (err) {
 console.error(`Error al obtener historial: ${err.message}`);
      throw err;
    }
  }

  async getGroupHistory(groupName) {
    try {
      const messages = await this.proxy.getGroupHistory(groupName);
 console.log(`Historial de grupo obtenido: ${groupName} (${messages.length} mensajes)`);
      return messages;
    } catch (err) {
 console.error(`Error al obtener historial de grupo: ${err.message}`);
      throw err;
    }
  }

  async createGroup(groupName, creator, members = []) {
    try {
      const result = await this.proxy.createGroup(groupName, creator, members);
 console.log(`Grupo creado: ${groupName} (${members.length + 1} miembros)`);
      return result;
    } catch (err) {
 console.error(`Error al crear grupo: ${err.message}`);
      throw err;
    }
  }

  async joinGroup(username, groupName) {
    try {
      const result = await this.proxy.joinGroup(username, groupName);
 console.log(`Usuario unido al grupo: ${username} -> ${groupName}`);
      return result;
    } catch (err) {
 console.error(`Error al unirse al grupo: ${err.message}`);
      throw err;
    }
  }

  async leaveGroup(username, groupName) {
    try {
      const result = await this.proxy.leaveGroup(username, groupName);
 console.log(`Usuario abandonó grupo: ${username} -> ${groupName}`);
      return result;
    } catch (err) {
 console.error(`Error al abandonar grupo: ${err.message}`);
      throw err;
    }
  }

  async getUserGroups(username) {
    try {
      const groups = await this.proxy.getUserGroups(username);
 console.log(`Grupos obtenidos: ${username} (${groups.length} grupos)`);
      return groups;
    } catch (err) {
 console.error(`Error al obtener grupos: ${err.message}`);
      throw err;
    }
  }

  async getGroupMembers(groupName) {
    try {
      const members = await this.proxy.getGroupMembers(groupName);
 console.log(`Miembros obtenidos: ${groupName} (${members.length} miembros)`);
      return members;
    } catch (err) {
 console.error(`Error al obtener miembros: ${err.message}`);
      throw err;
    }
  }

  async sendAudio(from, to, audioId, size, duration) {
    try {
      const result = await this.proxy.sendAudio(from, to, audioId, size, duration);
 console.log(`Audio privado enviado: ${from} -> ${to} (${size} bytes)`);
      return result;
    } catch (err) {
 console.error(`Error al enviar audio: ${err.message}`);
      throw err;
    }
  }

  async sendGroupAudio(from, groupName, audioId, size, duration) {
    try {
      const result = await this.proxy.sendGroupAudio(from, groupName, audioId, size, duration);
 console.log(`Audio de grupo enviado: ${from} -> ${groupName} (${size} bytes)`);
      return result;
    } catch (err) {
 console.error(`Error al enviar audio de grupo: ${err.message}`);
      throw err;
    }
  }

  async getPrivateAudioHistory(username, target) {
    try {
      const audioMessages = await this.proxy.getPrivateAudioHistory(username, target);
 console.log(`Historial de audio obtenido: ${target} (${audioMessages.length} audios)`);
      return audioMessages;
    } catch (err) {
 console.error(`Error al obtener historial de audio: ${err.message}`);
      throw err;
    }
  }

  async getGroupAudioHistory(groupName) {
    try {
      const audioMessages = await this.proxy.getGroupAudioHistory(groupName);
 console.log(`Historial de audio de grupo obtenido: ${groupName} (${audioMessages.length} audios)`);
      return audioMessages;
    } catch (err) {
 console.error(`Error al obtener historial de audio de grupo: ${err.message}`);
      throw err;
    }
  }

  async getOnlineUsers() {
    try {
      const users = await this.proxy.getOnlineUsers();
 console.log(`Usuarios en línea: ${users.length}`);
      return users;
    } catch (err) {
 console.error(`Error al obtener usuarios: ${err.message}`);
      throw err;
    }
  }

  async getAllUsers() {
    try {
      const users = await this.proxy.getAllUsers();
 console.log(`Total de usuarios: ${users.length}`);
      return users;
    } catch (err) {
 console.error(`Error al obtener todos los usuarios: ${err.message}`);
      throw err;
    }
  }

  async isUserOnline(username) {
    try {
      const isOnline = await this.proxy.isUserOnline(username);
 console.log(`Estado de ${username}: ${isOnline ? "En línea" : "Desconectado"}`);
      return isOnline;
    } catch (err) {
 console.error(`Error al verificar estado: ${err.message}`);
      throw err;
    }
  }

  async getServerStatus() {
    try {
      const status = await this.proxy.getServerStatus();
 console.log(`Estado del servidor: ${status}`);
      return status;
    } catch (err) {
 console.error(`Error al obtener estado: ${err.message}`);
      throw err;
    }
  }

  destroy() {
    if (this.communicator) {
      try {
        this.communicator.destroy();
 console.log("Conexión Ice cerrada");
      } catch (err) {
 console.error("Error cerrando Ice:", err.message);
      }
    }
  }
}

export default IceClient;
