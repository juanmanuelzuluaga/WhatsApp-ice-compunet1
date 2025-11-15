package network;

public interface TCPConnectionListener {
    void onConnectionReady(TCPConnection connection);
    void onReceiveObject(TCPConnection connection, Object message);
    void onDisconnect(TCPConnection connection);
    void onException(TCPConnection connection, Exception e);
}
