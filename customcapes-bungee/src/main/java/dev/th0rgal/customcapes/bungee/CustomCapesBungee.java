package dev.th0rgal.customcapes.bungee;

import dev.th0rgal.customcapes.bungee.commands.CapeCommand;
import dev.th0rgal.customcapes.core.api.CapesApiClient;
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
    private CapesApiClient apiClient;
    private BungeeAudiences audiences;
    private SkinApplierBungee skinApplier;

    @Override
    public void onEnable() {
        // Load configuration
        config = Config.load(getDataFolder());

        // Initialize API client
        apiClient = new CapesApiClient(config.getApiUrl(), config.getTimeoutSeconds());

        // Initialize Adventure audiences
        audiences = BungeeAudiences.create(this);

        // Initialize skin applier
        skinApplier = new SkinApplierBungee();

        // Register commands
        getProxy().getPluginManager().registerCommand(this, new CapeCommand(this));

        // Initialize bStats
        new Metrics(this, 23456); // Replace with actual bStats plugin ID

        getLogger().info("Custom Capes (BungeeCord) enabled! API: " + config.getApiUrl());

        // Check API health
        getProxy().getScheduler().runAsync(this, () -> {
            if (apiClient.isHealthy()) {
                getLogger().info("Capes API is reachable and healthy.");
            } else {
                getLogger().warning("Capes API is not reachable at " + config.getApiUrl());
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
        apiClient = new CapesApiClient(config.getApiUrl(), config.getTimeoutSeconds());
        getLogger().info("Configuration reloaded.");
    }

    @NotNull
    public Config getPluginConfig() {
        return config;
    }

    @NotNull
    public CapesApiClient getApiClient() {
        return apiClient;
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

