package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.WeakHashMap;

/** Export the loaded mesh bind geometry once, never its huge serialized Model representation. */
public final class MeshModelExport
{
    private static final Map<Model, Map<String, JsonObject>> CACHE = new WeakHashMap<>();

    private MeshModelExport()
    {}

    public static JsonObject describe(ModelInstance instance, Model model, ModelForm form)
    {
        JsonObject out = new JsonObject();
        JsonObject data = new JsonObject();
        out.add("cubicModel", data);
        Vector3f scale = new Vector3f(instance.getScale());
        StringBuilder variant = new StringBuilder("mesh-bind-normals-v2:").append(instance.id).append(scale).append(instance.config.culling.get());
        for (ModelGroup group : model.getOrderedGroups())
            for (ModelMesh mesh : group.meshes)
                variant.append(texture(instance, form, mesh));
        Map<String, JsonObject> variants = CACHE.computeIfAbsent(model, key -> new HashMap<>());
        JsonObject cached = variants.get(variant.toString());
        if (cached != null && Files.isRegularFile(Path.of(cached.get("bobj").getAsString())))
        {
            out.add("cubicModel", cached.deepCopy());
            return out;
        }

        try
        {
            if (scale.x <= 0F || Math.abs(scale.x - scale.y) > 1e-6 || Math.abs(scale.x - scale.z) > 1e-6)
                throw new IllegalArgumentException("Mesh model needs uniform positive model scale");
            for (ModelGroup group : model.getOrderedGroups())
            {
                if (!group.cubes.isEmpty() || group.initial.rotate.lengthSquared() > 1e-10)
                    throw new IllegalArgumentException("Mesh bridge does not support mixed cubes or rotated bind groups: " + group.id);
                for (ModelMesh mesh : group.meshes)
                    if (!mesh.data.isEmpty() || mesh.baseData.vertices.size() % 3 != 0
                        || mesh.baseData.vertices.size() != mesh.baseData.normals.size()
                        || mesh.baseData.vertices.size() != mesh.baseData.uvs.size())
                        throw new IllegalArgumentException("Unsupported mesh topology or shape keys: " + group.id);
            }

            Path cache = BBSMod.getAssetsPath(".bridge-models").toPath();
            Files.createDirectories(cache);
            Path folder = Files.createTempDirectory(cache, "mesh-");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int vertices = 0;
            int faces = 0;
            int object = 0;
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new DigestOutputStream(
                Files.newOutputStream(folder.resolve("model.bobj")), digest), StandardCharsets.UTF_8)))
            {
                for (ModelGroup group : model.getOrderedGroups())
                    for (ModelMesh mesh : group.meshes)
                    {
                        String name = "mesh_" + object++;
                        writer.write("o " + name + "\no_arm CubicRig\n");
                        Link texture = texture(instance, form, mesh);
                        Path textures = folder.resolve("textures").resolve(name);
                        Files.createDirectories(textures);
                        try (java.io.InputStream input = BBSMod.getProvider().getAsset(texture))
                        {
                            Files.copy(input, textures.resolve("texture.png"));
                        }
                        int written = writeMesh(writer, model, group, mesh, scale, vertices);
                        vertices += written;
                        faces += mesh.baseData.vertices.size() / 3;
                    }
                writer.write("arm_name CubicRig\n");
                for (ModelGroup group : model.getOrderedGroups())
                {
                    Vector3f p = new Vector3f(group.initial.translate).mul(scale.x / 16F);
                    p.x = -p.x; p.z = -p.z;
                    writer.write("arm_bone " + group.id + " " + (group.parent == null ? "" : group.parent.id + " ")
                        + p.x + " " + (p.y + .0625F) + " " + p.z + " -1 0 0 " + p.x
                        + " 0 1 0 " + p.y + " 0 0 -1 " + p.z + " 0 0 0 1\n");
                }
            }
            JsonObject config = new JsonObject();
            config.addProperty("culling", instance.config.culling.get());
            Files.writeString(folder.resolve("config.json"), config.toString());
            JsonArray units = new JsonArray();
            units.add(-scale.x / 16F); units.add(scale.x / 16F); units.add(scale.x / 16F);
            data.add("translationScale", units);
            data.addProperty("basisVersion", 2);
            data.addProperty("bobj", folder.resolve("model.bobj").toString());
            data.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
            data.addProperty("vertices", vertices);
            data.addProperty("faces", faces);
            data.addProperty("bones", model.getOrderedGroups().size());
            variants.put(variant.toString(), data.deepCopy());
        }
        catch (Exception failure)
        {
            data.addProperty("error", failure.toString());
        }
        return out;
    }

    private static int writeMesh(BufferedWriter writer, Model model, ModelGroup group, ModelMesh mesh,
        Vector3f scale, int base) throws java.io.IOException
    {
        PoseStack stack = new PoseStack();
        stack.scale(scale.x, scale.y, scale.z);
        stack.rotate(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        CubicCubeRenderer.moveToPivot(stack, mesh.origin);
        CubicCubeRenderer.rotate(stack, mesh.rotate);
        CubicCubeRenderer.moveBackFromPivot(stack, mesh.origin);
        Map<Corner, Integer> unique = new HashMap<>();
        int count = mesh.baseData.vertices.size();
        int[] indices = new int[count];
        ModelVertex vertex = new ModelVertex();
        for (int i = 0; i < count; i++)
        {
            Corner corner = new Corner(mesh.baseData.vertices.get(i), mesh.baseData.uvs.get(i), mesh.baseData.normals.get(i));
            Integer index = unique.get(corner);
            if (index == null)
            {
                index = base + unique.size() + 1;
                unique.put(corner, index);
                vertex.set(corner.position, corner.uv, model);
                Vector3f p = stack.last().pose().transformPosition(vertex.vertex);
                Vector3f n = stack.last().normal().transform(new Vector3f(corner.normal));
                writer.write("v " + p.x + " " + p.y + " " + p.z + "\nvw " + group.id + " 1\n"
                    + "vt " + vertex.uv.x + " " + (1F - vertex.uv.y) + "\nvn " + n.x + " " + n.y + " " + n.z + "\n");
            }
            indices[i] = index;
        }
        for (int i = 0; i < count; i += 3)
        {
            writer.write("f");
            for (int j = 0; j < 3; j++)
            {
                int index = indices[i + j];
                writer.write(" " + index + "/" + index + "/" + index);
            }
            writer.write('\n');
        }
        return unique.size();
    }

    private record Corner(Vector3f position, Vector2f uv, Vector3f normal)
    {}

    private static Link texture(ModelInstance instance, ModelForm form, ModelMesh mesh)
    {
        /* Match ModelFormRenderer: single-material models ignore folder/Kd defaults. */
        if (instance.materials.size() <= 1)
            return form.texture.get() == null ? instance.getTexture() : form.texture.get();
        Link picked = form.materialTextures.getLink(mesh.material);
        return picked == null ? instance.getMaterialTexture(mesh.material, instance.getTexture()) : picked;
    }
}
