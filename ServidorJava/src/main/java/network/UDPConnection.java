package network;

import java.net.*;
import java.io.IOException;

public class UDPConnection {
    private static final int BUFFER_SIZE = 2048;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private boolean running;
    private Thread receiverThread;
    private UDPAudioListener listener;

    public UDPConnection(int localPort, UDPAudioListener listener) throws SocketException {
        this.socket = new DatagramSocket(localPort);
        this.listener = listener;
        this.running = false;
    }

    public void setServerAddress(String host, int port) throws UnknownHostException {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
    }

    public void sendAudio(byte[] audioData, InetAddress address, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(audioData, audioData.length, address, port);
        socket.send(packet);
    }

    public void startListening() {
        if (running) return;

        running = true;
        receiverThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] audioData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, audioData, 0, packet.getLength());

                    if (listener != null) {
                        listener.onAudioReceived(audioData, packet.getAddress(), packet.getPort());
                    }
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[UDP] Error recibiendo: " + e.getMessage());
                    }
                }
            }
        });

        receiverThread.setDaemon(true);
        receiverThread.start();
    }


    public void sendAudioToServer(byte[] audioData) throws IOException {
        if (serverAddress == null) {
            throw new IOException("Dirección del servidor no configurada");
        }
        sendAudio(audioData, serverAddress, serverPort);
    }

    public void close() {
        running = false;
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }
}
