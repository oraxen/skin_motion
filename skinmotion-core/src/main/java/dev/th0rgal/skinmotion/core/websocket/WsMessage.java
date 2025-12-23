package dev.th0rgal.skinmotion.core.websocket;

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
        if (data == null || !data.has(key)) return null;
        return data.get(key).getAsString();
    }

    public int getInt(String key, int defaultValue) {
        if (data == null || !data.has(key)) return defaultValue;
        return data.get(key).getAsInt();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (data == null || !data.has(key)) return defaultValue;
        return data.get(key).getAsBoolean();
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

            JsonObject data = obj.has("data") ? obj.getAsJsonObject("data") : null;
            return new WsMessage(type, data);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create JSON for PlayerOnline message.
     */
    public static String playerOnline(String uuid, String username) {
        return String.format(
            "{\"type\":\"PlayerOnline\",\"data\":{\"minecraft_uuid\":\"%s\",\"minecraft_username\":\"%s\"}}",
            uuid, username
        );
    }

    /**
     * Create JSON for PlayerOffline message.
     */
    public static String playerOffline(String uuid) {
        return String.format(
            "{\"type\":\"PlayerOffline\",\"data\":{\"minecraft_uuid\":\"%s\"}}",
            uuid
        );
    }

    /**
     * Create JSON for Ping message.
     */
    public static String ping() {
        return "{\"type\":\"Ping\",\"data\":null}";
    }
}
