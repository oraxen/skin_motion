package dev.th0rgal.customcapes.core.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Available API backends for skin generation.
 */
public enum ApiBackend {
    /**
     * Custom Capes API - self-hosted backend with full cape support.
     */
    CUSTOM_CAPES("customcapes"),

    /**
     * MineSkin API - public API with cape support.
     * Note: Requires API key for higher rate limits.
     * 
     * @see <a href="https://api.mineskin.org">MineSkin API</a>
     */
    MINESKIN("mineskin");

    private final String id;

    ApiBackend(@NotNull String id) {
        this.id = id;
    }

    /**
     * Get the configuration identifier for this backend.
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * Find a backend by its configuration ID.
     *
     * @param id The backend ID (e.g., "mineskin", "customcapes")
     * @return The backend, or null if not found
     */
    @Nullable
    public static ApiBackend fromId(@NotNull String id) {
        for (ApiBackend backend : values()) {
            if (backend.id.equalsIgnoreCase(id)) {
                return backend;
            }
        }
        return null;
    }
}

