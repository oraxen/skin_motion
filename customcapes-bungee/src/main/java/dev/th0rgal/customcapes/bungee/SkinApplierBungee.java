package dev.th0rgal.customcapes.bungee;

import dev.th0rgal.customcapes.core.model.SkinProperty;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles skin application on BungeeCord proxies using reflection.
 */
public final class SkinApplierBungee {

    private static Class<?> loginResultClass;
    private static Class<?> propertyClass;
    private static Field loginProfileField;
    private static Field propertiesField;
    private static Constructor<?> loginResultConstructor;
    private static Constructor<?> propertyConstructor;

    private static boolean initialized = false;
    private static boolean available = false;

    // Store original skins for restoration
    private final Map<UUID, SkinProperty> originalSkins = new ConcurrentHashMap<>();

    public SkinApplierBungee() {
        init();
    }

    /**
     * Initialize reflection for BungeeCord internals.
     */
    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            // Get InitialHandler class
            Class<?> initialHandlerClass = Class.forName("net.md_5.bungee.connection.InitialHandler");
            
            // Get loginProfile field
            loginProfileField = findField(initialHandlerClass, "loginProfile");
            if (loginProfileField == null) {
                throw new NoSuchFieldException("loginProfile");
            }
            loginProfileField.setAccessible(true);

            // Get LoginResult class
            loginResultClass = loginProfileField.getType();

            // Get properties field from LoginResult
            propertiesField = findField(loginResultClass, "properties");
            if (propertiesField == null) {
                throw new NoSuchFieldException("properties");
            }
            propertiesField.setAccessible(true);

            // Get Property class
            Class<?> propertyArrayClass = propertiesField.getType();
            propertyClass = propertyArrayClass.getComponentType();

            // Get Property constructor
            propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);

            // Get LoginResult constructor - try multiple signatures
            try {
                // New BungeeCord: LoginResult(String, String, Property[])
                loginResultConstructor = loginResultClass.getConstructor(String.class, String.class, propertyArrayClass);
            } catch (NoSuchMethodException e) {
                // Old BungeeCord: LoginResult(String, Property[])
                loginResultConstructor = loginResultClass.getConstructor(String.class, propertyArrayClass);
            }

            available = true;
        } catch (Exception e) {
            System.err.println("[CustomCapes] Failed to initialize BungeeCord skin applier: " + e.getMessage());
            available = false;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Apply a skin property to a player.
     *
     * @param player   The player to apply the skin to
     * @param property The skin property
     */
    public void applySkin(@NotNull ProxiedPlayer player, @NotNull SkinProperty property) {
        if (!available) {
            return;
        }

        // Store original skin if not already stored
        storeOriginalSkin(player);

        try {
            applyToHandler(player.getPendingConnection(), property);
        } catch (Exception e) {
            System.err.println("[CustomCapes] Failed to apply skin to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Apply skin property directly to a PendingConnection.
     */
    public static void applyToHandler(@NotNull PendingConnection handler, @NotNull SkinProperty property) {
        if (!available) {
            return;
        }

        try {
            // Create new Property array with single element
            Object propertyArray = Array.newInstance(propertyClass, 1);
            Array.set(propertyArray, 0, propertyConstructor.newInstance(
                SkinProperty.TEXTURES_NAME,
                property.getValue(),
                property.getSignature()
            ));

            // Get current loginProfile
            Object loginProfile = loginProfileField.get(handler);

            if (loginProfile == null) {
                // Create new LoginResult
                Object newLoginResult;
                if (loginResultConstructor.getParameterCount() == 3) {
                    newLoginResult = loginResultConstructor.newInstance(null, null, propertyArray);
                } else {
                    newLoginResult = loginResultConstructor.newInstance(null, propertyArray);
                }
                loginProfileField.set(handler, newLoginResult);
            } else {
                // Update existing LoginResult properties
                propertiesField.set(loginProfile, propertyArray);
            }
        } catch (Exception e) {
            System.err.println("[CustomCapes] Failed to apply skin to handler: " + e.getMessage());
        }
    }

    /**
     * Get the current skin property from a player.
     *
     * @param player The player
     * @return The skin property, or null if not found
     */
    @Nullable
    public SkinProperty getSkinProperty(@NotNull ProxiedPlayer player) {
        if (!available) {
            return null;
        }

        try {
            Object loginProfile = loginProfileField.get(player.getPendingConnection());
            if (loginProfile == null) {
                return null;
            }

            Object properties = propertiesField.get(loginProfile);
            if (properties == null || Array.getLength(properties) == 0) {
                return null;
            }

            // Find textures property
            for (int i = 0; i < Array.getLength(properties); i++) {
                Object prop = Array.get(properties, i);
                
                Method getNameMethod = propertyClass.getMethod("getName");
                Method getValueMethod = propertyClass.getMethod("getValue");
                Method getSignatureMethod = propertyClass.getMethod("getSignature");

                String name = (String) getNameMethod.invoke(prop);
                if (SkinProperty.TEXTURES_NAME.equals(name)) {
                    String value = (String) getValueMethod.invoke(prop);
                    String signature = (String) getSignatureMethod.invoke(prop);
                    
                    if (value != null && signature != null) {
                        return new SkinProperty(value, signature);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomCapes] Failed to get skin from " + player.getName() + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * Restore a player's original skin.
     */
    public boolean restoreOriginalSkin(@NotNull ProxiedPlayer player) {
        SkinProperty original = originalSkins.remove(player.getUniqueId());
        if (original == null) {
            return false;
        }

        applySkin(player, original);
        return true;
    }

    /**
     * Store the player's original skin for later restoration.
     */
    private void storeOriginalSkin(@NotNull ProxiedPlayer player) {
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

    /**
     * Check if skin application is available.
     */
    public static boolean isAvailable() {
        init();
        return available;
    }
}

