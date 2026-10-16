package mchorse.bbs_mod.ui.forms.editors.utils;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
/* 26.2: com.mojang.blaze3d.shaders.Uniform ist ersatzlos entfallen -- es gibt keine
 * Einzel-Uniforms mehr, die man am Shader setzen koennte, nur noch Uniform-BLOECKE, die ein
 * Zeichendurchgang bindet. Diese Datei hat das schon hinter sich; nur der Import stand noch. */
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

import java.util.function.Supplier;

public class UIPickableFormRenderer extends UIFormRenderer implements GizmoViewport
{
    public UIFormEditor formEditor;

    private boolean update;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();

    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    private IEntity target;
    private Supplier<Boolean> renderForm;

    public UIPickableFormRenderer(UIFormEditor formEditor)
    {
        this.formEditor = formEditor;
    }

    public void updatable()
    {
        this.update = true;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public void setRenderForm(Supplier<Boolean> renderForm)
    {
        this.renderForm = renderForm;
    }

    public IEntity getTargetEntity()
    {
        return this.target == null ? this.entity : this.target;
    }

    public void setTarget(IEntity target)
    {
        this.target = target;
    }

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_form"));
        this.stencil.resizeGUI(this.area.w, this.area.h);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.ensureFramebuffer();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.formEditor.clickViewport(context, this.stencil))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.gizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    public GizmoInteraction getGizmoInteraction()
    {
        return this.gizmo;
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    /**
     * ⛔ UMGERECHNET, weil {@code camera.projection} als einzige der drei Gizmo-Ansichten
     * BBS-eigen ist: die alte OpenGL-Form (nah -1, fern +1). UIFilmController und
     * UIModelBlockPanel reichen Mojangs Weltmatrix durch, die schon umgekehrt ist. Ohne die
     * Umrechnung gewinnt hier die entferntere Flaeche — gemessen: 1.21.1 zeichnet in dieser
     * Draufsicht eine durchgehend rote Senkrechte (255,64,89), 26.2 zeigte ohne sie ein
     * Gemisch aus Gruen, Weiss und Blau. Begruendung an {@link Projektion#setzenUndUmkehren}.
     */
    @Override
    public Matrix4f getGizmoProjection()
    {
        return mchorse.bbs_mod.graphics.Projektion.umgekehrt(this.camera.projection);
    }

    @Override
    public Area getGizmoArea()
    {
        return this.area;
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        return this.formEditor.startGizmo(context, stencilIndex);
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        this.formEditor.pickFormFromRenderer(new Pair<>(form, bone));
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        this.formEditor.preFormRender(context, this.form);

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.target == null ? this.entity : this.target, context.batcher.pose(), net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        if (this.renderForm == null || this.renderForm.get())
        {
            /* ⛔ EINREIHEN STATT SOFORT ZEICHNEN (2026-08-04, Bugliste #16).
             *
             * Hier stand ein Sofortzug. Der landet in 26.2 im Standard-Framebuffer, BEVOR die
             * Oberflaeche gezeichnet wird -- und der Panel-Hintergrund des Editors deckt ihn
             * anschliessend zu. Gemessen, in dieser Reihenfolge:
             *
             *   editor.zielpuffer: gebunden=0        der Zug ging ins Standardziel
             *   0 von 30 Bildern sichtbar            im Ruhezustand, 0 von 20 in Bewegung
             *   Auswahlpuffer: Inhalt (805,151)-(1114,780) in 1920x970 -- Figur vollstaendig
             *
             * Der richtige Weg war die ganze Zeit da und wurde nur uebersprungen: die
             * Basisklasse {@link UIFormRenderer#renderUserModel} reiht das Modell als Bild im
             * Bild ein (Aufgabe 96, Schalter MODELL_UEBER_BILD_IM_BILD steht auf true), und
             * diese Unterklasse ueberschreibt genau diese Methode, ohne sie zu rufen.
             *
             * Bewusst nicht per super-Aufruf: die Basis nimmt {@code this.entity}, der Editor
             * braucht {@link #getTargetEntity()} (beim Bearbeiten einer Form AUF einem Replay
             * sind das verschiedene). Der Rest ist derselbe Zustand. */
            org.joml.Matrix4f pose = new org.joml.Matrix4f(context.batcher.pose().last().pose());
            net.minecraft.client.gui.navigation.ScreenRectangle flaeche =
                new net.minecraft.client.gui.navigation.ScreenRectangle(
                    this.area.x, this.area.y, this.area.w, this.area.h);

            context.batcher.bildImBild(new mchorse.bbs_mod.ui.framework.elements.utils.vorschau.VorschauModellZustand(
                this.form, this.getTargetEntity(), this.camera,
                new Matrix4f(this.camera.projection), pose,
                mchorse.bbs_mod.graphics.Standbild.uebergang(context.getTransition()),
                this.area.x, this.area.y, this.area.ex(), this.area.ey(),
                flaeche, flaeche));

            if (this.form.hitbox.get())
            {
                this.renderFormHitbox(context);
            }
        }

        /* Keep the gizmo the same on-screen size as in the film preview (see
         * Gizmo#setViewportScale); set before both the visual (renderAxes) and the
         * stencil pass below so the drawn handles and their pick hitbox match. */
        Gizmo.INSTANCE.setViewportScale(context.menu.height / (float) this.area.h);

        this.renderAxes(context);

        if (this.area.isInside(context))
        {
            GlStateManager._disableScissorTest();

            this.stencilMap.setup();
            this.stencil.apply();

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
            PoseStack stack = context.render.batcher.pose();

            stack.pushPose();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
            }

            /* Reorient the pick stencil into the active space to match the visual
             * (below), so hovering a ring lands where it's drawn. */
            Gizmo.INSTANCE.reorientForSpace(stack, this.formEditor.getGizmoSpace(), this.camera.view, this.getSceneAxes());

            /* Skip the gizmo's pick stencil while the hide-gizmo key is held, so its handles can't be
             * clicked when hidden. Form-part picking (the stencil rendered above) is left intact, and
             * the F8 axes toggle is untouched here on purpose. */
            if (!UIBaseMenu.isHideGizmoHeld())
            {
                Gizmo.INSTANCE.renderStencil(context.batcher.pose(), this.stencilMap);
            }

            stack.popPose();

            this.stencil.pickGUI(context, this.area, BBSSettings.gizmoHoverTolerance.get(), Gizmo.STENCIL_MAX);
            this.stencil.unbind(this.stencilMap);

            /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
             * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
             * zeichnen will, gibt das Ziel dort an. */

            if (mchorse.bbs_mod.graphics.GpuBackend.isOpenGl())
            {
                GlStateManager._enableScissorTest();
            }
        }
        else
        {
            this.stencil.clearPicking();
        }

        /* Das sichtbare Gizmo -- eingereiht statt sofort gezeichnet (siehe renderAxes). Reihenfolge
         * wie bei den anderen beiden Wirten: NACH dem Auswahl-Durchgang, VOR update(), damit der
         * Bildschirmradius der Kugel fuer die Zeigerpruefung aktuell ist. */
        if (UIBaseMenu.shouldRenderAxes())
        {
            this.gizmo.renderGizmo(context);
        }

        this.gizmo.update(context);
    }

    private void renderAxes(UIContext context)
    {
        Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
        PoseStack stack = context.render.batcher.pose();

        stack.pushPose();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
        }

        /* Reorient the drawn gizmo into the active space (the preview's own scene
         * axes for GLOBAL, screen axes for VIEW); LOCAL leaves it on the bone's
         * own axes. Kept in lockstep with the pick stencil above. The scene axes
         * are the renderer's transform ({@link UIModelRenderer#getSceneAxes}):
         * identity in a plain preview, the model block's own rotation when the
         * block is edited immersively — GLOBAL must follow the container the
         * form is drawn inside, or it points off the scene the user sees. */
        Gizmo.INSTANCE.reorientForSpace(stack, this.formEditor.getGizmoSpace(), this.camera.view, this.getSceneAxes());

        /* ⛔ HIER WIRD NICHT MEHR GEZEICHNET, SONDERN NUR DIE LAGE EINGEFANGEN (26.2).
         *
         * Bis 1.21.1 stand hier {@code Gizmo.INSTANCE.render(stack)} -- ein Sofortzug. In 26.2
         * sammelt der UI-Durchlauf nur ein, {@code GuiRenderer.render()} zeichnet danach; ein
         * Sofortzug landet also im HAUPTBILD und wird von der GUI uebermalt. Gemessen am
         * laufenden Spiel, im offenen Paletten-Editor:
         *
         *     129 Zuege in 2,5 s durch GizmoZug.zeichnen -- JEDER mit ziel = null (Hauptbild)
         *     Gizmo.renderInterface: 0 Aufrufe          -- der Bild-im-Bild-Weg lag brach
         *
         * Die beiden anderen Gizmo-Wirte machen es laengst richtig und sind die Vorlage:
         * {@code UIFilmController:1296} und {@code UIModelBlockPanel:668} fangen im 3D-Zug ein
         * und zeichnen im UI-Durchlauf ueber {@code GizmoInteraction#renderGizmo}. Nur dieser
         * dritte Wirt hing noch am Sofortzug -- das war die ganze Regression.
         *
         * Der Stapel traegt an dieser Stelle bereits Ursprungsmatrix und Raumdrehung (oben in
         * dieser Methode), und genau die braucht der eingereihte Zug. Darum wird hier
         * eingefangen und nicht spaeter: {@link Gizmo#renderInterface} baut seinen Zustand aus
         * {@code lastRenderMatrix}. */
        if (UIBaseMenu.shouldRenderAxes())
        {
            Gizmo.INSTANCE.captureVisual(stack);
        }

        stack.popPose();
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(context.batcher.pose(), -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(context.batcher.pose(), -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.update && this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        this.gizmo.renderSphereHighlight(context);
        this.gizmo.renderReadout(context);

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        RenderPipeline previewProgram = BBSShaders.getPickerPreviewProgram();
        /* Die Hervorhebung wird VORHER in ein eigenes Bild gerechnet, nicht beim Zeichnen
         * in die Oberflaeche -- ein GUI-Element kann die fuenf Werte nicht binden.
         * Entscheidung und verworfene Auswege stehen in Bildfilter. */
        mchorse.bbs_mod.graphics.Bildfilter.kennungHervorheben(texture,
            this.stencil.zwischenbild(), index,
            BBSSettings.stencilHighlightColor.get());

        /* 26.2: enableBlend entfaellt ersatzlos — hier wirkte es ohnehin nicht mehr.
         * texturedBox reicht die Zeichnung als GuiElementRenderState an Vanillas
         * GUI-Renderer weiter (Batcher2D#blit -> BlitRenderState), und der oeffnet
         * seinen Durchgang mit der Pipeline DES ELEMENTS; ein globaler Mischschalter
         * hat darauf keinen Zugriff. Das Mischen muss also die Pipeline des
         * Vorschau-Shaders mitbringen — so wie BBSPipelines#getSelection, das seinen
         * ColorTargetState von RenderPipelines.GUI_TEXTURED erbt. */
        context.batcher.texturedBox(() -> net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.stencil.zwischenbild(), Colors.WHITE, this.area.x, this.area.y, this.area.w, this.area.h, 0, h, w, 0, w, h);

        if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        if (this.renderForm == null || this.renderForm.get())
        {
            super.renderGrid(context);
        }
    }
}
