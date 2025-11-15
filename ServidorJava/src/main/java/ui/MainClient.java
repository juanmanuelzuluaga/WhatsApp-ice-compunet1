package ui;

import model.AudioMessage;
import network.TCPConnection;
import network.TCPConnectionListener;
import service.CallService;
import util.AudioPlayer;
import util.AudioRecorder;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class MainClient implements TCPConnectionListener, CallService.CallServiceListener {

    private TCPConnection connection;
    private CallService callService;
    private AudioRecorder audioRecorder;
    private AudioPlayer audioPlayer;
    private BufferedReader console;
    private String username;

    private volatile boolean loginResponseReceived = false;
    private volatile boolean isLoggedIn = false;

    private volatile boolean waitingForCallResponse = false;
    private volatile String pendingCallFrom = null;
    private volatile String pendingCallTarget = null;
    private volatile String pendingCallerIp = null;
    private volatile int pendingCallerUdpPort = 0;
    private volatile boolean pendingIsGroup = false;

    public static void main(String[] args) {
        String serverIP = "127.0.0.1";
        int serverPort = 5000;
        if (args.length >= 2) {
            serverIP = args[0];
            serverPort = Integer.parseInt(args[1]);
        }
        new MainClient(serverIP, serverPort);
    }

    private MainClient(String serverIP, int serverPort) {
        try {
            console = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("=== CHAT MULTIMEDIA - Cliente ===");
            initializeAudioSystem();
            connection = new TCPConnection(this, serverIP, serverPort);
            if (!performLogin()) {
                System.out.println("No se pudo iniciar sesión.");
                cleanup();
                return;
            }
            mainMenu();
        } catch (IOException e) {
            System.err.println("Error al conectar: " + e.getMessage());
        }
    }

    private void initializeAudioSystem() {
        try {
            int udpPort = 6000 + new Random().nextInt(1000);
            callService = new CallService(udpPort, this);
            audioRecorder = new AudioRecorder();
            audioPlayer = new AudioPlayer();
            System.out.println("Sistema de audio inicializado. Puerto UDP: " + udpPort);
        } catch (Exception e) {
            System.err.println("Error al iniciar audio: " + e.getMessage());
        }
    }

    private boolean performLogin() throws IOException {
        System.out.print("Tu nombre de usuario: ");
        username = console.readLine().trim();
        if (username.isEmpty()) return false;

        connection.sendObject("type:login|username:" + username);
        System.out.print("Conectando...");

        long start = System.currentTimeMillis();
        while (!loginResponseReceived && (System.currentTimeMillis() - start < 5000)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println();

        if (isLoggedIn) {
            System.out.println("¡Bienvenido, " + username + "!");
            return true;
        } else {
            System.out.println("Error: servidor no respondió o nombre en uso.");
            return false;
        }
    }

    private void mainMenu() {
        while (isLoggedIn) {
            try {
                printMenu();
                String opt = console.readLine().trim().toLowerCase();

                if (waitingForCallResponse) {
                    if (opt.equals("a") || opt.equals("aceptar")) {
                        handlePendingCall();
                        continue;
                    } else if (opt.equals("r") || opt.equals("rechazar")) {
                        rejectPendingCall();
                        continue;
                    }
                }

                if (callService.isInCall()) {
                    if (opt.equals("c") || opt.equals("colgar")) {
                        endCurrentCall();
                        continue;
                    }
                }

                switch (opt) {
                    case "1" -> sendTextMessage();
                    case "2" -> sendVoiceNote();
                    case "3" -> makeCall();
                    case "4" -> createGroup();
                    case "5" -> joinGroup();
                    case "6" -> viewHistory();
                    case "7" -> viewOnlineUsers();
                    case "8" -> viewGroups();
                    case "9" -> playReceivedAudios();
                    case "0" -> { logout(); return; }
                    default -> System.out.println("Opción inválida.");
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("\n" + "=".repeat(40));
        System.out.println(" MENU - Usuario: " + username);

        if (callService.isInCall()) {
            System.out.println(" [EN LLAMADA CON: " + callService.getCurrentTarget() + "]");
        }
        if (waitingForCallResponse) {
            System.out.println(" [LLAMADA ENTRANTE DE: " + pendingCallFrom + "]");
        }

        System.out.println("=".repeat(40));
        System.out.println("1. Mensaje de texto");
        System.out.println("2. Nota de voz");
        System.out.println("3. Llamada");
        System.out.println("4. Crear grupo");
        System.out.println("5. Unirse a grupo");
        System.out.println("6. Ver historial");
        System.out.println("7. Usuarios en línea");
        System.out.println("8. Grupos");
        System.out.println("9. Reproducir audios guardados");

        if (waitingForCallResponse) {
            System.out.println("\n--- LLAMADA PENDIENTE ---");
            System.out.println("a. Aceptar llamada");
            System.out.println("r. Rechazar llamada");
        }
        if (callService.isInCall()) {
            System.out.println("c. Colgar llamada");
        }

        System.out.println("\n0. Salir");
        System.out.print("Opción: ");
    }

    private void sendTextMessage() throws IOException {
        System.out.print("¿Privado (1) o grupo (2)? ");
        String type = console.readLine();
        System.out.print("Destinatario: ");
        String to = console.readLine().trim();
        System.out.print("Mensaje: ");
        String msg = console.readLine();

        if ("1".equals(type)) {
            connection.sendObject(String.format("type:private_message|from:%s|to:%s|content:%s", username, to, msg));
        } else if ("2".equals(type)) {
            connection.sendObject(String.format("type:group_message|from:%s|group:%s|content:%s", username, to, msg));
        }
        System.out.println("Mensaje enviado.");
    }

    private void sendVoiceNote() throws IOException {
        System.out.print("¿Privado (1) o grupo (2)? ");
        String type = console.readLine();
        System.out.print("Destinatario: ");
        String to = console.readLine().trim();
        System.out.print("Duración (seg): ");
        int dur = Integer.parseInt(console.readLine());

        String noteId = "voice_" + System.currentTimeMillis();
        audioRecorder.recordVoiceNote(noteId, dur);
        File file = new File("data/audio/" + noteId + ".wav");
        if (!file.exists()) {
            System.out.println("No se generó el archivo de audio.");
            return;
        }

        byte[] data = Files.readAllBytes(file.toPath());
        boolean isGroup = "2".equals(type);
        AudioMessage am = new AudioMessage(username, to, isGroup, data, dur);
        connection.sendObject(am);
        System.out.println("Nota de voz enviada.");
    }

    private void makeCall() throws IOException {
        if (waitingForCallResponse) {
            System.out.println("Tienes una llamada entrante pendiente. Acéptala o recházala primero.");
            return;
        }

        if (callService.isInCall()) {
            System.out.println("Ya estás en una llamada. Escribe 'colgar' para terminarla.");
            return;
        }

        System.out.print("¿Llamar a usuario (1) o grupo (2)? ");
        String type = console.readLine();
        System.out.print("Destinatario: ");
        String to = console.readLine().trim();

        boolean isGroup = "2".equals(type);
        int localUdpPort = callService.getUDPPort();

        connection.sendObject(String.format(
                "type:call_start|from:%s|to:%s|isGroup:%s|udpPort:%d",
                username, to, isGroup, localUdpPort
        ));
        System.out.println("Llamando a " + to + "...");
        System.out.println("Esperando que acepte la llamada...");
    }

    private void handlePendingCall() {
        if (!waitingForCallResponse) {
            System.out.println("No hay llamadas pendientes.");
            return;
        }

        try {
            System.out.println("Aceptando llamada de " + pendingCallFrom + "...");

            try {
                callService.setPeerAddress(pendingCallerIp, pendingCallerUdpPort);
            } catch (Exception e) {
                System.out.println("Error configurando UDP: " + e.getMessage());
                waitingForCallResponse = false;
                return;
            }

            String callId = "call_" + System.currentTimeMillis();
            if (callService.startCall(callId, pendingCallFrom)) {
                System.out.println("Llamada activa con " + pendingCallFrom + ". Habla ahora.");
                System.out.println("Escribe 'colgar' en el menú para terminar la llamada.");

                int myUdpPort = callService.getUDPPort();
                connection.sendObject(String.format(
                        "type:call_accept|from:%s|to:%s|udpPort:%d",
                        username, pendingCallFrom, myUdpPort
                ));

                waitingForCallResponse = false;
            } else {
                System.out.println("No se pudo iniciar la llamada.");
                waitingForCallResponse = false;
            }
        } catch (Exception e) {
            System.err.println("Error aceptando llamada: " + e.getMessage());
            waitingForCallResponse = false;
        }
    }

    private void rejectPendingCall() {
        if (!waitingForCallResponse) {
            System.out.println("No hay llamadas pendientes.");
            return;
        }

        System.out.println("Llamada de " + pendingCallFrom + " rechazada.");
        waitingForCallResponse = false;
        pendingCallFrom = null;
    }

    private void endCurrentCall() {
        if (callService.isInCall()) {
            String callId = callService.getCurrentCallId();
            callService.endCall(callId);
            connection.sendObject(String.format("type:call_end|from:%s|callId:%s", username, callId));
            System.out.println("Llamada finalizada.");
        }
    }

    private void createGroup() throws IOException {
        System.out.print("Nombre del grupo: ");
        String name = console.readLine().trim();
        connection.sendObject(String.format("type:create_group|group_name:%s|creator:%s", name, username));
        System.out.println("Solicitud enviada.");
    }

    private void joinGroup() throws IOException {
        System.out.print("Nombre del grupo: ");
        String name = console.readLine().trim();
        connection.sendObject(String.format("type:join_group|group_name:%s|username:%s", name, username));
        System.out.println("Solicitud enviada.");
    }

    private void viewHistory() throws IOException {
        System.out.print("Usuario o grupo: ");
        String target = console.readLine().trim();
        connection.sendObject(String.format("type:get_history|username:%s|target:%s", username, target));
        System.out.println("Nota: consultar el directorio con los txt de los chats respectivos");
    }

    private void viewOnlineUsers() {
        connection.sendObject("type:get_online_users|username:" + username);
    }

    private void viewGroups() {
        connection.sendObject("type:get_groups|username:" + username);
    }

    private void playReceivedAudios() {
        try {
            File audioDir = new File("data/audio");
            if (!audioDir.exists() || !audioDir.isDirectory()) {
                System.out.println("No hay carpeta de audios. Creando...");
                audioDir.mkdirs();
                System.out.println("No hay audios guardados aún.");
                return;
            }

            File[] audioFiles = audioDir.listFiles((dir, name) -> name.endsWith(".wav"));

            if (audioFiles == null || audioFiles.length == 0) {
                System.out.println("No hay audios para reproducir.");
                return;
            }

            System.out.println("\n" + "=".repeat(50));
            System.out.println("AUDIOS DISPONIBLES");
            System.out.println("=".repeat(50));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            for (int i = 0; i < audioFiles.length; i++) {
                String fileName = audioFiles[i].getName();
                long sizeKB = audioFiles[i].length() / 1024;
                String date = sdf.format(new Date(audioFiles[i].lastModified()));

                String type = fileName.startsWith("received_") ? "[Recibido]" :
                        fileName.startsWith("voice_") ? "[Enviado]" : "[Audio]";

                System.out.printf("%d. %s %s (%.1f KB) - %s\n",
                        i + 1, type, fileName, (double)sizeKB, date);
            }

            System.out.println("0. Volver al menú");
            System.out.print("\nSelecciona un audio para reproducir: ");

            String choice = console.readLine().trim();

            if (choice.equals("0")) {
                return;
            }

            try {
                int index = Integer.parseInt(choice);
                if (index < 1 || index > audioFiles.length) {
                    System.out.println("Opción inválida.");
                    return;
                }

                File selectedFile = audioFiles[index - 1];
                String fileNameWithoutExt = selectedFile.getName().replace(".wav", "");

                System.out.println("\nReproduciendo: " + selectedFile.getName());

                audioPlayer.playVoiceNote(fileNameWithoutExt);

            } catch (NumberFormatException e) {
                System.out.println("Debes ingresar un número.");
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logout() {
        System.out.println("Cerrando sesión...");
        connection.sendObject("type:logout|username:" + username);
        cleanup();
        isLoggedIn = false;
        System.out.println("¡Hasta pronto!");
    }

    private void cleanup() {
        if (callService != null) callService.cleanup();
        if (audioRecorder != null) audioRecorder.stopRecording();
        if (audioPlayer != null) audioPlayer.stopPlaying();
    }

    @Override
    public void onConnectionReady(TCPConnection conn) {
        //System.out.println("Conectado al servidor.");
    }

    @Override
    public void onReceiveObject(TCPConnection conn, Object obj) {
        if (obj instanceof String str) {
            processServerMessage(str);
        } else if (obj instanceof AudioMessage am) {
            handleReceivedAudioMessage(am);
        }
    }

    @Override
    public void onDisconnect(TCPConnection conn) {
        cleanup();
        isLoggedIn = false;
        System.out.println("\nDesconectado del servidor.");
    }

    @Override
    public void onException(TCPConnection conn, Exception e) {
        System.err.println("Error de conexión: " + e.getMessage());
    }

    @Override
    public void onCallServiceInitialized(int udpPort) {}

    @Override
    public void onCallStarted(String callId, String target) {}

    @Override
    public void onCallEnded(String callId) {}

    @Override
    public void onCallError(String msg) {
        System.err.println("Error en llamada: " + msg);
    }

    @Override
    public void onAudioPacketReceived(String callId) {}

    private void handleReceivedAudioMessage(AudioMessage am) {
        System.out.println("\n\nNota de voz recibida de: " + am.getFrom());
        String fileName = "received_" + am.getId();
        try {
            audioPlayer.saveVoiceNote(am.getAudioData(), fileName);
            System.out.println("   Guardada como: " + fileName + ".wav");
        } catch (Exception e) {
            System.err.println("   Error al guardar audio: " + e.getMessage());
        }
    }

    private void processServerMessage(String msg) {
        String[] parts = msg.split("\\|", 2);
        if (parts.length == 0) return;
        String typePart = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";

        if (!typePart.startsWith("type:")) return;
        String type = typePart.substring(5);

        switch (type) {
            case "login_success" -> {
                loginResponseReceived = true;
                isLoggedIn = true;
            }
            case "login_error" -> {
                loginResponseReceived = true;
                System.out.println(getVal(payload, "message"));
            }
            case "private_message" ->
                    System.out.println("\n" + getVal(payload, "from") + ": " + getVal(payload, "content"));
            case "group_message" ->
                    System.out.println("\n[" + getVal(payload, "group") + "] " + getVal(payload, "from") + ": " + getVal(payload, "content"));
            case "incoming_call" -> {
                String from = getVal(payload, "from");
                boolean isGroup = "true".equals(getVal(payload, "isGroup"));
                String target = getVal(payload, "to");
                String callerIp = getVal(payload, "callerIp");
                String callerUdpPortStr = getVal(payload, "callerUdpPort");

                if (callerIp.isEmpty() || callerUdpPortStr.isEmpty()) {
                    System.out.println("Datos de conexión incompletos.");
                    return;
                }

                pendingCallFrom = from;
                pendingCallTarget = target;
                pendingCallerIp = callerIp;
                pendingCallerUdpPort = Integer.parseInt(callerUdpPortStr);
                pendingIsGroup = isGroup;
                waitingForCallResponse = true;

                System.out.println("\n" + "=".repeat(50));
                System.out.println("LLAMADA ENTRANTE DE " + from.toUpperCase());
                if (isGroup) {
                    System.out.println("   (Llamada grupal a: " + target + ")");
                }
                System.out.println("=".repeat(50));
                System.out.println("\nEscribe 'a' o 'aceptar' en el menú para aceptar");
                System.out.println("Escribe 'r' o 'rechazar' en el menú para rechazar\n");
            }
            case "call_accepted" -> {
                String from = getVal(payload, "from");
                String receiverIp = getVal(payload, "receiverIp");
                String receiverUdpPortStr = getVal(payload, "receiverUdpPort");

                if (receiverIp.isEmpty() || receiverUdpPortStr.isEmpty()) {
                    System.out.println("Datos de conexión del receptor incompletos.");
                    return;
                }

                int receiverUdpPort = Integer.parseInt(receiverUdpPortStr);
                System.out.println(from + " aceptó la llamada!");
                System.out.println("   Conectando a " + receiverIp + ":" + receiverUdpPort);

                try {
                    callService.setPeerAddress(receiverIp, receiverUdpPort);

                    String callId = "call_" + System.currentTimeMillis();
                    if (callService.startCall(callId, from)) {
                        System.out.println("Llamada activa con " + from + ". Habla ahora.");
                        System.out.println("   Escribe 'colgar' en el menú para terminar.");
                    } else {
                        System.out.println("No se pudo iniciar la llamada.");
                    }
                } catch (Exception e) {
                    System.out.println("Error iniciando llamada: " + e.getMessage());
                }
            }
            case "call_waiting" -> {
                System.out.println("Esperando respuesta...");
            }
            case "system_message" ->
                    System.out.println("\n[Sistema] " + getVal(payload, "content"));
            case "online_users" ->
                    System.out.println("Usuarios en línea: " + getVal(payload, "users"));
            case "groups_list" ->
                    System.out.println("Grupos: " + getVal(payload, "groups"));
            case "error" ->
                    System.out.println("\n[Error] " + getVal(payload, "message"));
        }
    }

    private String getVal(String payload, String key) {
        for (String pair : payload.split("\\|")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return "";
    }
}
