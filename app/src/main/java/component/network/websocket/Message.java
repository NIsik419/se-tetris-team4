package component.network.websocket;

public class Message {
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
