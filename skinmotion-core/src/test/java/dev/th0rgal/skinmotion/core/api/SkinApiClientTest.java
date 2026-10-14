package dev.th0rgal.skinmotion.core.api;

import dev.th0rgal.skinmotion.core.model.SkinConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinApiClientTest {

    private static final String SKIN_JSON = "{\"success\":true,\"skin\":{"
            + "\"id\":\"1\",\"minecraft_uuid\":\"u\",\"minecraft_username\":\"Steve\","
            + "\"cape_type\":\"migrator\",\"loop_mode\":\"ping_pong\",\"frame_duration_ticks\":4,"
            + "\"frames\":["
            + "{\"frame_index\":2,\"texture_value\":\"v2\",\"texture_signature\":\"s2\"},"
            + "{\"frame_index\":0,\"texture_value\":\"v0\",\"texture_signature\":\"s0\",\"texture_url\":\"url\"},"
            + "{\"frame_index\":1,\"texture_value\":\"v1\",\"texture_signature\":\"\"},"
            + "{\"frame_index\":3,\"texture_value\":\"v3\",\"texture_signature\":\"s3\"}"
            + "]}}";

    @Test
    void parsesAndOrdersFramesDroppingUnsignedOnes() {
        SkinConfig skin = SkinApiClient.parseSkinResponse(200, SKIN_JSON);

        assertNotNull(skin);
        assertEquals(3, skin.getFrameCount());
        assertEquals("v0", skin.getFirstFrame().getTextureValue());
        assertEquals("v2", skin.getFrame(1).getTextureValue());
        assertEquals("v3", skin.getFrame(2).getTextureValue());
        assertEquals("migrator", skin.getCapeType());
        assertEquals("ping_pong", skin.getLoopMode());
        assertEquals(4, skin.getFrameDurationTicks());
        // "enabled" omitted by the API defaults to true
        assertTrue(skin.isEnabled());
        assertTrue(skin.isAnimated());
    }

    @Test
    void notFoundIsNull() {
        assertNull(SkinApiClient.parseSkinResponse(200, "{\"success\":false,\"error\":\"Skin not found\"}"));
        assertNull(SkinApiClient.parseSkinResponse(404, ""));
    }

    @Test
    void skinWithoutUsableFramesIsNull() {
        assertNull(SkinApiClient.parseSkinResponse(200, "{\"success\":true,\"skin\":{\"frames\":[]}}"));
        assertNull(SkinApiClient.parseSkinResponse(200, "{\"success\":true,\"skin\":{}}"));
    }

    @Test
    void errorsAreNotMistakenForMissingSkins() {
        assertThrows(SkinApiClient.SkinApiException.class, () -> SkinApiClient.parseSkinResponse(500, "oops"));
        assertThrows(SkinApiClient.SkinApiException.class, () -> SkinApiClient.parseSkinResponse(401, ""));
        assertThrows(SkinApiClient.SkinApiException.class, () -> SkinApiClient.parseSkinResponse(200, "{not json"));
        assertThrows(SkinApiClient.SkinApiException.class, () -> SkinApiClient.parseSkinResponse(200, ""));
    }

    @Test
    void disabledSkinIsNotAnimated() {
        SkinConfig skin = SkinApiClient.parseSkinResponse(200, SKIN_JSON.replace("\"frames\"", "\"enabled\":false,\"frames\""));

        assertNotNull(skin);
        assertFalse(skin.isAnimated());
    }

    @Test
    void contentComparisonDetectsTimingAndFrameChanges() {
        SkinConfig skin = SkinApiClient.parseSkinResponse(200, SKIN_JSON);
        SkinConfig same = SkinApiClient.parseSkinResponse(200, SKIN_JSON);
        SkinConfig slower = SkinApiClient.parseSkinResponse(200, SKIN_JSON.replace("\"frame_duration_ticks\":4", "\"frame_duration_ticks\":8"));
        SkinConfig otherFrame = SkinApiClient.parseSkinResponse(200, SKIN_JSON.replace("\"v3\"", "\"v9\""));

        assertTrue(skin.hasSameContent(same));
        assertFalse(skin.hasSameContent(slower));
        assertFalse(skin.hasSameContent(otherFrame));
        assertFalse(skin.hasSameContent(null));
    }
}
