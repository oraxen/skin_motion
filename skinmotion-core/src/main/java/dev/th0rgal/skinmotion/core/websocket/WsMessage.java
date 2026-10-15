package dev.th0rgal.skinmotion.core.websocket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * WebSocket message types matching the API.
 */
public class WsMessage {

    public enum Type {
        SKIN_UPDATED("SkinUpdated"),
        CONFIG_CHANGED("ConfigChanged"),
        PLAYER_ONLINE("PlayerOnline"),
        PLAYER_OFFLINE("PlayerOffline"),
        REQUEST_SKIN_DATA("RequestSkinData"),
        PING("Ping"),
        PONG("Pong");

        private final String jsonType;

        Type(String jsonType) {
            this.jsonType = jsonType;
        }

        public String getJsonType() {
            return jsonType;
        }

        @Nullable
        public static Type fromJson(String type) {
            for (Type t : values()) {
                if (t.jsonType.equals(type)) {
                    return t;
                }
            }
            return null;
        }
    }

    private final Type type;
    private final JsonObject data;

    public WsMessage(@NotNull Type type, @Nullable JsonObject data) {
        this.type = type;
        this.data = data;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    @Nullable
    public JsonObject getData() {
        return data;
    }

    @Nullable
    public String getString(String key) {
        JsonElement value = primitive(key);
        return value != null ? value.getAsString() : null;
    }

    public int getInt(String key, int defaultValue) {
        JsonElement value = primitive(key);
        try {
            return value != null ? value.getAsInt() : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonElement value = primitive(key);
        return value != null ? value.getAsBoolean() : defaultValue;
    }

    @Nullable
    private JsonElement primitive(String key) {
        if (data == null) return null;
        JsonElement value = data.get(key);
        return value != null && value.isJsonPrimitive() ? value : null;
    }

    /**
     * Parse a WebSocket message from JSON.
     */
    @Nullable
    public static WsMessage fromJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String typeStr = obj.get("type").getAsString();
            Type type = Type.fromJson(typeStr);
            if (type == null) return null;

            JsonElement data = obj.get("data");
            return new WsMessage(type, data != null && data.isJsonObject() ? data.getAsJsonObject() : null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create JSON for PlayerOnline message.
     */
    public static String playerOnline(String uuid, String username) {
        JsonObject data = new JsonObject();
        data.addProperty("minecraft_uuid", uuid);
        data.addProperty("minecraft_username", username);
        return envelope(Type.PLAYER_ONLINE, data);
    }

    /**
     * Create JSON for PlayerOffline message.
     */
    public static String playerOffline(String uuid) {
        JsonObject data = new JsonObject();
        data.addProperty("minecraft_uuid", uuid);
        return envelope(Type.PLAYER_OFFLINE, data);
    }

    private static String envelope(Type type, JsonObject data) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.getJsonType());
        message.add("data", data);
        return message.toString();
    }

    /**
     * Create JSON for Ping message.
     */
    public static String ping() {
        return "{\"type\":\"Ping\",\"data\":null}";
    }
}
