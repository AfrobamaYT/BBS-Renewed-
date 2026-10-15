package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.forms.FormSammler;
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
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class MorphRenderer
{
    public static boolean hidePlayer = false;

    /**
     * ⚠️ 26.2 — DIE ENTITY FEHLT IM ZEICHEN-ZWEIG. Ein Renderer sieht beim Einreichen nur
     * noch einen Zustand; die Entity gibt es ausschliesslich beim Einsammeln. Beide
     * BBS-Haken hier brauchen sie aber (Morph am Spieler, SelectorOwner am Mob), und der
     * Vanilla-Zustand traegt weder Entity noch UUID.
     *
     * <p>Der vorgesehene Weg dafuer ist Fabrics {@code RenderStateDataKey}: eine
     * Zusatzablage, die Fabric per Mixin an JEDEN Render-Zustand haengt (geprueft:
     * {@code RenderStateMixin implements FabricRenderState} listet
     * {@code EntityRenderState} unter seinen Zielen). Gefuellt wird sie EINMAL, am Ende von
     * {@code EntityRenderDispatcher.extractEntity} — siehe {@code EntityRenderDispatcherMixin}.
     *
     * <p>⚠️ Grenze: gefuellt ist der Schluessel nur fuer Zustaende, die ueber den
     * Dispatcher gelaufen sind (das ist der ganze Welt-Zeichenweg). Wer einen Zustand
     * direkt ueber {@code renderer.createRenderState(entity, delta)} baut, bekommt {@code
     * null} zurueck — dann gilt Vanilla-Verhalten, nicht der Absturz.
     */
    public static final RenderStateDataKey<Entity> ENTITY_AM_ZUSTAND = RenderStateDataKey.create(() -> "bbs:entity");

    /** Liest die beim Einsammeln hinterlegte Entity, oder {@code null}. */
    public static Entity entityVon(EntityRenderState zustand)
    {
        return zustand == null ? null : ((FabricRenderState) zustand).getData(ENTITY_AM_ZUSTAND);
    }

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

                /* ⚠️ LivingEntityRenderer.getOverlayCoords nimmt in 26.2 einen
                 * LivingEntityRenderState statt der Entity und liest daraus genau ein Feld:
                 * hasRedOverlay. Wir haben hier keinen solchen Zustand — also packen wir
                 * dasselbe Ergebnis direkt. Das ist Zeile fuer Zeile, was
                 * getOverlayCoords(player, 0F) bis 1.21.1 tat. */
                int overlay = OverlayTexture.pack(0F, player.hurtTime > 0 || player.deathTime > 0);

                matrixStack.pushPose();
                try
                {
                matrixStack.rotate(Axis.YP.rotationDegrees(-bodyYaw));

                /* ⛔ OHNE mitSammler STIRBT DAS SPIEL — derselbe Fehler wie beim Actor
                 * (ActorEntityRenderer, 2026-08-10), hier auf dem SPIELER-Weg. Vanilla
                 * rendert den Spieler ueber submitEntities; dort ist kein FormSammler
                 * angemeldet, FormRenderer.render greift per pflicht() danach und wirft.
                 * Die Ausnahme verschwindet im leeren catch in FormUtilsClient.render, das
                 * popPose() darunter wird nie erreicht, und ein Bild spaeter stirbt das
                 * Spiel an "Pose stack not empty" (LevelRenderer.checkPoseStack) — mit einem
                 * Stapelabzug ohne eine einzige BBS-Zeile.
                 *
                 * Gemeldet 2026-08-11: Morph-Panel oeffnen genuegt,
                 * crash-2026-08-11_01.14.00-client.txt nennt die Kette
                 * MorphRenderer.renderPlayer -> FormUtilsClient.render ->
                 * FormRenderer.render -> FormSammler.pflicht. */
                FormRenderingContext formContext = new FormRenderingContext()
                    .set(FormRenderType.ENTITY, morph.entity, matrixStack, i, overlay, g)
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera());
                FormSammler.mitSammler(vertexConsumerProvider, () -> NativeMorphCapture.original(morph.getForm(), formContext,
                    () -> FormUtilsClient.render(morph.getForm(), formContext)));

                /* ⛔ ZWEITRENDER ZUR ZEICHEN-ZEIT — ohne ihn sind MODELL-Morphs in der Welt
                 * unsichtbar. Der Aufruf darueber laeuft in der SAMMELphase: sein
                 * VAO-Sofortzug wird vom Hauptpass uebermalt (RenderDoc-belegt), nur seine
                 * Collector-Anteile (Ruestung, Item-/Block-Teile) stimmen. MorphAufschub
                 * zeichnet dieselbe Gestalt in AFTER_SOLID_FEATURES noch einmal — dann
                 * sitzt der Sofortzug UEBER Himmel und Terrain, und die Collector-Anteile
                 * des Zweitrenders fallen in einen Wegwerf-Speicher (sonst doppelt). */
                MorphAufschub.einreichen(morph.getForm(), morph.entity, matrixStack, i, overlay, g);

                }
                finally { matrixStack.popPose(); }
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
            try
            {
            matrixStack.rotate(Axis.YP.rotationDegrees(-bodyYaw));

            FormRenderingContext formContext = new FormRenderingContext()
                .set(FormRenderType.ENTITY, owner.entity, matrixStack, i, o, g)
                .camera(Minecraft.getInstance().gameRenderer.mainCamera());
            NativeMorphCapture.original(form, formContext, () -> FormUtilsClient.render(form, formContext));

            /* Zweitrender wie in renderPlayer: der Aufruf darueber liegt in der
             * Sammelphase, sein Modell-Sofortzug wird uebermalt. */
            MorphAufschub.einreichen(form, owner.entity, matrixStack, i, o, g);

            }
            finally { matrixStack.popPose(); }

            return true;
        }

        return false;
    }
}
