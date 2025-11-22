package network;

import java.io.*;
import java.net.Socket;

/**
 * TCPConnection compatible con:
 * - clientes que usan ObjectInputStream/ObjectOutputStream (modo objeto)
 * - clientes que usan texto (BufferedReader / PrintWriter) (modo texto)
 *
 * Detecta el modo en el arranque y luego procesa mensajes acorde a ese modo.
 */
public class TCPConnection {
    private final Socket socket;

    // Streams para modo objeto
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;

    // Streams para modo texto (fallback)
    private BufferedReader reader;
    private PrintWriter writer;

    private TCPConnectionListener listener;
    private volatile boolean connected;
    private Thread listenerThread;

    // true si estamos en modo texto, false si estamos en modo objeto
    private boolean textMode = false;

    public TCPConnection(Socket socket, TCPConnectionListener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        this.connected = true;

        // ⚠️ Forzamos modo texto SIEMPRE para compatibilidad con Node
        this.textMode = true;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

        startListening();
    }


    // Constructor auxiliar que crea socket cliente (si se usa)
    public TCPConnection(TCPConnectionListener listener, String ip, int port) throws IOException {
        this(new Socket(ip, port), listener);
    }

    private void startListening() {
        listenerThread = new Thread(() -> {
            try {
                // Notificar que la conexión está lista
                if (listener != null) {
                    listener.onConnectionReady(this);
                }

                if (!textMode) {
                    // modo objeto: leer objetos
                    Object obj;
                    while (connected && (obj = objectInputStream.readObject()) != null) {
                        if (listener != null) listener.onReceiveObject(this, obj);
                    }
                } else {
                    // modo texto: leer líneas con soporte para líneas largas
                    BufferedReader largeLineReader = new BufferedReader(new InputStreamReader(socket.getInputStream()), 1024 * 1024); // 1MB buffer
                    String line;
                    while (connected && (line = largeLineReader.readLine()) != null) {
                        if (listener != null) listener.onReceiveObject(this, line);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected && listener != null) {
                    listener.onException(this, e);
                }
            } finally {
                disconnect(); // asegura limpieza y onDisconnect
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Envía un objeto o texto al cliente, respetando el modo del cliente
     * Si el cliente está en modo texto y el objeto no es String, lo convierte a String via toString()
     */
    public synchronized void sendObject(Serializable object) {
        if (!connected) return;

        try {
            if (!textMode) {
                // modo objeto: enviar como objeto serializado
                objectOutputStream.writeObject(object);
                objectOutputStream.flush();
            } else {
                // modo texto: si es String, enviar tal cual; si no, enviar toString()
                if (object instanceof String s) {
                    writer.println(s);
                } else {
                    // Puedes mejorar aquí y usar JSON si quieres más estructura
                    writer.println(object.toString());
                }
                writer.flush();
            }
        } catch (IOException e) {
            if (listener != null) listener.onException(this, e);
        }
    }

    public synchronized void disconnect() {
        connected = false;
        try {
            try { if (objectOutputStream != null) objectOutputStream.close(); } catch (IOException ignored) {}
            try { if (objectInputStream != null) objectInputStream.close(); } catch (IOException ignored) {}
            try { if (reader != null) reader.close(); } catch (IOException ignored) {}
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
            if (listener != null) listener.onDisconnect(this);
        } catch (Exception e) {
            if (listener != null) listener.onException(this, e instanceof Exception ? (Exception) e : new Exception(e));
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getRemoteAddress() {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }
        return "Disconnected";
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public String toString() {
        return getRemoteAddress();
    }
}
