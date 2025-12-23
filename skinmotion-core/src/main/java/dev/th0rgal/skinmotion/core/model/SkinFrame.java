package dev.th0rgal.skinmotion.core.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single frame of an animated skin.
 * Contains the Mojang-signed texture data.
 */
public class SkinFrame {

    @SerializedName("frame_index")
    private int frameIndex;

    @SerializedName("texture_value")
    private String textureValue;

    @SerializedName("texture_signature")
    private String textureSignature;

    @SerializedName("texture_url")
    private String textureUrl;

    /**
     * Default constructor for Gson deserialization.
     */
    public SkinFrame() {
    }

    /**
     * Constructor for creating frames from local storage.
     */
    public SkinFrame(int frameIndex, @NotNull String textureValue, 
                     @NotNull String textureSignature, @Nullable String textureUrl) {
        this.frameIndex = frameIndex;
        this.textureValue = textureValue;
        this.textureSignature = textureSignature;
        this.textureUrl = textureUrl;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    @NotNull
    public String getTextureValue() {
        return textureValue != null ? textureValue : "";
    }

    @NotNull
    public String getTextureSignature() {
        return textureSignature != null ? textureSignature : "";
    }

    @Nullable
    public String getTextureUrl() {
        return textureUrl;
    }

    /**
     * Convert to SkinProperty for use with skin application.
     */
    @NotNull
    public SkinProperty toSkinProperty() {
        return new SkinProperty(getTextureValue(), getTextureSignature());
    }

    @Override
    public String toString() {
        return "SkinFrame{" +
                "frameIndex=" + frameIndex +
                ", hasValue=" + (textureValue != null && !textureValue.isEmpty()) +
                ", hasSignature=" + (textureSignature != null && !textureSignature.isEmpty()) +
                '}';
    }
}
