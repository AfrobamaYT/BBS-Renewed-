package com.afrobama.bbstools.client.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.resources.PixelsImageBridge;
import net.minecraft.client.Minecraft;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * OpenAI {@code gpt-image-1} based background removal via the images/edits endpoint.
 * Async — caller passes onSuccess/onError consumers which fire on the MC main thread.
 *
 * Flow:
 *   1. Encode source Pixels as PNG bytes
 *   2. POST multipart/form-data to /v1/images/edits with model=gpt-image-1
 *   3. Decode b64_json response, return Pixels (with alpha)
 *
 * API key read from BBSSettings.aiOpenaiKey (cml.json field "ai.openai_key").
 *
 * Network I/O — sends user's image to OpenAI. No image leaves the machine
 * unless this method is invoked.
 */
public final class AIOpenAIBackgroundRemover
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API_URL = "https://api.openai.com/v1/images/edits";
    private static final String DEFAULT_PROMPT = "Remove the background completely. Keep only the main subject on a fully transparent background. Do not invent new details — preserve the original colors and edges of the subject.";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private AIOpenAIBackgroundRemover() {}

    public static boolean isConfigured()
    {
        if (BBSSettings.aiOpenaiKey == null) return false;
        String key = BBSSettings.aiOpenaiKey.get();
        return key != null && !key.isBlank();
    }

    public static void removeBackground(Pixels source, Consumer<Pixels> onSuccess, Consumer<String> onError)
    {
        if (!isConfigured())
        {
            dispatch(() -> onError.accept("OpenAI API key not configured. Set ai.openai_key in cml.json."));
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
                LOGGER.error("[AIOpenAIBackgroundRemover] failed", t);
                dispatch(() -> onError.accept(t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        }, "BBS-AIOpenAIBackgroundRemover");
        worker.setDaemon(true);
        worker.start();
    }

    private static Pixels removeBackgroundSync(Pixels source) throws Exception
    {
        String key = BBSSettings.aiOpenaiKey.get();
        byte[] pngBytes = PixelsImageBridge.toPNGBytes(source);
        String boundary = "----BBSBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, pngBytes, DEFAULT_PROMPT);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .timeout(Duration.ofSeconds(180))
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() / 100 != 2)
        {
            throw new RuntimeException("OpenAI images/edits failed (HTTP " + resp.statusCode() + "): " + truncate(resp.body(), 400));
        }

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray data = json.getAsJsonArray("data");

        if (data == null || data.size() == 0)
        {
            throw new RuntimeException("OpenAI response missing data[]: " + truncate(resp.body(), 200));
        }

        JsonObject first = data.get(0).getAsJsonObject();

        if (!first.has("b64_json"))
        {
            throw new RuntimeException("OpenAI response missing b64_json: " + truncate(resp.body(), 200));
        }

        byte[] outPng = Base64.getDecoder().decode(first.get("b64_json").getAsString());
        return PixelsImageBridge.fromPNGBytes(outPng);
    }

    private static byte[] buildMultipartBody(String boundary, byte[] imageBytes, String prompt) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String dashBoundary = "--" + boundary;
        String crlf = "\r\n";

        appendField(out, dashBoundary, "model", "gpt-image-1", crlf);
        appendField(out, dashBoundary, "prompt", prompt, crlf);
        appendField(out, dashBoundary, "size", "auto", crlf);
        appendField(out, dashBoundary, "n", "1", crlf);
        appendField(out, dashBoundary, "background", "transparent", crlf);

        out.write((dashBoundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"image\"; filename=\"input.png\"" + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: image/png" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));

        return out.toByteArray();
    }

    private static void appendField(ByteArrayOutputStream out, String dashBoundary, String name, String value, String crlf) throws Exception
    {
        out.write((dashBoundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String s, int max)
    {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static void dispatch(Runnable r)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(r);
        else r.run();
    }

}
