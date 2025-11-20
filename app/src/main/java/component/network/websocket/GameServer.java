package component.network.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/game")
public class GameServer {

    private static Server server; // ← 이제 여기만 사용
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("[Server] Client connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String msg, Session sender) {
        System.out.println("[Server] recv from " + sender.getId() + ": " + msg);

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

    /** ★ 서버 시작 (Daemon Thread + static server 사용) */
    public static void startServer(int port) {
        Thread t = new Thread(() -> {
            try {
                server = new Server("localhost", port, "/", null, GameServer.class);
                server.start();
                System.out.println("[WebSocket] Server started on ws://localhost:" + port + "/game");

                // ★ System.in.read() 제거 → 서버가 막히지 않음
                // 서버는 stopServer()가 호출될 때까지 작동함

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.setDaemon(true); // ★ JVM 종료를 막지 않음
        t.start();
    }

    /** ★ 서버 종료 */
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
