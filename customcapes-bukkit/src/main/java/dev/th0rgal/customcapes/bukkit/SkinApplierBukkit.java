package dev.th0rgal.customcapes.bukkit;

import dev.th0rgal.customcapes.bukkit.paper.PaperSkinApplier;
import dev.th0rgal.customcapes.bukkit.spigot.SpigotSkinApplier;
import dev.th0rgal.customcapes.core.model.SkinProperty;
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

    private final CustomCapesPlugin plugin;
    private final boolean isPaper;
    
    // Store original skins for restoration
    private final Map<UUID, SkinProperty> originalSkins = new ConcurrentHashMap<>();

    public SkinApplierBukkit(@NotNull CustomCapesPlugin plugin) {
        this.plugin = plugin;
        this.isPaper = detectPaper();
        
        if (isPaper) {
            plugin.getLogger().info("Paper API detected - using native skin application");
        } else {
            plugin.getLogger().info("Spigot detected - using reflection-based skin application");
        }
    }

    /**
     * Apply a skin property to a player.
     *
     * @param player   The player to apply the skin to
     * @param property The skin property containing texture value and signature
     */
    public void applySkin(@NotNull Player player, @NotNull SkinProperty property) {
        if (!player.isOnline()) {
            return;
        }

        // Store original skin if not already stored
        storeOriginalSkin(player);

        // Run on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            // Apply the skin using the appropriate method
            if (isPaper) {
                PaperSkinApplier.applySkin(player, property);
            } else {
                SpigotSkinApplier.applySkin(player, property);
            }

            // Refresh the player for other players to see the change
            refreshPlayer(player);
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

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (isPaper) {
                PaperSkinApplier.applySkin(player, original);
            } else {
                SpigotSkinApplier.applySkin(player, original);
            }

            refreshPlayer(player);
        });

        return true;
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
     * Refresh the player so other players can see skin changes.
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
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (other.isOnline() && player.isOnline()) {
                    other.showPlayer(plugin, player);
                }
            }, 2L);
        }

        // Try to send health update for the player themselves (Paper-specific)
        if (isPaper) {
            try {
                player.sendHealthUpdate();
            } catch (NoSuchMethodError ignored) {
                // Not available on older Paper versions
            }
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

