package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

public class MorphRenderer
{
    public static boolean hidePlayer = false;

    public static boolean renderPlayer(AbstractClientPlayer player, float f, float g, PoseStack matrixStack, SubmitNodeCollector vertexConsumerProvider, int i)
    {
        if (hidePlayer)
        {
            if (FormUtilsClient.getCurrentForm() instanceof MobForm form && !form.isPlayer())
            {
                return true;
            }
        }

        Morph morph = Morph.getMorph(player);

        if (morph != null && morph.getForm() != null)
        {
            if (canRender())
            {
                /* 26.2: der Tiefentest ist kein globaler Schalter mehr, sondern Teil der
                 * Pipeline. Das enableDepthTest hier und das disableDepthTest hinter dem
                 * popPose entfallen deshalb ersatzlos: gezeichnet wird ueber
                 * FormUtilsClient.render mit FormRenderType.ENTITY, und dort bringt jeder
                 * Zeichenweg seinen Tiefenzustand selbst mit — die Pipeline des jeweiligen
                 * RenderType, beim Modell-Zeichenweg ModelVAOPipeline.DURCHSICHTIG
                 * (GREATER_THAN_OR_EQUAL mit Tiefenschreiben, reversed-Z). */
                float bodyYaw = Lerps.lerp(player.yBodyRotO, player.yBodyRot, g);
                int overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);

                matrixStack.pushPose();
                matrixStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

                FormUtilsClient.render(morph.getForm(), new FormRenderingContext()
                    .set(FormRenderType.ENTITY, morph.entity, matrixStack, i, overlay, g)
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

                matrixStack.popPose();
            }

            return true;
        }

        return false;
    }

    private static boolean canRender()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();
        
        if (menu instanceof UIDashboard dashboard)
        {
            UIDashboardPanel panel = dashboard.getPanels().panel;

            if (panel instanceof UIMorphingPanel morphingPanel)
            {
                return !morphingPanel.palette.editor.isEditing();
            }
        }

        return true;
    }

    public static boolean renderLivingEntity(LivingEntity livingEntity, float f, float g, PoseStack matrixStack, SubmitNodeCollector vertexConsumerProvider, int i, int o)
    {
        if (!(livingEntity instanceof ISelectorOwnerProvider))
        {
            return false;
        }

        SelectorOwner owner = ((ISelectorOwnerProvider) livingEntity).getOwner();

        owner.check();

        Form form = owner.getForm();

        if (form != null)
        {
            /* Tiefentest wie in renderPlayer: seit 26.2 Sache der Pipeline, nicht mehr ein
             * globaler Schalter. Das Paar enableDepthTest/disableDepthTest um den Formaufruf
             * entfaellt ersatzlos — der Zustand kommt aus dem Zeichenweg von
             * FormRenderType.ENTITY. */
            float bodyYaw = Lerps.lerp(livingEntity.yBodyRotO, livingEntity.yBodyRot, g);

            matrixStack.pushPose();
            matrixStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, owner.entity, matrixStack, i, o, g)
                .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

            matrixStack.popPose();

            return true;
        }

        return false;
    }
}