package mchorse.bbs_mod.ui.particles;

import net.minecraft.client.renderer.RenderPipelines;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.expiration.ParticleComponentKillPlane;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class UIParticleSchemeRenderer extends UIModelRenderer
{
    public ParticleEmitter emitter;

    /**
     * A 16x16 fully white texture bound to the particle shader's light map slot
     * (Sampler2) when previewing particles in the editor. See {@link #renderUserModel}.
     */
    private static DynamicTexture whiteLightmapTexture;

    private Vector3f vector = new Vector3f(0, 0, 0);

    /**
     * Die Modellansicht der Kill-Ebene ist die Einheitsmatrix: ihre Ecken tragen die Pose
     * bereits ({@code addVertex(matrix, ...)}), und die Projektion kommt aus dem
     * Kamerablock des Vorschau-Renderers. Wer hier die Pose noch einmal uebergibt,
     * verschiebt die Ebene ein zweites Mal.
     */
    private static final Matrix4f EBENEN_MODELLANSICHT = new Matrix4f();

    private static int getWhiteLightmapTextureId()
    {
        if (whiteLightmapTexture == null)
        {
            whiteLightmapTexture = new DynamicTexture(16, 16, false);

            NativeImage image = whiteLightmapTexture.getPixels();

            for (int y = 0; y < 16; y++)
            {
                for (int x = 0; x < 16; x++)
                {
                    image.setPixelRGBA(x, y, -1);
                }
            }

            whiteLightmapTexture.upload();
        }

        return whiteLightmapTexture.getId();
    }

    public UIParticleSchemeRenderer()
    {
        super();

        this.emitter = new ParticleEmitter();
    }

    public void setScheme(ParticleScheme scheme)
    {
        this.emitter = new ParticleEmitter();
        this.emitter.setScheme(scheme);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* Debug readout (particle count and emitter age) in the preview's bottom-right corner. */
        if (this.emitter != null && this.emitter.scheme != null)
        {
            String label = this.emitter.particles.size() + "P - " + this.emitter.age + "A";

            context.batcher.textShadow(label, this.area.ex() - 4 - context.batcher.getFont().getWidth(label), this.area.ey() - 12);
        }
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.emitter != null)
        {
            this.emitter.rotation.identity();
            this.emitter.update();
        }
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.emitter == null || this.emitter.scheme == null)
        {
            return;
        }

        this.emitter.setupCameraProperties(this.camera);
        this.emitter.rotation.identity();

        PoseStack stack = context.batcher.getContext().pose();

        stack.pushPose();
        stack.setIdentity();
        stack.mulPose(new Matrix4f(InverseView.get()).invert());

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        /* The vanilla dynamic light map binding (Sampler2) does not survive into this screen's
         * draw call, so the particle shader (particle.vsh does vertexColor = Color *
         * texelFetch(Sampler2, ...)) samples a black light map and the particles render pitch
         * black — only their alpha/shape survives. The editor preview has no world context
         * (emitter.world == null), so the billboard always uses pack(15, 15), i.e. fully bright,
         * which a correctly bound light map would sample as pure white anyway. Bind our own
         * 16x16 white texture to Sampler2 right before the draw to get that fullbright result
         * without depending on the (here broken) light map binding. In-world particles are
         * unaffected: they render through ParticleFormRenderer with the real light map. */
        RenderSystem.setShaderTexture(2, getWhiteLightmapTextureId());

        this.emitter.render(DefaultVertexFormat.PARTICLE, () -> RenderPipelines.TRANSLUCENT_PARTICLE, stack, OverlayTexture.NO_OVERLAY, context.getTransition());
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();

        stack.popPose();

        ParticleComponentKillPlane plane = this.emitter.scheme.get(ParticleComponentKillPlane.class);

        if (plane.a != 0 || plane.b != 0 || plane.c != 0)
        {
            this.renderPlane(context, plane.a, plane.b, plane.c, plane.d);
        }
    }

    private void renderPlane(UIContext context, float a, float b, float c, float d)
    {
        Matrix4f matrix = new Matrix4f().mul(context.batcher.getContext().pose());
        final float alpha = 0.5F;

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(1024))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            this.calculate(0, 0, a, b, c, d);
            builder.addVertex(matrix, this.vector.x, this.vector.y, this.vector.z).setColor(0, 1, 0, alpha);
            this.calculate(0, 1, a, b, c, d);
            builder.addVertex(matrix, this.vector.x, this.vector.y, this.vector.z).setColor(0, 1, 0, alpha);
            this.calculate(1, 0, a, b, c, d);
            builder.addVertex(matrix, this.vector.x, this.vector.y, this.vector.z).setColor(0, 1, 0, alpha);

            this.calculate(1, 0, a, b, c, d);
            builder.addVertex(matrix, this.vector.x, this.vector.y, this.vector.z).setColor(0, 1, 0, alpha);
            this.calculate(0, 1, a, b, c, d);
            builder.addVertex(matrix, this.vector.x, this.vector.y, this.vector.z).setColor(0, 1, 0, alpha);
            this.calculate(1, 1, a, b, c, d);
            builder.addVertex(matrix, this.vector.x, this.vector.y, this.vector.z).setColor(0, 1, 0, alpha);

            /* Die drei Schalter sind der alte Zustandscode um genau diesen Zug herum:
             * culling = false stand hier woertlich (disableCull/enableCull um den
             * Zeichenbefehl), mischen und tiefePruefen = false, weil renderUserModel
             * unmittelbar vor diesem Aufruf disableBlend() und disableDepthTest() ruft.
             * Der Shader war der Vanilla-Kern position_color. */
            SofortZeichner.zeichnen(
                FormPipelines.holen(PrimitiveTopology.TRIANGLES, false, false, false),
                builder.build(),
                EBENEN_MODELLANSICHT,
                null,
                null);
        }
    }

    private void calculate(float i, float j, float a, float b, float c, float d)
    {
        final float radius = 5;

        if (b != 0)
        {
            this.vector.x = -radius + radius * 2 * i;
            this.vector.z = -radius + radius * 2 * j;
            this.vector.y = (a * this.vector.x + c * this.vector.z + d) / -b;
        }
        else if (a != 0)
        {
            this.vector.y = -radius + radius * 2 * i;
            this.vector.z = -radius + radius * 2 * j;
            this.vector.x = (b * this.vector.y + c * this.vector.z + d) / -a;
        }
        else if (c != 0)
        {
            this.vector.x = -radius + radius * 2 * i;
            this.vector.y = -radius + radius * 2 * j;
            this.vector.z = (b * this.vector.y + a * this.vector.x + d) / -c;
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        super.renderGrid(context);

        if (UIBaseMenu.shouldRenderAxes())
        {
            Draw.coolerAxes(context.batcher.getContext().pose(), 1F, 0.005F);
        }
    }
}