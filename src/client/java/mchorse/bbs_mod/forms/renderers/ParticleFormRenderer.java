package mchorse.bbs_mod.forms.renderers;

import net.minecraft.client.renderer.RenderPipelines;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ParticleFormRenderer extends FormRenderer<ParticleForm> implements ITickable
{
    public static long lastUpdate = 0L;

    private ParticleEmitter emitter;
    private boolean checked;
    private boolean restart;
    private long lastParticleUpdate = lastUpdate;

    public ParticleFormRenderer(ParticleForm form)
    {
        super(form);
    }

    public ParticleEmitter getEmitter()
    {
        return this.emitter;
    }

    public void ensureEmitter(Level world, float transition)
    {
        if (this.lastParticleUpdate < lastUpdate)
        {
            this.lastParticleUpdate = lastUpdate;
            this.checked = false;
        }

        if (!this.checked)
        {
            ParticleScheme scheme = BBSModClient.getParticles().load(this.form.effect.get());

            if (scheme != null)
            {
                this.emitter = new ParticleEmitter();
                this.emitter.setScheme(scheme);
                this.emitter.setWorld(world);
            }

            this.checked = true;
        }

        if (this.emitter != null && !BBSRendering.isIrisShadowPass())
        {
            boolean lastPaused = this.emitter.paused;

            this.emitter.paused = this.form.paused.get();

            if (lastPaused != this.emitter.paused && !this.emitter.paused && this.emitter.age > 0 && !this.restart)
            {
                this.restart = true;
            }
        }
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEmitter(Minecraft.getInstance().level, context.getTransition());

        ParticleEmitter emitter = this.emitter;

        if (emitter != null)
        {
            PoseStack stack = context.batcher.pose();
            int scale = (y2 - y1) / 2;

            stack.pushPose();
            stack.translate((x2 + x1) / 2, (y2 + y1) / 2, 40);
            MatrixStackUtils.scaleStack(stack, scale, scale, scale);

            this.updateTexture(context.getTransition());
            emitter.lastGlobal.set(new Vector3f(0, 0, 0));
            emitter.rotation.identity();
            emitter.renderUI(stack, context.getTransition());

            stack.popPose();
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        if ((mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.earlyWorld()
            || mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.replaying()
                && mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.world(context))
            && mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.mesh("particle")) return;
        Matrix4f capturePose = new Matrix4f(context.stack.last().pose());
        if (!context.ui && !context.isPicking() && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes()
            && !BBSRendering.isIrisShadowPass()
            && mchorse.bbs_mod.client.renderer.NativeMorphCapture.suppressesParticle(this.form, context.entity, capturePose)) return;
        this.ensureEmitter(Minecraft.getInstance().level, context.transition);

        ParticleEmitter emitter = this.emitter;

        if (emitter != null)
        {
            emitter.setUserVariables(
                this.form.user1.get(),
                this.form.user2.get(),
                this.form.user3.get(),
                this.form.user4.get(),
                this.form.user5.get(),
                this.form.user6.get()
            );

            this.updateTexture(context.getTransition());

            Matrix4f matrix = new Matrix4f(InverseView.get());

            /* Since 1.21.1 the world render keeps the camera view in RenderSystem's global
             * model-view and gives the stack an identity base, so stack.last() no longer
             * carries the view rotation InverseView is meant to cancel. Fold the global
             * model-view in (identity, hence a no-op, in the form editor) so the emitter's
             * world origin and rotation come out right. */
            matrix.mul(RenderSystem.getModelViewMatrixCopy());
            matrix.mul(context.stack.last().pose());

            Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));
            translation.add(context.camera.position.x, context.camera.position.y, context.camera.position.z);

            GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;

            // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
            // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

            context.stack.pushPose();
            context.stack.setIdentity();
            /* The emitter builds its quads in camera-relative world space, so the effective
             * model-view (RenderSystem.getModelViewMatrixCopy() * stack) must be the pure camera
             * view. Since 1.21.1 holds that view in RenderSystem's global model-view (it used
             * to be identity), cancel it here so it isn't applied twice: stack = inv(global) * view.
             * In the form editor the global model-view is identity, so this stays the old `view`. */
            Matrix4f particleView = new Matrix4f(InverseView.get()).invert();
            context.stack.mulPose(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).invert().mul(particleView));

            emitter.lastGlobal.set(translation);
            emitter.rotation.set(matrix);

            if (!BBSRendering.isIrisShadowPass())
            {
                boolean early = mchorse.bbs_mod.client.renderer.NativeMorphCapture.capturingParticles()
                    || mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.earlyWorld();
                boolean nativeWorld = !context.isPicking() && !context.ui
                    && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes()
                    && (early || mchorse.bbs_mod.forms.FormTranslucentQueue.isActive())
                    && mchorse.bbs_mod.integrations.vitrail.VitrailRetainedParticles.active();
                boolean shadersEnabled = !nativeWorld && BBSRendering.isIrisShadersEnabled();

                VertexFormat format = context.isPicking() ? DefaultVertexFormat.POSITION_TEX_COLOR
                    : shadersEnabled ? DefaultVertexFormat.ENTITY : DefaultVertexFormat.PARTICLE;
                Supplier<RenderPipeline> shader = shadersEnabled
                    ? this.getShader(context, () -> RenderPipelines.ENTITY_TRANSLUCENT,
                        mchorse.bbs_mod.forms.renderers.utils.TexturedFormPicking::pipeline)
                    : this.getShader(context, () -> RenderPipelines.TRANSLUCENT_PARTICLE,
                        mchorse.bbs_mod.forms.renderers.utils.TexturedFormPicking::pipeline);

                emitter.setupCameraProperties(context.camera);
                if (mchorse.bbs_mod.client.renderer.NativeMorphCapture.capturingParticles() && nativeWorld)
                    mchorse.bbs_mod.client.renderer.NativeMorphCapture.particle(this.form, context.entity, capturePose, () ->
                        emitter.render(format, shader, context.stack, context.overlay, context.getTransition(), context.getPickingIndex(), true));
                else emitter.render(format, shader, context.stack, context.overlay, context.getTransition(), context.getPickingIndex(), nativeWorld);
            }

            context.stack.popPose();

        }
    }

    private void updateTexture(float transition)
    {
        if (this.emitter != null)
        {
            this.emitter.texture = this.form.texture.get();
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEmitter(entity.getWorld(), 0F);

        if (this.emitter != null)
        {
            /* Rewind the emitter if it was paused and resumed in order to make
             * particle effects with once emitter */
            if (this.restart)
            {
                this.emitter.stop();
                this.emitter.start();

                this.restart = false;
            }

            this.emitter.update();
        }
    }
}
