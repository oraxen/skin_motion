package dev.th0rgal.customcapes.core.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.th0rgal.customcapes.core.model.CapeType;
import dev.th0rgal.customcapes.core.model.SkinVariant;
import dev.th0rgal.customcapes.core.model.TextureData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of SkinApiProvider using the MineSkin API.
 * 
 * @see <a href="https://api.mineskin.org">MineSkin API</a>
 */
public final class MineSkinApiProvider implements SkinApiProvider {

    private static final String BASE_URL = "https://api.mineskin.org";
    private static final String USER_AGENT = "CustomCapes/1.0";
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Mapping of CapeType to MineSkin cape UUIDs.
     * These UUIDs are from the MineSkin /v2/capes endpoint.
     */
    private static final Map<CapeType, String> CAPE_UUID_MAP = Map.ofEntries(
            Map.entry(CapeType.MINECON_2011, "953cac8b-779d-476d-b5e3-04e8f3d2b6d0"),
            Map.entry(CapeType.MINECON_2012, "afd553b6-72fb-4a2c-a2ae-eab6f4e4a38d"),
            Map.entry(CapeType.MINECON_2013, "83d9b08d-62b0-41c3-8e54-27e2c57c8f88"),
            Map.entry(CapeType.MINECON_2015, "fdd24b54-cbb4-4a1c-8a33-5f0f1ee72b9c"),
            Map.entry(CapeType.MINECON_2016, "e7dfea16-07e2-43b0-898e-ea8c5c517113"),
            Map.entry(CapeType.MOJANG, "b77d9b9d-2391-4745-9d34-1a152b46fdfa"),
            Map.entry(CapeType.MOJANG_CLASSIC, "8f120319-3586-4b8a-b4df-20e4fc8c2f12"),
            Map.entry(CapeType.MOJANG_STUDIOS, "bcfbe84c-9446-4c37-a5d9-c4914f391cce"),
            Map.entry(CapeType.TRANSLATOR, "1bf91499-e9ac-4e60-8d2e-ae67c4d2b42e"),
            Map.entry(CapeType.COBALT, "ca29f5dd-9b4c-4f5a-b3b6-3a3073a85e5e"),
            Map.entry(CapeType.SCROLLS, "d8f8d973-0749-4a49-887e-6756c13981c2"),
            Map.entry(CapeType.REALMS_MAPMAKER, "17912790-c5eb-46bf-aaed-9ac47f9dc6e1"),
            Map.entry(CapeType.MILLIONTH_CUSTOMER, "a2e8d97e-c35f-4b6e-b24a-a8f5cc5f2d2d"),
            Map.entry(CapeType.PRISMARINE, "d7a1c2c8-4ccf-4f52-95db-5a3c91e7f8b1"),
            Map.entry(CapeType.BIRTHDAY, "2eec10f2-6315-4c51-b4c1-7b5a61fdaff3"),
            Map.entry(CapeType.MIGRATOR, "cd2b3c3e-e4d4-4e71-ab3e-dcb4b2f3bea9"),
            Map.entry(CapeType.CHERRY_BLOSSOM, "a45e0e7a-8f96-48dd-8330-6159de5e7a9e"),
            Map.entry(CapeType.ANNIVERSARY_15TH, "ef9e95b6-48a3-4fd7-93d4-7e7d9448d2f1"));

    private final HttpClient httpClient;
    private final Duration timeout;
    private final String apiKey;

    /**
     * Create a MineSkin API provider.
     *
     * @param apiKey         Optional API key for higher rate limits (can be null)
     * @param timeoutSeconds Request timeout in seconds
     */
    public MineSkinApiProvider(@Nullable String apiKey, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    @NotNull
    public String getName() {
        return "MineSkin API";
    }

    @Override
    @NotNull
    public TextureData generate(
            @NotNull String skinUrl,
            @NotNull CapeType capeType,
            @NotNull SkinVariant variant) throws SkinApiException {
        String capeUuid = getCapeUuid(capeType);
        if (capeUuid == null) {
            throw new SkinApiException("Cape type '" + capeType.getId() + "' is not supported by MineSkin");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("url", skinUrl);
        requestBody.addProperty("variant", variant.getId());
        requestBody.addProperty("visibility", "unlisted");
        requestBody.addProperty("cape", capeUuid);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/generate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest httpRequest = requestBuilder.build();

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
        String capeUuid = getCapeUuid(capeType);
        if (capeUuid == null) {
            return CompletableFuture.failedFuture(
                    new SkinApiException("Cape type '" + capeType.getId() + "' is not supported by MineSkin"));
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("url", skinUrl);
        requestBody.addProperty("variant", variant.getId());
        requestBody.addProperty("visibility", "unlisted");
        requestBody.addProperty("cape", capeUuid);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/generate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest httpRequest = requestBuilder.build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    @Override
    public boolean isHealthy() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/"))
                .header("User-Agent", USER_AGENT)
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

    /**
     * Get the MineSkin cape UUID for a given cape type.
     *
     * @param capeType The cape type
     * @return The MineSkin cape UUID, or null if not supported
     */
    @Nullable
    public static String getCapeUuid(@NotNull CapeType capeType) {
        return CAPE_UUID_MAP.get(capeType);
    }

    /**
     * Check if a cape type is supported by MineSkin.
     */
    public static boolean isCapeSupported(@NotNull CapeType capeType) {
        return CAPE_UUID_MAP.containsKey(capeType);
    }

    private TextureData parseResponse(HttpResponse<String> response) throws SkinApiException {
        JsonObject json;
        try {
            json = GSON.fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            throw new SkinApiException("Failed to parse MineSkin response: " + e.getMessage(), e);
        }

        // Check for success
        boolean success = json.has("success") && json.get("success").getAsBoolean();

        if (!success || response.statusCode() != 200) {
            String errorMessage = extractErrorMessage(json);
            throw new SkinApiException("MineSkin API error: " + errorMessage);
        }

        // Extract skin data
        JsonObject skin = json.getAsJsonObject("skin");
        if (skin == null) {
            throw new SkinApiException("MineSkin response missing skin data");
        }

        JsonObject texture = skin.getAsJsonObject("texture");
        if (texture == null) {
            throw new SkinApiException("MineSkin response missing texture data");
        }

        JsonObject data = texture.getAsJsonObject("data");
        if (data == null) {
            throw new SkinApiException("MineSkin response missing texture.data");
        }

        String value = data.get("value").getAsString();
        String signature = data.get("signature").getAsString();

        // Extract texture URL if available
        String textureUrl = null;
        JsonObject url = texture.getAsJsonObject("url");
        if (url != null && url.has("skin")) {
            textureUrl = url.get("skin").getAsString();
        }

        // Check if it's a duplicate (cached)
        boolean cached = skin.has("duplicate") && skin.get("duplicate").getAsBoolean();

        return new TextureData(value, signature, textureUrl, cached);
    }

    private String extractErrorMessage(JsonObject json) {
        if (json.has("errors") && json.get("errors").isJsonArray()) {
            JsonArray errors = json.getAsJsonArray("errors");
            if (!errors.isEmpty()) {
                JsonObject firstError = errors.get(0).getAsJsonObject();
                if (firstError.has("message")) {
                    return firstError.get("message").getAsString();
                }
                if (firstError.has("code")) {
                    return firstError.get("code").getAsString();
                }
            }
        }
        return "Unknown error (status: " + json.toString() + ")";
    }
}
