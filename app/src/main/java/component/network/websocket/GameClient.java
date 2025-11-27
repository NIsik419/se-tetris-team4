package component.network.websocket;

import javax.websocket.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.function.Consumer;

@ClientEndpoint
public class GameClient {

    private Session session;
    private final Consumer<Message> onMessageHandler;
    private Runnable onConnected;
    private Runnable onDisconnected;

    public GameClient(Consumer<Message> onMessageHandler) {
        this.onMessageHandler = onMessageHandler;
    }

    public void connect(String uri) throws Exception {
        System.out.println("[Client] ========================================");
        System.out.println("[Client] Attempting to connect to: " + uri);
        System.out.println("[Client] URL length: " + uri.length());
        
        // URL 정리 (혹시 모를 공백/특수문자 제거)
        uri = uri.trim();
        
        try {
            // URI 파싱 테스트
            URI parsedUri = URI.create(uri);
            String host = parsedUri.getHost();
            int port = parsedUri.getPort();
            String path = parsedUri.getPath();
            
            System.out.println("[Client] Parsed URI:");
            System.out.println("[Client]   Host: " + host);
            System.out.println("[Client]   Port: " + port);
            System.out.println("[Client]   Path: " + path);
            
            // DNS 해석 테스트
            InetAddress addr = InetAddress.getByName(host);
            System.out.println("[Client] DNS resolved: " + addr.getHostAddress());
            System.out.println("[Client] Is loopback: " + addr.isLoopbackAddress());
            System.out.println("[Client] Is reachable: " + addr.isReachable(5000));
            
            // 실제 연결 시도
            System.out.println("[Client] Starting WebSocket connection...");
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            
            // 타임아웃 설정 (옵션)
            container.setDefaultMaxSessionIdleTimeout(30000);
            
            container.connectToServer(this, parsedUri);
            System.out.println("[Client] ✓ Connection successful!");
            
        } catch (Exception e) {
            System.err.println("[Client] ✗ Connection failed!");
            System.err.println("[Client] Error type: " + e.getClass().getName());
            System.err.println("[Client] Error message: " + e.getMessage());
            e.printStackTrace();
            
            // 만약 실제 IP로 실패했다면 localhost로 재시도
            if (uri.contains("10.50.98.32")) {
                System.out.println("[Client] Retrying with localhost...");
                String localhostUri = uri.replace("10.50.98.32", "localhost");
                System.out.println("[Client] New URI: " + localhostUri);
                
                try {
                    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                    container.connectToServer(this, URI.create(localhostUri));
                    System.out.println("[Client] ✓ Connected via localhost!");
                    return;
                } catch (Exception e2) {
                    System.err.println("[Client] ✗ Localhost retry also failed!");
                    e2.printStackTrace();
                }
            }
            
            throw e;
        }
        
        System.out.println("[Client] ========================================");
    }

    public void setOnConnected(Runnable callback) {
        this.onConnected = callback;
    }
    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                System.out.println("[Client] Disconnected gracefully.");
            } catch (IOException e) {
                System.err.println("[Client] Error during disconnect: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("[Client] Connected to server: " + session.getId());
        System.out.println("[Client] Session max idle timeout: " + session.getMaxIdleTimeout());

        // 연결 성공 콜백 실행
        if (onConnected != null) {
            javax.swing.SwingUtilities.invokeLater(onConnected);
        }
    }

    @OnMessage
    public void onMessage(String msg) {
        System.out.println("[Client] Received message: " + msg.substring(0, Math.min(100, msg.length())));
        Message m = WebSocketUtil.fromJson(msg, Message.class);
        if (onMessageHandler != null)
            onMessageHandler.accept(m);
    }

    @OnClose
    public void onClose() {
        System.out.println("[Client] Disconnected from server.");
        this.session = null;
        if (onDisconnected != null) {
            System.out.println("[Client] Calling onDisconnected callback");
            javax.swing.SwingUtilities.invokeLater(onDisconnected);
        } else {
            System.out.println("[Client] WARNING: onDisconnected callback is null!");
        }
    }

    @OnError
    public void onError(Throwable t) {
        System.err.println("[Client] Error occurred!");
        System.err.println("[Client] Error type: " + t.getClass().getName());
        System.err.println("[Client] Error message: " + t.getMessage());
        t.printStackTrace();
        if (onDisconnected != null) {
            javax.swing.SwingUtilities.invokeLater(onDisconnected);
        }
    }

    public void send(Message msg) {
        if (session != null && session.isOpen()) {
            String json = WebSocketUtil.toJson(msg);
            System.out.println("[Client] Sending: " + json.substring(0, Math.min(100, json.length())));
            session.getAsyncRemote().sendText(json);
        } else {
            System.err.println("[Client] Cannot send - session is null or closed!");
        }
    }
    
    public boolean isConnected() {
        return session != null && session.isOpen();
    }
}