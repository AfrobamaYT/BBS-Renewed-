package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Das hochgeladene Netz eines BBS-Modells.
 *
 * <h2>26.2-Port: von eigenen VAOs auf GpuBuffer</h2>
 *
 * <p>Bis 1.21.1 verwaltete diese Klasse alles selbst: {@code glGenVertexArrays},
 * {@code glGenBuffers}, {@code glVertexAttribPointer}, {@code glDrawArrays} — ein
 * kompletter eigener Renderer neben Minecrafts. In 26.2 gibt es diese Aufrufe nicht mehr
 * ueber {@code RenderSystem}, und beim angekuendigten Wechsel auf Vulkan gaebe es sie
 * ueberhaupt nicht mehr.
 *
 * <p>Die STRUKTUR bleibt aber erhalten, und das ist der Grund, warum der Umbau klein
 * ausfaellt: BBS legt seine Daten in getrennten Puffern ab statt sie zu verschraenken,
 * und 26.2 kennt genau das ueber mehrere Vertex-Bindungen. Aus jedem
 * {@code glGenBuffers} + {@code glBufferData} wird ein {@link GpuBuffer}, aus jedem
 * {@code glVertexAttribPointer} eine Bindungsnummer in {@link ModelVAOPipeline}.
 *
 * <h2>Was dabei absichtlich wegfaellt</h2>
 *
 * <ul>
 *   <li><b>Der zweite VAO.</b> Frueher gab es zwei Attributbelegungen (einmal Iris-Layout,
 *       einmal {@code POSITION_TEXTURE_LIGHT_COLOR}) und {@code render} waehlte zur
 *       Laufzeit. In 26.2 gehoert das Vertexformat zur Pipeline — eine zweite Belegung
 *       ist eine zweite Pipeline, keine zweite Puffergruppe.</li>
 *   <li><b>Tangenten und Mittel-UV.</b> Die fuellte der alte Code nur fuer Iris-Shader
 *       (dort {@code glEnableVertexAttribArray}, sonst {@code glDisable}). Die
 *       Iris-Anbindung ist im 26.2-Port ausgeschlossen (siehe {@code sourceSets} in
 *       build.gradle); sobald sie zurueckkommt, braucht sie eine eigene Pipeline mit zwei
 *       zusaetzlichen Bindungen — die Daten dafuer liegen in {@link ModelVAOData} bereit.
 *       ⚠️ Das ist eine bewusst benannte Grenze, kein vergessener Fall.</li>
 *   <li><b>Farbe, Licht und Overlay.</b> Waren konstante Vertexattribute
 *       ({@code glVertexAttrib4f} / {@code glVertexAttribI2i}); die gibt es nicht mehr.
 *       Sie stehen jetzt im Uniform-Block, den {@link ModelVAORenderer} fuellt.</li>
 * </ul>
 */
public class ModelVAO implements IModelVAO
{
    private GpuBuffer position;
    private GpuBuffer normale;
    private GpuBuffer texturUV;

    /** Nullen — statische Modelle differenzieren (noch) keine Knochen im Pick; die
     *  Bindung muss trotzdem bedient werden, seit BINDUNG_LICHT in der Pipeline steht. */
    private GpuBuffer licht;

    private int ecken;
    private GpuBuffer vitrailCorners;

    public ModelVAO(ModelVAOData data)
    {
        this.upload(data);
    }

    /** With a pick id per vertex in place of the zeros (see MobPickNetz) */
    public ModelVAO(ModelVAOData data, float[] licht)
    {
        this.upload(data, licht);
    }

    public void upload(ModelVAOData data, float[] licht)
    {
        this.upload(data);
        this.licht.close();
        this.licht = puffer("bbs-modell-licht", licht);
    }

    public void delete()
    {
        this.schliessen();
    }

    private void schliessen()
    {
        if (this.vitrailCorners != null)
        {
            this.vitrailCorners.close();
            this.vitrailCorners = null;
        }
        if (this.position != null)
        {
            IrisModelBuffers.close(this.position);
            this.position = null;
        }

        if (this.normale != null)
        {
            IrisModelBuffers.close(this.normale);
            this.normale = null;
        }

        if (this.texturUV != null)
        {
            IrisModelBuffers.close(this.texturUV);
            this.texturUV = null;
        }

        if (this.licht != null)
        {
            this.licht.close();
            this.licht = null;
        }
    }

    public void upload(ModelVAOData data)
    {
        /* Ein erneuter Upload ersetzt das Netz — die alten Puffer muessen weg, sonst
         * bleibt ihr Speicher auf der Grafikkarte liegen, solange das Spiel laeuft. */
        this.schliessen();

        this.position = puffer("bbs-modell-position", data.vertices());
        this.normale = puffer("bbs-modell-normale", data.normals());
        this.texturUV = puffer("bbs-modell-uv", data.texCoords());
        this.licht = puffer("bbs-modell-licht", new float[data.vertices().length / 3 * 2]);

        this.ecken = data.vertices().length / 3;
    }

    /**
     * Legt einen Vertexpuffer auf der Grafikkarte an.
     *
     * <p>{@code createBuffer} liest den {@link ByteBuffer} beim Aufruf aus, darum wird der
     * Zwischenspeicher direkt danach wieder freigegeben. Er kommt von
     * {@link MemoryUtil}, nicht vom Java-Heap: Mojangs Schnittstelle verlangt einen
     * nativen Puffer, und die Bytereihenfolge muss die der Maschine sein.
     */
    private static GpuBuffer puffer(String name, float[] werte)
    {
        ByteBuffer roh = MemoryUtil.memAlloc(werte.length * Float.BYTES).order(ByteOrder.nativeOrder());

        try
        {
            roh.asFloatBuffer().put(werte);

            /* ⚠️ USAGE_COPY_DST, obwohl der Puffer MIT Daten angelegt wird: das Skinning
             * schreibt ihn per writeToBuffer jedes Bild neu (siehe schreiben()). Die Faustregel
             * "mit Daten angelegt = fertig, braucht kein COPY_DST" gilt hier NICHT und hat
             * genau deshalb einen Messlauf gekostet. Massgeblich ist nicht, wie der Puffer
             * entsteht, sondern ob er je wieder beschrieben wird. */
            return RenderSystem.getDevice().createBuffer(() -> name,
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC
                    | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    @Override
    public void zeichnen(RenderPass pass)
    {
        if (this.position == null)
        {
            return;
        }

        if (IrisModelBuffers.tryDraw(pass, this.position, this.normale, this.texturUV, null, this.ecken))
        {
            return;
        }

        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_POSITION, this.position.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_NORMALE, this.normale.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_UV0, this.texturUV.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_LICHT, this.licht.slice());

        /* Ohne Indexpuffer: die Ecken liegen in Zeichenreihenfolge, genau wie beim alten
         * glDrawArrays(GL_TRIANGLES, 0, count). Die Dreiecks-Topologie steht in der
         * Pipeline, nicht hier.
         *
         * ⚠️ REIHENFOLGE DER ARGUMENTE IST (Eckenzahl, Instanzen, erste Ecke, erste
         * Instanz) — belegt an drei Vanilla-Stellen, weil javap keine Parameternamen
         * traegt und die Verwechslung mit (erste Ecke, Anzahl) lautlos NICHTS zeichnet:
         *   Lightmap    draw(3, 1, 0, 0)   Vollbild-Dreieck
         *   PostPass    draw(3, 1, 0, 0)   dito
         *   SkyRenderer draw(10, 1, 0, 0)  Himmelsscheibe mit 10 Ecken */
        pass.draw(this.ecken, 1, 0, 0);
    }

    @Override
    public int eckenZahl()
    {
        return this.ecken;
    }

    public mchorse.bbs_mod.integrations.vitrail.VitrailMeshInputs vitrailInputs()
    {
        if (this.vitrailCorners == null)
            this.vitrailCorners = mchorse.bbs_mod.integrations.vitrail.VitrailCornerBuffer.create(this.ecken);
        return mchorse.bbs_mod.integrations.vitrail.VitrailMeshInputs.model(this.vitrailCorners, this.ecken,
            this.ecken, this.position, this.normale, this.texturUV, null);
    }
}
