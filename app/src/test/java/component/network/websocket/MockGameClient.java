package component.network.websocket;

import java.util.function.Consumer;

public class MockGameClient extends GameClient {

    public boolean connected = false;
    public boolean disconnected = false;
    public Message lastSent = null;
    Runnable onConnected = getOnConnected();
    Runnable onDisconnected = getOnDisconnected();

    public MockGameClient() {
        super(null);
    }

    @Override
    public void connect(String uri) {
        connected = true;
        if (onConnected != null) onConnected.run();
    }

    @Override
    public void disconnect() {
        disconnected = true;
        if (onDisconnected != null) onDisconnected.run();
    }

    @Override
    public void send(Message msg) {
        this.lastSent = msg;
    }
}
