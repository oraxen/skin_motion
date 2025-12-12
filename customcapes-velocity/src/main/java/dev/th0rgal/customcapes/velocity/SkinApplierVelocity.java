package dev.th0rgal.customcapes.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import dev.th0rgal.customcapes.core.model.SkinProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles skin application on Velocity proxies using the native GameProfile API.
 */
public final class SkinApplierVelocity {

    // Store original skins for restoration
    private final Map<UUID, SkinProperty> originalSkins = new ConcurrentHashMap<>();

    public SkinApplierVelocity() {
        // No initialization needed - Velocity has native support
    }

    /**
     * Apply a skin property to a player.
     *
     * @param player   The player to apply the skin to
     * @param property The skin property
     */
    public void applySkin(@NotNull Player player, @NotNull SkinProperty property) {
        // Store original skin if not already stored
        storeOriginalSkin(player);

        // Update the player's game profile properties
        List<GameProfile.Property> newProperties = updatePropertiesSkin(
            player.getGameProfileProperties(),
            property
        );
        
        player.setGameProfileProperties(newProperties);
    }

    /**
     * Restore a player's original skin.
     */
    public boolean restoreOriginalSkin(@NotNull Player player) {
        SkinProperty original = originalSkins.remove(player.getUniqueId());
        if (original == null) {
            return false;
        }

        List<GameProfile.Property> newProperties = updatePropertiesSkin(
            player.getGameProfileProperties(),
            original
        );
        
        player.setGameProfileProperties(newProperties);
        return true;
    }

    /**
     * Get the current skin property from a player.
     *
     * @param player The player
     * @return The skin property, or null if not found
     */
    @Nullable
    public SkinProperty getSkinProperty(@NotNull Player player) {
        for (GameProfile.Property property : player.getGameProfileProperties()) {
            if (SkinProperty.TEXTURES_NAME.equals(property.getName())) {
                String value = property.getValue();
                String signature = property.getSignature();
                
                if (value != null && signature != null) {
                    return new SkinProperty(value, signature);
                }
            }
        }
        return null;
    }

    /**
     * Update a GameProfile with a new skin property.
     */
    public GameProfile updateProfileSkin(@NotNull GameProfile profile, @NotNull SkinProperty property) {
        List<GameProfile.Property> newProperties = updatePropertiesSkin(profile.getProperties(), property);
        return new GameProfile(profile.getId(), profile.getName(), newProperties);
    }

    /**
     * Update a list of properties with a new skin.
     */
    private List<GameProfile.Property> updatePropertiesSkin(
            @NotNull List<GameProfile.Property> original,
            @NotNull SkinProperty property
    ) {
        List<GameProfile.Property> properties = new ArrayList<>(original);
        
        // Remove existing texture property
        properties.removeIf(p -> SkinProperty.TEXTURES_NAME.equals(p.getName()));
        
        // Add new texture property
        properties.add(new GameProfile.Property(
            SkinProperty.TEXTURES_NAME,
            property.getValue(),
            property.getSignature()
        ));
        
        return properties;
    }

    /**
     * Store the player's original skin for later restoration.
     */
    private void storeOriginalSkin(@NotNull Player player) {
        if (!originalSkins.containsKey(player.getUniqueId())) {
            SkinProperty current = getSkinProperty(player);
            if (current != null) {
                originalSkins.put(player.getUniqueId(), current);
            }
        }
    }

    /**
     * Clear stored original skin.
     */
    public void clearStoredSkin(@NotNull UUID playerId) {
        originalSkins.remove(playerId);
    }
}

