package dev.th0rgal.skinmotion.bukkit.npc;

import dev.th0rgal.skinmotion.core.model.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A fake player (NPC) that uses raw packet sending via reflection.
 * No Paper API or NMS compile dependencies needed.
 */
public class FakePlayer {

    private static final Random RANDOM = new Random();

    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final Location location;
    private SkinProperty currentSkin;
    private Object gameProfile;
    private final Set<Player> viewers = new HashSet<>();

    // Reflection cache
    private static boolean initialized = false;
    private static String initError = null;
    
    // Classes
    private static Class<?> craftPlayerClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> connectionClass;
    private static Class<?> gameProfileClass;
    private static Class<?> propertyClass;
    private static Class<?> playerInfoPacketClass;
    private static Class<?> addEntityPacketClass;
    private static Class<?> removeEntitiesPacketClass;
    private static Class<?> playerInfoRemovePacketClass;
    private static Class<?> entityTypeClass;
    private static Class<?> vec3Class;
    private static Class<?> actionEnumClass;
    private static Class<?> entryRecordClass;
    private static Class<?> gameTypeClass;
    private static Class<?> setEntityDataPacketClass;
    private static Class<?> synchedEntityDataClass;
    private static Class<?> dataValueClass;
    private static Class<?> entityDataSerializerClass;
    private static Object byteSerializer;
    
    // Methods
    private static Method getHandleMethod;
    private static Method sendMethod;
    private static Method getPropertiesMethod;
    private static Method propertyMapPutMethod;
    
    // Fields
    private static Field connectionField;
    private static Object playerEntityType;
    private static Object vec3Zero;
    private static Object survivalGameType;

    static {
        try {
            initReflection();
            initialized = true;
            Bukkit.getLogger().info("[FakePlayer] Reflection initialized successfully");
        } catch (Exception e) {
            initError = e.getMessage();
            Bukkit.getLogger().severe("[FakePlayer] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initReflection() throws Exception {
        // Find CraftPlayer class (Paper 1.20.5+ has non-versioned package)
        try {
            craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        } catch (ClassNotFoundException e) {
            // Fallback for older versions
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.split("\\.")[3];
            craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
        }

        getHandleMethod = craftPlayerClass.getMethod("getHandle");
        serverPlayerClass = getHandleMethod.getReturnType();

        // Find connection field
        for (Field f : serverPlayerClass.getFields()) {
            if (f.getName().equals("connection")) {
                connectionField = f;
                connectionClass = f.getType();
                break;
            }
        }

        // Find send method on connection
        for (Method m : connectionClass.getMethods()) {
            if (m.getName().equals("send") && m.getParameterCount() == 1) {
                sendMethod = m;
                break;
            }
        }

        // GameProfile from authlib
        gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        getPropertiesMethod = gameProfileClass.getMethod("getProperties");

        // NMS classes
        String nms = "net.minecraft";
        playerInfoPacketClass = Class.forName(nms + ".network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        addEntityPacketClass = Class.forName(nms + ".network.protocol.game.ClientboundAddEntityPacket");
        removeEntitiesPacketClass = Class.forName(nms + ".network.protocol.game.ClientboundRemoveEntitiesPacket");
        playerInfoRemovePacketClass = Class.forName(nms + ".network.protocol.game.ClientboundPlayerInfoRemovePacket");
        
        entityTypeClass = Class.forName(nms + ".world.entity.EntityType");
        playerEntityType = entityTypeClass.getField("PLAYER").get(null);
        
        vec3Class = Class.forName(nms + ".world.phys.Vec3");
        vec3Zero = vec3Class.getField("ZERO").get(null);
        
        gameTypeClass = Class.forName(nms + ".world.level.GameType");
        survivalGameType = Enum.valueOf((Class<Enum>) gameTypeClass, "SURVIVAL");
        
        actionEnumClass = Class.forName(nms + ".network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
        entryRecordClass = Class.forName(nms + ".network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        
        // Metadata packet classes for skin layers
        setEntityDataPacketClass = Class.forName(nms + ".network.protocol.game.ClientboundSetEntityDataPacket");
        synchedEntityDataClass = Class.forName(nms + ".network.syncher.SynchedEntityData");
        
        // Try to find DataValue class (1.19.3+) or fallback to DataItem
        try {
            dataValueClass = Class.forName(nms + ".network.syncher.SynchedEntityData$DataValue");
        } catch (ClassNotFoundException e) {
            // Older versions use DataItem
            dataValueClass = Class.forName(nms + ".network.syncher.SynchedEntityData$DataItem");
        }
        
        // Get BYTE serializer
        entityDataSerializerClass = Class.forName(nms + ".network.syncher.EntityDataSerializers");
        byteSerializer = entityDataSerializerClass.getField("BYTE").get(null);
    }
    
    /**
     * Send displayed skin parts metadata packet.
     * This makes all skin overlay layers (hat, jacket, sleeves, pants) visible.
     * 
     * @param connection The player's network connection
     * @param entityId The entity ID to update
     */
    private static void sendDisplayedSkinPartsPacket(Object connection, int entityId) {
        try {
            // Displayed Skin Parts byte: 0x7F = all parts visible
            // Bit 0 (0x01): Cape
            // Bit 1 (0x02): Jacket
            // Bit 2 (0x04): Left Sleeve
            // Bit 3 (0x08): Right Sleeve
            // Bit 4 (0x10): Left Pants Leg
            // Bit 5 (0x20): Right Pants Leg
            // Bit 6 (0x40): Hat
            byte displayedParts = 0x7F; // All parts visible
            
            // Player entity metadata index for displayed skin parts is 17 in 1.20.x
            int skinPartsIndex = 17;
            
            // Create DataValue for the skin parts
            // DataValue.create(EntityDataAccessor, value) or new DataValue(id, serializer, value)
            Object dataValue = null;
            
            for (Constructor<?> ctor : dataValueClass.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3 && params[0] == int.class) {
                    // Constructor: DataValue(int id, EntityDataSerializer serializer, Object value)
                    ctor.setAccessible(true);
                    dataValue = ctor.newInstance(skinPartsIndex, byteSerializer, displayedParts);
                    break;
                }
            }
            
            if (dataValue == null) {
                // Try alternative: use pack() method
                Method packMethod = dataValueClass.getMethod("pack", int.class, Object.class, Object.class);
                dataValue = packMethod.invoke(null, skinPartsIndex, byteSerializer, displayedParts);
            }
            
            // Create the metadata packet
            // ClientboundSetEntityDataPacket(int entityId, List<DataValue> packedItems)
            for (Constructor<?> ctor : setEntityDataPacketClass.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2 && params[0] == int.class && params[1] == List.class) {
                    ctor.setAccessible(true);
                    Object packet = ctor.newInstance(entityId, List.of(dataValue));
                    sendMethod.invoke(connection, packet);
                    return;
                }
            }
            
            Bukkit.getLogger().warning("[FakePlayer] Could not create metadata packet - skin layers may not show");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[FakePlayer] Failed to send skin parts metadata: " + e.getMessage());
        }
    }

    public FakePlayer(@NotNull Location location, @NotNull SkinProperty skin) {
        if (!initialized) {
            throw new RuntimeException("FakePlayer reflection not initialized: " + initError);
        }

        this.entityId = RANDOM.nextInt(Integer.MAX_VALUE / 2) + 1000000;
        this.uuid = UUID.randomUUID();
        this.name = "NPC" + Integer.toHexString(RANDOM.nextInt(0xFFFF));
        this.location = location.clone();
        this.currentSkin = skin;

        createGameProfile();
    }

    private void createGameProfile() {
        try {
            // Create GameProfile(UUID, String)
            Constructor<?> gpCtor = gameProfileClass.getConstructor(UUID.class, String.class);
            gameProfile = gpCtor.newInstance(uuid, name);

            // Get properties multimap
            Object properties = getPropertiesMethod.invoke(gameProfile);

            // Create Property("textures", value, signature)
            Constructor<?> propCtor = propertyClass.getConstructor(String.class, String.class, String.class);
            Object textureProperty = propCtor.newInstance("textures", currentSkin.getValue(), currentSkin.getSignature());

            // Add to properties map: put("textures", property)
            Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", textureProperty);

        } catch (Exception e) {
            Bukkit.getLogger().severe("[FakePlayer] Failed to create GameProfile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void spawn(@NotNull Player viewer) {
        if (viewers.contains(viewer) || gameProfile == null) return;
        viewers.add(viewer);

        try {
            Object handle = getHandleMethod.invoke(viewer);
            Object connection = connectionField.get(handle);

            // Send player info packet
            Object infoPacket = createPlayerInfoPacket();
            if (infoPacket != null) {
                sendMethod.invoke(connection, infoPacket);
                Bukkit.getLogger().info("[FakePlayer] Sent PlayerInfo packet");
            } else {
                Bukkit.getLogger().warning("[FakePlayer] PlayerInfo packet was NULL");
            }

            // Send spawn entity packet
            Object spawnPacket = createSpawnPacket();
            if (spawnPacket != null) {
                sendMethod.invoke(connection, spawnPacket);
                Bukkit.getLogger().info("[FakePlayer] Sent spawn packet for entity " + entityId);
            } else {
                Bukkit.getLogger().warning("[FakePlayer] Spawn packet was NULL - entity won't appear!");
            }

            // Remove from tablist after delay (keep longer so skin loads)
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("SkinMotion"),
                () -> removeFromTablist(viewer),
                40L // 2 seconds to let skin load
            );

        } catch (Exception e) {
            Bukkit.getLogger().severe("[FakePlayer] Failed to spawn: " + e.getMessage());
            e.printStackTrace();
            viewers.remove(viewer);
        }
    }

    private Object createPlayerInfoPacket() {
        try {
            // Create Entry record
            Constructor<?> entryCtor = entryRecordClass.getConstructors()[0];
            Class<?>[] paramTypes = entryCtor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (type == UUID.class) {
                    args[i] = uuid;
                } else if (type.getName().contains("GameProfile")) {
                    args[i] = gameProfile;
                } else if (type == boolean.class) {
                    args[i] = true; // listed - must be true for skin to show!
                } else if (type == int.class) {
                    args[i] = 0; // latency
                } else if (type.getName().contains("GameType")) {
                    args[i] = survivalGameType;
                } else {
                    args[i] = null; // Component, ChatSession, etc.
                }
            }

            Object entry = entryCtor.newInstance(args);

            // Create packet using static method or constructor
            // Try createPlayerInitializing first (takes Collection<ServerPlayer>)
            // But we need to use Entry-based constructor

            // Find constructor that takes EnumSet and Collection
            for (Constructor<?> ctor : playerInfoPacketClass.getConstructors()) {
                Class<?>[] ctorParams = ctor.getParameterTypes();
                if (ctorParams.length == 2 && 
                    ctorParams[0] == EnumSet.class && 
                    Collection.class.isAssignableFrom(ctorParams[1])) {
                    
                    // Get ADD_PLAYER action
                    Object addPlayerAction = Enum.valueOf((Class<Enum>) actionEnumClass, "ADD_PLAYER");
                    EnumSet<?> actions = EnumSet.of((Enum) addPlayerAction);
                    
                    // This constructor expects Collection<ServerPlayer>, not Entry
                    // We need a different approach...
                    break;
                }
            }

            // Use the Entry-list based approach with writeToBuffer
            // Actually, let's try a direct packet construction using internal record list

            // Alternative: Use reflection to set the entries list directly
            Object packet = createPacketViaEntries(entry);
            return packet;

        } catch (Exception e) {
            Bukkit.getLogger().severe("[FakePlayer] Failed to create info packet: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Object createPacketViaEntries(Object entry) throws Exception {
        // Create the packet with an empty/dummy setup, then replace entries
        // Use the EnumSet constructor but pass entries that won't cause ClassCastException

        // Get ADD_PLAYER action
        Object addPlayerAction = Enum.valueOf((Class<Enum>) actionEnumClass, "ADD_PLAYER");
        EnumSet<?> actions = EnumSet.of((Enum) addPlayerAction);

        // Find a way to construct... let's try the Unsafe route or buffer-based
        // Actually, let's use the record-canonical constructor that builds from entries

        // Try to find a private constructor or use Unsafe
        Constructor<?> entriesConstructor = null;
        for (Constructor<?> ctor : playerInfoPacketClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            // Look for (EnumSet, List<Entry>) pattern
            if (params.length == 2 && params[0] == EnumSet.class && params[1] == List.class) {
                entriesConstructor = ctor;
                break;
            }
        }

        if (entriesConstructor != null) {
            entriesConstructor.setAccessible(true);
            return entriesConstructor.newInstance(actions, List.of(entry));
        }

        // If no direct constructor, try creating via buffer
        return createPacketViaBuffer(entry, actions);
    }

    private Object createPacketViaBuffer(Object entry, EnumSet<?> actions) throws Exception {
        // This is complex - skip for now and return null
        // The packet will be created differently
        Bukkit.getLogger().warning("[FakePlayer] Could not find suitable packet constructor");
        return null;
    }

    private Object createSpawnPacket() {
        try {
            // Find constructor: (int id, UUID uuid, double x, y, z, float pitch, yaw, EntityType, int data, Vec3, double headYaw)
            for (Constructor<?> ctor : addEntityPacketClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                Bukkit.getLogger().info("[FakePlayer] Found AddEntity constructor with " + params.length + " params");
                
                if (params.length >= 10 && params[0] == int.class && params[1] == UUID.class) {
                    Object packet = ctor.newInstance(
                        entityId,
                        uuid,
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getPitch(),
                        location.getYaw(),
                        playerEntityType,
                        0,
                        vec3Zero,
                        (double) location.getYaw()
                    );
                    Bukkit.getLogger().info("[FakePlayer] Created spawn packet successfully");
                    return packet;
                }
            }
            Bukkit.getLogger().warning("[FakePlayer] No suitable AddEntity constructor found!");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[FakePlayer] Failed to create spawn packet: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private void removeFromTablist(Player viewer) {
        if (!viewer.isOnline()) return;
        try {
            Object handle = getHandleMethod.invoke(viewer);
            Object connection = connectionField.get(handle);

            Constructor<?> ctor = playerInfoRemovePacketClass.getConstructor(List.class);
            Object packet = ctor.newInstance(List.of(uuid));
            sendMethod.invoke(connection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void despawn(@NotNull Player viewer) {
        if (!viewers.remove(viewer)) return;
        try {
            Object handle = getHandleMethod.invoke(viewer);
            Object connection = connectionField.get(handle);

            // Remove entity
            Constructor<?> removeCtor = removeEntitiesPacketClass.getConstructor(int[].class);
            Object removePacket = removeCtor.newInstance((Object) new int[]{entityId});
            sendMethod.invoke(connection, removePacket);

            // Remove from tablist
            Constructor<?> infoRemoveCtor = playerInfoRemovePacketClass.getConstructor(List.class);
            Object infoRemovePacket = infoRemoveCtor.newInstance(List.of(uuid));
            sendMethod.invoke(connection, infoRemovePacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void despawnAll() {
        for (Player viewer : new HashSet<>(viewers)) {
            despawn(viewer);
        }
    }

    public void updateSkin(@NotNull SkinProperty newSkin) {
        this.currentSkin = newSkin;
        createGameProfile();

        for (Player viewer : new HashSet<>(viewers)) {
            if (!viewer.isOnline()) {
                viewers.remove(viewer);
                continue;
            }

            try {
                Object handle = getHandleMethod.invoke(viewer);
                Object connection = connectionField.get(handle);

                // All packets sent immediately in same tick - no delays!
                
                // 1. Remove entity
                Constructor<?> removeEntityCtor = removeEntitiesPacketClass.getConstructor(int[].class);
                Object removeEntityPacket = removeEntityCtor.newInstance((Object) new int[]{entityId});
                sendMethod.invoke(connection, removeEntityPacket);

                // 2. Remove from tablist
                Constructor<?> infoRemoveCtor = playerInfoRemovePacketClass.getConstructor(List.class);
                Object infoRemovePacket = infoRemoveCtor.newInstance(List.of(uuid));
                sendMethod.invoke(connection, infoRemovePacket);

                // 3. Add to tablist with new skin
                Object infoPacket = createPlayerInfoPacket();
                if (infoPacket != null) {
                    sendMethod.invoke(connection, infoPacket);
                }

                // 4. Respawn entity (will use new skin from tablist)
                Object spawnPacket = createSpawnPacket();
                if (spawnPacket != null) {
                    sendMethod.invoke(connection, spawnPacket);
                }

                // 5. Remove from tablist quickly (skin should already be cached)
                Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("SkinMotion"),
                    () -> {
                        if (viewer.isOnline()) {
                            removeFromTablist(viewer);
                        }
                    },
                    2L // Very quick - skin is already cached client-side
                );

            } catch (Exception e) {
                Bukkit.getLogger().warning("[FakePlayer] Failed to update skin: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public int getEntityId() { return entityId; }
    public UUID getUuid() { return uuid; }
    public Location getLocation() { return location.clone(); }
    public Set<Player> getViewers() { return Collections.unmodifiableSet(viewers); }

    /**
     * Send a skin update packet for a real player to a specific viewer.
     * This updates how the viewer sees the target player's skin.
     * 
     * @param viewer The player who will see the updated skin
     * @param target The player whose skin is being updated
     * @param skin   The new skin to display
     */
    public static void sendSkinUpdatePacket(@NotNull Player viewer, @NotNull Player target, @NotNull SkinProperty skin) {
        if (!initialized) {
            throw new RuntimeException("FakePlayer reflection not initialized: " + initError);
        }

        try {
            Object viewerHandle = getHandleMethod.invoke(viewer);
            Object viewerConnection = connectionField.get(viewerHandle);

            Object targetHandle = getHandleMethod.invoke(target);
            int targetEntityId = (int) targetHandle.getClass().getMethod("getId").invoke(targetHandle);

            // Get target's GameProfile and update textures
            Object targetGameProfile = targetHandle.getClass().getMethod("getGameProfile").invoke(targetHandle);
            Object properties = getPropertiesMethod.invoke(targetGameProfile);

            // Remove existing textures
            Method removeAllMethod = properties.getClass().getMethod("removeAll", Object.class);
            removeAllMethod.invoke(properties, "textures");

            // Add new texture property
            Constructor<?> propCtor = propertyClass.getConstructor(String.class, String.class, String.class);
            Object textureProperty = propCtor.newInstance("textures", skin.getValue(), skin.getSignature());
            Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", textureProperty);

            // Remove entity from viewer's client
            Constructor<?> removeEntityCtor = removeEntitiesPacketClass.getConstructor(int[].class);
            Object removeEntityPacket = removeEntityCtor.newInstance((Object) new int[]{targetEntityId});
            sendMethod.invoke(viewerConnection, removeEntityPacket);

            // Remove from tablist
            Constructor<?> infoRemoveCtor = playerInfoRemovePacketClass.getConstructor(List.class);
            Object infoRemovePacket = infoRemoveCtor.newInstance(List.of(target.getUniqueId()));
            sendMethod.invoke(viewerConnection, infoRemovePacket);

            // Re-add to tablist with new skin (using UPDATE_GAME_MODE action to refresh)
            // Create the player info entry with updated skin
            Object addPlayerAction = Enum.valueOf((Class<Enum>) actionEnumClass, "ADD_PLAYER");
            EnumSet<?> actions = EnumSet.of((Enum) addPlayerAction);

            // Build Entry record
            Constructor<?> entryCtor = null;
            for (Constructor<?> ctor : entryRecordClass.getDeclaredConstructors()) {
                if (ctor.getParameterCount() >= 6) {
                    entryCtor = ctor;
                    break;
                }
            }

            if (entryCtor != null) {
                entryCtor.setAccessible(true);
                Object[] args = new Object[entryCtor.getParameterCount()];
                Class<?>[] paramTypes = entryCtor.getParameterTypes();
                
                for (int i = 0; i < paramTypes.length; i++) {
                    if (paramTypes[i] == UUID.class) {
                        args[i] = target.getUniqueId();
                    } else if (paramTypes[i] == targetGameProfile.getClass() || paramTypes[i].getSimpleName().equals("GameProfile")) {
                        args[i] = targetGameProfile;
                    } else if (paramTypes[i] == boolean.class) {
                        args[i] = true; // listed
                    } else if (paramTypes[i] == int.class) {
                        args[i] = target.getPing(); // latency
                    } else if (paramTypes[i] == gameTypeClass) {
                        args[i] = survivalGameType;
                    } else {
                        args[i] = null;
                    }
                }

                Object entry = entryCtor.newInstance(args);

                // Create packet with entries
                for (Constructor<?> packetCtor : playerInfoPacketClass.getDeclaredConstructors()) {
                    Class<?>[] params = packetCtor.getParameterTypes();
                    if (params.length == 2 && params[0] == EnumSet.class && params[1] == List.class) {
                        packetCtor.setAccessible(true);
                        Object infoPacket = packetCtor.newInstance(actions, List.of(entry));
                        sendMethod.invoke(viewerConnection, infoPacket);
                        break;
                    }
                }
            }

            // Re-spawn the entity
            // Get target's spawn packet by calling the respawn packet method or manually
            Method getX = targetHandle.getClass().getMethod("getX");
            Method getY = targetHandle.getClass().getMethod("getY");
            Method getZ = targetHandle.getClass().getMethod("getZ");
            Method getXRot = targetHandle.getClass().getMethod("getXRot");
            Method getYRot = targetHandle.getClass().getMethod("getYRot");

            for (Constructor<?> ctor : addEntityPacketClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length >= 10 && params[0] == int.class && params[1] == UUID.class) {
                    Object spawnPacket = ctor.newInstance(
                        targetEntityId,
                        target.getUniqueId(),
                        (double) getX.invoke(targetHandle),
                        (double) getY.invoke(targetHandle),
                        (double) getZ.invoke(targetHandle),
                        (float) getXRot.invoke(targetHandle),
                        (float) getYRot.invoke(targetHandle),
                        playerEntityType,
                        0,
                        vec3Zero,
                        (double) (float) getYRot.invoke(targetHandle)
                    );
                    sendMethod.invoke(viewerConnection, spawnPacket);
                    break;
                }
            }
            
            // Send displayed skin parts metadata to show overlay layers (hat, jacket, sleeves, pants)
            sendDisplayedSkinPartsPacket(viewerConnection, targetEntityId);

            // Remove from tablist after short delay
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("SkinMotion"),
                () -> {
                    if (viewer.isOnline()) {
                        try {
                            Object handle = getHandleMethod.invoke(viewer);
                            Object conn = connectionField.get(handle);
                            Constructor<?> removeCtor = playerInfoRemovePacketClass.getConstructor(List.class);
                            Object removePacket = removeCtor.newInstance(List.of(target.getUniqueId()));
                            sendMethod.invoke(conn, removePacket);
                        } catch (Exception ignored) {}
                    }
                },
                2L
            );

        } catch (Exception e) {
            Bukkit.getLogger().warning("[FakePlayer] Failed to send skin update: " + e.getMessage());
        }
    }
}
