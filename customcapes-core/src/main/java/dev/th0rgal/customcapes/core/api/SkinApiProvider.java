package dev.th0rgal.customcapes.core.api;

import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import dev.th0rgal.customcapes.core.model.TextureData;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for skin generation API providers.
 * Allows switching between different backends (CustomCapes API, MineSkin,
 * etc.).
 */
public interface SkinApiProvider {

    /**
     * Get the name of this provider for logging/display purposes.
     */
    @NotNull
    String getName();

    /**
     * Generate a skin with a cape applied.
     *
     * @param skinUrl  URL of the player's current skin texture
     * @param capeType The cape type to apply
     * @param variant  The skin model variant (classic/slim)
     * @return The generated texture data with signed skin+cape
     * @throws SkinApiException if the request fails
     */
    @NotNull
    TextureData generate(
            @NotNull String skinUrl,
            @NotNull CapeType capeType,
            @NotNull SkinVariant variant) throws SkinApiException;

    /**
     * Asynchronously generate a skin with a cape applied.
     *
     * @param skinUrl  URL of the player's current skin texture
     * @param capeType The cape type to apply
     * @param variant  The skin model variant (classic/slim)
     * @return CompletableFuture containing the generated texture data
     */
    @NotNull
    CompletableFuture<TextureData> generateAsync(
            @NotNull String skinUrl,
            @NotNull CapeType capeType,
            @NotNull SkinVariant variant);

    /**
     * Check if this provider is properly configured and reachable.
     *
     * @return true if the provider is healthy
     */
    boolean isHealthy();

    /**
     * Exception thrown when a skin API request fails.
     */
    class SkinApiException extends RuntimeException {
        public SkinApiException(String message) {
            super(message);
        }

        public SkinApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
