package dev.th0rgal.customcapes.bukkit.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.th0rgal.customcapes.core.model.SkinProperty;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Paper-specific skin application using the native PlayerProfile API.
 * This provides the cleanest and most reliable skin application.
 */
public final class PaperSkinApplier {

    private PaperSkinApplier() {
        // Utility class
    }

    /**
     * Apply a skin property to a player using Paper's PlayerProfile API.
     *
     * @param player   The player to apply the skin to
     * @param property The skin property to apply
     */
    public static void applySkin(@NotNull Player player, @NotNull SkinProperty property) {
        PlayerProfile profile = player.getPlayerProfile();

        // Remove existing texture property
        profile.getProperties().removeIf(prop -> 
            prop.getName().equals(SkinProperty.TEXTURES_NAME));

        // Add new texture property
        profile.getProperties().add(new ProfileProperty(
            SkinProperty.TEXTURES_NAME,
            property.getValue(),
            property.getSignature()
        ));

        // Apply the updated profile
        player.setPlayerProfile(profile);

        // Send health update to refresh player's own view
        try {
            player.sendHealthUpdate();
        } catch (NoSuchMethodError ignored) {
            // Method not available on older Paper versions
        }
    }

    /**
     * Get the current skin property from a player using Paper's API.
     *
     * @param player The player to get the skin from
     * @return The skin property, or null if not found
     */
    @Nullable
    public static SkinProperty getSkinProperty(@NotNull Player player) {
        PlayerProfile profile = player.getPlayerProfile();

        for (ProfileProperty property : profile.getProperties()) {
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
}

