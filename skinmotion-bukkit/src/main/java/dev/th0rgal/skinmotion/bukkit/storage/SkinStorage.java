package dev.th0rgal.skinmotion.bukkit.storage;

import dev.th0rgal.skinmotion.core.model.SkinConfig;
import dev.th0rgal.skinmotion.core.model.SkinFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite-based storage for persisting skin configurations locally.
 * This allows skins to persist between login/logout without needing to
 * fetch from the API every time.
 */
public final class SkinStorage {

    private static final int SCHEMA_VERSION = 1;
    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

    public SkinStorage(@NotNull File dataFolder, @NotNull Logger logger) {
        this.databaseFile = new File(dataFolder, "skins.db");
        this.logger = logger;
    }

    /**
     * Initialize the database connection and create tables if needed.
     */
    public void initialize() throws SQLException {
        if (!databaseFile.getParentFile().exists()) {
            databaseFile.getParentFile().mkdirs();
        }

        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        
        createTables();
        migrateIfNeeded();
        
        logger.info("SQLite skin storage initialized: " + databaseFile.getAbsolutePath());
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Schema version table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY
                )
            """);

            // Skin configs table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS skin_configs (
                    minecraft_uuid TEXT PRIMARY KEY,
                    minecraft_username TEXT NOT NULL,
                    cape_type TEXT DEFAULT 'vanilla',
                    loop_mode TEXT DEFAULT 'loop',
                    frame_duration_ticks INTEGER DEFAULT 10,
                    enabled INTEGER DEFAULT 1,
                    last_updated INTEGER DEFAULT (strftime('%s', 'now'))
                )
            """);

            // Skin frames table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS skin_frames (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    minecraft_uuid TEXT NOT NULL,
                    frame_index INTEGER NOT NULL,
                    texture_value TEXT NOT NULL,
                    texture_signature TEXT NOT NULL,
                    texture_url TEXT,
                    FOREIGN KEY (minecraft_uuid) REFERENCES skin_configs(minecraft_uuid) ON DELETE CASCADE,
                    UNIQUE(minecraft_uuid, frame_index)
                )
            """);

            // Index for faster frame lookups
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_frames_uuid 
                ON skin_frames(minecraft_uuid)
            """);
        }
    }

    private void migrateIfNeeded() throws SQLException {
        int currentVersion = getSchemaVersion();
        
        if (currentVersion < SCHEMA_VERSION) {
            // Future migrations would go here
            setSchemaVersion(SCHEMA_VERSION);
        }
    }

    private int getSchemaVersion() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt("version");
            }
        } catch (SQLException e) {
            // Table might be empty
        }
        return 0;
    }

    private void setSchemaVersion(int version) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO schema_version (version) VALUES (?)")) {
            stmt.setInt(1, version);
            stmt.executeUpdate();
        }
    }

    /**
     * Save a skin configuration to local storage.
     */
    public void saveSkin(@NotNull SkinConfig config) {
        try {
            connection.setAutoCommit(false);

            // Insert or update config
            try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO skin_configs 
                (minecraft_uuid, minecraft_username, cape_type, loop_mode, frame_duration_ticks, enabled, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, strftime('%s', 'now'))
            """)) {
                stmt.setString(1, config.getMinecraftUuid());
                stmt.setString(2, config.getMinecraftUsername());
                stmt.setString(3, config.getCapeType());
                stmt.setString(4, config.getLoopMode());
                stmt.setInt(5, config.getFrameDurationTicks());
                stmt.setInt(6, config.isEnabled() ? 1 : 0);
                stmt.executeUpdate();
            }

            // Delete existing frames
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM skin_frames WHERE minecraft_uuid = ?")) {
                stmt.setString(1, config.getMinecraftUuid());
                stmt.executeUpdate();
            }

            // Insert new frames
            if (config.getFrames() != null && !config.getFrames().isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO skin_frames 
                    (minecraft_uuid, frame_index, texture_value, texture_signature, texture_url)
                    VALUES (?, ?, ?, ?, ?)
                """)) {
                    for (SkinFrame frame : config.getFrames()) {
                        stmt.setString(1, config.getMinecraftUuid());
                        stmt.setInt(2, frame.getFrameIndex());
                        stmt.setString(3, frame.getTextureValue());
                        stmt.setString(4, frame.getTextureSignature());
                        stmt.setString(5, frame.getTextureUrl());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            connection.commit();
            logger.fine("Saved skin for " + config.getMinecraftUsername());
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                logger.warning("Failed to rollback transaction: " + ex.getMessage());
            }
            logger.warning("Failed to save skin: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.warning("Failed to reset autocommit: " + e.getMessage());
            }
        }
    }

    /**
     * Load a skin configuration from local storage.
     */
    @Nullable
    public SkinConfig loadSkin(@NotNull UUID playerId) {
        String uuid = playerId.toString();
        
        try {
            // Load config
            String username;
            String capeType;
            String loopMode;
            int frameDuration;
            boolean enabled;
            
            try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT minecraft_username, cape_type, loop_mode, frame_duration_ticks, enabled
                FROM skin_configs WHERE minecraft_uuid = ?
            """)) {
                stmt.setString(1, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    username = rs.getString("minecraft_username");
                    capeType = rs.getString("cape_type");
                    loopMode = rs.getString("loop_mode");
                    frameDuration = rs.getInt("frame_duration_ticks");
                    enabled = rs.getInt("enabled") == 1;
                }
            }

            // Load frames
            List<SkinFrame> frames = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT frame_index, texture_value, texture_signature, texture_url
                FROM skin_frames WHERE minecraft_uuid = ? ORDER BY frame_index
            """)) {
                stmt.setString(1, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        frames.add(new SkinFrame(
                                rs.getInt("frame_index"),
                                rs.getString("texture_value"),
                                rs.getString("texture_signature"),
                                rs.getString("texture_url")
                        ));
                    }
                }
            }

            if (frames.isEmpty()) {
                return null;
            }

            return new SkinConfig(uuid, username, capeType, loopMode, frameDuration, enabled, frames);
        } catch (SQLException e) {
            logger.warning("Failed to load skin for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if a player has a persisted skin.
     */
    public boolean hasSkin(@NotNull UUID playerId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM skin_configs WHERE minecraft_uuid = ? LIMIT 1")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("Failed to check skin existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a player's skin from local storage.
     */
    public void deleteSkin(@NotNull UUID playerId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM skin_configs WHERE minecraft_uuid = ?")) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
            logger.fine("Deleted skin for " + playerId);
        } catch (SQLException e) {
            logger.warning("Failed to delete skin: " + e.getMessage());
        }
    }

    /**
     * Close the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("SQLite skin storage closed.");
            } catch (SQLException e) {
                logger.warning("Failed to close database: " + e.getMessage());
            }
        }
    }
}
