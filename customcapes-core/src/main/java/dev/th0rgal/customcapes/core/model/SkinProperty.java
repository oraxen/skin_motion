package dev.th0rgal.customcapes.core.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a Minecraft skin property containing the texture value and signature.
 * This is the standard format used by Mojang for skin/cape data.
 */
public final class SkinProperty {
    
    /**
     * The property name used by Minecraft for texture data.
     */
    public static final String TEXTURES_NAME = "textures";

    private final String value;
    private final String signature;

    public SkinProperty(@NotNull String value, @NotNull String signature) {
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.signature = Objects.requireNonNull(signature, "signature cannot be null");
    }

    /**
     * Get the Base64-encoded texture value.
     * This contains JSON with skin/cape URLs and metadata.
     */
    @NotNull
    public String getValue() {
        return value;
    }

    /**
     * Get the Mojang signature for verification.
     */
    @NotNull
    public String getSignature() {
        return signature;
    }

    /**
     * Create a SkinProperty from nullable values.
     *
     * @return The property, or null if either value is null or empty
     */
    @Nullable
    public static SkinProperty of(@Nullable String value, @Nullable String signature) {
        if (value == null || value.isEmpty() || signature == null || signature.isEmpty()) {
            return null;
        }
        return new SkinProperty(value, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkinProperty that = (SkinProperty) o;
        return value.equals(that.value) && signature.equals(that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, signature);
    }

    @Override
    public String toString() {
        return "SkinProperty{value='" + value.substring(0, Math.min(20, value.length())) + "...'}";
    }
}

