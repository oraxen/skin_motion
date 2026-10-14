package dev.th0rgal.skinmotion.core.animation;

import org.jetbrains.annotations.NotNull;

/**
 * Tracks the animation position of a single player's skin.
 * Not thread-safe: each cursor must only be ticked from one thread at a time.
 */
public final class FrameCursor {

    public static final String LOOP = "loop";
    public static final String ONCE = "once";
    public static final String PING_PONG = "ping_pong";

    /** Returned by {@link #tick} when the displayed frame does not change. */
    public static final int UNCHANGED = -1;

    private int frameIndex;
    private int direction = 1;
    private int ticksOnFrame;

    /**
     * Advance the cursor by one server tick.
     *
     * @param frameCount         Number of frames in the animation
     * @param frameDurationTicks Ticks each frame stays visible (values below 1 are treated as 1)
     * @param loopMode           {@link #LOOP}, {@link #ONCE} or {@link #PING_PONG}; unknown values loop
     * @return The new frame index, or {@link #UNCHANGED} if the displayed frame stays the same
     */
    public int tick(int frameCount, int frameDurationTicks, @NotNull String loopMode) {
        if (frameCount <= 1) {
            return UNCHANGED;
        }
        if (frameIndex >= frameCount) {
            frameIndex = 0;
            direction = 1;
        }

        ticksOnFrame++;
        if (ticksOnFrame < Math.max(1, frameDurationTicks)) {
            return UNCHANGED;
        }
        ticksOnFrame = 0;

        int next = nextIndex(frameCount, loopMode);
        if (next == frameIndex) {
            return UNCHANGED;
        }
        frameIndex = next;
        return frameIndex;
    }

    private int nextIndex(int frameCount, String loopMode) {
        switch (loopMode) {
            case ONCE -> {
                return Math.min(frameIndex + 1, frameCount - 1);
            }
            case PING_PONG -> {
                int next = frameIndex + direction;
                if (next >= frameCount) {
                    direction = -1;
                    next = frameCount - 2;
                } else if (next < 0) {
                    direction = 1;
                    next = 1;
                }
                return next;
            }
            default -> {
                return (frameIndex + 1) % frameCount;
            }
        }
    }

    public int getFrameIndex() {
        return frameIndex;
    }
}
