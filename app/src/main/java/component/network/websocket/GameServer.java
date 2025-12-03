package component.network.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/game")
public class GameServer {

    private static Server server; // 서버를 하나만 사용
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("[Server] Client connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String msg, Session sender) {

        for (Session s : sessions) {
            if (!s.equals(sender) && s.isOpen()) {
                s.getAsyncRemote().sendText(msg);
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("[Server] Client disconnected: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("[Server] Error on " + session.getId() + ": " + error.getMessage());
    }

    /** 서버 시작 */
    public static void startServer(int port) {
        Thread t = new Thread(() -> {
            try {
                // 모든 네트워크 인터페이스에서 수신
                server = new Server("0.0.0.0", port, "", null, GameServer.class);
                server.start();

                System.out.println("[WebSocket] Server started successfully!");
                System.out.println("[WebSocket] Local:  ws://localhost:" + port + "/game");
                System.out.println("[WebSocket] LAN:    ws://10.50.98.32:" + port + "/game");

                Thread.currentThread().join();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.setDaemon(false);
        t.start();
    }

    /** 서버 종료 */
    public static void stopServer() {
        try {
            if (server != null) {
                System.out.println("[WebSocket] Server stopping...");
                server.stop();
                System.out.println("[WebSocket] Server stopped.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
