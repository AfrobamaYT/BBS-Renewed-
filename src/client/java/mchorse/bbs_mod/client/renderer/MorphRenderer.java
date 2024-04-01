package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.immersive.ImmersiveCameraController;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.utils.math.Interpolations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class MorphRenderer
{
    public static boolean renderPlayer(AbstractClientPlayerEntity player, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i)
    {
        Morph morph = Morph.getMorph(player);

        if (morph != null && morph.form != null)
        {
            if (canRender())
            {
                RenderSystem.enableDepthTest();

                float bodyYaw = Interpolations.lerp(player.prevBodyYaw, player.bodyYaw, g);

                matrixStack.push();
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

                FormUtilsClient.render(morph.form, FormRenderingContext
                    .set(morph.entity, matrixStack, i, g)
                    .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

                matrixStack.pop();

                RenderSystem.disableDepthTest();
            }

            return true;
        }

        return false;
    }

    private static boolean canRender()
    {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;
        
        if (currentScreen instanceof UIScreen uiScreen)
        {
            if (uiScreen.getMenu() instanceof UIDashboard dashboard)
            {
                UIDashboardPanel panel = dashboard.getPanels().panel;

                if (panel instanceof UIMorphingPanel morphingPanel)
                {
                    return !morphingPanel.palette.editor.isEditing();
                }
            }
        }

        return true;
    }
}