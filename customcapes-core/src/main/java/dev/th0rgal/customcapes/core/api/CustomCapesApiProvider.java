package dev.th0rgal.customcapes.core.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import dev.th0rgal.customcapes.core.model.TextureData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of SkinApiProvider using the Custom Capes API backend.
 */
public final class CustomCapesApiProvider implements SkinApiProvider {

    private static final Gson GSON = new GsonBuilder().create();

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;

    public CustomCapesApiProvider(@NotNull String baseUrl, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    @NotNull
    public String getName() {
        return "CustomCapes API";
    }

    @Override
    @NotNull
    public TextureData generate(
            @NotNull String skinUrl,
            @NotNull CapeType capeType,
            @NotNull SkinVariant variant) throws SkinApiException {
        GenerateRequest request = new GenerateRequest(skinUrl, capeType, variant);
        String jsonBody = GSON.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/generate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException e) {
            throw new SkinApiException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinApiException("Request interrupted", e);
        }
    }

    @Override
    @NotNull
    public CompletableFuture<TextureData> generateAsync(
            @NotNull String skinUrl,
            @NotNull CapeType capeType,
            @NotNull SkinVariant variant) {
        GenerateRequest request = new GenerateRequest(skinUrl, capeType, variant);
        String jsonBody = GSON.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/generate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    @Override
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

    private TextureData parseResponse(HttpResponse<String> response) throws SkinApiException {
        if (response.statusCode() != 200) {
            throw new SkinApiException("API returned status " + response.statusCode() + ": " + response.body());
        }

        GenerateResponse generateResponse = GSON.fromJson(response.body(), GenerateResponse.class);

        if (!generateResponse.isSuccess()) {
            String error = generateResponse.getError();
            throw new SkinApiException(error != null ? error : "Unknown API error");
        }

        TextureData textureData = generateResponse.toTextureData();
        if (textureData == null) {
            throw new SkinApiException("API returned success but no texture data");
        }

        return textureData;
    }
}
