package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Zeichnet einen Block SOFORT in die Oberflaeche, statt ihn in den Weltdurchgang einzureihen.
 *
 * <h2>⛔ Wofuer (2026-08-04, Bugliste #13)</h2>
 *
 * <p>Der Nutzer meldete: "die grassblock textur die fuer den block replay ist, ist einfach voll
 * pixelig". Gemessen wurde daraufhin die Treppenstufe an der schraegen Wuerfelkante der Vorschau:
 * <b>4-5 Bildpunkte</b> bei ~140 px Kantenlaenge. Die Quelle hatte also ~30 px -- ein
 * 32-px-Inventarsymbol (16 x GUI-Skalierung 2), 4,4-fach vergroessert. Nicht die TEXTUR ist zu
 * grob; das fertige BILD wird aufgeblasen.</p>
 *
 * <p>Der naheliegende Rueckweg -- {@link Blockmodell#einreichen} in {@code renderInUI} -- zeichnet
 * NICHTS. Dreimal umkehrbar gemessen: 22085 Blockpixel mit Symbol, 0 mit Einreihung, wieder 22085
 * nach dem Zuruecknehmen. Grund: {@code submitCustomGeometry} legt den Zeichenauftrag im
 * Bildgraphen des WELTdurchgangs ab. In der Bild-im-Bild-Phase der Miniaturen gibt es den nicht,
 * und der Auftrag verfaellt still. Modelle funktionieren dort, weil BBS sie ueber seinen EIGENEN
 * Zeichner einreicht, nicht ueber Vanilla.</p>
 *
 * <p>Diese Klasse geht denselben Weg wie {@code BillboardFormRenderer}: Vierecke in einen eigenen
 * Puffer schreiben und mit {@link SofortZeichner} abschicken. Damit landet der Block in der
 * Aufloesung der Kachel statt als vergroessertes Symbol.</p>
 *
 * <p>⚠️ EIGENE KLASSE, nicht eine Methode in {@link Blockmodell}: der Aufrufer
 * ({@code BlockFormRenderer}) muss hotswapbar bleiben, und neue Klassen laden lazy, waehrend eine
 * neue Methode an einer geladenen Klasse ueber den Mixin-Weg nicht nachtraeglich hineingeht.</p>
 */
public final class Blockvorschau
{
    /** Wiederverwendeter Eckenspeicher -- ein Bild zeichnet viele Kacheln. */
    private static ByteBufferBuilder speicher;

    /**
     * Zeichnet den Block sofort mit der uebergebenen Modellansicht.
     *
     * @param modelView die fertige Ansicht der Kachel (aus {@code getUIMatrix} und dem Stapel)
     * @param farbe     Farbmodulator, {@code null} = weiss
     * @return {@code true}, wenn wirklich Geometrie abgeschickt wurde -- der Aufrufer kann
     *         daran entscheiden, ob er auf das Inventarsymbol zurueckfaellt
     */
    public static boolean zeichnen(BlockState block, PoseStack stapel, Matrix4f modelView,
        Vector4f farbe, int licht, int overlay)
    {
        if (block == null || !RenderSystem.isOnRenderThread())
        {
            return false;
        }

        RenderType ebene = Blockmodell.ebeneFuer(block);

        if (ebene == null)
        {
            return false;
        }

        if (speicher == null)
        {
            speicher = new ByteBufferBuilder(4096);
        }

        BufferBuilder bauer = SofortZeichner.beginnen(speicher, ebene.primitiveTopology(), ebene.format());

        if (!Blockmodell.vierecke(block, stapel.last(), bauer, licht, overlay))
        {
            bauer.build();

            return false;
        }

        MeshData netz = bauer.build();

        if (netz == null)
        {
            return false;
        }

        SofortZeichner.zeichnen(ebene.pipeline(), netz, modelView, farbe, blockAtlas());

        return true;
    }

    /**
     * Die Blockatlas-Textur als Bindung.
     *
     * <p>Kein Zwischenspeichern: der Atlas wird beim Neuladen von Ressourcen ausgetauscht, und
     * eine gehaltene Ansicht zeigte danach auf eine tote Textur.</p>
     */
    private static TextureSetup blockAtlas()
    {
        var textur = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);

        return TextureSetup.singleTexture(textur.getTextureView(), textur.getSampler());
    }

    private Blockvorschau()
    {}
}
