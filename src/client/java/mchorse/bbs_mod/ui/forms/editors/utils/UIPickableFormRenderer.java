package mchorse.bbs_mod.ui.forms.editors.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSShaders;
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
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import java.util.function.Supplier;

public class UIPickableFormRenderer extends UIFormRenderer
{
    private static final int SPHERE_PICK_MIN_RADIUS_PX = 12;
    private static final int BONE_VS_SPHERE_DRAG_THRESHOLD_PX = 4;

    public UIFormEditor formEditor;

    private boolean update;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();

    private boolean sphereHovered;
    private final Vector2f sphereScreenCenter = new Vector2f();
    /** Deferred bone-vs-sphere click. Non-null form means a press is
     *  in flight on a bone inside the sphere's pick disc — drag past
     *  {@link #BONE_VS_SPHERE_DRAG_THRESHOLD_PX} → trackball, release
     *  without drag → bone pick. */
    private int pendingDownX;
    private int pendingDownY;
    private Form pendingPickForm;
    private String pendingPickBone;

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
        if (this.pendingPickForm != null && context.mouseButton == 0)
        {
            Form form = this.pendingPickForm;
            String bone = this.pendingPickBone;

            this.pendingPickForm = null;
            this.pendingPickBone = null;

            this.formEditor.pickFormFromRenderer(new Pair<>(form, bone));

            return true;
        }

        return super.subMouseReleased(context);
    }

    public boolean isSphereHovered()
    {
        return this.sphereHovered;
    }

    public void beginPendingSpherePick(UIContext context, Pair<Form, String> pair)
    {
        this.pendingDownX = context.mouseX;
        this.pendingDownY = context.mouseY;
        this.pendingPickForm = pair.a;
        this.pendingPickBone = pair.b == null ? "" : pair.b;
    }

    @Override
    protected void processInputs(UIContext context)
    {
        super.processInputs(context);

        if (this.pendingPickForm != null)
        {
            int dx = context.mouseX - this.pendingDownX;
            int dy = context.mouseY - this.pendingDownY;

            if (dx * dx + dy * dy > BONE_VS_SPHERE_DRAG_THRESHOLD_PX * BONE_VS_SPHERE_DRAG_THRESHOLD_PX)
            {
                this.pendingPickForm = null;
                this.pendingPickBone = null;
                this.formEditor.startSphereGizmo(context);
            }
        }
    }

    private void updateSphereHover(UIContext context)
    {
        boolean hover = false;

        if (Gizmo.INSTANCE.isSphereInteractive() && !this.stencilWouldWinSpherePick()
            && Gizmo.INSTANCE.computeScreenCenter(this.camera.projection, this.area.x, this.area.y, this.area.w, this.area.h, this.sphereScreenCenter))
        {
            float radius = Math.max(SPHERE_PICK_MIN_RADIUS_PX, Gizmo.INSTANCE.computeScreenRadius(this.camera.projection, this.area.x, this.area.y, this.area.w, this.area.h));
            float dx = context.mouseX - this.sphereScreenCenter.x;
            float dy = context.mouseY - this.sphereScreenCenter.y;

            hover = dx * dx + dy * dy <= radius * radius;
        }

        this.sphereHovered = hover;
    }

    private boolean stencilWouldWinSpherePick()
    {
        if (!this.stencil.hasPicked()) return false;

        int idx = this.stencil.getIndex();

        return idx >= Gizmo.STENCIL_X && idx <= Gizmo.STENCIL_VIEW;
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
            .set(FormRenderType.PREVIEW, this.target == null ? this.entity : this.target, context.batcher.getContext().getMatrices(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        if (this.renderForm == null || this.renderForm.get())
        {
            FormUtilsClient.render(this.form, formContext);

            if (this.form.hitbox.get())
            {
                this.renderFormHitbox(context);
            }
        }

        this.renderAxes(context);

        if (this.area.isInside(context))
        {
            GlStateManager._disableScissorTest();

            this.stencilMap.setup();
            this.stencil.apply();

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
            MatrixStack stack = context.render.batcher.getContext().getMatrices();

            stack.push();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
            }

            Gizmo.INSTANCE.renderStencil(context.batcher.getContext().getMatrices(), this.stencilMap);

            stack.pop();

            this.stencil.pickGUI(context, this.area);
            this.stencil.unbind(this.stencilMap);

            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);

            GlStateManager._enableScissorTest();

            this.updateSphereHover(context);
        }
        else
        {
            this.stencil.clearPicking();

            this.sphereHovered = false;
        }
    }

    private void renderAxes(UIContext context)
    {
        Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
        MatrixStack stack = context.render.batcher.getContext().getMatrices();

        stack.push();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
        }

        /* Draw axes */
        if (UIBaseMenu.renderAxes)
        {
            RenderSystem.disableDepthTest();
            Gizmo.INSTANCE.render(stack);
            RenderSystem.enableDepthTest();
        }

        stack.pop();
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(context.batcher.getContext().getMatrices(), -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(context.batcher.getContext().getMatrices(), -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
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

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        ShaderProgram previewProgram = BBSShaders.getPickerPreviewProgram();
        GlUniform target = previewProgram.getUniform("Target");

        if (target != null)
        {
            target.set(index);
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, this.area.x, this.area.y, this.area.w, this.area.h, 0, h, w, 0, w, h);

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
