// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * /bbstools skin &lt;minecraft_username&gt;
 *
 * <p>Fetches the player's current skin PNG from Mojang's public profile API
 * and saves it to {@code <run-dir>/bbs/skins/&lt;username&gt;.png}. Runs
 * fully off the render thread via {@link CompletableFuture}; chat feedback
 * is posted back on the client thread when done (or on error).</p>
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>{@code GET https://api.mojang.com/users/profiles/minecraft/&lt;username&gt;}
 *       → JSON with {@code id} (UUID without dashes) and canonical {@code name}.</li>
 *   <li>{@code GET https://sessionserver.mojang.com/session/minecraft/profile/&lt;UUID&gt;}
 *       → JSON whose {@code properties[0].value} is a base64-encoded textures payload.</li>
 *   <li>Base64-decode → JSON containing {@code textures.SKIN.url}.</li>
 *   <li>GET the URL → raw PNG bytes, written atomically to disk.</li>
 * </ol>
 */
public final class SkinFetchCommand
{
    private static final String LOG_PREFIX = "[SkinFetchCommand] ";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private SkinFetchCommand()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("bbstools")
            .then(ClientCommandManager.literal("skin")
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                    .executes(ctx -> {
                        String username = StringArgumentType.getString(ctx, "username");
                        run(ctx.getSource(), username);
                        return 1;
                    }))));
    }

    private static void run(FabricClientCommandSource source, String username)
    {
        source.sendFeedback(Component.literal("[BBSTools] Fetching skin for " + username + "..."));
        log("Fetching skin for " + username);

        CompletableFuture
            .supplyAsync(() -> fetchAndSave(username))
            .whenComplete((savedPath, throwable) -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (throwable != null)
                    {
                        Throwable cause = (throwable.getCause() != null) ? throwable.getCause() : throwable;
                        String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                        source.sendError(Component.literal("[BBSTools] Skin fetch failed: " + msg));
                        log("ERROR: " + msg);
                        cause.printStackTrace();
                        return;
                    }
                    source.sendFeedback(Component.literal("[BBSTools] Skin saved: " + savedPath));
                    log("Saved skin to " + savedPath);
                });
            });
    }

    /**
     * Executed on the async pool — performs all three HTTP calls + disk write.
     * Throws (wrapped in CompletionException) on any failure; the whenComplete
     * handler routes the message back to chat.
     */
    private static Path fetchAndSave(String username)
    {
        try
        {
            // -- Step 1: username -> UUID --
            String profileJson = httpGetString(URI.create(
                "https://api.mojang.com/users/profiles/minecraft/" + username));
            JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
            if (!profile.has("id"))
            {
                throw new RuntimeException("No such Minecraft user: " + username);
            }
            String uuid = profile.get("id").getAsString();
            String canonicalName = profile.has("name") ? profile.get("name").getAsString() : username;
            log("Resolved " + username + " -> uuid=" + uuid + " name=" + canonicalName);

            // -- Step 2: UUID -> textures payload --
            String sessionJson = httpGetString(URI.create(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid));
            JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
            JsonArray properties = session.getAsJsonArray("properties");
            if (properties == null || properties.size() == 0)
            {
                throw new RuntimeException("Session response had no properties for " + canonicalName);
            }
            JsonObject texturesProp = null;
            for (JsonElement el : properties)
            {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("name") && "textures".equals(obj.get("name").getAsString()))
                {
                    texturesProp = obj;
                    break;
                }
            }
            if (texturesProp == null)
            {
                throw new RuntimeException("No 'textures' property for " + canonicalName);
            }
            String base64Value = texturesProp.get("value").getAsString();

            // -- Step 3: base64 -> JSON -> skin URL --
            String decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            JsonObject texturesJson = JsonParser.parseString(decoded).getAsJsonObject();
            JsonObject textures = texturesJson.getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN"))
            {
                throw new RuntimeException("Player has no SKIN texture: " + canonicalName);
            }
            String skinUrl = textures.getAsJsonObject("SKIN").get("url").getAsString();
            log("Skin URL: " + skinUrl);

            // -- Step 4: HTTP-GET PNG bytes --
            byte[] pngBytes = httpGetBytes(URI.create(skinUrl));
            if (pngBytes.length == 0)
            {
                throw new RuntimeException("Empty PNG response from " + skinUrl);
            }

            // -- Step 5: write to <run-dir>/bbs/skins/<username>.png --
            // Use the canonical (correctly-cased) name from Mojang for the filename
            // so /bbstools skin Notch and /bbstools skin notch both produce Notch.png.
            String safeName = canonicalName.replaceAll("[^A-Za-z0-9_\\-]", "_");
            Path runDir = Minecraft.getInstance().gameDirectory.toPath();
            Path skinsDir = runDir.resolve("bbs").resolve("skins");
            Files.createDirectories(skinsDir);
            Path out = skinsDir.resolve(safeName + ".png");
            // Atomic-ish write: tmp file then move.
            Path tmp = skinsDir.resolve(safeName + ".png.tmp");
            Files.write(tmp, pngBytes);
            try
            {
                Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            catch (Exception atomicFail)
            {
                // Some filesystems (e.g. cross-volume) reject ATOMIC_MOVE; fall back.
                Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return out;
        }
        catch (RuntimeException re)
        {
            throw re;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String httpGetString(URI uri) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "BBSTools-SkinFetch/1.0")
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = resp.statusCode();
        if (status == 204 || resp.body() == null || resp.body().isEmpty())
        {
            throw new RuntimeException("Mojang returned " + status + " (empty body) for " + uri);
        }
        if (status < 200 || status >= 300)
        {
            throw new RuntimeException("HTTP " + status + " for " + uri + ": " + truncate(resp.body()));
        }
        return resp.body();
    }

    private static byte[] httpGetBytes(URI uri) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "BBSTools-SkinFetch/1.0")
            .GET()
            .build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        int status = resp.statusCode();
        if (status < 200 || status >= 300)
        {
            throw new RuntimeException("HTTP " + status + " for skin URL " + uri);
        }
        return resp.body();
    }

    private static String truncate(String s)
    {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    private static void log(String msg)
    {
        // Mirror to stdout + the BBSTools console log file pattern via System.out
        // (BBSTools wires System.out into bbstools-console.log).
        System.out.println(LOG_PREFIX + msg);
    }

    // Suppress unused warning on Locale import on some toolchains.
    @SuppressWarnings("unused")
    private static String lc(String s) { return s.toLowerCase(Locale.ROOT); }

}
