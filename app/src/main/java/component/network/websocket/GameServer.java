package component.network.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/game")
public class GameServer {

    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("[Server] Client connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String msg, Session sender) {
        System.out.println("[Server] 메시지 수신 from " + sender.getId() + ": " + msg);
        System.out.println("[Server] 현재 세션 수: " + sessions.size());

        // 받은 메시지를 다른 세션에게 중계
        int relayed = 0;
        for (Session s : sessions) {
            if (!s.equals(sender) && s.isOpen()) {
                System.out.println("[Server] 중계 → " + s.getId());
                s.getAsyncRemote().sendText(msg);
                relayed++;
            }
        }
        System.out.println("[Server] " + relayed + "개 세션에 중계 완료");
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

    // 서버 시작
    public static void startServer(int port) {
        new Thread(() -> {
            Server server = new Server("localhost", port, "/", null, GameServer.class);
            try {
                server.start();
                System.out.println("[WebSocket] Server started on ws://localhost:" + port + "/game");
                System.out.println("Press Enter to stop server...");
                System.in.read();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    server.stop();
                    System.out.println("[WebSocket] Server stopped");
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
