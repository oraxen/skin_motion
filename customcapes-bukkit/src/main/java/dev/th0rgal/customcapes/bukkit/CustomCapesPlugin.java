package dev.th0rgal.customcapes.bukkit;

import dev.th0rgal.customcapes.bukkit.commands.CapeCommand;
import dev.th0rgal.customcapes.core.api.ApiBackend;
import dev.th0rgal.customcapes.core.api.CustomCapesApiProvider;
import dev.th0rgal.customcapes.core.api.MineSkinApiProvider;
import dev.th0rgal.customcapes.core.api.SkinApiProvider;
import dev.th0rgal.customcapes.core.config.Config;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main entry point for the Custom Capes Bukkit/Paper plugin.
 */
public final class CustomCapesPlugin extends JavaPlugin {

    private static CustomCapesPlugin instance;

    private Config config;
    private SkinApiProvider apiProvider;
    private BukkitAudiences audiences;
    private SkinApplierBukkit skinApplier;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        config = Config.load(getDataFolder());

        // Initialize API provider based on configuration
        apiProvider = createApiProvider(config);

        // Initialize Adventure audiences for messaging
        audiences = BukkitAudiences.create(this);

        // Initialize skin applier
        skinApplier = new SkinApplierBukkit(this);

        // Register commands
        CapeCommand capeCommand = new CapeCommand(this);
        getCommand("cape").setExecutor(capeCommand);
        getCommand("cape").setTabCompleter(capeCommand);

        // Initialize bStats metrics
        new Metrics(this, 23456); // Replace with actual bStats plugin ID

        getLogger().info("Custom Capes enabled! Using backend: " + apiProvider.getName());

        // Check API health in background
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (apiProvider.isHealthy()) {
                getLogger().info(apiProvider.getName() + " is reachable and healthy.");
            } else {
                getLogger().warning(apiProvider.getName() + " is not reachable.");
            }
        });
    }

    @Override
    public void onDisable() {
        if (audiences != null) {
            audiences.close();
        }
        instance = null;
        getLogger().info("Custom Capes disabled.");
    }

    /**
     * Reload the plugin configuration.
     */
    public void reload() {
        config = Config.load(getDataFolder());
        apiProvider = createApiProvider(config);
        getLogger().info("Configuration reloaded. Using backend: " + apiProvider.getName());
    }

    /**
     * Create the appropriate API provider based on configuration.
     */
    @NotNull
    private SkinApiProvider createApiProvider(@NotNull Config config) {
        if (config.getBackend() == ApiBackend.MINESKIN) {
            return new MineSkinApiProvider(
                    config.getMineskinApiKey(),
                    config.getTimeoutSeconds()
            );
        }
        // Default to CustomCapes API
        return new CustomCapesApiProvider(
                config.getCustomCapesUrl(),
                config.getTimeoutSeconds()
        );
    }

    @NotNull
    public static CustomCapesPlugin get() {
        return instance;
    }

    @NotNull
    public Config getPluginConfig() {
        return config;
    }

    /**
     * Get the configured skin API provider.
     */
    @NotNull
    public SkinApiProvider getApiProvider() {
        return apiProvider;
    }

    @NotNull
    public BukkitAudiences getAudiences() {
        return audiences;
    }

    @NotNull
    public SkinApplierBukkit getSkinApplier() {
        return skinApplier;
    }
}
