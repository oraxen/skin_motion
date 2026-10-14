package dev.th0rgal.skinmotion.bukkit.nms;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.th0rgal.skinmotion.core.model.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Builds and sends {@code ClientboundPlayerInfoUpdatePacket(ADD_PLAYER)} packets carrying a custom texture,
 * using reflection against the Mojang-mapped server (Paper/Folia 1.20.5+, including 26.x).
 * <p>
 * Supports both authlib layouts: the record {@code GameProfile(UUID, String, PropertyMap)} with an immutable
 * {@code PropertyMap} (authlib 7+, Minecraft 1.21.9+) and the older mutable {@code getProperties()} map.
 */
public final class PlayerInfoPackets {

    private static final String NMS = "net.minecraft.network.protocol.game.";

    private final Method getHandle;
    private final Field connectionField;
    private final Method sendMethod;
    private final Constructor<?> propertyConstructor;
    @Nullable
    private final Constructor<?> recordProfileConstructor;
    @Nullable
    private final Constructor<?> propertyMapConstructor;
    @Nullable
    private final Constructor<?> legacyProfileConstructor;
    @Nullable
    private final Method legacyGetProperties;
    private final Constructor<?> entryConstructor;
    private final Class<?>[] entryParameterTypes;
    private final Object addPlayerAction;
    private final Constructor<?> packetConstructor;

    private PlayerInfoPackets() throws ReflectiveOperationException {
        Class<?> craftPlayer = craftBukkitClass("entity.CraftPlayer");
        getHandle = craftPlayer.getMethod("getHandle");
        Class<?> serverPlayer = getHandle.getReturnType();

        connectionField = serverPlayer.getField("connection");
        sendMethod = findSendMethod(connectionField.getType());

        Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> property = Class.forName("com.mojang.authlib.properties.Property");
        Class<?> propertyMap = Class.forName("com.mojang.authlib.properties.PropertyMap");
        propertyConstructor = property.getConstructor(String.class, String.class, String.class);

        Constructor<?> recordCtor = null;
        Constructor<?> mapCtor = null;
        Constructor<?> legacyCtor = null;
        Method legacyProperties = null;
        try {
            recordCtor = gameProfile.getConstructor(UUID.class, String.class, propertyMap);
            mapCtor = propertyMap.getConstructor(Multimap.class);
        } catch (NoSuchMethodException e) {
            legacyCtor = gameProfile.getConstructor(UUID.class, String.class);
            legacyProperties = gameProfile.getMethod("getProperties");
        }
        recordProfileConstructor = recordCtor;
        propertyMapConstructor = mapCtor;
        legacyProfileConstructor = legacyCtor;
        legacyGetProperties = legacyProperties;

        Class<?> packetClass = Class.forName(NMS + "ClientboundPlayerInfoUpdatePacket");
        Class<?> entryClass = Class.forName(NMS + "ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> actionClass = Class.forName(NMS + "ClientboundPlayerInfoUpdatePacket$Action");

        entryConstructor = canonicalConstructor(entryClass);
        entryParameterTypes = entryConstructor.getParameterTypes();
        if (!List.of(entryParameterTypes).contains(gameProfile)) {
            throw new NoSuchMethodException("PlayerInfoUpdate entry has no GameProfile component");
        }
        addPlayerAction = actionClass.getField("ADD_PLAYER").get(null);
        packetConstructor = packetClass.getDeclaredConstructor(EnumSet.class, List.class);
        packetConstructor.setAccessible(true);
    }

    /**
     * Resolve every reflective handle up front.
     *
     * @return the packet helper, or null when this server is not supported
     */
    @Nullable
    public static PlayerInfoPackets create(@NotNull Logger logger) {
        try {
            PlayerInfoPackets packets = new PlayerInfoPackets();
            logger.info("Animation packets ready ("
                    + (packets.recordProfileConstructor != null ? "record GameProfile" : "legacy GameProfile")
                    + ", " + packets.entryParameterTypes.length + "-field PlayerInfo entry)");
            return packets;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warning("Animated skins are unavailable on this server (requires a Mojang-mapped Paper/Folia "
                    + "1.20.5+ runtime): " + e);
            return null;
        }
    }

    /**
     * Create an ADD_PLAYER packet announcing {@code playerId} with the given texture.
     */
    @NotNull
    public Object createAddPlayerPacket(@NotNull UUID playerId, @NotNull String name, @NotNull SkinProperty skin)
            throws ReflectiveOperationException {
        Object profile = createProfile(playerId, name, skin);

        Object[] args = new Object[entryParameterTypes.length];
        for (int i = 0; i < entryParameterTypes.length; i++) {
            Class<?> type = entryParameterTypes[i];
            if (type == UUID.class) {
                args[i] = playerId;
            } else if (type.isInstance(profile)) {
                args[i] = profile;
            } else if (type == boolean.class) {
                args[i] = true;
            } else if (type == int.class) {
                args[i] = 0;
            } else {
                // Only the profile is serialized for ADD_PLAYER; the other fields are ignored
                args[i] = null;
            }
        }
        Object entry = entryConstructor.newInstance(args);
        return packetConstructor.newInstance(enumSetOf(addPlayerAction), List.of(entry));
    }

    /**
     * Send a packet to a player. Safe to call from any thread: the connection queues writes on its channel.
     */
    public void send(@NotNull Player viewer, @NotNull Object packet) throws ReflectiveOperationException {
        Object connection = connectionField.get(getHandle.invoke(viewer));
        if (connection != null) {
            sendMethod.invoke(connection, packet);
        }
    }

    private Object createProfile(UUID playerId, String name, SkinProperty skin) throws ReflectiveOperationException {
        Object texture = propertyConstructor.newInstance(SkinProperty.TEXTURES_NAME, skin.getValue(), skin.getSignature());
        if (recordProfileConstructor != null && propertyMapConstructor != null) {
            Object properties = propertyMapConstructor.newInstance(ImmutableMultimap.of(SkinProperty.TEXTURES_NAME, texture));
            return recordProfileConstructor.newInstance(playerId, name, properties);
        }

        Object profile = legacyProfileConstructor.newInstance(playerId, name);
        Object properties = legacyGetProperties.invoke(profile);
        properties.getClass().getMethod("put", Object.class, Object.class)
                .invoke(properties, SkinProperty.TEXTURES_NAME, texture);
        return profile;
    }

    private static Class<?> craftBukkitClass(String name) throws ClassNotFoundException {
        // Paper 1.20.5+ dropped the versioned package; Spigot still relocates it
        String versionedPackage = Bukkit.getServer().getClass().getPackage().getName();
        try {
            return Class.forName(versionedPackage + "." + name);
        } catch (ClassNotFoundException e) {
            return Class.forName("org.bukkit.craftbukkit." + name);
        }
    }

    private static Method findSendMethod(Class<?> connectionClass) throws NoSuchMethodException {
        Class<?> packetInterface;
        try {
            packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
        } catch (ClassNotFoundException e) {
            throw new NoSuchMethodException("net.minecraft.network.protocol.Packet not found");
        }
        return connectionClass.getMethod("send", packetInterface);
    }

    private static Constructor<?> canonicalConstructor(Class<?> type) throws NoSuchMethodException {
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
            }
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor;
        }
        Constructor<?> widest = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (widest == null || constructor.getParameterCount() > widest.getParameterCount()) {
                widest = constructor;
            }
        }
        if (widest == null) {
            throw new NoSuchMethodException(type.getName() + " has no constructor");
        }
        widest.setAccessible(true);
        return widest;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EnumSet<?> enumSetOf(Object action) {
        return EnumSet.of((Enum) action);
    }
}
