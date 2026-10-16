package mchorse.bbs_mod.graphics;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import mchorse.bbs_mod.client.BBSRendering;

/**
 * Ein Netz, das auf der Grafikkarte LIEGENBLEIBT und mehrfach gezeichnet werden kann.
 *
 * <h2>Warum es das getrennt von {@link SofortZeichner} gibt</h2>
 *
 * <p>Der Sofortzeichner verbraucht sein Netz: hochladen, zeichnen, freigeben. Fuer den
 * groessten Teil von BBS ist das richtig. Die Zwei-Pass-Durchsichtigkeit braucht aber das
 * Gegenteil — dieselbe Geometrie wird ZWEIMAL gezeichnet, einmal sofort (die
 * undurchsichtigen Texel, mit Tiefenschreiben) und einmal am Bildende, wenn die
 * durchsichtige Warteschlange sortiert abgearbeitet wird. Bis dahin muss sie liegenbleiben.
 *
 * <p>Genau das war frueher {@code VertexBuffer}: aufbauen, behalten, mehrfach
 * {@code drawWithShader}. Die Klasse gibt es in 26.2 nicht mehr, die Aufgabe schon.
 *
 * <h2>Wem das Netz gehoert</h2>
 *
 * <p>Dem Aufrufer. Wer es anlegt, ruft {@link #close()} — bei der Warteschlange ist das
 * {@code release()} nach dem Leeren. Wird es vergessen, bleibt der Speicher auf der
 * Grafikkarte liegen, solange das Spiel laeuft; einen Sammler dafuer gibt es nicht.
 */
public class GehaltenesNetz implements AutoCloseable
{
    private GpuBuffer ecken;

    /** Was gebunden wird: der eigene Puffer oder ein Stueck aus Vanillas Bildspeicher. */
    private GpuBufferSlice eckenAnsicht;

    /**
     * Eigene Indizes gibt es nur bei sortierter Durchsichtigkeit — dort bestimmt die
     * Reihenfolge das Bild. Sonst bleibt das Feld leer und es wird Mojangs geteilter
     * Indexpuffer benutzt, der fuer Vierecke und Dreiecke immer dieselbe Folge hat.
     */
    private GpuBuffer eigeneIndizes;
    private GpuBufferSlice indexAnsicht;
    private boolean closed;

    private final MeshData.DrawState zustand;

    public GehaltenesNetz(MeshData netz)
    {
        this(netz, false);
    }

    /**
     * Fuer Netze, die nur im laufenden RenderPearl-Submit gezeichnet werden (SofortZeichner, Rig-Widgets).
     *
     * <p>Die Ecken landen in Vanillas {@code TransientMemory} -- persistent gemappte Bloecke,
     * die je Bild rotiert werden -- statt in einem eigenen Puffer, der nach dem Zug sofort
     * wieder geloescht wird. Anlegen und Loeschen kosteten im Rig-Film 4,1-4,5 % (Playback) bzw.
     * 5,2-5,3 % (Stillstand) des Renderthreads, danach 1,3-1,8 % (27.09.2026). Das Stueck gilt
     * Im Vulkan-Backend gilt das Stueck nur bis zum naechsten Submit; die echte
     * TransientGpuBuffer-Lebensdauer wird vor jedem Draw geprueft. Die genannten
     * Profilwerte stammen aus dem frueheren GL-Lauf.</p>
     */
    public static GehaltenesNetz kurzlebig(MeshData netz)
    {
        return new GehaltenesNetz(netz, true);
    }

    private GehaltenesNetz(MeshData netz, boolean kurzlebig)
    {
        try (MeshData zuSchliessen = netz)
        {
            this.zustand = zuSchliessen.drawState();

            if (kurzlebig)
            {
                this.eckenAnsicht = RenderSystem.getDevice().createCommandEncoder().transientMemory()
                    .uploadGpu(zuSchliessen.vertexBuffer(), 16L, GpuBuffer.USAGE_VERTEX);
            }
            else
            {
                this.ecken = RenderSystem.getDevice().createBuffer(
                    () -> "bbs-gehaltene-ecken", GpuBuffer.USAGE_VERTEX, zuSchliessen.vertexBuffer());
                this.eckenAnsicht = this.ecken.slice();
            }

            ByteBuffer indizes = zuSchliessen.indexBuffer();

            if (indizes != null)
            {
                if (kurzlebig)
                {
                    this.indexAnsicht = RenderSystem.getDevice().createCommandEncoder().transientMemory()
                        .uploadGpu(indizes, this.zustand.indexType().bytes, GpuBuffer.USAGE_INDEX);
                }
                else
                {
                    this.eigeneIndizes = RenderSystem.getDevice().createBuffer(
                        () -> "bbs-gehaltene-indizes", GpuBuffer.USAGE_INDEX, indizes);
                    this.indexAnsicht = this.eigeneIndizes.slice();
                }
            }
        }
        catch (RuntimeException | Error failure)
        {
            this.close();
            throw failure;
        }
    }

    public int eckenZahl()
    {
        return this.zustand.vertexCount();
    }

    /** Stock feature execution over the actual retained buffers, without reinterpreting vertices. */
    public net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo execution(RenderPipeline pipeline)
    {
        if (this.closed) throw new IllegalStateException("Retained mesh execution after close");
        this.bindungPruefen(pipeline);
        int stride = this.zustand.format().getVertexSize();
        validateVertices(this.eckenAnsicht, this.zustand.vertexCount(), stride);
        if (this.eckenAnsicht.offset() % stride != 0)
            throw new IllegalStateException("Stock feature execution cannot represent this vertex slice offset");
        int baseVertex = Math.toIntExact(this.eckenAnsicht.offset() / stride);
        GpuBuffer indices;
        com.mojang.renderpearl.api.pipeline.IndexType type;
        int firstIndex = 0;
        if (this.indexAnsicht != null)
        {
            indices = this.indexAnsicht.buffer();
            type = this.zustand.indexType();
            firstIndex = indexStart(this.indexAnsicht, type, this.zustand.indexCount());
        }
        else
        {
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(this.zustand.primitiveTopology());
            indices = sequential.getBuffer(this.zustand.indexCount());
            type = sequential.type();
        }
        return new net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo(this.eckenAnsicht.buffer(),
            indices, type, baseVertex, firstIndex, this.zustand.indexCount(), this.zustand.primitiveTopology());
    }

    /**
     * Zeichnet das Netz in einem eigenen Durchgang.
     *
     * @param pipeline  bestimmt Shader, Blend, Tiefe und Cull -- in 26.2 ist das der Ort,
     *                  an dem frueher depthMask/enableCull/disableCull standen
     * @param modelView die Modellansicht dieses Zuges (bei aufgeschobenen Zuegen die
     *                  EINGEFANGENE, nicht die gerade aktuelle)
     * @param farbe     Farbmodulator, {@code null} = weiss und undurchsichtig
     * @param textur    Texturbindung, {@code null} fuer Pipelines ohne Sampler
     * @param ziel      Renderziel, {@code null} fuer das Hauptbild
     * @param zusatz    optionale weitere Bindungen (etwa BBS' eigener Modell-Block mit
     *                  Normalenmatrix und Durchgangsart), oder {@code null}
     */
    public void zeichnen(RenderPipeline pipeline, Matrix4f modelView, Vector4f farbe,
        TextureSetup textur, RenderTarget ziel, Consumer<RenderPass> zusatz)
    {
        if (ziel == null && Zeichenziel.eigenes())
        {
            this.zeichnen(pipeline, modelView, farbe, textur, Zeichenziel.farbe(), Zeichenziel.tiefe(), zusatz);
            return;
        }
        if (ziel == null && BBSRendering.outputColorTextureOverride != null)
        {
            this.zeichnen(pipeline, modelView, farbe, textur, BBSRendering.outputColorTextureOverride,
                BBSRendering.outputDepthTextureOverride, zusatz);
            return;
        }
        RenderTarget echtes = ziel != null ? ziel : Minecraft.getInstance().gameRenderer.mainRenderTarget();

        this.zeichnen(pipeline, modelView, farbe, textur,
            echtes.getColorTextureView(), echtes.getDepthTextureView(), zusatz);
    }

    /**
     * Wie oben, aber mit den Zieltexturen statt eines {@link RenderTarget}.
     *
     * <p>Gebraucht fuer BBS' EIGENE Puffer: der Auswahl-Durchgang und die Kugel-Maske des
     * Gizmos zeichnen in einen BBS-Framebuffer, der kein Vanilla-RenderTarget ist. Ohne
     * diesen Weg landeten ihre Kennungsfarben sichtbar im Bild UND das Angeklickte waere
     * nicht mehr auffindbar -- ein doppelter stiller Fehler.
     *
     * @param tiefenZiel darf {@code null} sein: ein Durchgang ohne Tiefenanhang. Richtig
     *                   fuer Masken, die ohnehin ohne Tiefentest zeichnen; wer Tiefe
     *                   braucht und {@code null} uebergibt, bekommt keine Fehlermeldung,
     *                   sondern ein Bild ohne Verdeckung.
     */
    public void zeichnen(RenderPipeline pipeline, Matrix4f modelView, Vector4f farbe,
        TextureSetup textur, GpuTextureView farbZiel, GpuTextureView tiefenZiel, Consumer<RenderPass> zusatz)
    {
        if (this.closed) throw new IllegalStateException("GehaltenesNetz draw after close");
        if (this.eckenAnsicht == null || this.zustand.vertexCount() <= 0)
        {
            messen("leer", pipeline);

            return;
        }

        messen("kommt-an", pipeline);
        bindungPruefen(pipeline);
        validateVertices(this.eckenAnsicht, this.zustand.vertexCount(), this.zustand.format().getVertexSize());

        /* ⛔ DER INDEXTYP GEHOERT ZU DEM PUFFER, DER WIRKLICH GEBUNDEN WIRD (2026-08-05,
         * Bugliste #17b).
         *
         * Hier stand die Breite aus dem NETZ (zustand.indexType()), waehrend der Puffer aus
         * Mojangs Folgespeicher kam. Der waehlt seine Breite nach der angeforderten Anzahl:
         * bis 65535 Indizes SHORT, darueber INT. Am laufenden Spiel gemessen (13824 Ecken):
         * `typ-dreieck=INT typ-netz=SHORT`.
         *
         * Die Folge ist kein Absturz und keine Logzeile: die GPU liest 32-Bit-Indizes als
         * 16-Bit, jeder Index zerfaellt in zwei, und die oberen Haelften sind bei kleinen
         * Zahlen 0. Es entsteht ein Faecher ab Ecke 0 -- die Rotationsringe des Gizmos
         * wurden zu GEFUELLTEN Scheiben mit Saegezahnrand, sichtbar in jedem Editor.
         *
         * Kleine Netze (Balken, Wuerfel, Ebenen) blieben zufaellig richtig, weil der
         * Folgespeicher fuer sie ebenfalls SHORT liefert -- darum fiel es nur an den
         * Ringen auf. Wer den Typ aus dem Netz nimmt, hat zwei Quellen fuer eine Sache. */
        GpuBuffer indizes;
        com.mojang.renderpearl.api.pipeline.IndexType indexTyp;
        int firstIndex = 0;

        if (this.indexAnsicht != null)
        {
            indizes = this.indexAnsicht.buffer();
            indexTyp = this.zustand.indexType();
            firstIndex = indexStart(this.indexAnsicht, indexTyp, this.zustand.indexCount());
        }
        else
        {
            RenderSystem.AutoStorageIndexBuffer folge =
                RenderSystem.getSequentialBuffer(this.zustand.primitiveTopology());

            /* ⚠️ ERST holen, DANN fragen: die Breite steht erst fest, nachdem der Puffer
             * fuer diese Anzahl gewachsen ist. */
            indizes = folge.getBuffer(this.zustand.indexCount());
            indexTyp = folge.type();
        }

        GpuBufferSlice transformationen = RenderSystem.getDynamicUniforms()
            .writeTransform(modelView, farbe == null ? new Vector4f(1F, 1F, 1F, 1F) : farbe);

        /* Der Ausschnitt gehoert in 26.2 zum Durchgang, nicht mehr in einen globalen
         * viewport-Aufruf. null heisst ganzes Ziel; siehe Zeichenbereich.
         *
         * Zurechtgeschnitten, weil ein zu grosser Ausschnitt hier abbricht statt zu
         * beschneiden ("RenderArea out of bounds for texture of 854x480", Bugliste #9).
         * Der Ausschnitt wird gegen die Fenstergroesse gerechnet, das Ziel ist aber nicht
         * immer das Fenster -- Begruendung in Zeichenbereich#begrenztAuf. */
        RenderPass.RenderArea bereich = Zeichenbereich.begrenztAuf(
            Zeichenbereich.aktiv(), farbZiel.getWidth(0), farbZiel.getHeight(0));

        /* Inside a Sammelpass scope consecutive draws to the same target share one pass. */
        RenderPass geteilt = GpuBackend.isOpenGl() ? Sammelpass.holen(farbZiel, tiefenZiel, bereich) : null;
        try (DrawPassLease lease = geteilt != null ? DrawPassLease.borrowExisting(geteilt, farbZiel, tiefenZiel)
            : DrawPassLease.open(RenderSystem.getDevice().createCommandEncoder(), () -> "bbs-gehalten",
                farbZiel, tiefenZiel, bereich))
        {
            RenderPass durchgang = lease.pass();
            pipeline = lease.pipeline(pipeline);
            durchgang.setPipeline(RenderSystem.getCompiledPipeline(pipeline));

            RenderSystem.bindDefaultUniforms(durchgang);
            durchgang.setUniform("DynamicTransforms", transformationen);

            if (textur != null)
            {
                bindeWennDa(durchgang, "Sampler0", textur.texure0(), textur.sampler0());
                bindeWennDa(durchgang, "Sampler1", textur.texure1(), textur.sampler1());
                bindeWennDa(durchgang, "Sampler2", textur.texure2(), textur.sampler2());
            }

            if (zusatz != null)
            {
                zusatz.accept(durchgang);
            }

            durchgang.setVertexBuffer(0, this.eckenAnsicht);
            durchgang.setIndexBuffer(indizes, indexTyp);
            durchgang.drawIndexed(this.zustand.indexCount(), 1, firstIndex, 0, 0);

            messen("gezeichnet", pipeline);
        }
    }

    static int indexStart(GpuBufferSlice slice, com.mojang.renderpearl.api.pipeline.IndexType type, int count)
    {
        validateSlice(slice, GpuBuffer.USAGE_INDEX, Math.multiplyExact((long) count, type.bytes));
        if (slice.offset() % type.bytes != 0) throw new IllegalArgumentException("Index slice offset is not element aligned");
        return Math.toIntExact(slice.offset() / type.bytes);
    }

    static void validateVertices(GpuBufferSlice slice, int count, int stride)
    {
        if (count < 0 || stride <= 0) throw new IllegalArgumentException("Invalid vertex geometry dimensions");
        validateSlice(slice, GpuBuffer.USAGE_VERTEX, Math.multiplyExact((long) count, stride));
    }

    private static void validateSlice(GpuBufferSlice slice, int usage, long required)
    {
        if (slice == null || slice.buffer().isClosed())
            throw new IllegalStateException("GPU mesh slice is closed or expired after its RenderPearl submit");
        if (required < 0 || slice.offset() < 0 || slice.length() < required
            || Math.addExact(slice.offset(), slice.length()) > slice.buffer().size()
            || (slice.buffer().usage() & usage) == 0)
            throw new IllegalArgumentException("GPU mesh slice exceeds its actual buffer/range/usage contract");
    }

    /**
     * Messfuehler an der Grenze: WELCHE Geometrie kommt hier ueberhaupt an?
     *
     * <p>⭐ WOFUER (Aufgabe 96, schwarze Modellvorschau): diese Methode steigt oben still
     * aus, wenn kein Eckenpuffer da ist oder der Zaehler 0 ergibt -- ohne Fehler, ohne
     * Logzeile. Damit sehen "die Geometrie kommt gar nicht an" und "sie kommt an und wird
     * nicht sichtbar" von aussen GENAU GLEICH aus. Das ist die Frage, die vor jedem
     * weiteren Fix beantwortet sein muss, und sie war nie gemessen.
     *
     * <p>⛔ ZAEHLER IN SYSTEM-PROPERTIES, NICHT IN EINEM NEUEN FELD. Ein frisch
     * hinzugefuegtes statisches Feld ist nach einem Hotswap NULL -- statische
     * Initialisierer laufen beim Tausch nicht neu. Properties existieren schon und
     * ueberleben jeden Tausch.
     *
     * <p>Standardmaessig AUS (kostet dann einen getProperty je Zeichenauftrag). Anschalten
     * und auslesen ueber den EXEC-Kanal:
     *
     * <pre>
     * System.setProperty("bbs.netz.mess", "1")
     * System.getProperties().stringPropertyNames().stream()
     *     .filter(s -&gt; s.startsWith("bbs.netz.")).sorted()
     *     .map(s -&gt; s + "=" + System.getProperty(s))
     *     .collect(java.util.stream.Collectors.joining("\n"))
     * </pre>
     */
    private void messen(String was, RenderPipeline pipeline)
    {
        if (!Messpunkt.istAn())
        {
            return;
        }

        /* ⭐ MIT AUFRUFER. Die Pipeline allein sagt "Dreiecke", aber nicht WER Linien durch
         * eine Dreiecks-pipeline schickt -- und genau das ist die Frage (Viewport-befund 2:
         * aus Hilfslinien wird eine gefuellte Flaeche). rufer() ist teuer, darum erst nach
         * dem Schalter-test. */
        Messpunkt.zaehle("netz." + was,
            (pipeline == null ? "ohne" : String.valueOf(pipeline.getLocation()))
            + " vertex=" + (this.zustand == null ? -1 : this.zustand.vertexCount())
            + " von " + Messpunkt.rufer(3));
    }

    /**
     * Prueft VOR dem Zeichnen, ob die Pipeline zu dem passt, was diese Klasse liefern KANN --
     * und scheitert laut, statt die JVM mitzunehmen.
     *
     * <h2>Warum das noetig ist</h2>
     *
     * <p>Diese Klasse hat genau EINEN Eckenpuffer und bindet ihn auf Nummer 0. Eine Pipeline
     * darf aber mehrere getrennte Bindungen erklaeren -- BBS' eigener Modellweg tut das
     * (Position, Normale, UV0 als drei Puffer, siehe {@code ModelVAOPipeline}). Trifft so
     * eine Pipeline auf ein verschraenktes Netz, geht zweierlei schief:
     *
     * <ul>
     *   <li>Die Bindungen 1 und 2 bleiben ungesetzt, werden aber gelesen.</li>
     *   <li>Bindung 0 bekommt den falschen Schritt: erklaert die Pipeline 12 Byte je Ecke
     *       und das Netz liefert 36, nimmt der Shader jede dritte Byte-Gruppe als neue Ecke
     *       und liest dreimal so weit, wie der Puffer reicht.</li>
     * </ul>
     *
     * <p>Beides ist ein FORMAL GUELTIGER Zeichenaufruf: {@code glGetError} schweigt, es gibt
     * keine Ausnahme, und die JVM verschwindet ohne Bericht. Genau so starb der Film-Editor
     * (Aufgabe 89) -- gefunden nur ueber den Java-Stack in einem hs_err-Bericht, den es bei
     * dieser Fehlerart nicht immer gibt.
     *
     * @throws IllegalStateException wenn die Pipeline mehr als einen Puffer erwartet oder
     *                               ihr Format eine andere Eckenbreite hat als das Netz
     */
    private void bindungPruefen(RenderPipeline pipeline)
    {
        /* ⚠️ Das Feld hat IMMER die volle Laenge (16 Plaetze) und ist an den unbenutzten
         * Stellen null -- seine Laenge sagt also nichts. Gezaehlt werden muss, was wirklich
         * belegt ist. (Dieser Irrtum hat hier einmal 16 statt 1 gemeldet.) */
        VertexFormat[] bindungen = pipeline.getVertexFormatBindings().toArray(new VertexFormat[0]);
        int belegt = 0;

        for (VertexFormat format : bindungen)
        {
            if (format != null)
            {
                belegt += 1;
            }
        }

        if (belegt != 1 || bindungen[0] == null)
        {
            throw new IllegalStateException(
                "Pipeline " + pipeline.getLocation() + " erwartet " + belegt
                + " getrennte Eckenpuffer (Bindung 0 " + (bindungen[0] == null ? "LEER" : "belegt")
                + "), diese Klasse kann aber nur EINEN verschraenkten auf Nummer 0 binden."
                + " Fuer den Sofortweg die verschraenkte Pipeline-Familie waehlen"
                + " (ModelVAOPipeline.waehlenVerschraenkt), nicht die des VAO-Wegs.");
        }

        int erwartet = bindungen[0].getVertexSize();
        int geliefert = this.zustand.format().getVertexSize();

        if (erwartet != geliefert || !bindungen[0].equals(this.zustand.format()))
        {
            throw new IllegalStateException(
                "Pipeline " + pipeline.getLocation() + " erwartet " + erwartet
                + " Byte je Ecke im Format " + bindungen[0] + ", das Netz liefert " + geliefert
                + " Byte im Format " + this.zustand.format()
                + ". Schrittweite und Attribute muessen beide zur Pipeline passen.");
        }
    }

    private static void bindeWennDa(RenderPass durchgang, String name, GpuTextureView ansicht, GpuSampler abtaster)
    {
        if (ansicht != null)
        {
            durchgang.setUniform(name, ansicht, abtaster);
        }
    }

    @Override
    public void close()
    {
        if (this.closed) return;
        this.closed = true;
        this.eckenAnsicht = null;
        this.indexAnsicht = null;

        if (this.ecken != null)
        {
            this.ecken.close();
            this.ecken = null;
        }

        if (this.eigeneIndizes != null)
        {
            this.eigeneIndizes.close();
            this.eigeneIndizes = null;
        }
    }
}
