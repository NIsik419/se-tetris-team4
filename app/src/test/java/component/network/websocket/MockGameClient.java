package component.network.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MockGameClient extends GameClient {

    public boolean connected = false;
    public boolean disconnected = false;
    public Message lastSent = null;
    public final List<Message> sent = new ArrayList<>();
    Runnable onConnected = getOnConnected();
    Runnable onDisconnected = getOnDisconnected();

    public MockGameClient() {
        super(msg -> {}); // parent constructor
    }

     @Override
    public void send(Message msg) {
        sent.add(msg);
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

   
}
