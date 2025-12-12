package dev.th0rgal.customcapes.core.api;

import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import org.jetbrains.annotations.NotNull;

/**
 * Request body for the /generate endpoint.
 */
public final class GenerateRequest {
    
    private final String skin_url;
    private final String cape_type;
    private final String variant;

    public GenerateRequest(
            @NotNull String skinUrl,
            @NotNull CapeType capeType,
            @NotNull SkinVariant variant
    ) {
        this.skin_url = skinUrl;
        this.cape_type = capeType.getId();
        this.variant = variant.getId();
    }

    public String getSkinUrl() {
        return skin_url;
    }

    public String getCapeType() {
        return cape_type;
    }

    public String getVariant() {
        return variant;
    }
}

