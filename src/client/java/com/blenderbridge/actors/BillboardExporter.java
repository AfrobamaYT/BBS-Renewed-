package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import java.io.IOException;
import java.util.Base64;

/** Static image-form geometry/material inputs, matching BillboardFormRenderer. */
public final class BillboardExporter
{
    private BillboardExporter() {}

    public static JsonObject serialize(Replay replay)
    {
        JsonObject out = new JsonObject();
        if (!(replay.form.get() instanceof BillboardForm form)) return out;
        JsonObject data = new JsonObject();
        out.add("billboard", data);
        var link = form.texture.get();
        data.addProperty("texture", link == null ? "" : link.toString());
        if (link == null) return out;
        var texture = BBSModClient.getTextures().getTexture(link);
        data.addProperty("width", texture.width);
        data.addProperty("height", texture.height);
        data.addProperty("facingCamera", form.billboard.get());
        data.addProperty("linear", form.linear.get());
        data.addProperty("shading", form.shading.get());
        data.addProperty("resizeCrop", form.resizeCrop.get());
        data.addProperty("offsetX", form.offsetX.get());
        data.addProperty("offsetY", form.offsetY.get());
        data.addProperty("rotation", form.rotation.get());
        JsonArray crop = new JsonArray();
        var c = form.crop.get();
        for (float v : new float[]{c.x, c.y, c.z, c.w}) crop.add(v);
        data.add("crop", crop);
        JsonArray color = new JsonArray();
        var tint = form.color.get();
        for (float v : new float[]{tint.r, tint.g, tint.b, tint.a}) color.add(v);
        data.add("color", color);
        /* External assets already have a shared local file. Packaged defaults do not. */
        var file = BBSMod.getProvider().getFile(link);
        if (file != null && file.isFile()) data.addProperty("file", file.getAbsolutePath());
        else try (var stream = BBSMod.getProvider().getAsset(link))
        {
            byte[] bytes = stream.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("packaged image exceeds 4 MiB");
            data.addProperty("imageBytes", Base64.getEncoder().encodeToString(bytes));
        }
        catch (IOException exception)
        {
            data.addProperty("error", exception.getMessage());
        }
        return out;
    }
}
