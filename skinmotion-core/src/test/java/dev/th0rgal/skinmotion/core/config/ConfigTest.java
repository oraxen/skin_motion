package dev.th0rgal.skinmotion.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @TempDir
    Path dataFolder;

    private Path configFile() {
        return dataFolder.resolve("config.yml");
    }

    private String read() throws IOException {
        return Files.readString(configFile(), StandardCharsets.UTF_8);
    }

    @Test
    void createsDefaultFile() throws IOException {
        Config config = Config.load(dataFolder.toFile());

        assertEquals("https://ccapi.thomas.md", config.getApiUrl());
        assertEquals("", config.getPluginApiKey());
        assertEquals("default", config.getServerId());
        assertTrue(config.isSendLinkOnJoin());
        assertTrue(read().contains("plugin_api_key"));
    }

    @Test
    void addsMissingKeysWithoutOverwritingUserValues() throws IOException {
        Files.writeString(configFile(), "api:\n  url: http://localhost:8080/\n  custom: kept\n");

        Config config = Config.load(dataFolder.toFile());

        assertEquals("http://localhost:8080/", config.getApiUrl());
        String written = read();
        assertTrue(written.contains("url: http://localhost:8080/"));
        assertTrue(written.contains("custom: kept"));
        assertTrue(written.contains("plugin_api_key"));
        assertTrue(written.contains("send_link_on_join"));
    }

    @Test
    void parsesStringNumbersAndBooleansAndClampsTimeout() throws IOException {
        Files.writeString(configFile(), "api:\n  timeout_seconds: '0'\n  plugin_api_key: '  secret  '\n"
                + "skin:\n  send_link_on_join: 'false'\n");

        Config config = Config.load(dataFolder.toFile());

        assertEquals(1, config.getTimeoutSeconds());
        assertEquals("secret", config.getPluginApiKey());
        assertFalse(config.isSendLinkOnJoin());
    }

    @Test
    void malformedYamlUsesDefaultsAndKeepsFile() throws IOException {
        String broken = "api: [unclosed\n";
        Files.writeString(configFile(), broken);

        Config config = Config.load(dataFolder.toFile());

        assertEquals("https://ccapi.thomas.md", config.getApiUrl());
        assertEquals(broken, read());
    }

    @Test
    void wrongSectionTypesAreIgnored() throws IOException {
        Files.writeString(configFile(), "api: just-a-string\nskin: 3\n");

        Config config = Config.load(dataFolder.toFile());

        assertEquals("https://ccapi.thomas.md", config.getApiUrl());
        assertTrue(config.isSendLinkOnJoin());
    }

    @Test
    void blankUrlFallsBackToDefault() throws IOException {
        Files.writeString(configFile(), "api:\n  url: '  '\n");

        assertEquals("https://ccapi.thomas.md", Config.load(dataFolder.toFile()).getApiUrl());
    }
}
