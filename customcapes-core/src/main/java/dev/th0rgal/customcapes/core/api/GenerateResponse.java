package dev.th0rgal.customcapes.core.api;

import dev.th0rgal.customcapes.core.model.TextureData;
import org.jetbrains.annotations.Nullable;

/**
 * Response from the /generate endpoint.
 */
public final class GenerateResponse {

    private boolean success;
    @Nullable
    private ResponseData data;
    @Nullable
    private String error;
    private double delay_seconds;

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public ResponseData getData() {
        return data;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public double getDelaySeconds() {
        return delay_seconds;
    }

    /**
     * Convert the response data to a TextureData object.
     */
    @Nullable
    public TextureData toTextureData() {
        if (data == null) {
            return null;
        }
        return new TextureData(data.value, data.signature, data.texture_url, data.cached);
    }

    /**
     * Nested data structure from the API response.
     */
    public static final class ResponseData {
        private String value;
        private String signature;
        @Nullable
        private String texture_url;
        private boolean cached;

        public String getValue() {
            return value;
        }

        public String getSignature() {
            return signature;
        }

        @Nullable
        public String getTextureUrl() {
            return texture_url;
        }

        public boolean isCached() {
            return cached;
        }
    }
}

