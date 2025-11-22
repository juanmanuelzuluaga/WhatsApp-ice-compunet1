console.log("Iniciando aplicación...")

// Configuración del cliente
const API_URL = "http://localhost:3000/api"

// Verificar conexión con el servidor
;(async function verificarServidor() {
  try {
    console.log("Verificando conexión con el proxy...")
    const response = await fetch(`${API_URL}/test`)
    if (response.ok) {
      const data = await response.json()
      console.log("Conexión con el proxy:", data)
    } else {
      console.warn("Proxy respondió con estado:", response.status)
    }
  } catch (err) {
    console.error("Error al conectar con el proxy:", err)
  }
})()

// Funciones de API
async function apiLogin(username) {
  try {
    console.log("Intentando login con:", username)
    const res = await fetch(`${API_URL}/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ username: username.trim() }),
    })

    console.log("Respuesta del servidor:", res.status, res.statusText)

    if (!res.ok) {
      const error = await res.text()
      console.error("Error del servidor:", error)
      throw new Error(`Login failed (${res.status}): ${error}`)
    }

    const data = await res.json()
    console.log("Login exitoso:", data)
    return data
  } catch (err) {
    console.error("Error en login:", err)
    throw err
  }
}

async function apiSendMessage(from, to, content) {
  try {
    console.log(`Enviando mensaje de ${from} a ${to}: ${content.substring(0, 30)}...`)
    const res = await fetch(`${API_URL}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ from, to, content }),
    })

    if (!res.ok) {
      const error = await res.text()
      throw new Error(`Error al enviar mensaje (${res.status}): ${error}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error enviando mensaje:", err)
    throw err
  }
}

async function apiSendGroupMessage(from, group_name, content) {
  try {
    console.log(`Enviando mensaje de grupo de ${from} a ${group_name}: ${content.substring(0, 30)}...`)
    const res = await fetch(`${API_URL}/sendGroupMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ from, group_name, content }),
    })

    if (!res.ok) {
      const error = await res.text()
      throw new Error(`Error al enviar mensaje de grupo (${res.status}): ${error}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error enviando mensaje de grupo:", err)
    throw err
  }
}

async function apiCreateGroup(group_name, creator, members = []) {
  try {
    console.log(`Creando grupo ${group_name} por ${creator} con miembros: ${members.join(", ")}`)
    const res = await fetch(`${API_URL}/createGroup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ group_name, creator, members }),
    })

    if (!res.ok) {
      const error = await res.text()
      throw new Error(`Error creando grupo: ${error}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error creando grupo:", err)
    throw err
  }
}

async function apiGetGroups(username) {
  try {
    console.log(`Obteniendo grupos para ${username}`)
    const res = await fetch(`${API_URL}/groups/${username}`)

    if (!res.ok) {
      const error = await res.text()
      throw new Error(`Error obteniendo grupos: ${error}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error obteniendo grupos:", err)
    throw err
  }
}

async function apiGetHistory(target, username, isGroup = false) {
  try {
    console.log(`Obteniendo historial de ${target}`)
    const res = await fetch(`${API_URL}/history/${target}?username=${username}&isGroup=${isGroup}`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    })

    if (!res.ok) {
      throw new Error(`Error obteniendo historial: ${res.status}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error obteniendo historial:", err)
    throw err
  }
}

async function apiJoinGroup(username, group_name) {
  try {
    console.log(`Usuario ${username} uniéndose al grupo ${group_name}`)
    const res = await fetch(`${API_URL}/joinGroup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, group_name }),
    })

    if (!res.ok) {
      const error = await res.text()
      throw new Error(`Error al unirse al grupo: ${error}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error al unirse al grupo:", err)
    throw err
  }
}

async function apiGetOnlineUsers(username) {
  try {
    console.log(`Obteniendo usuarios online para ${username}`)
    const res = await fetch(`${API_URL}/onlineUsers/${username}`)

    if (!res.ok) {
      throw new Error(`Error obteniendo usuarios online: ${res.status}`)
    }

    return await res.json()
  } catch (err) {
    console.error("Error obteniendo usuarios online:", err)
    throw err
  }
}

// Asegurarnos de que el DOM está cargado
document.addEventListener("DOMContentLoaded", () => {
  console.log("DOM cargado, configurando eventos...")
  const loginForm = document.getElementById("login-form")
  const usernameInput = document.getElementById("username")
  const loginStatus = document.getElementById("login-status")

  const contactsSection = document.getElementById("contacts-section")
  const contactsList = document.getElementById("contacts-list")
  const btnCreateGroup = document.getElementById("btn-create-group")
  const createGroupForm = document.getElementById("create-group-form")
  const groupNameInput = document.getElementById("group-name")
  const groupMembersInput = document.getElementById("group-members")
  const cancelCreate = document.getElementById("cancel-create")

  const chatSection = document.getElementById("chat-section")
  const chatTitle = document.getElementById("chat-title")
  const chatSubtitle = document.getElementById("chat-subtitle")
  const chatLog = document.getElementById("chat-log")
  const messageForm = document.getElementById("message-form")
  const messageInput = document.getElementById("message")
  const btnAudioMsg = document.getElementById("btn-audio-msg")

  const state = {
    user: null,
    contacts: [],
    messages: {},
    activeChat: null,
    isRecording: false,
    mediaRecorder: null,
    audioChunks: [],
  }

  // 🎤 Función para iniciar grabación de audio
  async function startAudioRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      state.mediaRecorder = new MediaRecorder(stream)
      state.audioChunks = []
      state.isRecording = true

      state.mediaRecorder.ondataavailable = (e) => {
        state.audioChunks.push(e.data)
      }

      state.mediaRecorder.onstop = async () => {
        const audioBlob = new Blob(state.audioChunks, { type: "audio/wav" })
        
        // Convertir a Base64 usando una promesa
        const base64Audio = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => {
            const result = reader.result.split(",")[1]
            resolve(result)
          }
          reader.onerror = reject
          reader.readAsDataURL(audioBlob)
        })
        
        // Obtener el chat activo
        const chat = state.contacts.find((c) => c.id === state.activeChat)
        if (!chat) {
          console.error("No hay chat activo")
          state.isRecording = false
          return
        }

        try {
          console.log(`📤 Enviando audio: ${base64Audio.length} bytes`)
          
          // Enviar audio
          const res = await fetch(`${API_URL}/sendAudio`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              from: state.user,
              to: chat.type === "group" ? null : chat.name,
              group_name: chat.type === "group" ? chat.name : null,
              audio_data: base64Audio,
            }),
          })

          if (res.ok) {
            const resData = await res.json()
            console.log("✅ Audio enviado correctamente:", resData)
            // Agregar el audio a los mensajes locales
            const chatId = state.activeChat
            state.messages[chatId] = state.messages[chatId] || []
            state.messages[chatId].push({
              from: state.user,
              text: "[🎵 Nota de voz]",
              time: Date.now(),
              isAudio: true,
              audioData: base64Audio,
            })
            renderMessages(chatId)
          } else {
            const errorText = await res.text()
            console.error("❌ Error al enviar audio:", res.status, errorText)
            alert(`Error al enviar audio: ${res.status}`)
          }
        } catch (err) {
          console.error("❌ Error enviando audio:", err)
          alert("Error: " + err.message)
        }
        
        state.isRecording = false
      }

      state.mediaRecorder.start()
      const btnAudio = document.getElementById("btn-audio-msg")
      if (btnAudio) btnAudio.textContent = "⏹️"
    } catch (err) {
      console.error("Error accediendo al micrófono:", err)
      alert("No se pudo acceder al micrófono: " + err.message)
    }
  }

  // 🎤 Función para detener grabación
  function stopAudioRecording() {
    if (state.mediaRecorder && state.isRecording) {
      state.mediaRecorder.stop()
      state.mediaRecorder.stream.getTracks().forEach((track) => track.stop())
      const btnAudio = document.getElementById("btn-audio-msg")
      if (btnAudio) btnAudio.textContent = "🎤"
    }
  }

  function renderContacts() {
    contactsList.innerHTML = ""
    if (state.contacts.length === 0) {
      const li = document.createElement("li")
      li.className = "muted"
      li.textContent = "No hay contactos aún. Crea uno o un grupo."
      contactsList.appendChild(li)
      return
    }

    state.contacts.forEach((c) => {
      const li = document.createElement("li")
      li.dataset.id = c.id
      const avatar = document.createElement("div")
      avatar.className = "avatar"
      avatar.textContent = c.name.charAt(0).toUpperCase()
      const meta = document.createElement("div")
      meta.style.flex = "1"
      const name = document.createElement("div")
      name.className = "contact-name"
      name.textContent = c.name
      const sub = document.createElement("div")
      sub.className = "contact-meta"
      sub.textContent = c.type === "group" ? `Grupo • ${c.members.length} miembros` : "Contacto"
      meta.appendChild(name)
      meta.appendChild(sub)
      li.appendChild(avatar)
      li.appendChild(meta)
      li.addEventListener("click", () => openChat(c.id))
      contactsList.appendChild(li)
    })
  }

  function openChat(id) {
    const c = state.contacts.find((x) => x.id === id)
    if (!c) return
    state.activeChat = id
    chatTitle.textContent = c.name + (c.type === "group" ? " (grupo)" : "")
    
    // Mostrar miembros del grupo si existe
    if (c.type === "group" && c.members && c.members.length > 0) {
      chatSubtitle.textContent = `Miembros (${c.members.length}): ${c.members.join(", ")}`
    } else if (c.type === "group") {
      chatSubtitle.textContent = "Miembros: (cargando...)"
    } else {
      chatSubtitle.textContent = ""
    }
    
    chatSection.classList.remove("hidden")
    messageForm.classList.remove("hidden")
    renderMessages(id)
  }

  function renderMessages(chatId) {
    chatLog.innerHTML = ""
    const msgs = state.messages[chatId] || []
    
    msgs.forEach((m) => {
      const div = document.createElement("div")
      div.className = "message " + (m.from === state.user ? "me" : "their")
      
      if (m.isAudio) {
        // Crear un reproductor de audio
        const audioElement = document.createElement("audio")
        audioElement.controls = true
        audioElement.style.width = "100%"
        
        if (m.audioData) {
          // Ya tenemos los datos base64
          audioElement.src = "data:audio/wav;base64," + m.audioData
          console.log(`🎵 Audio reproductor con datos locales: ${m.audioId || "local"}`)
        } else if (m.audioId) {
          // Necesitamos cargar los datos desde el servidor
          console.log(`⏳ Cargando audio del servidor: ${m.audioId}`)
          audioElement.style.opacity = "0.6"
          audioElement.disabled = true
          
          // Cargar audio de forma asincrónica
          fetch(`${API_URL}/audio/${m.audioId}`)
            .then((res) => {
              if (!res.ok) throw new Error(`HTTP ${res.status}`)
              return res.json()
            })
            .then((data) => {
              if (data.ok && data.audio_data) {
                m.audioData = data.audio_data
                audioElement.src = "data:audio/wav;base64," + data.audio_data
                audioElement.style.opacity = "1"
                audioElement.disabled = false
                console.log(`✅ Audio cargado: ${m.audioId} (${data.audio_data.length} bytes)`)
              } else {
                throw new Error(data.error || "Respuesta inválida")
              }
            })
            .catch((err) => {
              console.error(`❌ Error cargando audio ${m.audioId}:`, err)
              audioElement.style.opacity = "0.3"
              audioElement.title = "Error cargando audio: " + err.message
            })
        }
        
        const content = document.createElement("div")
        content.className = "content"
        content.appendChild(audioElement)
        
        div.appendChild(content)
      } else {
        div.innerHTML = `<div class="content">${escapeHtml(m.text)}</div>`
      }
      
      const meta = document.createElement("span")
      meta.className = "meta"
      meta.textContent = `${m.from} • ${formatTime(m.time)}`
      div.appendChild(meta)
      
      chatLog.appendChild(div)
    })
    chatLog.scrollTop = chatLog.scrollHeight
  }

  function escapeHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;")
  }

  function formatTime(ts) {
    const d = new Date(ts)
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
  }

  // ✅ Función para procesar notificaciones del servidor
  function processNotification(notification) {
    console.log("📨 Procesando notificación:", notification)

    if (notification.includes("type:private_message")) {
      const parts = notification.split("|")
      const messageData = {}
      parts.forEach((part) => {
        const [key, value] = part.split(":")
        if (key && value) messageData[key] = value
      })

      const from = messageData.from
      const to = messageData.to
      const content = messageData.content

      if (from && content && to === state.user) {
        // Mensaje privado recibido
        const contactId = "u:" + from.toLowerCase().replace(/\s+/g, "_")

        // Si no existe el contacto, crearlo
        if (!state.contacts.find((c) => c.id === contactId)) {
          addContact(from)
          renderContacts()
        }

        // Agregar el mensaje
        state.messages[contactId] = state.messages[contactId] || []
        state.messages[contactId].push({ from, text: content, time: Date.now() })

        // Si el chat activo es con este contacto, actualizar la vista
        if (state.activeChat === contactId) {
          renderMessages(contactId)
        }

        console.log(`✅ Mensaje privado recibido de ${from}`)
      }
    } else if (notification.includes("type:audio")) {
      // 🎵 Procesar notificación de audio privado
      const parts = notification.split("|")
      const audioData = {}
      parts.forEach((part) => {
        const [key, value] = part.split(":")
        if (key && value) audioData[key] = value
      })

      const from = audioData.from
      const to = audioData.to
      const audioId = audioData.audio_id

      if (from && to === state.user && audioId) {
        const contactId = "u:" + from.toLowerCase().replace(/\s+/g, "_")

        // Si no existe el contacto, crearlo
        if (!state.contacts.find((c) => c.id === contactId)) {
          addContact(from)
          renderContacts()
        }

        // Agregar el audio como mensaje
        const messageObj = {
          from,
          text: "[🎵 Nota de voz]",
          time: Date.now(),
          isAudio: true,
          audioId: audioId,
          audioData: null, // Se cargará cuando se renderice
        }
        
        state.messages[contactId] = state.messages[contactId] || []
        state.messages[contactId].push(messageObj)

        // Si el chat activo es con este contacto, actualizar la vista
        if (state.activeChat === contactId) {
          renderMessages(contactId)
        }

        console.log(`✅ Nota de voz recibida de ${from}`)
      }
    } else if (notification.includes("type:group_audio")) {
      // 🎵 Procesar notificación de audio en grupo
      const parts = notification.split("|")
      const audioData = {}
      parts.forEach((part) => {
        const [key, value] = part.split(":")
        if (key && value) audioData[key] = value
      })

      const from = audioData.from
      const group = audioData.group
      const audioId = audioData.audio_id

      if (from && group && audioId) {
        const groupId = "g:" + group.toLowerCase().replace(/\s+/g, "_")

        // Agregar el audio como mensaje (sin audio_data por ahora)
        const messageObj = {
          from,
          text: "[🎵 Nota de voz]",
          time: Date.now(),
          isAudio: true,
          audioId: audioId,
          audioData: null, // Se cargará cuando se renderice
        }
        
        state.messages[groupId] = state.messages[groupId] || []
        state.messages[groupId].push(messageObj)

        // Si el chat activo es este grupo, actualizar
        if (state.activeChat === groupId) {
          renderMessages(groupId)
        }

        console.log(`✅ Nota de voz recibida en grupo ${group} de ${from}`)
      }
    } else if (notification.includes("type:group_message")) {
      const parts = notification.split("|")
      const messageData = {}
      parts.forEach((part) => {
        const [key, value] = part.split(":")
        if (key && value) messageData[key] = value
      })

      const from = messageData.from
      const group = messageData.group
      const content = messageData.content

      if (from && content && group) {
        const groupId = "g:" + group.toLowerCase().replace(/\s+/g, "_")

        // Agregar el mensaje al grupo
        state.messages[groupId] = state.messages[groupId] || []
        state.messages[groupId].push({ from, text: content, time: Date.now() })

        // Si el chat activo es este grupo, actualizar
        if (state.activeChat === groupId) {
          renderMessages(groupId)
        }

        console.log(`✅ Mensaje de grupo recibido en ${group}`)
      }
    } else if (notification.includes("type:join_group_success")) {
      // El usuario actual se unió a un grupo exitosamente
      const parts = notification.split("|")
      const data = {}
      parts.forEach((part) => {
        const [key, value] = part.split(":")
        if (key && value) data[key] = value
      })

      const group = data.group
      if (group) {
        const groupId = "g:" + group.toLowerCase().replace(/\s+/g, "_")
        if (!state.contacts.find((c) => c.id === groupId)) {
          const newGroup = {
            id: groupId,
            name: group,
            type: "group",
            members: [],
          }
          state.contacts.push(newGroup)
          state.messages[groupId] = []
          renderContacts()
          console.log(`✅ Te uniste al grupo ${group}`)
        }
      }
    } else if (notification.includes("type:group_created")) {
      // Se creó un nuevo grupo - procesar miembros
      const parts = notification.split("|")
      const groupData = {}
      parts.forEach((part) => {
        const [key, value] = part.split(":")
        if (key && value) groupData[key] = value
      })

      const groupName = groupData.group_name
      const members = groupData.members ? groupData.members.split(",").map(m => m.trim()).filter(Boolean) : []

      if (groupName) {
        const groupId = "g:" + groupName.toLowerCase().replace(/\s+/g, "_")
        const existingGroup = state.contacts.find((c) => c.id === groupId)
        
        if (existingGroup) {
          // Actualizar miembros del grupo existente
          existingGroup.members = members
          renderContacts()
          if (state.activeChat === groupId) {
            openChat(groupId)
          }
        } else {
          // Crear nuevo grupo
          const newGroup = {
            id: groupId,
            name: groupName,
            type: "group",
            members: members,
          }
          state.contacts.push(newGroup)
          state.messages[groupId] = []
          renderContacts()
        }
        
        console.log(`✅ Grupo ${groupName} procesado con ${members.length} miembros`)
      }
    } else if (notification.includes("type:system_message")) {
      // Procesar mensajes del sistema que podrían incluir eventos de grupo
      const content = notification.split("content:")[1] || ""
      
      if (content.includes("se unió al grupo")) {
        // Alguien se unió a un grupo
        console.log("🔔 Notificación:", content)
      } else if (content.includes("creado por")) {
        // Se creó un nuevo grupo - agregar si no existe
        const match = content.match(/Grupo '([^']+)'/)
        if (match) {
          const groupName = match[1]
          const groupId = "g:" + groupName.toLowerCase().replace(/\s+/g, "_")
          
          if (!state.contacts.find((c) => c.id === groupId)) {
            const newGroup = {
              id: groupId,
              name: groupName,
              type: "group",
              members: [],
            }
            state.contacts.push(newGroup)
            state.messages[groupId] = []
            renderContacts()
            console.log(`✅ Nuevo grupo detectado: ${groupName}`)
          }
        }
      } else {
        console.log("🔔 Notificación del sistema:", content)
      }
    }
  }

  // ✅ Función para hacer polling de notificaciones
  let pollingInterval = null
  function startNotificationPolling(username) {
    if (pollingInterval) clearInterval(pollingInterval)

    pollingInterval = setInterval(async () => {
      try {
        // Obtener notificaciones
        const res = await fetch(`${API_URL}/notifications/${username}`)
        if (res.ok) {
          const data = await res.json()
          if (data.ok && data.notifications && data.notifications.length > 0) {
            data.notifications.forEach((notif) => processNotification(notif))
          }
        }

        // Cada 5 segundos, también actualizar la lista de grupos como fallback
        if (Date.now() % 5000 < 1000) {
          try {
            const groupsData = await apiGetGroups(username)
            let groups = []

            if (Array.isArray(groupsData)) {
              groups = groupsData
            } else if (groupsData && groupsData.ok && groupsData.java_response) {
              // La respuesta es: type:groups_list|groups:Amigos,pedrito
              // Necesitamos extraer solo la parte de "groups:"
              const response = String(groupsData.java_response)
              const match = response.match(/groups:([^|]*)/)
              if (match && match[1]) {
                groups = match[1]
                  .split(",")
                  .map((s) => s.trim())
                  .filter(Boolean)
              }
            }

            // Agregar nuevos grupos que no estén en la lista
            groups.forEach((groupName) => {
              const groupStr = String(groupName).trim()
              if (groupStr && !groupStr.includes("type:")) {
                const groupId = "g:" + groupStr.toLowerCase().replace(/\s+/g, "_")
                if (!state.contacts.find((c) => c.id === groupId)) {
                  const group = {
                    id: groupId,
                    name: groupStr,
                    type: "group",
                    members: [],
                  }
                  state.contacts.push(group)
                  state.messages[groupId] = []
                  renderContacts()
                  console.log(`✅ Grupo sincronizado: ${groupStr}`)
                }
              }
            })
          } catch (err) {
            // Ignorar errores de sincronización de grupos
          }
        }
      } catch (err) {
        console.error("Error obteniendo notificaciones:", err)
      }
    }, 1000) // Polling cada 1 segundo
  }

  function escapeHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;")
  }

  function formatTime(ts) {
    const d = new Date(ts)
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
  }

  loginForm.addEventListener("submit", async (e) => {
    e.preventDefault()
    e.stopPropagation()
    console.log("Formulario de login enviado")

    const name = usernameInput.value.trim()
    console.log("Nombre ingresado:", name)

    if (!name) {
      loginStatus.textContent = "Ingresa un nombre válido."
      loginStatus.style.color = "#e74c3c"
      return
    }

    loginStatus.textContent = "Conectando..."
    loginStatus.style.color = "#3498db"

    try {
      console.log(`Intentando conectar a ${API_URL}/login`)
      const response = await apiLogin(name)
      console.log("Respuesta del login:", response)

      state.user = name
      loginStatus.textContent = `Bienvenido, ${name}`
      loginStatus.style.color = "#27ae60"
      contactsSection.classList.remove("hidden")
      usernameInput.disabled = true
      loginForm.querySelector("button").disabled = true

      const btnNewContact = document.getElementById("btn-new-contact")
      if (btnNewContact) {
        btnNewContact.style.display = "block"
      }

      try {
        const groupsData = await apiGetGroups(name)
        let groups = []

        if (Array.isArray(groupsData)) {
          groups = groupsData
        } else if (groupsData && groupsData.ok && groupsData.java_response) {
          // La respuesta es: type:groups_list|groups:Amigos,pedrito
          // Necesitamos extraer solo la parte de "groups:"
          const response = String(groupsData.java_response)
          const match = response.match(/groups:([^|]*)/)
          if (match && match[1]) {
            groups = match[1]
              .split(",")
              .map((s) => s.trim())
              .filter(Boolean)
          }
        }

        groups.forEach((groupName) => {
          const groupStr = String(groupName).trim()
          if (groupStr && !groupStr.includes("type:")) {
            const group = {
              id: "g:" + groupStr.toLowerCase().replace(/\s+/g, "_"),
              name: groupStr,
              type: "group",
              members: [],
            }
            if (!state.contacts.find((c) => c.id === group.id)) {
              state.contacts.push(group)
              state.messages[group.id] = []
            }
          }
        })
        renderContacts()

        // 🚀 Iniciar polling de notificaciones
        startNotificationPolling(name)
      } catch (err) {
        console.warn("No se pudieron cargar los grupos (no crítico):", err)
      }
    } catch (err) {
      loginStatus.textContent = "Error: " + err.message
      loginStatus.style.color = "#e74c3c"
      console.error("Login error:", err)
    }
  })

  function addContact(name) {
    const id = "u:" + name.toLowerCase().replace(/\s+/g, "_")
    const contact = { id, name, type: "user", members: [name] }
    state.contacts.push(contact)
    state.messages[id] = state.messages[id] || []
  }

  // Group creation
  btnCreateGroup.addEventListener("click", () => {
    createGroupForm.classList.remove("hidden")
    btnCreateGroup.disabled = true
  })

  cancelCreate.addEventListener("click", () => {
    createGroupForm.classList.add("hidden")
    btnCreateGroup.disabled = false
    groupNameInput.value = ""
    groupMembersInput.value = ""
  })

  createGroupForm.addEventListener("submit", async (e) => {
    e.preventDefault()
    const gname = groupNameInput.value.trim()
    const membersRaw = groupMembersInput.value.trim()
    if (!gname || !membersRaw) return
    const members = membersRaw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)

    try {
      // Agregar el creador a la lista de miembros
      const allMembers = [state.user, ...members.filter(m => m !== state.user)]
      
      // 1. Crear el grupo con todos los miembros
      await apiCreateGroup(gname, state.user, allMembers)
      
      const id = "g:" + gname.toLowerCase().replace(/\s+/g, "_")
      const group = { id, name: gname, type: "group", members: allMembers }
      state.contacts.push(group)
      state.messages[id] = state.messages[id] || []
      renderContacts()
      createGroupForm.classList.add("hidden")
      btnCreateGroup.disabled = false
      groupNameInput.value = ""
      groupMembersInput.value = ""
    } catch (err) {
      console.error("Error al crear grupo:", err)
      alert("No se pudo crear el grupo: " + err.message)
    }
  })

  // 🎤 Botón de audio
  if (btnAudioMsg) {
    btnAudioMsg.addEventListener("click", () => {
      if (!state.activeChat) {
        alert("Selecciona un chat primero")
        return
      }
      if (state.isRecording) {
        stopAudioRecording()
      } else {
        startAudioRecording()
      }
    })
  }

  // Message sending
  messageForm.addEventListener("submit", async (e) => {
    e.preventDefault()
    const text = messageInput.value.trim()
    if (!text || !state.activeChat) return
    const chatId = state.activeChat
    const chat = state.contacts.find((c) => c.id === chatId)
    if (!chat) return

    try {
      // Detectar si es un grupo o un contacto
      if (chat.type === "group") {
        // Mensaje de grupo
        await apiSendGroupMessage(state.user, chat.name, text)
      } else {
        // Mensaje privado
        await apiSendMessage(state.user, chat.name, text)
      }
      
      const msg = { from: state.user, text, time: Date.now() }
      state.messages[chatId] = state.messages[chatId] || []
      state.messages[chatId].push(msg)
      messageInput.value = ""
      renderMessages(chatId)
    } catch (err) {
      console.error("Error al enviar mensaje:", err)
      alert("No se pudo enviar el mensaje: " + err.message)
    }
  })

  // Helper: open first chat when available
  function openFirstIfNone() {
    if (!state.activeChat && state.contacts.length > 0) {
      openChat(state.contacts[0].id)
    }
  }

  // Expose a simple API to add contacts manually via button
  const btnNewContact = document.getElementById("btn-new-contact")
  if (btnNewContact) {
    btnNewContact.addEventListener("click", () => {
      const name = prompt("Nombre del nuevo contacto:")
      if (name) {
        addContact(name.trim())
        renderContacts()
        openFirstIfNone()
      }
    })
  }
})
