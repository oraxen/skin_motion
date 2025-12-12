package dev.th0rgal.customcapes.core.config;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration manager for the Custom Capes plugin.
 */
public final class Config {

    private static final String DEFAULT_API_URL = "https://ccapi.thomas.md";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private String apiUrl;
    private int timeoutSeconds;

    // Messages
    private String prefix;
    private String capeApplied;
    private String capeCleared;
    private String capeNotFound;
    private String error;
    private String noPermission;
    private String playerOnly;
    private String usage;
    private String listHeader;
    private String listEntry;
    private String applying;

    private Config() {
        // Use defaults initially
        setDefaults();
    }

    private void setDefaults() {
        this.apiUrl = DEFAULT_API_URL;
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        this.prefix = "<gray>[<gold>Capes</gold>]</gray> ";
        this.capeApplied = "<green>Cape applied successfully!";
        this.capeCleared = "<green>Cape removed.";
        this.capeNotFound = "<red>Cape type not found: <white>%cape%";
        this.error = "<red>Failed to apply cape: <white>%error%";
        this.noPermission = "<red>You don't have permission to do that.";
        this.playerOnly = "<red>This command can only be used by players.";
        this.usage = "<yellow>Usage: <white>/cape <list|clear|<cape_type>>";
        this.listHeader = "<gold>Available capes:";
        this.listEntry = "<gray>- <white>%cape%";
        this.applying = "<yellow>Applying cape, please wait...";
    }

    /**
     * Load configuration from a file, creating defaults if it doesn't exist.
     *
     * @param dataFolder The plugin's data folder
     * @return The loaded configuration
     */
    @NotNull
    public static Config load(@NotNull File dataFolder) {
        Config config = new Config();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");
        
        if (!configFile.exists()) {
            config.save(configFile);
            return config;
        }

        try (InputStream input = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);
            
            if (data != null) {
                config.loadFromMap(data);
            }
        } catch (IOException e) {
            System.err.println("[CustomCapes] Failed to load config: " + e.getMessage());
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private void loadFromMap(Map<String, Object> data) {
        Map<String, Object> api = (Map<String, Object>) data.get("api");
        if (api != null) {
            this.apiUrl = getString(api, "url", DEFAULT_API_URL);
            this.timeoutSeconds = getInt(api, "timeout_seconds", DEFAULT_TIMEOUT_SECONDS);
        }

        Map<String, Object> messages = (Map<String, Object>) data.get("messages");
        if (messages != null) {
            this.prefix = getString(messages, "prefix", prefix);
            this.capeApplied = getString(messages, "cape_applied", capeApplied);
            this.capeCleared = getString(messages, "cape_cleared", capeCleared);
            this.capeNotFound = getString(messages, "cape_not_found", capeNotFound);
            this.error = getString(messages, "error", error);
            this.noPermission = getString(messages, "no_permission", noPermission);
            this.playerOnly = getString(messages, "player_only", playerOnly);
            this.usage = getString(messages, "usage", usage);
            this.listHeader = getString(messages, "list_header", listHeader);
            this.listEntry = getString(messages, "list_entry", listEntry);
            this.applying = getString(messages, "applying", applying);
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Save the configuration to a file.
     */
    public void save(@NotNull File configFile) {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> api = new LinkedHashMap<>();
        api.put("url", apiUrl);
        api.put("timeout_seconds", timeoutSeconds);
        data.put("api", api);

        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("prefix", prefix);
        messages.put("cape_applied", capeApplied);
        messages.put("cape_cleared", capeCleared);
        messages.put("cape_not_found", capeNotFound);
        messages.put("error", error);
        messages.put("no_permission", noPermission);
        messages.put("player_only", playerOnly);
        messages.put("usage", usage);
        messages.put("list_header", listHeader);
        messages.put("list_entry", listEntry);
        messages.put("applying", applying);
        data.put("messages", messages);

        try {
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            }
            
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setWidth(120);
            
            Yaml yaml = new Yaml(options);
            try (Writer writer = new FileWriter(configFile)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            System.err.println("[CustomCapes] Failed to save config: " + e.getMessage());
        }
    }

    // Getters

    @NotNull
    public String getApiUrl() {
        return apiUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @NotNull
    public String getPrefix() {
        return prefix;
    }

    @NotNull
    public String getCapeApplied() {
        return capeApplied;
    }

    @NotNull
    public String getCapeCleared() {
        return capeCleared;
    }

    @NotNull
    public String getCapeNotFound() {
        return capeNotFound;
    }

    @NotNull
    public String getError() {
        return error;
    }

    @NotNull
    public String getNoPermission() {
        return noPermission;
    }

    @NotNull
    public String getPlayerOnly() {
        return playerOnly;
    }

    @NotNull
    public String getUsage() {
        return usage;
    }

    @NotNull
    public String getListHeader() {
        return listHeader;
    }

    @NotNull
    public String getListEntry() {
        return listEntry;
    }

    @NotNull
    public String getApplying() {
        return applying;
    }
}

