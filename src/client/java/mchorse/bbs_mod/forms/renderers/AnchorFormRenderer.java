package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

public class AnchorFormRenderer extends FormRenderer<AnchorForm>
{
    public static final Link ANCHOR_PREVIEW = Link.assets("textures/anchor.png");

    private IEntity entity = new StubEntity();

    public AnchorFormRenderer(AnchorForm form)
    {
        super(form);
    }

    /**
     * ⛔ OFFENER FEHLER, GLEICHE URSACHE WIE BEI {@code TrailFormRenderer} (2026-08-10).
     *
     * <p>Gemessen im A/B gegen bbs-1211-ref, Anker-Form in den ersten Replay gesetzt,
     * Eigenrauschen beider Seiten 0,00 %:
     *
     * <pre>
     *   1.21.1   Dreibein klein in der Listenzelle
     *   26.2     Dreibein riesig unten ueber der Werkzeugleiste, Zelle leer
     *   A/B      1,14 bis 1,54 %
     * </pre>
     *
     * <p>Dieselbe Signatur wie Trail: die Vorschau verlaesst ihre Zelle, rund vierfach zu
     * gross und versetzt. Ausgeschlossen ist damit der Form-Renderer selbst --
     * {@code renderInUI} ist zwischen beiden Baeumen byte-gleich, und die Textur
     * {@code assets:textures/anchor.png} ist auf beiden Seiten 40x40. Der Defekt sitzt im
     * gemeinsamen Miniaturenweg, nicht hier. Der volle Messstand steht bei
     * {@code TrailFormRenderer#renderInUI}.
     */
    @Override
    /**
     * ⛔ NUR OHNE TEILE. Mit Teilen zeichnet renderInUI raeumlich und schiebt dabei auf
     * context.batcher.pose() -- inline gelaufen bleibt der Weltstapel unausgeglichen und das
     * Bild stirbt an "Pose stack not empty" (LevelRenderer.checkPoseStack), gemeldet vom
     * Nutzer 2026-08-11 beim Klicken und Scrollen im Morph-Panel,
     * crash-2026-08-11_00.30.26-client.txt. Der raeumliche Zweig gehoert weiter in die
     * Miniaturkachel.
     */
    protected boolean flacheVorschau()
    {
        return this.form.parts.getAll().isEmpty();
    }

    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        if (this.form.parts.getAll().isEmpty())
        {
            Texture texture = context.render.getTextures().getTexture(ANCHOR_PREVIEW);

            int w = texture.width;
            int h = texture.height;
            int x = (x1 + x2) / 2;
            int y = (y1 + y2) / 2;

            context.batcher.fullTexturedBox(texture, x - w / 2, y - h / 2, w, h);
        }
        else
        {
            PoseStack stack = context.batcher.pose();
            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

            /* 26.2: depthFunc(GL_LEQUAL) ist ersatzlos entfallen. Der Aufruf schaltete den
             * Tiefentest fuer die Dauer des 3D-Einschubs vom Immer-Durchlassen der
             * Oberflaeche auf den normalen Test zurueck. Genau den bringt jetzt jeder
             * Teil-Draw selbst mit: BBS-Modelle ueber ModelVAOPipeline - dort steht
             * GREATER_THAN_OR_EQUAL, das reversed-Z-Gegenstueck zu GL_LEQUAL, NICHT
             * LESS_THAN_OR_EQUAL -, Item- und Blockteile ueber die Pipeline ihres
             * RenderType (RenderType#pipeline()). */
            stack.pushPose();

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            /* Why? I don't know, because fuck you */
            stack.rotate(Axis.YN.rotationDegrees(180F));
            stack.last().normal().getScale(Vectors.EMPTY_3F);
            stack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            this.renderBodyParts(new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.entity, stack, net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY, context.getTransition())
                .inUI());

            stack.popPose();

            /* 26.2: depthFunc(GL_ALWAYS) ist ersatzlos entfallen. Es stellte den Zustand
             * her, den die 2D-Oberflaeche nach dem 3D-Einschub erwartete. Tiefe ist kein
             * globaler Schalter mehr - was danach in der Oberflaeche zeichnet, bekommt sie
             * aus seiner eigenen GUI-Pipeline (RenderPipelines.GUI_TEXTURED bzw.
             * BBSPipelines). Es gibt nichts zurueckzustellen. */
        }
    }
}
