package dev.th0rgal.skinmotion.bukkit;

import dev.th0rgal.skinmotion.bukkit.npc.FakePlayer;
import dev.th0rgal.skinmotion.core.model.SkinConfig;
import dev.th0rgal.skinmotion.core.model.SkinFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task that broadcasts animated skin frames to viewers.
 * Each player with an animated skin gets their frames cycled for all viewers.
 */
public class SkinAnimationTask implements Runnable {

    private final SkinMotionPlugin plugin;
    private BukkitTask task;

    // Track current frame index per player
    private final Map<UUID, Integer> playerFrameIndex = new ConcurrentHashMap<>();

    // Track tick counter per player (for variable frame rates)
    private final Map<UUID, Integer> playerTickCounter = new ConcurrentHashMap<>();

    public SkinAnimationTask(SkinMotionPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Run every tick
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 1L);
        plugin.getLogger().info("[SkinAnimation] Animation task started");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        playerFrameIndex.clear();
        playerTickCounter.clear();
        plugin.getLogger().info("[SkinAnimation] Animation task stopped");
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SkinConfig config = plugin.getPlayerSkinConfig(player.getUniqueId());

            // Skip if no config, not animated, or disabled
            if (config == null || !config.isAnimated()) {
                continue;
            }

            UUID playerId = player.getUniqueId();

            // Increment tick counter
            int tickCounter = playerTickCounter.getOrDefault(playerId, 0) + 1;

            // Check if it's time to advance to next frame
            if (tickCounter >= config.getFrameDurationTicks()) {
                tickCounter = 0;

                // Get current frame index
                int frameIndex = playerFrameIndex.getOrDefault(playerId, 0);
                int frameCount = config.getFrameCount();

                // Advance to next frame based on loop mode
                frameIndex = advanceFrame(frameIndex, frameCount, config.getLoopMode());
                playerFrameIndex.put(playerId, frameIndex);

                // Get the frame
                SkinFrame frame = config.getFrame(frameIndex);
                if (frame != null) {
                    // Broadcast skin update to all viewers (except the player themselves)
                    broadcastSkinUpdate(player, frame);
                }
            }

            playerTickCounter.put(playerId, tickCounter);
        }
    }

    /**
     * Advance to the next frame based on loop mode.
     */
    private int advanceFrame(int current, int frameCount, String loopMode) {
        if (frameCount <= 1) {
            return 0;
        }

        switch (loopMode) {
            case "ping_pong" -> {
                // TODO: Implement ping-pong (need direction tracking)
                return (current + 1) % frameCount;
            }
            case "once" -> {
                if (current < frameCount - 1) {
                    return current + 1;
                }
                return current; // Stay on last frame
            }
            default -> { // "loop"
                return (current + 1) % frameCount;
            }
        }
    }

    /**
     * Broadcast a skin update to all players who can see the target player.
     */
    private void broadcastSkinUpdate(Player target, SkinFrame frame) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // Don't update the player's view of themselves
            if (viewer.equals(target)) {
                continue;
            }

            // Check if viewer can see target
            if (!viewer.canSee(target)) {
                continue;
            }

            // Check distance (only broadcast to nearby players)
            if (viewer.getWorld().equals(target.getWorld()) &&
                    viewer.getLocation().distanceSquared(target.getLocation()) < 64 * 64) {

                // Send skin update packet
                try {
                    FakePlayer.sendSkinUpdatePacket(viewer, target, frame.toSkinProperty());
                } catch (Exception e) {
                    // Log once per player, not every tick
                    if (playerTickCounter.get(target.getUniqueId()) == 0) {
                        plugin.getLogger().warning("Failed to send skin update to " + viewer.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Reset animation state for a player.
     */
    public void resetPlayer(UUID playerId) {
        playerFrameIndex.remove(playerId);
        playerTickCounter.remove(playerId);
    }
}
