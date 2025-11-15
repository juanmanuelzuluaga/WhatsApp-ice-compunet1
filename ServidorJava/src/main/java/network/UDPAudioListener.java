package network;

import java.net.InetAddress;

public interface UDPAudioListener {
    void onAudioReceived(byte[] audioData, InetAddress from, int port);
}
