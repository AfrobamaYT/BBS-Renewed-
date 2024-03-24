package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class MorphRenderer
{
    public static boolean renderPlayer(AbstractClientPlayerEntity abstractClientPlayerEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i)
    {
        Morph morph = Morph.getMorph(abstractClientPlayerEntity);

        if (morph != null && morph.form != null)
        {
            RenderSystem.enableDepthTest();

            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f));

            FormUtilsClient.render(morph.form, FormRenderingContext
                    .set(new MCEntity(abstractClientPlayerEntity), matrixStack, i, g)
                    .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

            matrixStack.pop();

            RenderSystem.disableDepthTest();

            return true;
        }

        return false;
    }
}