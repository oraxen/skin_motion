package dev.th0rgal.customcapes.bungee;

import dev.th0rgal.customcapes.bungee.commands.CapeCommand;
import dev.th0rgal.customcapes.core.api.ApiBackend;
import dev.th0rgal.customcapes.core.api.CustomCapesApiProvider;
import dev.th0rgal.customcapes.core.api.MineSkinApiProvider;
import dev.th0rgal.customcapes.core.api.SkinApiProvider;
import dev.th0rgal.customcapes.core.config.Config;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;
import org.jetbrains.annotations.NotNull;

/**
 * Main entry point for the Custom Capes BungeeCord plugin.
 */
public final class CustomCapesBungee extends Plugin {

    private Config config;
    private SkinApiProvider apiProvider;
    private BungeeAudiences audiences;
    private SkinApplierBungee skinApplier;

    @Override
    public void onEnable() {
        // Load configuration
        config = Config.load(getDataFolder());

        // Initialize API provider based on configuration
        apiProvider = createApiProvider(config);

        // Initialize Adventure audiences
        audiences = BungeeAudiences.create(this);

        // Initialize skin applier
        skinApplier = new SkinApplierBungee();

        // Register commands
        getProxy().getPluginManager().registerCommand(this, new CapeCommand(this));

        // Initialize bStats
        new Metrics(this, 23456); // Replace with actual bStats plugin ID

        getLogger().info("Custom Capes (BungeeCord) enabled! Using backend: " + apiProvider.getName());

        // Check API health
        getProxy().getScheduler().runAsync(this, () -> {
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
        getLogger().info("Custom Capes (BungeeCord) disabled.");
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
    public BungeeAudiences getAudiences() {
        return audiences;
    }

    @NotNull
    public SkinApplierBungee getSkinApplier() {
        return skinApplier;
    }
}
