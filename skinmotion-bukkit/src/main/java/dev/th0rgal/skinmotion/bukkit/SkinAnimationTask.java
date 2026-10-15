package dev.th0rgal.skinmotion.bukkit;

import dev.th0rgal.skinmotion.bukkit.nms.PlayerInfoPackets;
import dev.th0rgal.skinmotion.bukkit.scheduler.PlatformScheduler;
import dev.th0rgal.skinmotion.core.animation.FrameCursor;
import dev.th0rgal.skinmotion.core.model.SkinConfig;
import dev.th0rgal.skinmotion.core.model.SkinFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task that broadcasts animated skin frames to viewers.
 * Each player with an animated skin gets their frames cycled for every player tracking them;
 * the player themselves keeps seeing the first frame.
 * <p>
 * Frame timing runs once per tick on the main thread (global region on Folia). Sending a frame
 * runs on the thread owning the animated player, so viewer access is region-safe on Folia.
 */
public class SkinAnimationTask implements Runnable {

    private final SkinMotionPlugin plugin;
    private final PlatformScheduler scheduler;
    @Nullable
    private final PlayerInfoPackets packets;
    private PlatformScheduler.Cancellable task;

    private final Map<UUID, FrameCursor> cursors = new ConcurrentHashMap<>();
    private final Set<UUID> warnedPlayers = ConcurrentHashMap.newKeySet();

    public SkinAnimationTask(@NotNull SkinMotionPlugin plugin, @NotNull PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.packets = PlayerInfoPackets.create(plugin.getLogger());
    }

    public void start() {
        if (packets == null) {
            plugin.getLogger().warning("[SkinAnimation] Animation disabled; players will only get their first frame");
            return;
        }
        task = scheduler.runGlobalTimer(this, 20L, 1L);
        plugin.getLogger().info("[SkinAnimation] Animation task started");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
            plugin.getLogger().info("[SkinAnimation] Animation task stopped");
        }
        cursors.clear();
        warnedPlayers.clear();
    }

    public boolean isAvailable() {
        return packets != null;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            SkinConfig config = plugin.getPlayerSkinConfig(playerId);

            // Skip if no config, not animated, or disabled
            if (config == null || !config.isAnimated()) {
                cursors.remove(playerId);
                continue;
            }

            FrameCursor cursor = cursors.computeIfAbsent(playerId, id -> new FrameCursor());
            int frameIndex = cursor.tick(config.getFrameCount(), config.getFrameDurationTicks(), config.getLoopMode());
            if (frameIndex == FrameCursor.UNCHANGED) {
                continue;
            }

            SkinFrame frame = config.getFrame(frameIndex);
            if (frame == null) {
                continue;
            }

            if (scheduler.isOwnedByCurrentThread(player)) {
                broadcastFrame(player, frame);
            } else {
                scheduler.runForEntity(player, () -> broadcastFrame(player, frame));
            }
        }
    }

    /**
     * Show a frame to every player currently tracking {@code target}.
     * The viewer's client only builds a new skin when the player info entry is re-created, so the
     * target is hidden, re-announced with the frame's texture, then shown again. Bukkit's show
     * re-sends the real profile, but the client keeps the first ADD_PLAYER entry it receives
     * and applies the remaining updates (listed, game mode, chat session) on top of it.
     */
    private void broadcastFrame(@NotNull Player target, @NotNull SkinFrame frame) {
        if (packets == null || !target.isOnline()) {
            return;
        }

        Object packet;
        try {
            packet = packets.createAddPlayerPacket(target.getUniqueId(), target.getName(), frame.toSkinProperty());
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(target, "Failed to build skin frame packet for " + target.getName() + ": " + e);
            return;
        }

        List<Player> viewers = new ArrayList<>(target.getTrackedBy());
        for (Player viewer : viewers) {
            if (viewer.equals(target) || !viewer.isOnline() || !viewer.canSee(target)
                    || !scheduler.isOwnedByCurrentThread(viewer)) {
                continue;
            }

            viewer.hidePlayer(plugin, target);
            try {
                packets.send(viewer, packet);
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce(target, "Failed to send skin frame of " + target.getName() + " to " + viewer.getName() + ": " + e);
            } finally {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    private void warnOnce(Player target, String message) {
        if (warnedPlayers.add(target.getUniqueId())) {
            plugin.getLogger().warning(message);
        }
    }

    /**
     * Restart a player's animation from the first frame.
     */
    public void resetPlayer(UUID playerId) {
        cursors.remove(playerId);
    }

    /**
     * Drop all animation state for a player who left.
     */
    public void removePlayer(UUID playerId) {
        cursors.remove(playerId);
        warnedPlayers.remove(playerId);
    }
}
