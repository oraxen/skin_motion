package dev.th0rgal.customcapes.core.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Response data from the Custom Capes API containing texture information.
 */
public final class TextureData {

    private final String value;
    private final String signature;
    private final String textureUrl;
    private final boolean cached;

    public TextureData(
            @NotNull String value,
            @NotNull String signature,
            @Nullable String textureUrl,
            boolean cached
    ) {
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.signature = Objects.requireNonNull(signature, "signature cannot be null");
        this.textureUrl = textureUrl;
        this.cached = cached;
    }

    /**
     * Get the Base64-encoded texture value.
     */
    @NotNull
    public String getValue() {
        return value;
    }

    /**
     * Get the Mojang signature.
     */
    @NotNull
    public String getSignature() {
        return signature;
    }

    /**
     * Get the texture URL on textures.minecraft.net (may be null).
     */
    @Nullable
    public String getTextureUrl() {
        return textureUrl;
    }

    /**
     * Whether this result was served from cache.
     */
    public boolean isCached() {
        return cached;
    }

    /**
     * Convert this texture data to a SkinProperty for application.
     */
    @NotNull
    public SkinProperty toSkinProperty() {
        return new SkinProperty(value, signature);
    }

    @Override
    public String toString() {
        return "TextureData{cached=" + cached + ", textureUrl='" + textureUrl + "'}";
    }
}

