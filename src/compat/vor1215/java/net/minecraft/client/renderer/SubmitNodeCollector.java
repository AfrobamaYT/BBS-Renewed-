package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** BBS-Shim fuer Minecraft vor 1.21.9 (siehe OrderedSubmitNodeCollector). */
public interface SubmitNodeCollector extends OrderedSubmitNodeCollector
{
    OrderedSubmitNodeCollector order(int order);

    interface CustomGeometryRenderer
    {
        void render(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
