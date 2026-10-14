package dev.th0rgal.skinmotion.core.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Configuration for a player's custom skin.
 */
public class SkinConfig {

    @SerializedName("id")
    private String id;

    @SerializedName("minecraft_uuid")
    private String minecraftUuid;

    @SerializedName("minecraft_username")
    private String minecraftUsername;

    @SerializedName("cape_type")
    private String capeType;

    @SerializedName("loop_mode")
    private String loopMode;

    @SerializedName("frame_duration_ticks")
    private int frameDurationTicks;

    @SerializedName("enabled")
    private Boolean enabled;

    @SerializedName("frames")
    private List<SkinFrame> frames;

    /**
     * Default constructor for Gson deserialization.
     */
    public SkinConfig() {
    }

    /**
     * Constructor for creating configs from local storage.
     */
    public SkinConfig(@NotNull String minecraftUuid, @NotNull String minecraftUsername,
                      @NotNull String capeType, @NotNull String loopMode,
                      int frameDurationTicks, boolean enabled,
                      @Nullable List<SkinFrame> frames) {
        this.id = minecraftUuid; // Use UUID as ID for local storage
        this.minecraftUuid = minecraftUuid;
        this.minecraftUsername = minecraftUsername;
        this.capeType = capeType;
        this.loopMode = loopMode;
        this.frameDurationTicks = frameDurationTicks;
        this.enabled = enabled;
        this.frames = frames;
    }

    @NotNull
    public String getId() {
        return id != null ? id : "";
    }

    @NotNull
    public String getMinecraftUuid() {
        return minecraftUuid != null ? minecraftUuid : "";
    }

    @NotNull
    public String getMinecraftUsername() {
        return minecraftUsername != null ? minecraftUsername : "";
    }

    @NotNull
    public String getCapeType() {
        return capeType != null ? capeType : "vanilla";
    }

    @NotNull
    public String getLoopMode() {
        return loopMode != null ? loopMode : "loop";
    }

    public int getFrameDurationTicks() {
        return frameDurationTicks > 0 ? frameDurationTicks : 10;
    }

    /**
     * Whether the animation is enabled. Defaults to true when the API omits the field.
     */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    @Nullable
    public List<SkinFrame> getFrames() {
        return frames;
    }

    public int getFrameCount() {
        return frames != null ? frames.size() : 0;
    }

    /**
     * Check if this skin has animation (more than 1 frame).
     */
    public boolean isAnimated() {
        return frames != null && frames.size() > 1 && isEnabled();
    }

    /**
     * Return a copy whose frames are ordered by {@code frame_index} and stripped of
     * frames lacking a signed texture, so frame positions match the animation order.
     */
    @NotNull
    public SkinConfig sanitized() {
        List<SkinFrame> validFrames = new ArrayList<>();
        if (frames != null) {
            for (SkinFrame frame : frames) {
                if (frame != null && !frame.getTextureValue().isEmpty() && !frame.getTextureSignature().isEmpty()) {
                    validFrames.add(frame);
                }
            }
        }
        validFrames.sort(Comparator.comparingInt(SkinFrame::getFrameIndex));

        SkinConfig copy = new SkinConfig(getMinecraftUuid(), getMinecraftUsername(), getCapeType(), getLoopMode(),
                frameDurationTicks, isEnabled(), Collections.unmodifiableList(validFrames));
        copy.id = id;
        return copy;
    }

    /**
     * Check whether two configs would render identically (same frames, timing and mode).
     */
    public boolean hasSameContent(@Nullable SkinConfig other) {
        if (other == null) {
            return false;
        }
        if (getFrameCount() != other.getFrameCount()
                || getFrameDurationTicks() != other.getFrameDurationTicks()
                || isEnabled() != other.isEnabled()
                || !getLoopMode().equals(other.getLoopMode())
                || !getCapeType().equals(other.getCapeType())) {
            return false;
        }
        for (int i = 0; i < getFrameCount(); i++) {
            SkinFrame a = getFrame(i);
            SkinFrame b = other.getFrame(i);
            if (!a.getTextureValue().equals(b.getTextureValue())
                    || !a.getTextureSignature().equals(b.getTextureSignature())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a specific frame by index.
     */
    @Nullable
    public SkinFrame getFrame(int index) {
        if (frames == null || index < 0 || index >= frames.size()) {
            return null;
        }
        return frames.get(index);
    }

    /**
     * Get the first frame (used for the player's actual skin).
     */
    @Nullable
    public SkinFrame getFirstFrame() {
        return getFrame(0);
    }

    @Override
    public String toString() {
        return "SkinConfig{" +
                "minecraftUuid='" + minecraftUuid + '\'' +
                ", capeType='" + capeType + '\'' +
                ", loopMode='" + loopMode + '\'' +
                ", frameDurationTicks=" + frameDurationTicks +
                ", enabled=" + isEnabled() +
                ", frameCount=" + getFrameCount() +
                '}';
    }
}
