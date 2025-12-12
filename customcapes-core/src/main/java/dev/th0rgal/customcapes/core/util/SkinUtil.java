package dev.th0rgal.customcapes.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.th0rgal.customcapes.core.model.SkinProperty;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for working with Minecraft skin textures.
 */
public final class SkinUtil {

    private static final Gson GSON = new Gson();

    private SkinUtil() {
        // Utility class
    }

    /**
     * Decode a skin property value to extract the skin URL.
     * The value is Base64-encoded JSON containing texture URLs.
     *
     * @param property The skin property to decode
     * @return The skin URL, or null if not found
     */
    @Nullable
    public static String extractSkinUrl(@NotNull SkinProperty property) {
        return extractSkinUrl(property.getValue());
    }

    /**
     * Decode a Base64 texture value to extract the skin URL.
     *
     * @param base64Value The Base64-encoded texture value
     * @return The skin URL, or null if not found
     */
    @Nullable
    public static String extractSkinUrl(@NotNull String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) {
                return null;
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) {
                return null;
            }

            return skin.has("url") ? skin.get("url").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract the skin variant (model) from a skin property.
     *
     * @param property The skin property to decode
     * @return The skin variant
     */
    @NotNull
    public static SkinVariant extractVariant(@NotNull SkinProperty property) {
        return extractVariant(property.getValue());
    }

    /**
     * Extract the skin variant (model) from a Base64 texture value.
     *
     * @param base64Value The Base64-encoded texture value
     * @return The skin variant (defaults to CLASSIC if not found)
     */
    @NotNull
    public static SkinVariant extractVariant(@NotNull String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) {
                return SkinVariant.CLASSIC;
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) {
                return SkinVariant.CLASSIC;
            }

            JsonObject metadata = skin.getAsJsonObject("metadata");
            if (metadata == null) {
                return SkinVariant.CLASSIC;
            }

            String model = metadata.has("model") ? metadata.get("model").getAsString() : null;
            return SkinVariant.fromString(model);
        } catch (Exception e) {
            return SkinVariant.CLASSIC;
        }
    }

    /**
     * Check if a skin property has a cape.
     *
     * @param property The skin property to check
     * @return true if the texture includes a cape
     */
    public static boolean hasCape(@NotNull SkinProperty property) {
        return hasCape(property.getValue());
    }

    /**
     * Check if a Base64 texture value includes a cape.
     *
     * @param base64Value The Base64-encoded texture value
     * @return true if the texture includes a cape
     */
    public static boolean hasCape(@NotNull String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) {
                return false;
            }

            return textures.has("CAPE");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract the cape URL from a skin property if present.
     *
     * @param property The skin property to decode
     * @return The cape URL, or null if not found
     */
    @Nullable
    public static String extractCapeUrl(@NotNull SkinProperty property) {
        return extractCapeUrl(property.getValue());
    }

    /**
     * Extract the cape URL from a Base64 texture value if present.
     *
     * @param base64Value The Base64-encoded texture value
     * @return The cape URL, or null if not found
     */
    @Nullable
    public static String extractCapeUrl(@NotNull String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) {
                return null;
            }

            JsonObject cape = textures.getAsJsonObject("CAPE");
            if (cape == null) {
                return null;
            }

            return cape.has("url") ? cape.get("url").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decode the full texture JSON from a Base64 value.
     *
     * @param base64Value The Base64-encoded texture value
     * @return The decoded JSON string, or null on error
     */
    @Nullable
    public static String decodeTextureJson(@NotNull String base64Value) {
        try {
            return new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

