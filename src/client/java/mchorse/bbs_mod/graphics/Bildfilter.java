package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.client.BBSPipelines;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Fuehrt einen BBS-Shader ueber eine Textur aus und schreibt das Ergebnis in eine zweite.
 *
 * <h2>Die Entwurfsentscheidung dahinter (2026-07-31)</h2>
 *
 * <p>Drei BBS-Shader zeichnen in die Oberflaeche und brauchen dabei mehr eigene Werte, als
 * dort unterzubringen sind: {@code picker_preview} (Kennung + Hervorhebungsfarbe, fuenf
 * Werte), {@code multilink} (sechs) und {@code replay_outline} (acht). In 26.2 kann ein
 * GUI-Element keine eigenen Uniforms binden -- gemessen, nicht vermutet:
 * {@code GuiElementRenderState} hat vier Methoden, und {@code GuiRenderer} bindet je
 * Durchgang genau die Standard-Uniforms, einen eigenen Block und bis zu drei Sampler aus
 * {@code TextureSetup}. Fuer einen zusaetzlichen Block ist kein Platz vorgesehen.
 *
 * <h2>Warum nicht die drei naheliegenden Wege</h2>
 *
 * <ul>
 *   <li><b>Werte in die Vertexfarbe packen</b> -- Vanillas eigener Ausweg (die
 *       Uebelkeits-Ueberlagerung macht es so). Hier passt es nicht: vier Kanaele fuer
 *       fuenf bis acht Werte, und die Vertexfarbe ist in allen drei Shadern bereits als
 *       Farbe belegt. Werte wegzulassen, bis sie passen, heisst Einstellungen des Nutzers
 *       still zu verlieren (Alpha der Hervorhebung, Dicke der Umrandung).</li>
 *   <li><b>PictureInPictureRenderer</b> -- Mojangs Weg fuer GUI-Inhalte mit eigener
 *       Zeichenlogik. Sieht passend aus, loest es aber NICHT: die abstrakte Methode heisst
 *       {@code renderToTexture(T, PoseStack, SubmitNodeCollector)}. Man reicht Auftraege
 *       ein, man oeffnet keinen eigenen Durchgang -- also wieder keine eigenen Uniforms.
 *       (Nachgesehen im Bytecode, weil die Klasse dem Namen nach genau richtig klingt.)</li>
 *   <li><b>Ausserhalb der Oberflaeche zeichnen</b> -- landet unter dem gesamten GUI-Stapel,
 *       weil 26.2 die Oberflaeche erst sammelt und danach zeichnet. Die Hervorhebung
 *       verschwaende hinter den Bedienelementen.</li>
 * </ul>
 *
 * <h2>Der gewaehlte Weg</h2>
 *
 * <p>Die IDEE von PictureInPicture, ohne dessen Einschraenkung: <b>vorher in eine Textur
 * rechnen, danach ein ganz normales Bild in die Oberflaeche blitten.</b>
 *
 * <p>Der Filterdurchgang gehoert BBS allein -- eigenes Ziel, eigene Pipeline, eigener
 * Uniform-Block, so viele Werte wie noetig. Was die Oberflaeche danach sieht, ist eine
 * fertige Textur, und die kann sie ohne Sonderfall zeichnen. Ein Zwischenbild kostet
 * Speicher, aber es macht den Sonderfall zu einem Normalfall -- und der naechste
 * Mojang-Umbau der GUI-Schicht kann daran nichts mehr brechen.
 *
 * <p>⚠️ Jede Aufrufstelle braucht ihr EIGENES Ziel. Die Oberflaeche zeichnet erst, nachdem
 * alles gesammelt ist; ein geteiltes Zwischenbild waere zum Zeichenzeitpunkt laengst von
 * der naechsten Aufrufstelle ueberschrieben, und alle zeigten dasselbe.
 */
public final class Bildfilter
{
    /** Der Block ist absichtlich aus vollen 16-Byte-Zeilen gebaut, siehe ModelVAORenderer. */
    /**
     * EIN Block fuer alle Filter, absichtlich als Vereinigung statt drei getrennter.
     *
     * <p>Die drei Shader brauchen verschiedene Werte, aber alle nur wenige. Drei Bloecke
     * hiessen drei Bindegruppen, drei Groessen und drei Gelegenheiten, std140 falsch
     * auszurichten. Ein gemeinsamer Block kostet ein paar ungenutzte Bytes und hat dafuer
     * genau EINE Reihenfolge, die Java und GLSL teilen.
     *
     * <p>⚠️ Jeder Shader muss den Block VOLLSTAENDIG und in gleicher Reihenfolge
     * deklarieren, auch die Felder, die er nicht liest. std140 richtet sonst anders aus.
     */
    private static final int BLOCK_GROESSE = 3 * 16;

    private static GpuBuffer block;

    /**
     * Hebt die getroffene Kennung in einem Auswahlbild hervor.
     *
     * @param quelle  das Auswahlbild mit den Kennungsfarben
     * @param ziel    wohin das Ergebnis geht -- NICHT dieselbe Textur wie die Quelle
     * @param kennung die Kennung, die hervorgehoben werden soll
     * @param farbe   die Hervorhebungsfarbe als gepacktes ARGB
     */
    public static void kennungHervorheben(Texture quelle, Texture ziel, int kennung, int farbe)
    {
        schreibeBlock(kennung, 0, 0, farbe, 0F, 0F);

        loeschen(ziel);

        lauf(BBSPipelines.getPickerPreview(), quelle, null, ziel.view());
    }

    /**
     * Zeichnet einen Umriss um alles, was ab {@code abIndex} im Auswahlbild steht.
     *
     * @param schrittX ein BILDSCHIRMpixel in UV-Einheiten, nicht ein Texel -- das
     *                 Auswahlbild hat Videoaufloesung, ein Texel darin ist viel kleiner
     *                 als ein Pixel auf dem Schirm. Mit Texeln lag der Nachbar im selben
     *                 Bildschirmpixel und die Linie war gestrichelt statt durchgezogen.
     */
    public static void umrissZeichnen(Texture quelle, Texture ziel, int abIndex, int bisIndex, int farbe, int breite, float schrittX, float schrittY)
    {
        /* bisIndex (ausschliessend) = erste Widget-Kennung: Rig-Controls/Marker liegen im
         * Auswahlbild HINTER den Knochen der Form und bekommen keinen Umriss. 0 = kein Limit. */
        schreibeBlock(abIndex, breite, bisIndex, farbe, schrittX, schrittY);
        loeschen(ziel);

        lauf(BBSPipelines.getReplayOutline(), quelle, null, ziel.view());
    }

    /**
     * Verpixelt eine Textur oder legt das Radiergummi-Muster darueber.
     *
     * @param atlas das Symbol-Atlasbild -- der Radiergummi-Zweig liest sein Muster daraus
     */
    public static void pixelnRadieren(Texture quelle, Texture atlas, Texture ziel, float breite, float hoehe, int kantenlaenge, boolean radieren)
    {
        schreibeBlock(0, kantenlaenge, radieren ? 1 : 0, 0, breite, hoehe);
        loeschen(ziel);

        lauf(BBSPipelines.getMultilink(), quelle, atlas, ziel.view());
    }

    /** Der Shader verwirft, was er nicht trifft -- ohne Loeschen bliebe das letzte Bild stehen. */
    private static void loeschen(Texture ziel)
    {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorTexture(ziel.gpu(), new org.joml.Vector4f(0F, 0F, 0F, 0F));
    }

    /**
     * Zeichnet ein bildfuellendes Viereck mit der gegebenen Pipeline.
     *
     * <p>Die Ecken laufen von 0 bis 1 in beiden Richtungen, die Modellansicht ist eine
     * flache Projektion darauf -- der Durchgang deckt damit genau das Ziel ab, unabhaengig
     * von dessen Groesse.
     */
    private static void lauf(RenderPipeline pipeline, Texture quelle, Texture zweite, GpuTextureView ziel)
    {
        /* ⛔ DIE PROJEKTION GEHOERT DIESEM DURCHGANG, NICHT DEM LETZTEN WELTBILD (2026-08-02).
         *
         * Der Vertex-Shader rechnet ProjMat * ModelViewMat * Position. Hier wurde nur die
         * ModelViewMat uebergeben (die flache Ortho unten); ProjMat kam ueber
         * GehaltenesNetz -> RenderSystem.bindDefaultUniforms aus dem GLOBALEN Puffer, und der
         * traegt an dieser Stelle die PERSPEKTIVE des Weltdurchgangs: GameRenderer setzt in
         * 26.2 nur noch PERSPECTIVE, die GUI-Ortho setzt erst GuiRenderer.draw() -- also im
         * ZEICHNEN, waehrend dieser Filter im SAMMELN laeuft.
         *
         * Perspektive heisst w = -z_eye. Nach der flachen ModelViewMat liegt das Viereck bei
         * z_eye = 0, also w = 0 an ALLEN VIER ECKEN: die Flaeche ist entartet und liefert
         * KEIN Fragment. Kein GL-Fehler, keine Logzeile, alle Zaehler voll -- der Umriss
         * wurde 1008-mal "gezeichnet" und war nie zu sehen.
         *
         * Vanilla umgeht das bei seinen Vollbildfiltern ueber core/screenquad.vsh (gar keine
         * Matrizen, NDC direkt aus gl_VertexID); FramebufferFormRenderer setzt die Projektion
         * selbst. Nur dieser Weg tat weder das eine noch das andere.
         *
         * Die Ortho wandert damit von der ModelView in die Projektion, und die ModelView wird
         * die Einheitsmatrix -- zweimal projizieren waere derselbe Fehler nur andersherum. */
        Projektion.sichern();
        Projektion.setzenFlach(new Matrix4f().setOrtho(0F, 1F, 0F, 1F, -1F, 1F));

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(512))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(speicher, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);

            bauer.addVertex(0F, 0F, 0F).setUv(0F, 0F);
            bauer.addVertex(0F, 1F, 0F).setUv(0F, 1F);
            bauer.addVertex(1F, 1F, 0F).setUv(1F, 1F);
            bauer.addVertex(1F, 0F, 0F).setUv(1F, 0F);


            mchorse.bbs_mod.sonden.Sonden.gpu.vorZeichnen(
                "Bildfilter.lauf/umriss", 0, 0, 0, 4, 1);

            try (GehaltenesNetz netz = new GehaltenesNetz(bauer.buildOrThrow()))
            {
                netz.zeichnen(pipeline,
                    new Matrix4f(),
                    null,
                    zweite == null
                        ? TextureSetup.singleTexture(quelle.view(), quelle.sampler())
                        : TextureSetup.doubleTexture(quelle.view(), quelle.sampler(), zweite.view(), zweite.sampler()),
                    ziel, null,
                    (durchgang) -> durchgang.setUniform(BBSPipelines.FILTER_BLOCK, block));
            }
        }
        finally
        {
            Projektion.zurueck();
        }
    }

    private static void schreibeBlock(int ganz1, int ganz2, int ganz3, int farbe, float masseX, float masseY)
    {
        if (block == null)
        {
            /* ⚠️ USAGE_COPY_DST ist PFLICHT, weil dieser Block pro Bild neu beschrieben wird
             * (writeToBuffer weiter unten). 26.2 prueft die Nutzungsarten und lehnt das
             * Schreiben sonst ab: "Buffer needs USAGE_COPY_DST to be a destination for a
             * copy". Ein reiner USAGE_UNIFORM-Puffer waere ein Puffer, den man einmal
             * fuellt und danach nur noch liest -- das ist dieser hier gerade nicht. */
            block = RenderSystem.getDevice().createBuffer(() -> "bbs-bildfilter-block",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLOCK_GROESSE);
        }

        ByteBuffer roh = MemoryUtil.memAlloc(BLOCK_GROESSE).order(ByteOrder.nativeOrder());

        try
        {
            /* Zeile 1 (ivec4 Ganz): x = gesuchte Kennung bzw. kleinster Index, y = Dicke
             * bzw. Kantenlaenge der Verpixelung, z = Radiergummi an/aus. */
            roh.putInt(ganz1).putInt(ganz2).putInt(ganz3).putInt(0);

            /* Zeile 2 (vec4 Farbe): aus gepacktem ARGB in vier Fliesskommawerte. */
            roh.putFloat((farbe >> 16 & 0xFF) / 255F);
            roh.putFloat((farbe >> 8 & 0xFF) / 255F);
            roh.putFloat((farbe & 0xFF) / 255F);
            roh.putFloat((farbe >>> 24 & 0xFF) / 255F);

            /* Zeile 3 (vec4 Masse): x/y = Schrittweite bzw. Groesse in Texeln. */
            roh.putFloat(masseX).putFloat(masseY).putFloat(0F).putFloat(0F);

            roh.flip();

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(block.slice(), roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    private Bildfilter()
    {}
}
