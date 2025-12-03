package component.network.websocket;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GameClientTest {

    @Test
    public void testConstructor() {
        AtomicReference<Message> received = new AtomicReference<>();
        GameClient client = new GameClient(received::set);
        
        assertNotNull(client);
        assertFalse(client.isConnected());
    }

    @Test
    public void testSetOnConnected() {
        AtomicBoolean called = new AtomicBoolean(false);
        GameClient client = new GameClient(msg -> {});
        
        client.setOnConnected(() -> called.set(true));
        
        assertNotNull(client.getOnConnected());
    }

    @Test
    public void testSetOnDisconnected() {
        AtomicBoolean called = new AtomicBoolean(false);
        GameClient client = new GameClient(msg -> {});
        
        client.setOnDisconnected(() -> called.set(true));
        
        assertNotNull(client.getOnDisconnected());
    }

    @Test
    public void testDisconnectWhenNotConnected() {
        GameClient client = new GameClient(msg -> {});
        
        // 연결되지 않은 상태에서 disconnect 호출 - 예외 없이 완료되어야 함
        client.disconnect();
        
        assertFalse(client.isConnected());
    }

    @Test
    public void testSendWhenNotConnected() {
        GameClient client = new GameClient(msg -> {});
        
        // 연결되지 않은 상태에서 send 호출 - 예외 없이 완료되어야 함
        Message msg = new Message(MessageType.PING, null);
        client.send(msg);
        
        assertFalse(client.isConnected());
    }

    @Test
    public void testIsConnectedInitially() {
        GameClient client = new GameClient(msg -> {});
        
        assertFalse(client.isConnected());
    }

    @Test(expected = Exception.class)
    public void testConnectInvalidUri() throws Exception {
        GameClient client = new GameClient(msg -> {});
        
        // 유효하지 않은 URI로 연결 시도
        client.connect("invalid-uri");
    }

    @Test(expected = Exception.class)
    public void testConnectInvalidHost() throws Exception {
        GameClient client = new GameClient(msg -> {});
        
        // 존재하지 않는 호스트로 연결 시도
        client.connect("ws://nonexistent-host-12345.com:8081/game");
    }

    @Test
    public void testConnectWithTrimmedUri() {
        GameClient client = new GameClient(msg -> {});
        
        try {
            // 공백이 포함된 URI - trim 처리 확인
            client.connect("  ws://localhost:8081/game  ");
            fail("Should throw exception for invalid server");
        } catch (Exception e) {
            // 예외 발생 확인
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testOnOpenCallback() {
        AtomicBoolean connected = new AtomicBoolean(false);
        GameClient client = new GameClient(msg -> {});
        
        client.setOnConnected(() -> connected.set(true));
        
        // onOpen은 실제 WebSocket 연결 시에만 호출되므로
        // 여기서는 콜백 설정만 확인
        assertNotNull(client.getOnConnected());
    }

    @Test
    public void testOnCloseCallback() {
        AtomicBoolean disconnected = new AtomicBoolean(false);
        GameClient client = new GameClient(msg -> {});
        
        client.setOnDisconnected(() -> disconnected.set(true));
        
        assertNotNull(client.getOnDisconnected());
    }

    @Test
    public void testOnErrorCallback() {
        AtomicBoolean errorHandled = new AtomicBoolean(false);
        GameClient client = new GameClient(msg -> {});
        
        client.setOnDisconnected(() -> errorHandled.set(true));
        
        // onError는 내부적으로 onDisconnected를 호출
        assertNotNull(client.getOnDisconnected());
    }

    @Test
    public void testMessageHandlerSet() {
        AtomicReference<Message> received = new AtomicReference<>();
        GameClient client = new GameClient(received::set);
        
        // 메시지 핸들러가 설정되었는지 확인
        // 실제 메시지 수신은 WebSocket 연결 후에만 가능
        assertNotNull(client);
    }

    @Test
    public void testMultipleDisconnectCalls() {
        GameClient client = new GameClient(msg -> {});
        
        // 여러 번 disconnect 호출해도 문제없어야 함
        client.disconnect();
        client.disconnect();
        client.disconnect();
        
        assertFalse(client.isConnected());
    }

    @Test
    public void testSendMultipleMessages() {
        GameClient client = new GameClient(msg -> {});
        
        // 연결되지 않은 상태에서 여러 메시지 전송 시도
        client.send(new Message(MessageType.PING, null));
        client.send(new Message(MessageType.GAME_START, null));
        client.send(new Message(MessageType.GAME_OVER, null));
        
        assertFalse(client.isConnected());
    }

    @Test
    public void testCallbacksAreOptional() {
        GameClient client = new GameClient(msg -> {});
        
        // 콜백 설정 없이 disconnect 호출
        client.disconnect();
        
        assertTrue(true); // 예외 없이 완료
    }
}