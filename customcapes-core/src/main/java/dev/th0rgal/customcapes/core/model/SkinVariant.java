package dev.th0rgal.customcapes.core.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Skin model variant for Minecraft skins.
 */
public enum SkinVariant {
    /**
     * Classic (Steve) model with 4-pixel wide arms.
     */
    CLASSIC("classic"),
    
    /**
     * Slim (Alex) model with 3-pixel wide arms.
     */
    SLIM("slim");

    private final String id;

    SkinVariant(@NotNull String id) {
        this.id = id;
    }

    @NotNull
    public String getId() {
        return id;
    }

    /**
     * Parse a skin variant from a string.
     *
     * @param value The variant string (e.g., "slim", "alex")
     * @return The variant, defaulting to CLASSIC if unknown
     */
    @NotNull
    public static SkinVariant fromString(@Nullable String value) {
        if (value == null) {
            return CLASSIC;
        }
        String lower = value.toLowerCase();
        if (lower.equals("slim") || lower.equals("alex")) {
            return SLIM;
        }
        return CLASSIC;
    }
}

