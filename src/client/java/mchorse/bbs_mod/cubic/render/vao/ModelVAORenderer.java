package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Zeichnet ein {@link IModelVAO} in das laufende Weltbild.
 *
 * <h2>26.2: vom Shader-Objekt zum Zeichendurchgang</h2>
 *
 * <p>Frueher setzte diese Klasse ein Dutzend Uniforms von Hand an einem
 * {@code ShaderInstance}, rief {@code shader.apply()} und liess den VAO zeichnen. In 26.2
 * gibt es weder {@code ShaderInstance} noch einzelne Uniforms: gezeichnet wird in einen
 * {@link RenderPass}, der eine {@link RenderPipeline} bekommt und seine Werte aus
 * Uniform-Bloecken liest.
 *
 * <p>Die Reihenfolge ist von Vanillas eigenen Ad-hoc-Zeichnern abgeschaut
 * ({@code SkyRenderer}, {@code CloudRenderer}, {@code WorldBorderRenderer} — sie machen
 * genau dasselbe fuer Himmel, Wolken und Weltgrenze):
 * Geraet holen, Kommandoschreiber anlegen, Durchgang gegen das Hauptziel oeffnen,
 * Standard-Uniforms binden, eigene Werte daruebersetzen, zeichnen, schliessen.
 *
 * <h2>⚠️ Ein Durchgang je Modell — das ist der bekannte Preis</h2>
 *
 * <p>Vanilla sammelt gleichartige Geometrie und zeichnet sie gebuendelt. Dieser Weg
 * oeffnet fuer JEDES Modell einen eigenen Durchgang, weil BBS' Modelle mitten im
 * Weltrendering an beliebiger Stelle auftauchen. Fuer eine Handvoll Schauspieler ist das
 * richtig; wenn eine Szene hunderte Modelle zeigt, ist genau hier der Hebel fuer
 * Sammeln — nicht in den Modellen selbst. Erst messen, dann buendeln.
 */
public class ModelVAORenderer
{
    /**
     * Der eigene Uniform-Block, absichtlich nur aus 16-Byte-Feldern gebaut.
     *
     * <p>std140 richtet Felder unterschiedlich aus, je nach Typ — ein {@code ivec2} nach
     * einer {@code mat3} landet nicht dort, wo man es naiv erwartet, und die Folge waere
     * ein falsches Bild ohne Fehlermeldung. Darum steht hier alles als volle 16-Byte-Zeile:
     * die Normalenmatrix als drei Zeilen, Licht und Overlay zusammen in einer vierten,
     * die Durchgangsart in einer fuenften. Kein Feld braucht Fuellbytes, kein Offset ist
     * geraten.
     */
    private static final int BLOCK_GROESSE = 5 * 16;

    private static GpuBuffer block;

    /**
     * The full model-view a draw issued right now would use. Deferred translucent commands
     * capture it at enqueue time, because the global model-view is different by the time
     * the queue flushes at the end of the frame.
     *
     * <p>26.2: {@code getModelViewMatrix()} heisst jetzt {@code getModelViewMatrixCopy()}
     * und liefert eine Kopie — hier ist das richtig, es wird ohnehin nur gelesen.
     */
    public static Matrix4f captureModelView(PoseStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(stack.last().pose());
    }

    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, PoseStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        render(pipeline, modelVAO, captureModelView(stack), stack.last().normal(), r, g, b, a, light, overlay, 0);
    }

    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, int passMode)
    {
        if (modelVAO.eckenZahl() <= 0)
        {
            return;
        }

        RenderTarget ziel = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        CommandEncoder schreiber = RenderSystem.getDevice().createCommandEncoder();

        /* Modellansicht und Farbe gehen in Vanillas DynamicTransforms-Block. BBS setzt eine
         * EIGENE Modellansicht (die eingefangene, siehe captureModelView) — writeTransform
         * fuellt genau diesen Block fuer einen Zeichenaufruf und gibt den Ausschnitt
         * zurueck, den der Durchgang binden muss. */
        GpuBufferSlice transformationen = RenderSystem.getDynamicUniforms()
            .writeTransform(modelView, new Vector4f(r, g, b, a));

        schreibeBlock(normalMat, light, overlay, passMode);

        /* Leeres Optional und leeres OptionalDouble heissen: Farb- und Tiefenpuffer NICHT
         * loeschen. Das Modell wird in das bestehende Weltbild gezeichnet — mit einem
         * Loeschwert waere die Welt weg. */
        try (RenderPass durchgang = schreiber.createRenderPass(
            () -> "bbs-modell", ziel.getColorTextureView(), Optional.empty(),
            ziel.getDepthTextureView(), OptionalDouble.empty()))
        {
            durchgang.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(durchgang);

            /* NACH bindDefaultUniforms, nicht davor: die Standardbindung setzt die globale
             * Modellansicht, und genau die soll hier ueberschrieben werden. */
            durchgang.setUniform("DynamicTransforms", transformationen);
            durchgang.setUniform(ModelVAOPipeline.MODELL_BLOCK, block);

            modelVAO.zeichnen(durchgang);
        }
    }

    /**
     * Fuellt den eigenen Block fuer den naechsten Zeichenaufruf.
     *
     * <p>Der Puffer wird einmal angelegt und wiederbeschrieben. Das ist erlaubt, weil
     * zwischen dem Schreiben und dem Zeichnen kein zweiter Aufruf liegt — wer das aendert
     * (etwa um mehrere Modelle in EINEM Durchgang zu zeichnen), braucht einen Ringpuffer,
     * sonst ueberschreibt das zweite Modell die Werte des ersten, bevor die Karte sie
     * gelesen hat.
     */
    private static void schreibeBlock(Matrix3f normalMat, int light, int overlay, int passMode)
    {
        if (block == null)
        {
            block = RenderSystem.getDevice().createBuffer(
                () -> "bbs-modell-block", GpuBuffer.USAGE_UNIFORM, BLOCK_GROESSE);
        }

        ByteBuffer roh = MemoryUtil.memAlloc(BLOCK_GROESSE).order(ByteOrder.nativeOrder());

        try
        {
            /* Die Normalenmatrix als drei volle Zeilen; die vierte Spalte jeder Zeile ist
             * Fuellung, die der Shader ignoriert (er liest mat3). */
            zeile(roh, normalMat.m00(), normalMat.m01(), normalMat.m02());
            zeile(roh, normalMat.m10(), normalMat.m11(), normalMat.m12());
            zeile(roh, normalMat.m20(), normalMat.m21(), normalMat.m22());

            /* Licht und Overlay sind gepackte Koordinatenpaare; der alte Shader las sie als
             * zwei ivec2 und holte damit aus den Karten. Sie liegen hier zusammen in einer
             * Zeile: x/y = Licht, z/w = Overlay. */
            roh.putInt(light & 0xFFFF).putInt(light >> 16 & 0xFFFF);
            roh.putInt(overlay & 0xFFFF).putInt(overlay >> 16 & 0xFFFF);

            /* Die Durchgangsart steuert die Zwei-Pass-Durchsichtigkeit im Fragmentshader:
             * 0 = ein Durchgang, 1 = nur undurchsichtige Texel (schreiben Tiefe),
             * 2 = nur halbdurchsichtige (sortiert, ohne Tiefenschreiben). */
            roh.putInt(passMode).putInt(0).putInt(0).putInt(0);

            /* ⚠️ IViewRotMat steht bewusst NICHT im Block. Der alte Shader deklarierte es
             * ({@code uniform mat3 IViewRotMat}), benutzte es in main() aber nirgends —
             * nachgelesen in model.vsh, nicht angenommen. Ein toter Wert kostet drei volle
             * Zeilen. Wer ihn braucht, erweitert den Block UND zieht BLOCK_GROESSE mit. */

            roh.flip();

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(block.slice(), roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    /** Eine 16-Byte-Zeile: drei Werte plus eine Fuellstelle. */
    private static void zeile(ByteBuffer puffer, float x, float y, float z)
    {
        puffer.putFloat(x).putFloat(y).putFloat(z).putFloat(0F);
    }
}
