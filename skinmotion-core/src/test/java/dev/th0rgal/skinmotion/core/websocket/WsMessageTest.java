package dev.th0rgal.skinmotion.core.websocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WsMessageTest {

    @Test
    void parsesSkinUpdated() {
        WsMessage message = WsMessage.fromJson(
                "{\"type\":\"SkinUpdated\",\"data\":{\"minecraft_uuid\":\"abc\",\"minecraft_username\":\"Steve\"}}");

        assertNotNull(message);
        assertEquals(WsMessage.Type.SKIN_UPDATED, message.getType());
        assertEquals("abc", message.getString("minecraft_uuid"));
        assertEquals("Steve", message.getString("minecraft_username"));
    }

    @Test
    void toleratesNullAndNonObjectData() {
        WsMessage pong = WsMessage.fromJson("{\"type\":\"Pong\",\"data\":null}");
        assertNotNull(pong);
        assertNull(pong.getData());

        WsMessage changed = WsMessage.fromJson("{\"type\":\"ConfigChanged\",\"data\":{\"minecraft_uuid\":null}}");
        assertNotNull(changed);
        assertNull(changed.getString("minecraft_uuid"));
        assertEquals(7, changed.getInt("missing", 7));
    }

    @Test
    void rejectsUnknownOrMalformed() {
        assertNull(WsMessage.fromJson("{\"type\":\"Nope\"}"));
        assertNull(WsMessage.fromJson("not json"));
        assertNull(WsMessage.fromJson("{}"));
    }

    @Test
    void escapesOutgoingPayloads() {
        JsonObject online = JsonParser.parseString(WsMessage.playerOnline("id", "we\"ird")).getAsJsonObject();
        assertEquals("PlayerOnline", online.get("type").getAsString());
        assertEquals("we\"ird", online.getAsJsonObject("data").get("minecraft_username").getAsString());

        JsonObject offline = JsonParser.parseString(WsMessage.playerOffline("id")).getAsJsonObject();
        assertEquals("PlayerOffline", offline.get("type").getAsString());
        assertEquals("id", offline.getAsJsonObject("data").get("minecraft_uuid").getAsString());
    }
}
