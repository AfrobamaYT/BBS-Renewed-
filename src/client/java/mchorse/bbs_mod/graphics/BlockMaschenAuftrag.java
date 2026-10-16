package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;

import java.util.function.Function;

/** Owns a Fabric mesh until its deferred block draw completes. */
public final class BlockMaschenAuftrag implements SubmitNodeCollector.CustomGeometryRenderer
{
    private final Mesh mesh;
    private final Function<VertexConsumer, VertexConsumer> color;
    private final int[] tints;
    private final int light;
    private final int overlay;

    public static boolean einreichen(Object mesh, PoseStack stack, SubmitNodeCollector collector, RenderType layer,
        Function<VertexConsumer, VertexConsumer> color, int[] tints, int light, int overlay)
    {
        if (!(mesh instanceof MutableMesh mutable))
        {
            return false;
        }

        collector.submitCustomGeometry(stack, layer,
            new BlockMaschenAuftrag(mutable.immutableCopy(), color, tints, light, overlay));

        return true;
    }

    private BlockMaschenAuftrag(Mesh mesh, Function<VertexConsumer, VertexConsumer> color, int[] tints, int light, int overlay)
    {
        this.mesh = mesh;
        this.color = color;
        this.tints = tints;
        this.light = light;
        this.overlay = overlay;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer consumer)
    {
        VertexConsumer colored = this.color.apply(consumer);
        QuadInstance instance = new QuadInstance();

        instance.setLightCoords(this.light);
        instance.setOverlayCoords(this.overlay);
        this.mesh.forEach((quad) ->
        {
            BakedQuad baked = quad.toBakedQuad(null);
            int index = baked.materialInfo().tintIndex();
            boolean tinted = index != -1 && index < this.tints.length;

            /* Same tint rule as Blockmodell's immediate mesh path. */
            instance.setColor(tinted ? ARGB.multiply(-1, this.tints[index]) : -1);
            colored.putBakedQuad(pose, baked, instance);
        });
    }
}
