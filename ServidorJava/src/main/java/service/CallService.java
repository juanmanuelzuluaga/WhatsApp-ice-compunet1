package service;

import network.UDPConnection;
import network.UDPAudioListener;
import util.AudioRecorder;
import util.AudioPlayer;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class CallService implements UDPAudioListener {
    private AudioRecorder audioRecorder;
    private AudioPlayer audioPlayer;
    private UDPConnection udpConnection;
    private CallServiceListener listener;

    private InetAddress peerAddress;
    private int peerPort;

    private Map<String, CallSession> activeCalls = new HashMap<>();
    private boolean isInCall = false;
    private String currentCallId;
    private String currentTarget;

    public enum CallState {
        IDLE, CALLING, IN_CALL, ENDING
    }

    private CallState currentState = CallState.IDLE;

    public interface CallServiceListener {
        void onCallServiceInitialized(int udpPort);
        void onCallStarted(String callId, String target);
        void onCallEnded(String callId);
        void onCallError(String message);
        void onAudioPacketReceived(String callId);
    }

    public CallService(int udpPort, CallServiceListener listener) throws SocketException {
        this.audioRecorder = new AudioRecorder();
        this.audioPlayer = new AudioPlayer();
        this.listener = listener;
        this.udpConnection = new UDPConnection(udpPort, this);
        this.udpConnection.startListening();

        if (listener != null) {
            listener.onCallServiceInitialized(udpPort);
        }
    }

    public void setPeerAddress(String host, int port) throws UnknownHostException {
        this.peerAddress = InetAddress.getByName(host);
        this.peerPort = port;
        System.out.println("[CallService] Peer configurado: " + host + ":" + port);
    }

    public boolean startCall(String callId, String targetUser) {
        if (isInCall) {
            if (listener != null) {
                listener.onCallError("Ya hay una llamada activa: " + currentCallId);
            }
            return false;
        }

        if (peerAddress == null) {
            if (listener != null) {
                listener.onCallError("Dirección del peer no configurada");
            }
            return false;
        }

        try {
            currentCallId = callId;
            currentTarget = targetUser;
            currentState = CallState.CALLING;
            isInCall = true;

            CallSession session = new CallSession(callId, targetUser);
            activeCalls.put(callId, session);

            System.out.println("[CallService] Iniciando llamada con " + targetUser);
            System.out.println("   Local UDP: " + udpConnection.getLocalPort());
            System.out.println("   Peer UDP: " + peerAddress.getHostAddress() + ":" + peerPort);

            audioPlayer.startPlayingForCall();
            System.out.println("[CallService] Reproducción iniciada");

            audioRecorder.startRecordingForCall(audioData -> {
                sendAudioPacketToPeer(audioData);
            });
            System.out.println("[CallService] Grabación iniciada");

            currentState = CallState.IN_CALL;

            if (listener != null) {
                listener.onCallStarted(callId, targetUser);
            }

            return true;

        } catch (Exception e) {
            currentState = CallState.IDLE;
            isInCall = false;
            if (listener != null) {
                listener.onCallError("Error iniciando llamada: " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
    }

    public void endCall(String callId) {
        if (!isInCall || !callId.equals(currentCallId)) {
            if (listener != null) {
                listener.onCallError("No hay llamada activa con ID: " + callId);
            }
            return;
        }

        try {
            currentState = CallState.ENDING;
            audioRecorder.stopRecording();
            audioPlayer.stopPlaying();
            activeCalls.remove(callId);

            String endedCallId = currentCallId;
            isInCall = false;
            currentCallId = null;
            currentTarget = null;
            currentState = CallState.IDLE;

            if (listener != null) {
                listener.onCallEnded(endedCallId);
            }

        } catch (Exception e) {
            if (listener != null) {
                listener.onCallError("Error terminando llamada: " + e.getMessage());
            }
        }
    }

    private void sendAudioPacketToPeer(byte[] audioData) {
        if (udpConnection != null && isInCall && peerAddress != null) {
            try {
                udpConnection.sendAudio(audioData, peerAddress, peerPort);
                if (System.currentTimeMillis() % 100 < 10) {
                    System.out.println("[CallService] Enviando audio: " + audioData.length + " bytes a " +
                            peerAddress.getHostAddress() + ":" + peerPort);
                }
            } catch (Exception e) {
                System.err.println("[CallService] Error enviando audio: " + e.getMessage());
            }
        }
    }

    @Override
    public void onAudioReceived(byte[] audioData, InetAddress from, int port) {
        if (isInCall && audioPlayer != null) {
            System.out.println("[CallService] Audio recibido: " + audioData.length + " bytes desde " +
                    from.getHostAddress() + ":" + port);
            audioPlayer.addAudioData(audioData);
            if (listener != null && currentCallId != null) {
                listener.onAudioPacketReceived(currentCallId);
            }
        } else {
            System.out.println("[CallService] Audio recibido pero no en llamada o player nulo");
        }
    }

    public void cleanup() {
        if (isInCall) {
            endCall(currentCallId);
        }
        if (udpConnection != null) {
            udpConnection.close();
        }
    }

    public boolean isInCall() {
        return isInCall;
    }

    public CallState getCurrentState() {
        return currentState;
    }

    public String getCurrentCallId() {
        return currentCallId;
    }

    public String getCurrentTarget() {
        return currentTarget;
    }

    public int getUDPPort() {
        return udpConnection != null ? udpConnection.getLocalPort() : -1;
    }

    private static class CallSession {
        String callId;
        String targetUser;
        long startTime;

        public CallSession(String callId, String targetUser) {
            this.callId = callId;
            this.targetUser = targetUser;
            this.startTime = System.currentTimeMillis();
        }
    }
}
