package dev.th0rgal.customcapes.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.th0rgal.customcapes.core.api.CapesApiClient;
import dev.th0rgal.customcapes.core.config.Config;
import dev.th0rgal.customcapes.velocity.commands.CapeCommand;
import org.bstats.velocity.Metrics;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Main entry point for the Custom Capes Velocity plugin.
 */
@Plugin(
    id = "customcapes",
    name = "CustomCapes",
    version = "1.0.0",
    description = "Apply custom capes to players using the Custom Capes API",
    authors = {"th0rgal"}
)
public final class CustomCapesVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private Config config;
    private CapesApiClient apiClient;
    private SkinApplierVelocity skinApplier;

    @Inject
    public CustomCapesVelocity(
            ProxyServer server,
            Logger logger,
            @DataDirectory Path dataDirectory,
            Metrics.Factory metricsFactory
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Load configuration
        config = Config.load(dataDirectory.toFile());

        // Initialize API client
        apiClient = new CapesApiClient(config.getApiUrl(), config.getTimeoutSeconds());

        // Initialize skin applier
        skinApplier = new SkinApplierVelocity();

        // Register commands
        CommandManager commandManager = server.getCommandManager();
        commandManager.register(
            commandManager.metaBuilder("cape")
                .aliases("capes")
                .plugin(this)
                .build(),
            new CapeCommand(this)
        );

        // Initialize bStats
        metricsFactory.make(this, 23456); // Replace with actual bStats plugin ID

        logger.info("Custom Capes (Velocity) enabled! API: {}", config.getApiUrl());

        // Check API health in background
        server.getScheduler().buildTask(this, () -> {
            if (apiClient.isHealthy()) {
                logger.info("Capes API is reachable and healthy.");
            } else {
                logger.warn("Capes API is not reachable at {}", config.getApiUrl());
            }
        }).schedule();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Custom Capes (Velocity) disabled.");
    }

    /**
     * Reload the plugin configuration.
     */
    public void reload() {
        config = Config.load(dataDirectory.toFile());
        apiClient = new CapesApiClient(config.getApiUrl(), config.getTimeoutSeconds());
        logger.info("Configuration reloaded.");
    }

    @NotNull
    public ProxyServer getServer() {
        return server;
    }

    @NotNull
    public Logger getLogger() {
        return logger;
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
    public SkinApplierVelocity getSkinApplier() {
        return skinApplier;
    }
}

