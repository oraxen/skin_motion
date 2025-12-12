package dev.th0rgal.customcapes.core.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enum of all available cape types from the Custom Capes API.
 * These correspond to the cape_type enum in the backend database.
 */
public enum CapeType {
    VANILLA("vanilla", "Vanilla"),
    MINECON_2011("minecon_2011", "Minecon 2011"),
    MINECON_2012("minecon_2012", "Minecon 2012"),
    MINECON_2013("minecon_2013", "Minecon 2013"),
    MINECON_2015("minecon_2015", "Minecon 2015"),
    MINECON_2016("minecon_2016", "Minecon 2016"),
    MOJANG("mojang", "Mojang"),
    MOJANG_CLASSIC("mojang_classic", "Mojang Classic"),
    MOJANG_STUDIOS("mojang_studios", "Mojang Studios"),
    REALMS_MAPMAKER("realms_mapmaker", "Realms Mapmaker"),
    COBALT("cobalt", "Cobalt"),
    SCROLLS("scrolls", "Scrolls"),
    TRANSLATOR("translator", "Translator"),
    MILLIONTH_CUSTOMER("millionth_customer", "Millionth Customer"),
    PRISMARINE("prismarine", "Prismarine"),
    BIRTHDAY("birthday", "Birthday"),
    MIGRATOR("migrator", "Migrator"),
    CHERRY_BLOSSOM("cherry_blossom", "Cherry Blossom"),
    ANNIVERSARY_15TH("anniversary_15th", "15th Anniversary");

    private static final Map<String, CapeType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(CapeType::getId, Function.identity()));

    private final String id;
    private final String displayName;

    CapeType(@NotNull String id, @NotNull String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /**
     * Get the API identifier for this cape type.
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * Get the human-readable display name.
     */
    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Find a cape type by its ID.
     *
     * @param id The cape type ID (e.g., "migrator")
     * @return The cape type, or null if not found
     */
    @Nullable
    public static CapeType fromId(@NotNull String id) {
        return BY_ID.get(id.toLowerCase());
    }

    /**
     * Check if a cape type ID is valid.
     */
    public static boolean isValid(@NotNull String id) {
        return BY_ID.containsKey(id.toLowerCase());
    }
}

