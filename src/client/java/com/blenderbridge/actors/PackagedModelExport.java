package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.model.loaders.IModelLoader;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.WeakHashMap;

/** Materialize packaged BOBJ resources through the same provider as BBS itself. */
public final class PackagedModelExport
{
    private static final Map<ModelInstance, Map<String, JsonObject>> CACHE = new WeakHashMap<>();
    private PackagedModelExport()
    {}

    public static JsonObject describe(Replay replay)
    {
        JsonObject out = new JsonObject();
        if (!(replay.form.get() instanceof ModelForm form))
        {
            return out;
        }
        ModelInstance instance = BBSModClient.getModels().getModel(form.model.get());
        if (instance == null || !(instance.model instanceof BOBJModel))
        {
            return out;
        }
        Link model = Link.assets("models/" + form.model.get());
        Collection<Link> links = BBSMod.getProvider().getLinksFromPath(model, true);
        Link source = IModelLoader.getLink(model.combine("model.bobj"), links, ".bobj");
        File external = BBSMod.getProvider().getFile(source);
        if (external != null && external.isFile())
        {
            return out;
        }
        Link texture = form.texture.get() == null ? instance.getTexture() : form.texture.get();
        String variant = String.valueOf(texture) + instance.getScale() + instance.config.culling.get();
        Map<String, JsonObject> variants = CACHE.computeIfAbsent(instance, key -> new HashMap<>());
        JsonObject data = variants.get(variant);
        if (data != null && data.has("model") && Files.isRegularFile(Path.of(data.get("bobj").getAsString())))
        {
            out.add("packagedModel", data.deepCopy());
            return out;
        }
        data = new JsonObject();
        out.add("packagedModel", data);
        try
        {
            byte[] modelBytes = read(source, 64 * 1024 * 1024);
            byte[] textureBytes = read(texture, 16 * 1024 * 1024);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(modelBytes);
            digest.update(textureBytes);
            digest.update(variant.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(digest.digest());
            Path folder = BBSMod.getAssetsPath(".bridge-models/" + hash).toPath();
            Files.createDirectories(folder);
            JsonObject config = new JsonObject();
            JsonArray scale = new JsonArray();
            scale.add(instance.getScale().x);
            scale.add(instance.getScale().y);
            scale.add(instance.getScale().z);
            config.add("scale", scale);
            config.addProperty("culling", instance.config.culling.get());
            config.addProperty("texture", "assets:.bridge-models/" + hash + "/texture.png");
            Files.write(folder.resolve("texture.png"), textureBytes);
            Files.writeString(folder.resolve("config.json"), config.toString());
            Files.write(folder.resolve("model.bobj"), modelBytes);
            data.addProperty("bobj", folder.resolve("model.bobj").toString());
            data.addProperty("source", source.toString());
            data.addProperty("model", form.model.get());
            data.addProperty("sha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(modelBytes)));
            variants.put(variant, data.deepCopy());
        }
        catch (Exception error)
        {
            data.addProperty("error", error.toString());
        }
        return out;
    }

    private static byte[] read(Link link, int limit) throws IOException
    {
        if (link == null)
        {
            throw new IOException("Missing packaged model resource");
        }
        try (InputStream stream = BBSMod.getProvider().getAsset(link))
        {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit)
            {
                throw new IOException("Resource exceeds bridge limit: " + link);
            }
            return bytes;
        }
    }
}
