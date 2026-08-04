package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.graphics.Messpunkt;
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
 * <h2>⛔ STAND 2026-08-04: GEBAUT, ABER ES KOMMEN KEINE VIERECKE</h2>
 *
 * <p>Verdrahtet und am laufenden Spiel gemessen. Der Weg laeuft (Zaehler {@code blockvorschau}
 * = 7464), die Zeichenebene wird gefunden -- aber die Modellteile fehlen:</p>
 *
 * <pre>
 *   blockmodell.aufloesung = teile=null  ebene=RenderType[item_cutout: ... blocks.png ...]
 *   blockvorschau          = keine vierecke
 * </pre>
 *
 * <p>{@code BlockModelResolver.update(zustand, block, kontext)} fuellt in dieser Phase also die
 * Ebene, aber nicht {@code modelParts}. Geprueft und AUSGESCHLOSSEN: der geteilte
 * {@code BlockModelRenderState} ist es nicht -- die Vorschau bekam einen eigenen
 * ({@code Blockmodell#zustandVorschau}), das Ergebnis blieb {@code teile=null}.</p>
 *
 * <p>OFFENE FRAGE, an der es weitergeht: warum liefert derselbe Aufloeser im Weltdurchgang
 * Teile und in der Bild-im-Bild-Phase nicht? Der direkte Vergleich fehlt noch, weil in dieser
 * Instanz gerade keine Block-Form in der Welt zeichnet ({@code blockmodell.welt} zaehlt nicht).
 * Beide Sonden stehen im Baum; sobald eine Block-Form gleichzeitig in Welt und Vorschau
 * sichtbar ist, steht die Antwort in einem Bild.</p>
 *
 * <p>⚠️ DARUM IST DER AUFRUFER NICHT VERDRAHTET: solange hier nichts herauskommt, waere es ein
 * Weg, der jedes Bild aufloest und dann doch aufs Symbol zurueckfaellt.</p>
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

        Messpunkt.zaehle("blockvorschau", ebene == null ? "keine ebene" : "ebene=" + ebene);

        if (ebene == null)
        {
            return false;
        }

        if (speicher == null)
        {
            speicher = new ByteBufferBuilder(4096);
        }

        BufferBuilder bauer = SofortZeichner.beginnen(speicher, ebene.primitiveTopology(), ebene.format());

        if (!Blockmodell.viereckeAusZustand(stapel.last(), bauer, licht, overlay))
        {
            Messpunkt.zaehle("blockvorschau", "keine vierecke");

            bauer.build();

            return false;
        }

        MeshData netz = bauer.build();

        Messpunkt.zaehle("blockvorschau.netz", netz == null ? "leer"
            : netz.drawState().vertexCount() + " ecken");

        if (netz == null)
        {
            return false;
        }

        /* ⛔ IN DAS GESETZTE ZIEL ZEICHNEN, NICHT INS HAUPTBILD (2026-08-04, der Fund).
         *
         * Die fuenfstellige Variante von SofortZeichner reicht `ziel = null` durch -- das
         * heisst HAUPTBILD. In der Bild-im-Bild-Phase laeuft aber ein eigenes Ziel
         * ({@link Zeichenziel}, von MiniaturSammelRenderer gesetzt), und was ins Hauptbild
         * geht, uebermalt die GUI unmittelbar danach. Genau darum kamen bei allen Versuchen
         * 794 statt 19138 Bildpunkte an: gezeichnet wurde, nur woanders.
         *
         * Die siebenstellige Variante nimmt die Zieltexturen direkt -- der Weg, den auch der
         * Auswahl-Durchgang geht. */
        var zielFarbe = Zeichenziel.farbe();
        var zielTiefe = Zeichenziel.tiefe();

        if (zielFarbe != null)
        {
            SofortZeichner.zeichnen(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT,
                netz, modelView, farbe, blockAtlas(), zielFarbe, zielTiefe);
        }
        else
        {
            SofortZeichner.zeichnen(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT,
                netz, modelView, farbe, blockAtlas());
        }

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
