package mchorse.bbs_mod.ui.framework.elements.utils;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;

import java.util.function.Consumer;

/**
 * Eigene Geometrie in der Oberflaeche — der UI-Gegenspieler zu
 * {@link mchorse.bbs_mod.graphics.SofortZeichner}.
 *
 * <h2>⛔ WARUM SOFORT-ZEICHNEN IN DER OBERFLAECHE FALSCH IST</h2>
 *
 * <p>Das ist der Fehler, den drei unabhaengige Pruefungen derselben Welle gemeldet haben,
 * bevor er jemand gesehen hat — und sie hatten recht.
 *
 * <p>26.2 zeichnet die Oberflaeche in ZWEI Phasen. {@code UIScreen.extractRenderState}
 * sagt es woertlich: dieser Durchlauf haengt nur Elemente an den {@code GuiRenderState};
 * {@code GuiRenderer.render()} zeichnet sie DANACH. BBS' gesamter UI-Code laeuft in der
 * Sammelphase.
 *
 * <p>Wer dort einen {@code RenderPass} gegen das Hauptziel oeffnet, zeichnet SOFORT — also
 * bevor die Oberflaeche ueberhaupt gezeichnet wird. Das Ergebnis landet unter dem ganzen
 * GUI: Keyframe-Punkte verschwinden hinter ihrem eigenen Hintergrund, Kurven hinter dem
 * Panel. Es sieht nicht nach einem Fehler aus, sondern nach fehlenden Elementen — und
 * kein Compiler warnt.
 *
 * <p>Richtig ist, sich in dieselbe Schlange zu stellen: ein {@link GuiElementRenderState},
 * das der {@code GuiRenderer} zusammen mit allem anderen zeichnet, in der richtigen
 * Reihenfolge und mit der richtigen Ueberdeckung.
 *
 * <h2>Benutzung</h2>
 *
 * <p>Der Eckenaufbau wandert in ein Lambda, statt vorher in einen Puffer geschrieben zu
 * werden — {@code buildVertices} wird spaeter gerufen, wenn der Renderer an der Reihe ist:
 *
 * <pre>{@code
 * batcher.netz(FormPipelines.holen(VertexFormat.Mode.QUADS, true, false, false), (ecken) ->
 * {
 *     ecken.addVertexWith2DPose(pose, x1, y1).setColor(farbe);
 *     // ...
 * }, bereich);
 * }</pre>
 *
 * <p>⚠️ Das Netz muss zum Vertexformat der Pipeline passen. Die GUI-Pipelines erwarten
 * {@code POSITION_COLOR} beziehungsweise {@code POSITION_TEX_COLOR}; wer eine
 * Weltpipeline hier einreiht, bekommt Muell statt eines Fehlers.
 */
public record UINetz(
    RenderPipeline gewaehltePipeline,
    Consumer<VertexConsumer> ecken,
    TextureSetup textur,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements GuiElementRenderState
{
    @Override
    public void buildVertices(VertexConsumer consumer)
    {
        this.ecken.accept(consumer);
    }

    @Override
    public RenderPipeline pipeline()
    {
        return this.gewaehltePipeline;
    }

    @Override
    public TextureSetup textureSetup()
    {
        return this.textur == null ? TextureSetup.noTexture() : this.textur;
    }
}
