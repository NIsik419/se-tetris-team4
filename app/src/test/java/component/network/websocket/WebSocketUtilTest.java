package component.network.websocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class WebSocketUtilTest {

    @Test
    public void testJsonRoundTrip() {
        Message m = new Message(MessageType.PING, "testPayload");

        String json = WebSocketUtil.toJson(m);
        Message mm = WebSocketUtil.fromJson(json, Message.class);

        assertEquals(m.type, mm.type);
    }
}
