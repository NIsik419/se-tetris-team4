package component.network.websocket;

import com.google.gson.*;
import java.awt.Color;

public class WebSocketUtil {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Color.class, new JsonSerializer<Color>() {
                @Override
                public JsonElement serialize(Color c, java.lang.reflect.Type typeOfSrc,
                        JsonSerializationContext context) {
                    if (c == null)
                        return JsonNull.INSTANCE;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("r", c.getRed());
                    obj.addProperty("g", c.getGreen());
                    obj.addProperty("b", c.getBlue());
                    obj.addProperty("a", c.getAlpha());
                    return obj;
                }
            })
            .registerTypeAdapter(Color.class, new JsonDeserializer<Color>() {
                @Override
                public Color deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                        JsonDeserializationContext context)
                        throws JsonParseException {
                    JsonObject obj = json.getAsJsonObject();
                    int r = obj.get("r").getAsInt();
                    int g = obj.get("g").getAsInt();
                    int b = obj.get("b").getAsInt();
                    int a = obj.has("a") ? obj.get("a").getAsInt() : 255;
                    return new Color(r, g, b, a);
                }
            })
            .create();

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {

        // 문자열 JSON unwrap 처리
        if (json != null &&
                json.startsWith("\"") && json.endsWith("\"")) {
            json = json.substring(1, json.length() - 1)
                    .replace("\\\"", "\"");
        }

        return gson.fromJson(json, clazz);
    }

}
