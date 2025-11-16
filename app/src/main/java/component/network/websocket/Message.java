package component.network.websocket;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    public MessageType type;
    public String data;

    public Message() {}

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.data = WebSocketUtil.toJson(payload);
    }

    @Override
    public String toString() {
        return "Message{" + "type=" + type + ", data='" + data + '\'' + '}';
    }
}
