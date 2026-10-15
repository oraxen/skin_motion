package dev.th0rgal.skinmotion.core.util;

import dev.th0rgal.skinmotion.core.model.SkinProperty;
import dev.th0rgal.skinmotion.core.model.SkinVariant;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinUtilTest {

    private static final String SKIN_URL = "http://textures.minecraft.net/texture/abc";
    private static final String CAPE_URL = "http://textures.minecraft.net/texture/cape";

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void extractsSlimSkinAndCape() {
        String value = encode("{\"timestamp\":1,\"textures\":{"
                + "\"SKIN\":{\"url\":\"" + SKIN_URL + "\",\"metadata\":{\"model\":\"slim\"}},"
                + "\"CAPE\":{\"url\":\"" + CAPE_URL + "\"}}}");
        SkinProperty property = new SkinProperty(value, "sig");

        assertEquals(SKIN_URL, SkinUtil.extractSkinUrl(property));
        assertEquals(SkinVariant.SLIM, SkinUtil.extractVariant(property));
        assertTrue(SkinUtil.hasCape(property));
        assertEquals(CAPE_URL, SkinUtil.extractCapeUrl(property));
    }

    @Test
    void classicSkinWithoutCape() {
        String value = encode("{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_URL + "\"}}}");

        assertEquals(SKIN_URL, SkinUtil.extractSkinUrl(value));
        assertEquals(SkinVariant.CLASSIC, SkinUtil.extractVariant(value));
        assertFalse(SkinUtil.hasCape(value));
        assertNull(SkinUtil.extractCapeUrl(value));
    }

    @Test
    void malformedValuesFallBackSafely() {
        for (String value : new String[]{"", "not base64!", encode("null"), encode("[]"), encode("{\"textures\":5}")}) {
            assertNull(SkinUtil.extractSkinUrl(value), value);
            assertEquals(SkinVariant.CLASSIC, SkinUtil.extractVariant(value), value);
            assertFalse(SkinUtil.hasCape(value), value);
            assertNull(SkinUtil.extractCapeUrl(value), value);
        }
        assertNull(SkinUtil.decodeTextureJson("not base64!"));
    }
}
