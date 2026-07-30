package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class CustomVertexConsumerProvider implements SubmitNodeCollector
{
    private static Consumer<RenderType> runnables;

    private final SubmitNodeCollector delegate;
    private Function<VertexConsumer, VertexConsumer> substitute;
    private boolean ui;

    public static void drawLayer(RenderType layer)
    {
        if (runnables != null)
        {
            runnables.accept(layer);
        }
    }

    public static void hijackVertexFormat(Consumer<RenderType> runnable)
    {
        runnables = runnable;
    }

    public static void clearRunnables()
    {
        runnables = null;
    }

    public CustomVertexConsumerProvider(SubmitNodeCollector delegate)
    {
        this.delegate = delegate;
    }

    public void setSubstitute(Function<VertexConsumer, VertexConsumer> substitute)
    {
        this.substitute = substitute;

        if (this.substitute == null)
        {
            RecolorVertexConsumer.newColor = null;
        }
    }

    public void setUI(boolean ui)
    {
        this.ui = ui;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer)
    {
        VertexConsumer buffer = this.delegate.getBuffer(renderLayer);

        if (this.substitute != null)
        {
            VertexConsumer apply = this.substitute.apply(buffer);

            if (apply != null)
            {
                return apply;
            }
        }

        return buffer;
    }

    /**
     * Translucent layers of buffered forms (blocks, items) defer into the frame's sorted
     * translucent queue instead of drawing immediately — otherwise their semi-transparent
     * pixels write depth mid-frame and occlude forms drawn after them. Active only when the
     * current form renderer published its sort origin (never in picking or UI paths).
     *
     * <p>MERGE fsgh/2.5 (1.20.4 → 1.21.1): upstream greift hier in die interna von
     * {@code Immediate}. Deren 1.20.4-form (fallbackBuffer + activeConsumers-set +
     * {@code Optional<RenderLayer> currentLayer} + {@code builder.isBuilding()}) gibt es in
     * 1.21.1 nicht mehr — dort steht ein {@code pending}-map (layer → laufender builder)
     * und ein nacktes {@code currentLayer}. Beide felder sind protected, wir erben aber
     * nicht mehr von {@code Immediate} (seit dem 1.21.1-port haelt diese klasse ein
     * delegate), darum sind sie im accesswidener geoeffnet.</p>
     *
     * <p>KEIN {@code @Override}: das interface {@link SubmitNodeCollector} kennt nur
     * {@code getBuffer}. Gerufen wird die methode aus unserem eigenen {@link #draw()}.</p>
     */
    public void draw(RenderType layer)
    {
        Vector3f origin = FormTranslucentQueue.getSortOrigin();

        /* Text layers defer only inside a recorded group (labels): the group preserves the
         * text-over-background order, and text keeps its depth writes there. */
        boolean textLayer = FormTranslucentQueue.isGroupOpen() && layer.format() == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;

        if (origin == null || !FormTranslucentQueue.isActive() || !(textLayer || isDeferrableTranslucent(layer)))
        {
            this.delegate.endBatch(layer);

            return;
        }

        /* pending.remove == "diesen layer NICHT zeichnen, ich nehme den puffer selbst".
         * Steht nichts drin, hat die ebene diesen frame gar nichts gesammelt. */
        BufferBuilder builder = this.delegate.startedBuilders.remove(layer);

        if (builder == null)
        {
            return;
        }

        if (this.delegate.lastSharedType == layer)
        {
            this.delegate.lastSharedType = null;
        }

        MeshData built = builder.build();

        if (built == null)
        {
            return;
        }

        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        buffer.bind();
        buffer.upload(built);
        VertexBuffer.unbind();

        FormTranslucentQueue.add(new FormTranslucentQueue.RenderLayerCommand(layer, buffer, new Matrix4f(RenderSystem.getModelViewMatrixCopy()), new Vector3f(origin), textLayer));
    }

    private static boolean isDeferrableTranslucent(RenderType layer)
    {
        String name = layer.toString();

        return name.contains("translucent") && !name.contains("glint");
    }

    public void draw()
    {
        /* MERGE fsgh/2.5: {@code Immediate.draw()} ruft intern sein EIGENES draw(layer).
         * Solange upstream von Immediate ERBTE, landete das per virtual dispatch in der
         * ueberschriebenen methode; mit unserem delegate (1.21.1-port) kaeme die umleitung
         * in die translucent-queue dort nie an. Also gehen wir die offenen ebenen selbst
         * durch. Danach ist pending leer, und das delegate zeichnet nur noch, was es
         * ueber eigene allocator-ebenen haelt. */
        for (RenderType layer : new ArrayList<>(this.delegate.startedBuilders.keySet()))
        {
            this.draw(layer);
        }

        this.delegate.endBatch();

        if (this.ui)
        {
            /* Force back the depth func because it seems like stuff rendered by a vertex
             * consumer is resetting the depth func to GL_LESS, and since this vertex consumer
             * is designed  */
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }
}
