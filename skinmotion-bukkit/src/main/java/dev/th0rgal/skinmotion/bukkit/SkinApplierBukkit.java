package dev.th0rgal.skinmotion.bukkit;

import dev.th0rgal.skinmotion.bukkit.paper.PaperSkinApplier;
import dev.th0rgal.skinmotion.bukkit.scheduler.PlatformScheduler;
import dev.th0rgal.skinmotion.bukkit.spigot.SpigotSkinApplier;
import dev.th0rgal.skinmotion.core.model.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles skin application on Bukkit/Paper/Spigot servers.
 * Automatically detects and uses the best available method.
 */
public final class SkinApplierBukkit {

    private final SkinMotionPlugin plugin;
    private final PlatformScheduler scheduler;
    private final boolean isPaper;
    
    // Store original skins for restoration
    private final Map<UUID, SkinProperty> originalSkins = new ConcurrentHashMap<>();

    public SkinApplierBukkit(@NotNull SkinMotionPlugin plugin, @NotNull PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.isPaper = detectPaper();
        
        if (isPaper) {
            plugin.getLogger().info("Paper API detected - using native skin application");
        } else {
            plugin.getLogger().info("Spigot detected - using reflection-based skin application");
        }
    }

    /**
     * Apply a skin property to a player. May be called from any thread.
     *
     * @param player   The player to apply the skin to
     * @param property The skin property containing texture value and signature
     */
    public void applySkin(@NotNull Player player, @NotNull SkinProperty property) {
        scheduler.runForEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }

            // Store original skin if not already stored
            storeOriginalSkin(player);

            if (property.equals(getCurrentSkin(player))) {
                return;
            }
            setSkin(player, property);
        });
    }

    /**
     * Restore a player's original skin.
     *
     * @param player The player to restore
     * @return true if the original skin was restored
     */
    public boolean restoreOriginalSkin(@NotNull Player player) {
        SkinProperty original = originalSkins.remove(player.getUniqueId());
        if (original == null) {
            return false;
        }

        if (!player.isOnline()) {
            return false;
        }

        scheduler.runForEntity(player, () -> {
            if (player.isOnline()) {
                setSkin(player, original);
            }
        });

        return true;
    }

    private void setSkin(@NotNull Player player, @NotNull SkinProperty property) {
        if (isPaper) {
            // Paper re-sends the profile to every viewer and respawns the player for themselves
            PaperSkinApplier.applySkin(player, property);
        } else {
            SpigotSkinApplier.applySkin(player, property);
            refreshPlayer(player);
        }
    }

    /**
     * Get the player's current skin property.
     *
     * @param player The player
     * @return The current skin property, or null if not found
     */
    @Nullable
    public SkinProperty getCurrentSkin(@NotNull Player player) {
        if (isPaper) {
            return PaperSkinApplier.getSkinProperty(player);
        } else {
            return SpigotSkinApplier.getSkinProperty(player);
        }
    }

    /**
     * Store the player's original skin for later restoration.
     */
    private void storeOriginalSkin(@NotNull Player player) {
        if (!originalSkins.containsKey(player.getUniqueId())) {
            SkinProperty current = getCurrentSkin(player);
            if (current != null) {
                originalSkins.put(player.getUniqueId(), current);
            }
        }
    }

    /**
     * Clear stored original skin when player leaves.
     */
    public void clearStoredSkin(@NotNull UUID playerId) {
        originalSkins.remove(playerId);
    }

    /**
     * Refresh the player so other players can see skin changes (Spigot only).
     * Uses hide/show technique to force client updates.
     */
    private void refreshPlayer(@NotNull Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (!other.canSee(player)) {
                continue;
            }

            // Hide and show the player to force skin update
            other.hidePlayer(plugin, player);
            
            // Small delay to ensure the hide packet is sent
            scheduler.runForEntityLater(player, () -> {
                if (other.isOnline() && player.isOnline()) {
                    other.showPlayer(plugin, player);
                }
            }, 2L);
        }
    }

    /**
     * Check if Paper API is available.
     */
    private boolean detectPaper() {
        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            // Also check if the method exists
            Player.class.getMethod("setPlayerProfile", 
                Class.forName("com.destroystokyo.paper.profile.PlayerProfile"));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Check if running on Paper.
     */
    public boolean isPaper() {
        return isPaper;
    }
}

