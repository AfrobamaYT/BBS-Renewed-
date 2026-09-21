package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.joml.Matrices;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class BOBJModelVAO implements IModelVAO
{
    private static final int PARALLEL_SKIN_THRESHOLD = 16384;
    private static final int SKIN_CACHE_SIZE = 3;
    private static final ThreadLocal<SkinScratch> SKIN_SCRATCH =
        ThreadLocal.withInitial(SkinScratch::new);

    private static final class SkinScratch
    {
        public final Vector4f vertex = new Vector4f();
        public final Vector3f normal = new Vector3f();
    }

    private static final class SkinPose
    {
        public final GpuBuffer position;
        public final GpuBuffer normale;

        public SkinPose(GpuBuffer position, GpuBuffer normale)
        {
            this.position = position;
            this.normale = normale;
        }
    }

    public BOBJLoader.CompiledData data;
    public BOBJArmature armature;

    private int count;

    /**
     * 26.2: aus sechs GL-Puffernummern werden drei GpuBuffer.
     *
     * <p>Position und Normale werden pro Bild neu beschrieben (das Skinning rechnet auf der
     * CPU), die Texturkoordinaten liegen fest. Ihre GROESSE aendert sich nie — darum
     * genuegt {@code writeToBuffer}, das den Inhalt ersetzt, ohne neu anzulegen.
     *
     * <p>⚠️ DREI PUFFER FEHLEN BEWUSST, und zwar dieselben wie bei {@link ModelVAO} plus
     * einer:
     * <ul>
     *   <li>Tangenten und Mittel-UV wurden nur fuer Iris-Shader gefuellt; die Anbindung ist
     *       im 26.2-Port ausgeschlossen.</li>
     *   <li>Der Licht-Puffer trug im Stencil-Durchgang ein Licht JE ECKE (der Knochen mit
     *       dem groessten Gewicht bestimmte es). Das ist kein konstanter Wert und passt
     *       darum nicht in den Uniform-Block — es braucht eine vierte Vertex-Bindung und
     *       eine eigene Pipeline-Variante. Solange die fehlt, faehrt auch der
     *       Stencil-Durchgang mit dem konstanten Licht aus dem Block. Sichtbar wird das
     *       nur im Picking, und nur bei Modellen mit knochenabhaengiger Beleuchtung.</li>
     * </ul>
     */
    private GpuBuffer position;
    private GpuBuffer normale;
    private GpuBuffer texturUV;

    /** Knochenteil der Pick-Kennung je Ecke (ivec2, y ungenutzt) — die vierte Bindung,
     *  die der Feldkommentar oben lange als fehlend fuehrte. Befuellt nur im
     *  Stencil-Durchgang; sonst bleibt der Puffer auf Nullen (Kennung = Basis). */
    private GpuBuffer licht;

    private float[] tmpVertices;
    private float[] tmpNormals;
    private int[] tmpLight;
    /** Dominanter Knochen je Ecke. Haengt nur von den importierten Gewichten ab. */
    private int[] pickingBones;
    private float[] tmpTangents;
    private final LinkedHashMap<Long, SkinPose> skinCache = new LinkedHashMap<>(8, 0.75F, true);
    private SkinPose aktiveSkinPose;

    /** Picking-Licht hat einen eigenen Dirty-Zustand; eine Stencil-Passage bewegt kein Mesh. */
    private boolean lichtGueltig;
    private boolean lichtMitKnochen;

    /**
     * Bumped on every VBO upload. The VBO is shared between actors using the same model, so a
     * deferred translucent command compares this against the value it captured to know whether
     * someone re-skinned the mesh since — and re-uploads from its armature snapshot if so.
     */
    private int uploadCount;

    public BOBJModelVAO(BOBJLoader.CompiledData data)
    {
        this.data = data;
        this.armature = this.data.mesh.armature;

        this.initBuffers();
    }

    /**
     * Initiate buffers. This method is responsible for allocating 
     * buffers for the data to be passed to VBOs and also generating the 
     * VBOs themselves. 
     */
    private void initBuffers()
    {
        this.count = this.data.normData.length / 3;
        this.tmpVertices = new float[this.data.posData.length];
        this.tmpNormals = new float[this.data.normData.length];
        this.tmpLight = new int[this.count * 2];
        this.pickingBones = new int[this.count];
        this.tmpTangents = new float[this.count * 4];

        for (int i = 0; i < this.count; i++)
        {
            this.pickingBones[i] = this.dominanterKnochen(i);
        }

        this.position = puffer("bobj-position", this.data.posData);
        this.normale = puffer("bobj-normale", this.data.normData);
        this.texturUV = puffer("bobj-uv", this.data.texData);
        this.licht = pufferInt("bobj-licht", this.tmpLight);
    }

    /**
     * Legt einen Vertexpuffer auf der Grafikkarte an.
     *
     * <p>Der Zwischenspeicher ist nativ (Mojangs Schnittstelle verlangt das) und wird
     * gleich wieder freigegeben — {@code createBuffer} liest ihn beim Aufruf aus.
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
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    /**
     * Schreibt den Inhalt eines vorhandenen Puffers neu — fuer das Ergebnis des Skinnings.
     *
     * <p>Die Groesse bleibt gleich, es wird also nichts neu angelegt. Genau darum ist der
     * Weg billig genug fuer jedes Bild.
     */
    private static void schreiben(GpuBuffer ziel, float[] werte)
    {
        ByteBuffer roh = MemoryUtil.memAlloc(werte.length * Float.BYTES).order(ByteOrder.nativeOrder());

        try
        {
            roh.asFloatBuffer().put(werte);

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(ziel.slice(), roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    private static GpuBuffer pufferInt(String name, int[] werte)
    {
        ByteBuffer roh = MemoryUtil.memAlloc(werte.length * Integer.BYTES).order(ByteOrder.nativeOrder());

        try
        {
            roh.asIntBuffer().put(werte);

            return RenderSystem.getDevice().createBuffer(() -> name,
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    private static void schreibenInt(GpuBuffer ziel, int[] werte)
    {
        ByteBuffer roh = MemoryUtil.memAlloc(werte.length * Integer.BYTES).order(ByteOrder.nativeOrder());

        try
        {
            roh.asIntBuffer().put(werte);

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(ziel.slice(), roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }
    }

    /**
     * Clean up resources which were used by this  
     */
    public void delete()
    {
        this.skinCacheLeeren();

        if (this.position != null)
        {
            this.position.close();
            this.position = null;
        }

        if (this.normale != null)
        {
            this.normale.close();
            this.normale = null;
        }

        if (this.texturUV != null)
        {
            this.texturUV.close();
            this.texturUV = null;
        }

        if (this.licht != null)
        {
            this.licht.close();
            this.licht = null;
        }
    }

    public int getUploadCount()
    {
        return this.uploadCount;
    }

    /** A deep copy of the armature's current skinning matrices, for deferred re-uploads. */
    public Matrix4f[] snapshotArmature()
    {
        Matrix4f[] matrices = this.armature.matrices;
        Matrix4f[] snapshot = new Matrix4f[matrices.length];

        for (int i = 0; i < matrices.length; i++)
        {
            snapshot[i] = matrices[i] == null ? null : new Matrix4f(matrices[i]);
        }

        return snapshot;
    }

    /**
     * Update this mesh. This method is responsible for applying
     * matrix transformations to vertices and normals according to its
     * bone owners and these bone influences.
     */
    public void updateMesh(StencilMap stencilMap)
    {
        this.updateMesh(stencilMap, this.armature.matrices);
    }

    public void updateMesh(StencilMap stencilMap, mchorse.bbs_mod.obj.shapes.ShapeKeys keys)
    {
        /* ⛔ Getriebene Shape Keys ERST hier, nach setupMatrices: der Treiber liest Regler
         * und Knochenlagen, und die stehen vorher noch auf dem letzten Bild. In eine KOPIE,
         * damit die Werte des Animators an der Form unangetastet bleiben — das Rig
         * ergaenzt, es uebernimmt nicht. */
        if (this.armature != null
            && this.armature.zwang instanceof mchorse.bbs_mod.cubic.ik.RigMechanik mechanik
            && mechanik.treibtShapeKeys())
        {
            keys = keys == null ? new mchorse.bbs_mod.obj.shapes.ShapeKeys() : keys.copy();
            mechanik.shapeKeys(this.armature, keys);
        }

        this.shapeKeys = keys;

        this.updateMesh(stencilMap, this.armature.matrices);
    }

    /**
     * Die Shape-Key-Werte DIESES Bildes. Sie kommen von der Form, nicht vom Modell —
     * dasselbe Netz kann in zwei Formen verschieden weit verzogen sein.
     */
    private mchorse.bbs_mod.obj.shapes.ShapeKeys shapeKeys;
    /** Ausgangslagen mit eingerechneten Shape Keys; nur belegt, wenn welche wirken. */
    private float[] verzogen;

    /**
     * ⛔ Shape Keys wirken VOR dem Skinning. Blender verzieht das Ruhenetz und haeutet
     * danach — umgekehrt wandert die Verformung durch die Knochenmatrizen und der Effekt
     * haengt an der Pose. Deshalb steht das hier und nicht hinter der Schleife.
     *
     * @return die Ausgangslagen, entweder das Original oder die verzogene Kopie
     */
    private float[] ausgangslagen()
    {
        java.util.Map<String, float[]> bahnen = this.data.shapeData;

        if (bahnen.isEmpty() || this.shapeKeys == null || this.shapeKeys.shapeKeys.isEmpty())
        {
            return this.data.posData;
        }

        float[] basis = this.data.posData;
        boolean gerechnet = false;

        for (java.util.Map.Entry<String, Float> e : this.shapeKeys.shapeKeys.entrySet())
        {
            float[] ziel = bahnen.get(e.getKey());
            float wert = e.getValue() == null ? 0F : e.getValue();

            if (ziel == null || wert == 0F)
            {
                continue;
            }

            if (!gerechnet)
            {
                if (this.verzogen == null || this.verzogen.length != basis.length)
                {
                    this.verzogen = new float[basis.length];
                }

                System.arraycopy(basis, 0, this.verzogen, 0, basis.length);
                gerechnet = true;
            }

            /* Wie der Cubic-Renderer: base + wert * (ziel - base), aufaddiert ueber alle
             * Keys, damit sich zwei Keys ueberlagern statt einander zu ersetzen. */
            for (int i = 0; i < basis.length; i++)
            {
                this.verzogen[i] += wert * (ziel[i] - basis[i]);
            }
        }

        return gerechnet ? this.verzogen : basis;
    }

    /** B-Bone-Segmentwahl je Vertex-Gewicht, einmal aus der Ruhelage (siehe {@code BOBJHaut}). */
    private mchorse.bbs_mod.bobj.BOBJHaut.Segmente segmente;

    private mchorse.bbs_mod.bobj.BOBJHaut.Segmente segmente()
    {
        if (this.segmente == null)
        {
            this.segmente = new mchorse.bbs_mod.bobj.BOBJHaut.Segmente();
        }

        this.segmente.sichern(this.count);

        return this.segmente;
    }

    public void updateMesh(StencilMap stencilMap, Matrix4f[] matrices)
    {
        /* ⛔ NUR HAEUTEN UND HOCHLADEN, WENN SICH DIE MATRIZEN GEAENDERT HABEN. Gemessen am
         * 2026-08-21 mit async-profiler: `schreiben` (der Weg in den GPU-Puffer) stand mit
         * 15,0 % als groesster Einzelposten der CPU — bei 84 FPS rund 1,8 ms je Bild. Dabei
         * ist die Pose seit dem Cache in `BOBJArmature.setupMatrices` haeufig unveraendert,
         * und dann sind sowohl das Skinning ueber alle Ecken als auch der Upload umsonst:
         * im Puffer steht bereits genau das Ergebnis.
         *
         * Der Fingerabdruck laeuft nur ueber Daten, die die Geometrie wirklich aendern.
         * Picking-Licht hat unten einen eigenen Cache. Damit kann der Stencil-Durchgang
         * dieselbe ausgewertete Geometrie verwenden, statt sie ein zweites Mal zu haeuten. */
        long signatur = signatur(matrices, this.shapeKeys);
        if (signatur != this.letzteMeshSignatur || !this.meshGueltig)
        {
            this.letzteMeshSignatur = signatur;
            this.meshGueltig = true;

            float[] oldVertices = this.ausgangslagen();
            float[] oldNormals = this.data.normData;
            SkinPose pose = this.skinCache.get(signatur);

            if (pose == null)
            {
                pose = this.neueSkinPose();
                mchorse.bbs_mod.bobj.BOBJHaut.Segmente segmente = this.segmente();

                if (this.count >= PARALLEL_SKIN_THRESHOLD && ForkJoinPool.getCommonPoolParallelism() > 1)
                {
                    IntStream.range(0, this.count).parallel().forEach((i) ->
                        this.hautVertex(oldVertices, oldNormals, this.tmpVertices,
                            this.tmpNormals, matrices, segmente, i));
                }
                else
                {
                    for (int i = 0; i < this.count; i++)
                    {
                        this.hautVertex(oldVertices, oldNormals, this.tmpVertices,
                            this.tmpNormals, matrices, segmente, i);
                    }
                }

                this.processData(this.tmpVertices, this.tmpNormals, matrices);
                schreiben(pose.position, this.tmpVertices);
                schreiben(pose.normale, this.tmpNormals);
                this.skinCache.put(signatur, pose);
            }

            this.aktiveSkinPose = pose;
            this.uploadCount += 1;
        }

        /* ⚠️ Tangenten (Iris) bleiben geparkt (keine Bindung, 26.2-Ausschluss). Das
         * knochenabhaengige Licht ist seit 2026-08-13 zurueck: BINDUNG_LICHT traegt den
         * Knochenteil der Pick-Kennung je Ecke — ohne ihn kannte der Auswahlpuffer nur
         * die Replay-Basis, ein Klick fand nie einen Knochen. */
        this.lichtAktualisieren(stencilMap);
    }

    private SkinPose neueSkinPose()
    {
        if (this.skinCache.isEmpty())
        {
            return this.neueGpuSkinPose();
        }

        if (this.skinCache.size() < SKIN_CACHE_SIZE)
        {
            return this.neueGpuSkinPose();
        }

        Iterator<Map.Entry<Long, SkinPose>> iterator = this.skinCache.entrySet().iterator();
        SkinPose pose = iterator.next().getValue();

        iterator.remove();

        return pose;
    }

    private SkinPose neueGpuSkinPose()
    {
        return new SkinPose(puffer("bobj-position-pose", this.data.posData),
            puffer("bobj-normale-pose", this.data.normData));
    }

    private void skinCacheLeeren()
    {
        for (SkinPose pose : this.skinCache.values())
        {
            pose.position.close();
            pose.normale.close();
        }

        this.skinCache.clear();
        this.aktiveSkinPose = null;
    }

    private void hautVertex(float[] oldVertices, float[] oldNormals, float[] newVertices,
                            float[] newNormals, Matrix4f[] matrices,
                            mchorse.bbs_mod.bobj.BOBJHaut.Segmente segmente, int i)
    {
        SkinScratch scratch = SKIN_SCRATCH.get();

        /* Haeuten wie Blender — EINE Stelle fuer Zeichnen und Messung (BOBJHaut). */
        mchorse.bbs_mod.bobj.BOBJHaut.vertex(this.armature, oldVertices, oldNormals,
            this.data.weightData, this.data.boneIndexData, matrices, i, segmente,
            scratch.vertex, scratch.normal);

        int n = i * 3;
        newVertices[n] = scratch.vertex.x;
        newVertices[n + 1] = scratch.vertex.y;
        newVertices[n + 2] = scratch.vertex.z;
        newNormals[n] = scratch.normal.x;
        newNormals[n + 1] = scratch.normal.y;
        newNormals[n + 2] = scratch.normal.z;
    }

    /**
     * Aktualisiert nur die Picking-Kennung. Sie ist keine ausgewertete Geometrie und darf
     * deshalb keinen Skinning-Durchgang oder Positions-/Normalen-Upload ungueltig machen.
     */
    private void lichtAktualisieren(StencilMap stencilMap)
    {
        if (stencilMap == null)
        {
            return;
        }

        boolean mitKnochen = stencilMap.increment;

        if (this.lichtGueltig && this.lichtMitKnochen == mitKnochen)
        {
            return;
        }

        for (int i = 0; i < this.count; i++)
        {
            this.tmpLight[i * 2] = mitKnochen ? this.pickingBones[i] : 0;
            this.tmpLight[i * 2 + 1] = 0;
        }

        schreibenInt(this.licht, this.tmpLight);
        this.lichtMitKnochen = mitKnochen;
        this.lichtGueltig = true;
    }

    /** Gleiche Tie-Break-Regel wie BOBJHaut.vertex, aber einmalig statt pro Bild. */
    private int dominanterKnochen(int vertex)
    {
        float maxWeight = -1F;
        int bone = -1;

        for (int k = 0; k < 4; k++)
        {
            float weight = this.data.weightData[vertex * 4 + k];

            if (weight > 0F && weight > maxWeight)
            {
                bone = this.data.boneIndexData[vertex * 4 + k];
                maxWeight = weight;
            }
        }

        return Math.max(0, bone);
    }

    protected void processData(float[] newVertices, float[] newNormals, Matrix4f[] matrices)
    {}

    /**
     * Legt die eigenen Vertexpuffer an die Bindungen der Pipeline und zeichnet.
     *
     * <p>Was frueher hier stand — VAO binden, Attribute ein- und ausschalten, konstante
     * Attribute fuer Farbe, Licht und Overlay setzen, {@code shader.apply()} — gibt es in
     * 26.2 nicht mehr. Die Bindungen stehen in der Pipeline, die konstanten Werte im
     * Uniform-Block, und beides setzt {@link ModelVAORenderer} vor dem Aufruf.
     */
    @Override
    public void zeichnen(RenderPass pass)
    {
        if (this.position == null)
        {
            return;
        }

        GpuBuffer aktuellePosition = this.aktiveSkinPose == null ? this.position : this.aktiveSkinPose.position;
        GpuBuffer aktuelleNormale = this.aktiveSkinPose == null ? this.normale : this.aktiveSkinPose.normale;

        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_POSITION, aktuellePosition.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_NORMALE, aktuelleNormale.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_LICHT, this.licht.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_UV0, this.texturUV.slice());

        /* Reihenfolge: (Eckenzahl, Instanzen, erste Ecke, erste Instanz) — belegt an
         * Vanillas Lightmap, PostPass und SkyRenderer. Die Verwechslung mit
         * (erste Ecke, Anzahl) zeichnet lautlos nichts. */
        pass.draw(this.count, 1, 0, 0);
    }

    @Override
    public int eckenZahl()
    {
        return this.count;
    }

    /* ================== Mesh-Cache ================== */

    private long letzteMeshSignatur;
    private boolean meshGueltig;

    /** Nach dem naechsten {@link #updateMesh} wird wieder voll gehaeutet und hochgeladen. */
    public void meshMarkieren()
    {
        this.meshGueltig = false;
        this.skinCacheLeeren();
    }

    private static long signatur(Matrix4f[] matrices, mchorse.bbs_mod.obj.shapes.ShapeKeys shapeKeys)
    {
        long h = 17L;

        if (matrices != null)
        {
            for (Matrix4f m : matrices)
            {
                h = h * 31L + (m == null ? 0 : m.hashCode());
            }
        }

        if (shapeKeys != null)
        {
            h = h * 31L + shapeKeys.shapeKeys.hashCode();
        }

        return h;
    }
}
