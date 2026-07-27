package com.afrobama.bbstools.client.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.resources.PixelsImageBridge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Background-removal via Replicate API (851-labs/background-remover or
 * compatible models). Async — caller passes onSuccess/onError consumers
 * which fire on the MC main thread.
 *
 * Flow:
 *   1. Encode source Pixels as PNG base64 data URI
 *   2. POST /v1/predictions with model version + image
 *   3. Poll /v1/predictions/{id} until status=succeeded or failed
 *   4. Download output URL, decode PNG, return Pixels (with alpha)
 *
 * API key is read from BBSSettings.aiApiKey (cml.json). Model version
 * from BBSSettings.aiBgRemoveModel (defaults to a pinned 851-labs build).
 *
 * Network I/O — sends user's image to Replicate. No image leaves the
 * machine unless this method is invoked.
 */
public final class AIBackgroundRemover
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API_BASE = "https://api.replicate.com/v1";
    private static final int POLL_INTERVAL_MS = 1500;
    private static final int MAX_POLL_ATTEMPTS = 80;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private AIBackgroundRemover() {}

    public static boolean isConfigured()
    {
        if (BBSSettings.aiApiKey == null) return false;
        String key = BBSSettings.aiApiKey.get();
        return key != null && !key.isBlank();
    }

    public static void removeBackground(Pixels source, Consumer<Pixels> onSuccess, Consumer<String> onError)
    {
        if (!isConfigured())
        {
            dispatch(() -> onError.accept("Replicate API key not configured. Set ai.api_key in cml.json."));
            return;
        }

        Thread worker = new Thread(() ->
        {
            try
            {
                Pixels result = removeBackgroundSync(source);
                dispatch(() -> onSuccess.accept(result));
            }
            catch (Throwable t)
            {
                LOGGER.error("[AIBackgroundRemover] failed", t);
                dispatch(() -> onError.accept(t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        }, "BBS-AIBackgroundRemover");
        worker.setDaemon(true);
        worker.start();
    }

    private static Pixels removeBackgroundSync(Pixels source) throws Exception
    {
        String key = BBSSettings.aiApiKey.get();
        String modelVersion = BBSSettings.aiBgRemoveModel.get();

        /* model field is "owner/name:hash" or just "hash". Replicate accepts
         * "version" as the bare hash. Strip the "owner/name:" prefix. */
        String version = modelVersion;
        int colon = version.lastIndexOf(':');
        if (colon >= 0) version = version.substring(colon + 1);

        /* Step 1: Encode source as base64 data URI */
        byte[] pngBytes = PixelsImageBridge.toPNGBytes(source);
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);

        /* Step 2: POST to /predictions */
        JsonObject input = new JsonObject();
        input.addProperty("image", dataUri);

        JsonObject body = new JsonObject();
        body.addProperty("version", version);
        body.add("input", input);

        HttpRequest createReq = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/predictions"))
            .header("Authorization", "Token " + key)
            .header("Content-Type", "application/json")
            .header("Prefer", "wait=30")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(Duration.ofSeconds(60))
            .build();

        HttpResponse<String> createResp = HTTP.send(createReq, HttpResponse.BodyHandlers.ofString());

        if (createResp.statusCode() / 100 != 2)
        {
            throw new RuntimeException("Replicate predictions create failed (HTTP " + createResp.statusCode() + "): " + createResp.body());
        }

        JsonObject prediction = JsonParser.parseString(createResp.body()).getAsJsonObject();
        String id = prediction.get("id").getAsString();
        String status = prediction.has("status") ? prediction.get("status").getAsString() : "starting";

        /* Step 3: Poll /predictions/{id} until terminal status */
        int attempts = 0;
        while (!isTerminalStatus(status))
        {
            if (attempts++ >= MAX_POLL_ATTEMPTS)
            {
                throw new RuntimeException("Replicate poll timeout after " + MAX_POLL_ATTEMPTS + " attempts (id=" + id + ")");
            }

            Thread.sleep(POLL_INTERVAL_MS);

            HttpRequest pollReq = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/predictions/" + id))
                .header("Authorization", "Token " + key)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();

            HttpResponse<String> pollResp = HTTP.send(pollReq, HttpResponse.BodyHandlers.ofString());
            if (pollResp.statusCode() / 100 != 2)
            {
                throw new RuntimeException("Replicate poll failed (HTTP " + pollResp.statusCode() + "): " + pollResp.body());
            }

            prediction = JsonParser.parseString(pollResp.body()).getAsJsonObject();
            status = prediction.has("status") ? prediction.get("status").getAsString() : "unknown";
        }

        if (!"succeeded".equals(status))
        {
            String error = prediction.has("error") && !prediction.get("error").isJsonNull()
                ? prediction.get("error").getAsString()
                : status;
            throw new RuntimeException("Replicate prediction " + status + ": " + error);
        }

        /* Step 4: Download output URL */
        String outputUrl;
        if (prediction.get("output").isJsonArray())
        {
            outputUrl = prediction.getAsJsonArray("output").get(0).getAsString();
        }
        else
        {
            outputUrl = prediction.get("output").getAsString();
        }

        HttpRequest dlReq = HttpRequest.newBuilder()
            .uri(URI.create(outputUrl))
            .GET()
            .timeout(Duration.ofSeconds(60))
            .build();

        HttpResponse<byte[]> dlResp = HTTP.send(dlReq, HttpResponse.BodyHandlers.ofByteArray());
        if (dlResp.statusCode() / 100 != 2)
        {
            throw new RuntimeException("Output download failed (HTTP " + dlResp.statusCode() + "): " + outputUrl);
        }

        return PixelsImageBridge.fromPNGBytes(dlResp.body());
    }

    private static boolean isTerminalStatus(String status)
    {
        return "succeeded".equals(status) || "failed".equals(status) || "canceled".equals(status);
    }

    private static void dispatch(Runnable r)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.execute(r);
        else r.run();
    }

}
