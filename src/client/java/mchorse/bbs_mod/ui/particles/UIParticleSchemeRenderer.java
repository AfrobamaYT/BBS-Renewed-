package mchorse.bbs_mod.ui.particles;

import net.minecraft.client.renderer.RenderPipelines;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class UIParticleSchemeRenderer extends UIModelRenderer
{
    public ParticleEmitter emitter;

    /* ⛔ 26.2: Hier lag eine 16x16 komplett weisse Textur samt ihrem Erzeuger
     * (getWhiteLightmapTextureId). Sie wurde ueber RenderSystem.setShaderTexture an den
     * Lichtkarten-Platz (Sampler2) des Partikel-Shaders gebunden, damit die Vorschau nicht
     * pechschwarz zeichnet — siehe die Erklaerung in renderUserModel, wo diese Bindung
     * ersatzlos entfallen ist.
     *
     * Beides ist weg, und zwar zusammen: eine Textur wird nicht mehr global vorher gesetzt,
     * sondern im Durchgang gebunden, und eine rohe GL-Nummer (das war der Rueckgabewert)
     * gibt DynamicTexture ohnehin nicht mehr heraus. Die Textur ohne ihre Bindestelle
     * stehenzulassen haette nur einen Erzeuger ohne Aufrufer ergeben.
     *
     * ⚠️ OFFEN: ob die Vorschau ohne diese Bindung richtig aussieht, entscheidet die neue
     * Pipeline (RenderPipelines.TRANSLUCENT_PARTICLE) — am laufenden Bild zu pruefen, nicht
     * hier zu erraten. Wenn sie schwarz zeichnet, gehoert die Ersatzloesung in den Durchgang
     * (TextureSetup), nicht wieder hierher. */

    private Vector3f vector = new Vector3f(0, 0, 0);

    /**
     * Die Modellansicht der Kill-Ebene ist die Einheitsmatrix: ihre Ecken tragen die Pose
     * bereits ({@code addVertex(matrix, ...)}), und die Projektion kommt aus dem
     * Kamerablock des Vorschau-Renderers. Wer hier die Pose noch einmal uebergibt,
     * verschiebt die Ebene ein zweites Mal.
     */
    private static final Matrix4f EBENEN_MODELLANSICHT = new Matrix4f();

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

        PoseStack stack = context.batcher.pose();

        stack.pushPose();
        stack.setIdentity();
        stack.mulPose(new Matrix4f(InverseView.get()).invert());

        /* 26.2: enableBlend ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: mischen an. */
        /* 26.2: enableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefentest an. */

        /* 26.2: setShaderColor ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Farbmodulator = 1F, 1F, 1F, 1F. */

        /* The vanilla dynamic light map binding (Sampler2) does not survive into this screen's
         * draw call, so the particle shader (particle.vsh does vertexColor = Color *
         * texelFetch(Sampler2, ...)) samples a black light map and the particles render pitch
         * black — only their alpha/shape survives. The editor preview has no world context
         * (emitter.world == null), so the billboard always uses pack(15, 15), i.e. fully bright,
         * which a correctly bound light map would sample as pure white anyway. Bind our own
         * 16x16 white texture to Sampler2 right before the draw to get that fullbright result
         * without depending on the (here broken) light map binding. In-world particles are
         * unaffected: they render through ParticleFormRenderer with the real light map. */
        /* 26.2: setShaderTexture ist entfallen -- eine Textur wird im Durchgang
         * gebunden (TextureSetup/bindTexture), nicht global vorher. */

        /* ⛔ NICHT emitter.render(...) -- das oeffnet einen eigenen Zeichendurchgang und
         * landet in der Oberflaeche UNTER dem GUI. Genau das machte die Vorschau schwarz,
         * waehrend der Zaehler daneben aktive Partikel meldete (Aufgabe 94). */
        this.emitter.renderInOberflaeche(context.batcher, DefaultVertexFormat.PARTICLE,
            () -> RenderPipelines.TRANSLUCENT_PARTICLE, stack, OverlayTexture.NO_OVERLAY,
            context.getTransition(),
            new net.minecraft.client.gui.navigation.ScreenRectangle(
                this.area.x, this.area.y, this.area.w, this.area.h));
        /* 26.2: disableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefentest aus. */
        /* 26.2: disableBlend ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: mischen aus. */

        stack.popPose();

        ParticleComponentKillPlane plane = this.emitter.scheme.get(ParticleComponentKillPlane.class);

        if (plane.a != 0 || plane.b != 0 || plane.c != 0)
        {
            this.renderPlane(context, plane.a, plane.b, plane.c, plane.d);
        }
    }

    private void renderPlane(UIContext context, float a, float b, float c, float d)
    {
        Matrix4f matrix = new Matrix4f(context.batcher.pose().last().pose());
        final float alpha = 0.5F;

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(1024))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

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
                FormPipelines.holen(VertexFormat.Mode.TRIANGLES, false, false, false),
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
            Draw.coolerAxes(context.batcher.pose(), 1F, 0.005F);
        }
    }
}
