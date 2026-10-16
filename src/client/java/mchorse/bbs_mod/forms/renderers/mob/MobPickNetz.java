package mchorse.bbs_mod.forms.renderers.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Pose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A mob form in the pick pass, bone by bone. 26.3 hands entities to the frame graph, which draws
 * them later into the world's image, so the pick buffer never saw a mob form at all, not even as a
 * whole. Here the posed model's cubes are compiled by vanilla's own {@code ModelPart.compile} into
 * one mesh, each vertex carrying its bone's place in {@link MobKnochen} plus one (0 is the form
 * itself), and drawn with the model forms' picker pipeline. Same contract as BBS FS e341fa733
 * (Wemppy4/bbs-fs, MIT), where the part light carried the id through vanilla's own draw.
 */
public final class MobPickNetz
{
    /** One mesh per form renderer: a new upload replaces the last one only once the next frame picks */
    private ModelVAO netz;

    public void zeichnen(Entity entity, Pose pose, float transition, Link textur, PoseStack stack, int kennung, boolean jeKnochen)
    {
        MobKnochen rig = MobKnochen.von(entity);

        if (rig == null)
        {
            return;
        }

        List<String> reihe = new ArrayList<>(rig.teile.keySet());
        Sammler sammler = new Sammler();
        Link[] haut = {textur};

        PoseStack netzStack = new PoseStack();

        MobMatrizen.aufstellen(entity, pose, transition, netzStack, (renderer, zustand, wurzel, namen) ->
        {
            if (haut[0] == null) haut[0] = Link.create(renderer.getTextureLocation(zustand).toString());

            wurzel.translateAndRotate(netzStack);

            for (ModelPart kind : wurzel.children.values()) gehen(kind, namen, reihe, jeKnochen, netzStack, sammler);
        });

        if (sammler.ecken.isEmpty())
        {
            return;
        }

        ModelVAOData daten = sammler.daten();

        if (this.netz == null) this.netz = new ModelVAO(daten, sammler.licht());
        else this.netz.upload(daten, sammler.licht());

        Texture haupt = BBSModClient.getTextures().getTexture(haut[0]);

        ModelVAORenderer.render(ModelVAOPipeline.PICKER_MODELLE, this.netz, stack, 1F, 1F, 1F, 1F, 0, OverlayTexture.NO_OVERLAY, haupt, kennung);
    }

    /** The descent ModelPart.render makes; the id of a part is its bone's, or its parent's when it has none */
    private static void gehen(ModelPart teil, Map<ModelPart, String> namen, List<String> reihe, boolean jeKnochen, PoseStack stack, Sammler sammler)
    {
        if (!teil.visible)
        {
            return;
        }

        int vorher = sammler.knochen;
        String name = namen.get(teil);

        if (jeKnochen && name != null) sammler.knochen = reihe.indexOf(name) + 1;

        stack.pushPose();
        teil.translateAndRotate(stack);

        if (!teil.skipDraw) teil.compile(stack.last(), sammler, sammler.knochen, OverlayTexture.NO_OVERLAY, -1);

        for (ModelPart kind : teil.children.values()) gehen(kind, namen, reihe, jeKnochen, stack, sammler);

        stack.popPose();
        sammler.knochen = vorher;
    }

    /** Takes the quads vanilla writes and keeps them as triangles: position, uv, normal and the bone */
    private static final class Sammler implements VertexConsumer
    {
        private final List<float[]> ecken = new ArrayList<>();
        private float[] jetzt;
        private int knochen;

        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            this.jetzt = new float[] {x, y, z, 0, 0, 0, 1, 0, Float.intBitsToFloat(this.knochen)};
            this.ecken.add(this.jetzt);

            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v)
        {
            this.jetzt[3] = u;
            this.jetzt[4] = v;

            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z)
        {
            this.jetzt[5] = x;
            this.jetzt[6] = y;
            this.jetzt[7] = z;

            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }

        /** Each quad as the triangles 0-1-2 and 0-2-3, the winding vanilla's quads have */
        private List<float[]> dreiecke()
        {
            List<float[]> out = new ArrayList<>();

            for (int i = 0; i + 3 < this.ecken.size(); i += 4)
            {
                for (int k : new int[] {0, 1, 2, 0, 2, 3}) out.add(this.ecken.get(i + k));
            }

            return out;
        }

        private ModelVAOData daten()
        {
            List<float[]> d = this.dreiecke();
            float[] pos = new float[d.size() * 3], normale = new float[d.size() * 3], uv = new float[d.size() * 2];

            for (int i = 0; i < d.size(); i++)
            {
                float[] e = d.get(i);

                System.arraycopy(e, 0, pos, i * 3, 3);
                System.arraycopy(e, 5, normale, i * 3, 3);
                System.arraycopy(e, 3, uv, i * 2, 2);
            }

            return new ModelVAOData(pos, normale, new float[d.size() * 4], uv);
        }

        /** The bone as the int bits the RG32_SINT binding reads */
        private float[] licht()
        {
            List<float[]> d = this.dreiecke();
            float[] licht = new float[d.size() * 2];

            for (int i = 0; i < d.size(); i++) licht[i * 2] = d.get(i)[8];

            return licht;
        }
    }
}
