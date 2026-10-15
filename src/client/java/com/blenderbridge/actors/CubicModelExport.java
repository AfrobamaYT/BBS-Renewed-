package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.util.Map;
import java.util.WeakHashMap;

/** Use BBS' parsed cubes, UVs and cube transforms, rather than guessing JSON geometry. */
public final class CubicModelExport
{
    private static final Map<Model, JsonObject> CACHE = new WeakHashMap<>();
    private CubicModelExport() {}

    public static JsonObject describe(Replay replay)
    {
        JsonObject out = new JsonObject();
        if (!(replay.form.get() instanceof ModelForm form)) return out;
        var instance = BBSModClient.getModels().getModel(form.model.get());
        if (instance == null || !(instance.model instanceof Model source)) return out;
        JsonObject cached = CACHE.get(source);
        if (cached != null && cached.has("basisVersion") && cached.get("basisVersion").getAsInt() == 3)
        { out.add("cubicModel", cached.deepCopy()); return out; }
        JsonObject data = new JsonObject();
        out.add("cubicModel", data);
        try
        {
            int cubes = 0;
            for (var group : source.getOrderedGroups()) cubes += group.cubes.size();
            if (cubes > 10000) throw new IllegalArgumentException("Cubic export exceeds 10000 cubes");
            Vector3f scale = instance.getScale();
            if (Math.abs(scale.x-scale.y)>1e-6 || Math.abs(scale.x-scale.z)>1e-6 || scale.x<=0)
                throw new IllegalArgumentException("Cubic model needs uniform positive model scale");
            /* Meshes use a cached bind-geometry export, never the costly Model serialization. */
            for (ModelGroup group : source.getOrderedGroups())
            {
                if (!group.meshes.isEmpty())
                    return MeshModelExport.describe(instance, source, form);
            }
            Model model = new Model(source.parser);
            model.fromData(source.toData());
            model.initialize();
            String identity = "cubic-render-basis-v3:"+form.model.get()+source.toData().toString()+scale;
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var folder = BBSMod.getAssetsPath(".bridge-models/"+hash).toPath();
            Files.createDirectories(folder);
            StringBuilder text = new StringBuilder("o CubicModel\no_arm CubicRig\n");
            int vertex = 1;
            /* A rest rotation stays out of the bind geometry: BBS adds it to the pose as an euler
             * sum (Model.applyPose: current.rotate = initial + pose) and builds the ZYX rotation from
             * that sum, so Blender gets it as the same constant offset on the bone rotation. */
            JsonObject restRotations = new JsonObject();
            for (var group : model.getOrderedGroups())
            {
                if (!group.meshes.isEmpty())
                    throw new IllegalArgumentException("Unsupported cubic group geometry: "+group.id);
                if (group.initial.rotate.lengthSquared()>1e-10)
                {
                    JsonArray rest = new JsonArray();
                    rest.add(Math.toRadians(group.initial.rotate.x));
                    rest.add(Math.toRadians(group.initial.rotate.y));
                    rest.add(Math.toRadians(group.initial.rotate.z));
                    restRotations.add(group.id, rest);
                }
                for (var cube : group.cubes)
                {
                    PoseStack stack = new PoseStack();
                    stack.scale(scale.x, scale.y, scale.z);
                    /* ModelFormRenderer turns both model types by PI. Only BOBJ turns
                     * a second time in ModelInstance.render. Bake the cubic-only turn
                     * into the shared bind space, never into authored actor yaw. */
                    stack.last().pose().rotateY((float) Math.PI);
                    CubicCubeRenderer.moveToPivot(stack, cube.pivot);
                    CubicCubeRenderer.rotate(stack, cube.rotate);
                    CubicCubeRenderer.moveBackFromPivot(stack, cube.pivot);
                    for (var quad : cube.quads)
                    {
                        int count = quad.vertices.size();
                        if (count < 3) continue;
                        int first = vertex;
                        for (var v : quad.vertices)
                        {
                            Vector3f p = stack.last().pose().transformPosition(new Vector3f(v.vertex));
                            text.append("v ").append(p.x).append(' ').append(p.y).append(' ').append(p.z).append('\n');
                            text.append("vw ").append(group.id).append(" 1\n");
                            text.append("vt ").append(v.uv.x).append(' ').append(1-v.uv.y).append('\n');
                            vertex++;
                        }
                        text.append('f');
                        for (int i=first;i<vertex;i++) text.append(' ').append(i).append('/').append(i);
                        text.append('\n');
                    }
                }
            }
            text.append("arm_name CubicRig\n");
            for (var group : model.getOrderedGroups())
            {
                Vector3f p = new Vector3f(group.initial.translate).mul(scale.x/16);
                p.x = -p.x;
                p.z = -p.z;
                text.append("arm_bone ").append(group.id).append(' ');
                if (group.parent != null) text.append(group.parent.id).append(' ');
                text.append(p.x).append(' ').append(p.y+.0625F).append(' ').append(p.z).append(' ');
                text.append("-1 0 0 ").append(p.x).append(" 0 1 0 ").append(p.y)
                    .append(" 0 0 -1 ").append(p.z).append(" 0 0 0 1\n");
            }
            try (var stream = BBSMod.getProvider().getAsset(instance.getTexture()))
            {
                Files.copy(stream, folder.resolve("texture.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            JsonObject config = new JsonObject();
            config.addProperty("texture", "assets:.bridge-models/"+hash+"/texture.png");
            Files.writeString(folder.resolve("config.json"), config.toString());
            Files.writeString(folder.resolve("model.bobj"), text.toString());
            data.addProperty("bobj", folder.resolve("model.bobj").toString());
            JsonArray units = new JsonArray();units.add(-scale.x/16);units.add(scale.x/16);units.add(scale.x/16);
            data.add("translationScale", units);
            data.add("restRotations", restRotations);
            data.addProperty("basisVersion", 3);
            data.addProperty("vertices", vertex-1);
            data.addProperty("bones", model.getOrderedGroups().size());
            CACHE.put(source, data.deepCopy());
        }
        catch (Exception error) { data.addProperty("error", error.getMessage()); }
        return out;
    }
}
