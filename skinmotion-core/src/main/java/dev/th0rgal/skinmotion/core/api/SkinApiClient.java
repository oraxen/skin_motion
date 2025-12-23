package dev.th0rgal.skinmotion.core.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.th0rgal.skinmotion.core.model.SkinConfig;
import dev.th0rgal.skinmotion.core.model.SkinFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * HTTP client for the SkinMotion API.
 * Handles token generation and skin configuration fetching.
 */
public final class SkinApiClient {

    private static final Gson GSON = new GsonBuilder().create();

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final Logger logger;

    public SkinApiClient(@NotNull String baseUrl, @NotNull String apiKey, int timeoutSeconds, @Nullable Logger logger) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.logger = logger != null ? logger : Logger.getLogger("SkinApiClient");
    }

    /**
     * Generate a dashboard token for a player.
     *
     * @param minecraftUuid     Player's Minecraft UUID
     * @param minecraftUsername Player's Minecraft username
     * @param serverId          Server identifier
     * @return Response containing the token and dashboard URL
     */
    @NotNull
    public CompletableFuture<TokenResponse> generateToken(
            @NotNull String minecraftUuid,
            @NotNull String minecraftUsername,
            @NotNull String serverId) {

        String jsonBody = GSON.toJson(new TokenRequest(minecraftUuid, minecraftUsername, serverId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tokens/generate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new SkinApiException("Failed to generate token: HTTP " + response.statusCode());
                    }
                    try {
                        TokenResponse tokenResponse = GSON.fromJson(response.body(), TokenResponse.class);
                        if (!tokenResponse.success) {
                            throw new SkinApiException("Failed to generate token");
                        }
                        return tokenResponse;
                    } catch (JsonParseException e) {
                        throw new SkinApiException("Failed to parse token response", e);
                    }
                });
    }

    /**
     * Fetch a player's skin configuration.
     *
     * @param minecraftUuid Player's Minecraft UUID
     * @return The skin configuration, or null if not found
     */
    @NotNull
    public CompletableFuture<SkinConfig> getSkinConfig(@NotNull String minecraftUuid) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/skins/" + minecraftUuid))
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return null;
                    }
                    try {
                        SkinResponse skinResponse = GSON.fromJson(response.body(), SkinResponse.class);
                        if (!skinResponse.success || skinResponse.skin == null) {
                            return null;
                        }
                        return skinResponse.skin;
                    } catch (JsonParseException e) {
                        logger.warning("Failed to parse skin response: " + e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Check if the API is healthy.
     */
    public boolean isHealthy() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // Request/Response DTOs

    private static class TokenRequest {
        final String minecraft_uuid;
        final String minecraft_username;
        final String server_id;

        TokenRequest(String uuid, String username, String serverId) {
            this.minecraft_uuid = uuid;
            this.minecraft_username = username;
            this.server_id = serverId;
        }
    }

    public static class TokenResponse {
        public boolean success;
        public String token;
        public String dashboard_url;
        public long expires_in_hours;
    }

    private static class SkinResponse {
        boolean success;
        SkinConfig skin;
        String error;
    }

    public static class SkinApiException extends RuntimeException {
        public SkinApiException(String message) {
            super(message);
        }

        public SkinApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
