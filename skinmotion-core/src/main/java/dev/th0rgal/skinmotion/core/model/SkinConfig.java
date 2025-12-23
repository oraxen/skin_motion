package dev.th0rgal.skinmotion.core.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private boolean enabled;

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

    public boolean isEnabled() {
        return enabled;
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
        return frames != null && frames.size() > 1 && enabled;
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
                ", enabled=" + enabled +
                ", frameCount=" + getFrameCount() +
                '}';
    }
}
