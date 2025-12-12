package dev.th0rgal.customcapes.bukkit.spigot;

import dev.th0rgal.customcapes.core.model.SkinProperty;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Spigot-specific skin application using reflection to access GameProfile.
 * This is a fallback for servers without the Paper PlayerProfile API.
 */
public final class SpigotSkinApplier {

    private static Class<?> craftPlayerClass;
    private static Method getProfileMethod;
    private static Method getPropertiesMethod;
    private static Class<?> propertyClass;
    private static java.lang.reflect.Constructor<?> propertyConstructor;
    
    private static boolean initialized = false;
    private static boolean available = false;

    private SpigotSkinApplier() {
        // Utility class
    }

    /**
     * Initialize reflection for accessing Spigot internals.
     */
    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            // Get CraftPlayer class
            String version = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            
            // Get getProfile method
            getProfileMethod = craftPlayerClass.getMethod("getProfile");
            
            // Get GameProfile's getProperties method
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            getPropertiesMethod = gameProfileClass.getMethod("getProperties");
            
            // Get Property class
            propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
            
            available = true;
        } catch (Exception e) {
            // Try alternative class path for newer versions (1.20.5+)
            try {
                craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                getProfileMethod = craftPlayerClass.getMethod("getProfile");
                
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                getPropertiesMethod = gameProfileClass.getMethod("getProperties");
                
                propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
                
                available = true;
            } catch (Exception e2) {
                System.err.println("[CustomCapes] Failed to initialize Spigot skin applier: " + e2.getMessage());
                available = false;
            }
        }
    }

    /**
     * Apply a skin property to a player using reflection.
     *
     * @param player   The player to apply the skin to
     * @param property The skin property to apply
     */
    @SuppressWarnings("unchecked")
    public static void applySkin(@NotNull Player player, @NotNull SkinProperty property) {
        init();
        if (!available) {
            return;
        }

        try {
            Object profile = getProfileMethod.invoke(player);
            Object properties = getPropertiesMethod.invoke(profile);
            
            // PropertyMap extends ForwardingMultimap<String, Property>
            // Remove existing textures
            if (properties instanceof com.google.common.collect.Multimap) {
                com.google.common.collect.Multimap<String, Object> propertyMap = 
                    (com.google.common.collect.Multimap<String, Object>) properties;
                
                propertyMap.removeAll(SkinProperty.TEXTURES_NAME);
                
                // Add new property
                Object newProperty = propertyConstructor.newInstance(
                    SkinProperty.TEXTURES_NAME,
                    property.getValue(),
                    property.getSignature()
                );
                propertyMap.put(SkinProperty.TEXTURES_NAME, newProperty);
            }
        } catch (Exception e) {
            System.err.println("[CustomCapes] Failed to apply skin via Spigot: " + e.getMessage());
        }
    }

    /**
     * Get the current skin property from a player using reflection.
     *
     * @param player The player to get the skin from
     * @return The skin property, or null if not found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static SkinProperty getSkinProperty(@NotNull Player player) {
        init();
        if (!available) {
            return null;
        }

        try {
            Object profile = getProfileMethod.invoke(player);
            Object properties = getPropertiesMethod.invoke(profile);
            
            if (properties instanceof com.google.common.collect.Multimap) {
                com.google.common.collect.Multimap<String, Object> propertyMap = 
                    (com.google.common.collect.Multimap<String, Object>) properties;
                
                Collection<Object> textureProperties = propertyMap.get(SkinProperty.TEXTURES_NAME);
                if (textureProperties.isEmpty()) {
                    return null;
                }
                
                Object prop = textureProperties.iterator().next();
                
                // Get value and signature from Property
                Method getValueMethod = propertyClass.getMethod("getValue");
                Method getSignatureMethod = propertyClass.getMethod("getSignature");
                
                String value = (String) getValueMethod.invoke(prop);
                String signature = (String) getSignatureMethod.invoke(prop);
                
                if (value != null && signature != null) {
                    return new SkinProperty(value, signature);
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomCapes] Failed to get skin via Spigot: " + e.getMessage());
        }

        return null;
    }

    /**
     * Check if Spigot skin application is available.
     */
    public static boolean isAvailable() {
        init();
        return available;
    }
}

