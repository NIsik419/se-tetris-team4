package component.network.websocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class MessageTest {

    @Test
    public void testMessageToString() {
        Message m = new Message(MessageType.PING, "hello");
        assertTrue(m.toString().contains("PING"));
        assertTrue(m.data.contains("hello"));
    }
}
