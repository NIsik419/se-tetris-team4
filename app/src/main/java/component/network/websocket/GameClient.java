package component.network.websocket;

import javax.websocket.*;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

@ClientEndpoint
public class GameClient {

    private Session session;
    private final Consumer<Message> onMessageHandler;
    private Runnable onConnected;

    public GameClient(Consumer<Message> onMessageHandler) {
        this.onMessageHandler = onMessageHandler;
    }

    public void connect(String uri) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, URI.create(uri));
    }

    public void setOnConnected(Runnable callback) {
        this.onConnected = callback;
    }

    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("[Client] Connected to server: " + session.getId());

        // 연결 성공 콜백 실행
        if (onConnected != null) {
            javax.swing.SwingUtilities.invokeLater(onConnected);
        }
    }

    @OnMessage
    public void onMessage(String msg) {
        Message m = WebSocketUtil.fromJson(msg, Message.class);
        if (onMessageHandler != null)
            onMessageHandler.accept(m);
    }

    @OnClose
    public void onClose() {
        System.out.println("[Client] Disconnected from server.");
    }

    @OnError
    public void onError(Throwable t) {
        System.err.println("[Client] Error: " + t.getMessage());
    }

    public void send(Message msg) {
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(WebSocketUtil.toJson(msg));
        }
    }
}