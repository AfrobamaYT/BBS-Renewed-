package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.buffers.GpuBuffer;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Deferred translucency for forms.
 *
 * <p>Form renderers draw immediately, in entity iteration order, with depth writes on — so a
 * semi-transparent pixel drawn early would occlude anything drawn later behind it. Instead,
 * draws whose texture (or color) has semi-transparency run twice: an immediate opaque pass
 * (shader keeps only opaque texels, which write depth), and a command enqueued here. At the
 * end of the frame — after every form has drawn — {@link #flush()} replays the commands
 * sorted far-to-near with depth writes off, so translucent pixels blend over everything
 * without ever hiding it.</p>
 *
 * <p>Commands replay finished draw calls with matrices captured at enqueue time — they never
 * re-run animation, IK or physics.</p>
 */
public class FormTranslucentQueue
{
    public static final int PASS_SINGLE = 0;
    public static final int PASS_OPAQUE = 1;
    public static final int PASS_TRANSLUCENT = 2;

    private static final List<DrawCommand> commands = new ArrayList<>();
    /** Freigegebene, unveraenderliche Pose-Snapshots je VAO; nur der Render-Thread greift zu. */
    private static final Map<BOBJModelVAO, ArrayDeque<Matrix4f[]>> armatureSnapshots = new WeakHashMap<>();
    private static boolean active;

    /**
     * Camera-space origin of the form currently being drawn through the buffered vertex
     * consumer path (blocks, items) — its translucent layers can't know their position from
     * the camera-space vertices alone. Non-null also acts as the opt-in for deferring those
     * layers; picking and UI paths never set it.
     */
    private static Vector3f sortOrigin;

    /** Non-null while a group is being recorded: added commands collect here instead of the queue. */
    private static GroupCommand group;

    public static void setSortOrigin(Vector3f origin)
    {
        sortOrigin = origin;
    }

    public static Vector3f getSortOrigin()
    {
        return sortOrigin;
    }

    public static boolean isGroupOpen()
    {
        return group != null;
    }

    /**
     * Start recording a group: until {@link #endGroup()}, added commands collect into one
     * composite command that replays them in insertion order at flush. For forms whose parts
     * depend on each other's depth (a label's text against its background) — the group sorts
     * against other forms as a whole, while its internals keep their original draw order.
     */
    public static void beginGroup(Vector3f cameraSpaceOrigin, boolean cull)
    {
        group = new GroupCommand(cameraSpaceOrigin, cull);
        sortOrigin = new Vector3f(cameraSpaceOrigin);
    }

    public static void endGroup()
    {
        GroupCommand finished = group;

        group = null;
        sortOrigin = null;

        if (finished != null && !finished.children.isEmpty())
        {
            add(finished);
        }
    }

    public static boolean isActive()
    {
        /* The Iris shadow pass re-renders the scene into the shadow map mid-frame: forms there
         * must draw immediately (the shadow map needs their full geometry), and nothing may
         * enqueue — the queue belongs to the main pass. */
        return active && !BBSRendering.isIrisShadowPass();
    }

    /**
     * Whether a draw splits into an immediate opaque pass (writes depth) + a deferred translucent
     * pass. Only when translucency is <em>intrinsic to the texture</em> and the colour is not
     * faded: the solid texels draw now and write depth, the see-through ones defer. A uniform
     * colour fade (alpha &lt; 1) instead takes {@link #needsWholeDefer}, because splitting would
     * push every texel of an otherwise-opaque model into the depth-less translucent pass — the
     * model would stop writing depth entirely and lose its self-occlusion. False outside an active
     * queue scope (UI previews, first-person arm), during picking (the stencil needs every pixel),
     * and on shaders without the PassMode uniform (vanilla programs — the Iris path).
     */
    public static boolean needsSplit(RenderPipeline pipeline, StencilMap stencilMap, Texture texture, float alpha)
    {
        return alpha >= 1F && texture != null && texture.hasTranslucency()
            && isActive() && stencilMap == null && ModelVAOPipeline.istModellPipeline(pipeline);
    }

    /**
     * Whether the whole draw defers as one unit with depth writes kept on, sorted between models:
     * a uniform colour fade on the BBS model shader. The faded model keeps writing depth so it
     * still self-occludes instead of collapsing into an unordered translucent blob, and the
     * transition out of alpha == 1 stays continuous (no pop). Never true under Iris: model draws
     * suspend the queue and render immediately in their own phase, because replaying a captured
     * Iris program at the end of the frame lands after a deferred pack's shading composite
     * (Photon never shades it — the model vanishes).
     */
    public static boolean needsWholeDefer(RenderPipeline pipeline, StencilMap stencilMap, float alpha)
    {
        return alpha < 1F && isActive() && stencilMap == null && ModelVAOPipeline.istModellPipeline(pipeline);
    }

    /**
     * ⛔ 26.2: setPassMode gibt es nicht mehr — und das ist Absicht.
     *
     * <p>Frueher wurde die Durchgangsart als Uniform am Shader-Objekt gesetzt und galt
     * dann fuer alles Folgende. In 26.2 steht sie im Uniform-Block, den
     * {@code ModelVAORenderer} je Zeichenaufruf fuellt, also reist sie als PARAMETER mit:
     * {@code ModelVAORenderer.render(..., PASS_OPAQUE)}.
     *
     * <p>Der Unterschied ist nicht kosmetisch. Ein gesetzter Zustand galt bis zum
     * naechsten Setzen — wer ihn zuruueckzusetzen vergass, faerbte fremde Zeichnungen ein.
     * Ein Parameter kann das nicht.
     */
    private FormTranslucentQueue()
    {}

    public static void add(DrawCommand command)
    {
        if (group != null && command != group)
        {
            group.children.add(command);
        }
        else if (active)
        {
            commands.add(command);
        }
        else
        {
            /* No scope to defer into — draw right away so no pixels are lost. */
            command.draw();
            command.release();
        }
    }

    /** Capture private storage for one deferred command; {@link BOBJCommand#release()} returns it. */
    public static Matrix4f[] captureArmature(BOBJModelVAO vao)
    {
        Matrix4f[] source = vao.armature.matrices;
        ArrayDeque<Matrix4f[]> pool = armatureSnapshots.get(vao);

        if (pool == null)
        {
            pool = new ArrayDeque<>();
            armatureSnapshots.put(vao, pool);
        }

        Matrix4f[] snapshot = pool.pollFirst();

        if (snapshot == null || snapshot.length != source.length)
        {
            snapshot = new Matrix4f[source.length];
        }

        for (int i = 0; i < source.length; i++)
        {
            Matrix4f matrix = source[i];

            if (matrix == null)
            {
                snapshot[i] = null;
            }
            else if (snapshot[i] == null)
            {
                snapshot[i] = new Matrix4f(matrix);
            }
            else
            {
                snapshot[i].set(matrix);
            }
        }

        return snapshot;
    }

    private static void releaseArmature(BOBJModelVAO vao, Matrix4f[] snapshot)
    {
        armatureSnapshots.get(vao).addFirst(snapshot);
    }

    public static void begin()
    {
        release();

        active = true;
    }

    /**
     * Temporarily deactivate the queue: nested offscreen renders (framebuffer forms) run under
     * their own projection mid-frame and must not enqueue into the world's flush. Returns the
     * previous state for {@link #restore(boolean)}.
     */
    public static boolean suspend()
    {
        boolean wasActive = active;

        active = false;

        return wasActive;
    }

    public static void restore(boolean wasActive)
    {
        active = wasActive;
    }

    /**
     * Draw all deferred translucent commands, far to near, without depth writes. Deactivates
     * the queue — later draws (the first-person hand) fall back to single-pass rendering.
     */
    public static void flush()
    {
        active = false;

        if (commands.isEmpty())
        {
            return;
        }

        commands.sort((a, b) -> Float.compare(b.distanceSq, a.distanceSq));

        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;

        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        /* Tiefentest, Tiefenvergleich und Mischung wurden hier frueher global gesetzt
         * (enableDepthTest, depthFunc(GL_LEQUAL), enableBlend, defaultBlendFunc). Seit 26.2
         * gibt es diese Schalter nicht mehr — jeder Zeichenweg bringt den Zustand aus seiner
         * eigenen Pipeline mit, und zwar in genau dieser Kombination: die Modell-Kommandos
         * ueber ModelVAOPipeline.DURCHSICHTIG bzw. DURCHSICHTIG_BEIDSEITIG
         * (BlendFunction.TRANSLUCENT + Tiefentest), RenderLayerCommand ueber die Pipeline
         * seines RenderType (layer.pipeline()).
         * ⛔ GL_LEQUAL heisst dort GREATER_THAN_OR_EQUAL, NICHT LESS_THAN_OR_EQUAL: 26.2
         * rechnet die Tiefe umgekehrt (naeher = groesserer Wert, Loeschwert 0.0). Wer das
         * mechanisch uebersetzt, dreht den Test um und alles verschwindet hinter allem. */

        for (DrawCommand command : commands)
        {
            /* Split-pass commands hold only semi-transparent texels and never write depth;
             * whole-draw commands (the Iris path) carry the entire mesh, so they keep depth
             * writes for correct self-occlusion — the sort already ordered them between models. */
            /* 26.2: hier standen depthMask und enableCull/disableCull als globaler Zustand
             * vor jedem Zug. Beides gibt es nicht mehr -- jeder Befehl waehlt seine Pipeline
             * nach command.depthWrite und command.cull, und die traegt den Zustand. Der
             * Unterschied ist nicht kosmetisch: ein gesetzter Zustand galt bis zum naechsten
             * Setzen und faerbte alles ein, was danach kam. Eine Pipeline kann das nicht. */

            try
            {
                command.draw();
            }
            catch (Exception e)
            {}
            finally
            {
                command.release();
            }
        }

        commands.clear();

        /* Cull, Tiefenmaske und Blend werden nicht mehr zurückgesetzt: seit 26.2 gibt es keinen
         * globalen GL-Zustand, den ein späterer Zeichenweg erben könnte. Jeder folgende Aufruf
         * bekommt Blend, Tiefe und Cull aus der Pipeline seines RenderType (RenderType.pipeline()),
         * also gibt es nichts mehr zurückzugeben. */

    }

    /** Drop any leftover commands (e.g. a frame whose flush point never ran) and free their resources. */
    private static void release()
    {
        for (DrawCommand command : commands)
        {
            command.release();
        }

        commands.clear();

        /* A group left open by an aborted render must not leak into the next frame. */
        if (group != null)
        {
            group.release();
            group = null;
        }

        sortOrigin = null;
    }

    public static abstract class DrawCommand
    {
        public final float distanceSq;
        public final boolean cull;
        public final boolean depthWrite;

        /** The origin must be camera-space (a captured model-view translation) — it's the sort key. */
        protected DrawCommand(Vector3f cameraSpaceOrigin, boolean cull, boolean depthWrite)
        {
            this.distanceSq = cameraSpaceOrigin.lengthSquared();
            this.cull = cull;
            this.depthWrite = depthWrite;
        }

        public abstract void draw();

        public void release()
        {}
    }

    /**
     * Replays a cubic model group's static VAO. The two-pass form draws the BBS model shader's
     * translucent pass without depth writes; the whole-draw form (a uniform colour fade) replays
     * the entire draw and keeps depth writes.
     */
    public static class ModelVAOCommand extends DrawCommand
    {
        private final IModelVAO vao;
        private final Supplier<RenderPipeline> pipeline;
        private final int passMode;
        private final Texture texture;
        private final Matrix4f modelView;
        private final Matrix3f normalMat;
        private final float r, g, b, a;
        private final int light;
        private final int overlay;

        /** The two-pass translucent replay (BBS model shader). */
        public ModelVAOCommand(IModelVAO vao, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            this(vao, BBSShaders::getModel, PASS_TRANSLUCENT, false, texture, modelView, normalMat, r, g, b, a, light, overlay, cull);
        }

        public ModelVAOCommand(IModelVAO vao, Supplier<RenderPipeline> pipeline, int passMode, boolean depthWrite, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            super(modelView.getTranslation(new Vector3f()), cull, depthWrite);

            this.vao = vao;
            this.pipeline = pipeline;
            this.passMode = passMode;
            this.texture = texture;
            this.modelView = modelView;
            this.normalMat = normalMat;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.light = light;
            this.overlay = overlay;
        }

        @Override
        public void draw()
        {
            if (this.texture != null)
            {
                BBSModClient.getTextures().bindTexture(this.texture);
            }

            /* 26.2: die Durchgangsart reist als Parameter mit, statt vorher am Shader
             * gesetzt und danach zurueckgestellt zu werden. Das Zuruecksetzen entfaellt
             * damit ersatzlos — ein Parameter kann keine fremde Zeichnung einfaerben. */
            /* Die Textur GEHT AUSDRUECKLICH MIT (Sampler0 des Zuges). Sie global zu binden
             * -- die Zeile darueber -- erreicht den Durchgang seit 26.2 nicht mehr; ohne den
             * Parameter zeichnet der aufgeschobene Zug weiss (Aufgabe 99). Der Rueckfall in
             * ModelVAORenderer greift hier bewusst NICHT als Entwurf: zwischen Einreihen und
             * Zeichnen liegt der Rest des Bildes, die globale Bindung waere dann geraten. */
            ModelVAORenderer.render(this.pipeline.get(), this.vao, this.modelView, this.normalMat,
                this.r, this.g, this.b, this.a, this.light, this.overlay, this.passMode, 0, this.texture);
        }
    }

    /**
     * Replays a BOBJ mesh. Its VBO holds CPU-skinned vertices and is shared between actors
     * using the same model, so the command keeps an armature snapshot: if someone re-skinned
     * the VBO since capture (the upload counter moved), it re-uploads from the snapshot first.
     */
    public static class BOBJCommand extends DrawCommand
    {
        private final BOBJModelVAO vao;
        private final Supplier<RenderPipeline> pipeline;
        private final int passMode;
        private final Matrix4f[] armatureSnapshot;
        private final int uploadCount;
        private final Texture texture;
        private final Matrix4f modelView;
        private final Matrix3f normalMat;
        private final float r, g, b, a;
        private final int light;
        private final int overlay;
        private final float emission;

        /** The two-pass translucent replay (BBS model shader). */
        public BOBJCommand(BOBJModelVAO vao, Matrix4f[] armatureSnapshot, int uploadCount, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            this(vao, armatureSnapshot, uploadCount, texture, modelView, normalMat, r, g, b, a, light, overlay, 0F, cull);
        }

        public BOBJCommand(BOBJModelVAO vao, Matrix4f[] armatureSnapshot, int uploadCount, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, float emission, boolean cull)
        {
            this(vao, BBSShaders::getModel, PASS_TRANSLUCENT, false, armatureSnapshot, uploadCount, texture, modelView, normalMat, r, g, b, a, light, overlay, emission, cull);
        }

        public BOBJCommand(BOBJModelVAO vao, Supplier<RenderPipeline> pipeline, int passMode, boolean depthWrite, Matrix4f[] armatureSnapshot, int uploadCount, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, boolean cull)
        {
            this(vao, pipeline, passMode, depthWrite, armatureSnapshot, uploadCount, texture, modelView, normalMat, r, g, b, a, light, overlay, 0F, cull);
        }

        public BOBJCommand(BOBJModelVAO vao, Supplier<RenderPipeline> pipeline, int passMode, boolean depthWrite, Matrix4f[] armatureSnapshot, int uploadCount, Texture texture, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, float emission, boolean cull)
        {
            super(modelView.getTranslation(new Vector3f()), cull, depthWrite);

            this.vao = vao;
            this.pipeline = pipeline;
            this.passMode = passMode;
            this.armatureSnapshot = armatureSnapshot;
            this.uploadCount = uploadCount;
            this.texture = texture;
            this.modelView = modelView;
            this.normalMat = normalMat;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.light = light;
            this.overlay = overlay;
            this.emission = emission;
        }

        @Override
        public void draw()
        {
            /* Die Pipeline holt der Zeichenaufruf selbst — siehe unten. */

            if (this.texture != null)
            {
                BBSModClient.getTextures().bindTexture(this.texture);
            }

            if (this.vao.getUploadCount() != this.uploadCount)
            {
                this.vao.updateMesh(null, this.armatureSnapshot);
            }

            /* BOBJModelVAO steht jetzt auf GpuBuffer und implementiert IModelVAO — der
             * Befehl geht damit denselben Weg wie ModelVAOCommand, und this.passMode
             * reist als Parameter mit statt vorher gesetzt zu werden. */
            /* Die Textur GEHT AUSDRUECKLICH MIT (Sampler0 des Zuges). Sie global zu binden
             * -- die Zeile darueber -- erreicht den Durchgang seit 26.2 nicht mehr; ohne den
             * Parameter zeichnet der aufgeschobene Zug weiss (Aufgabe 99). Der Rueckfall in
             * ModelVAORenderer greift hier bewusst NICHT als Entwurf: zwischen Einreihen und
             * Zeichnen liegt der Rest des Bildes, die globale Bindung waere dann geraten. */
            ModelVAORenderer.render(this.pipeline.get(), this.vao, this.modelView, this.normalMat,
                this.r, this.g, this.b, this.a, this.light, this.overlay, this.passMode, 0, this.texture, this.emission);
        }

        @Override
        public void release()
        {
            releaseArmature(this.vao, this.armatureSnapshot);
        }
    }

    /**
     * A recorded sequence of commands that replays as one unit in its original internal order.
     * Sorts against other commands by its own origin; children's origins are ignored.
     */
    public static class GroupCommand extends DrawCommand
    {
        private final List<DrawCommand> children = new ArrayList<>();

        public GroupCommand(Vector3f cameraSpaceOrigin, boolean cull)
        {
            /* Depth writes stay on: the parts depend on each other's depth (text over its
             * background), and the far-to-near sort already ordered the group among forms. */
            super(cameraSpaceOrigin, cull, true);
        }

        @Override
        public void draw()
        {
            for (DrawCommand child : this.children)
            {
                child.draw();
            }
        }

        @Override
        public void release()
        {
            for (DrawCommand child : this.children)
            {
                child.release();
            }
        }
    }

    /**
     * Replays a buffered vanilla render layer (translucent block/item geometry, label text).
     * The layer's own startDrawing binds its texture and shader — with Iris that routes
     * through its override naturally.
     */
    /* ⛔ HIER STAND RenderLayerCommand, und er ist ERLEDIGT statt portiert.
     *
     * Er spielte eine halbdurchsichtige VANILLA-Ebene ab, die BBS zuvor aus Vanillas
     * Sofort-Warteschlange gestohlen hatte -- damit ihre Pixel nicht mitten im Bild Tiefe
     * schrieben und spaeter gezeichnete Formen verdeckten. In 26.2 sortiert der Bildgraph
     * eingereichte Auftraege selbst; genau das war der Zweck des Stehlens.
     *
     * ⚠️ Die uebrigen Befehle dieser Warteschlange bleiben. Sie tragen BBS' EIGENE
     * Geometrie (Modellnetze, Billboards), und die kennt der Bildgraph nicht -- er kann
     * sie also auch nicht einsortieren. Wer beides verwechselt, baut die geloeschte
     * Haelfte wieder ein. */

    /**
     * Spielt Geometrie ab, die in einem {@link GehaltenesNetz} liegengeblieben ist -- der
     * CPU-Modellweg und die Billboards. Das Netz gehoert dem Befehl und wird nach dem
     * Leeren der Warteschlange freigegeben.
     *
     * <h2>26.2: drei Dinge wandern vom Zustand in den Zeichenaufruf</h2>
     *
     * <ul>
     *   <li><b>Tiefenschreiben und Cull</b> standen frueher als globaler Zustand vor dem
     *       Zeichnen ({@code depthMask}, {@code enableCull}). Jetzt sind sie Teil der
     *       Pipeline, also waehlt der Befehl seine Pipeline danach aus.</li>
     *   <li><b>Die Normalenmatrix</b> war ein Uniform am Shader-Objekt. Jetzt steht sie in
     *       BBS' Modell-Block, den {@code ModelVAORenderer.blockFuer} fuellt -- derselbe
     *       Block, den der VAO-Weg benutzt, damit beide Haelften eines Modells gleich
     *       aussehen.</li>
     *   <li><b>Die Durchgangsart</b> (ganz / nur undurchsichtig / nur halbdurchsichtig)
     *       reist im selben Block mit, statt vorher gesetzt zu werden.</li>
     * </ul>
     *
     * <p>⚠️ Die Textur wird ueber die Pipeline-Bindung gesetzt, nicht mehr ueber
     * {@code bindTexture} als globalen Zustand -- ein Durchgang mit unvollstaendigen
     * Bindungen zeichnet NICHT falsch, sondern gar nicht.
     */
    public static class NetzBefehl extends DrawCommand
    {
        private final GehaltenesNetz netz;
        private final Supplier<RenderPipeline> shader;
        private final int passMode;
        private final Texture texture;
        private final Matrix4f modelView;
        private final Matrix3f normalMat;
        private final Runnable preDraw;
        private final Runnable postDraw;

        /** Der geteilte halbdurchsichtige Durchgang: nur die durchscheinenden Texel, ohne Tiefenschreiben. */
        public NetzBefehl(GehaltenesNetz netz, Supplier<RenderPipeline> shader, Texture texture, Matrix4f modelView, Matrix3f normalMat, Vector3f cameraSpaceOrigin, boolean cull, Runnable preDraw, Runnable postDraw)
        {
            this(netz, shader, PASS_TRANSLUCENT, false, texture, modelView, normalMat, cameraSpaceOrigin, cull, preDraw, postDraw);
        }

        public NetzBefehl(GehaltenesNetz netz, Supplier<RenderPipeline> shader, int passMode, boolean depthWrite, Texture texture, Matrix4f modelView, Matrix3f normalMat, Vector3f cameraSpaceOrigin, boolean cull, Runnable preDraw, Runnable postDraw)
        {
            super(cameraSpaceOrigin, cull, depthWrite);

            this.netz = netz;
            this.shader = shader;
            this.passMode = passMode;
            this.texture = texture;
            this.modelView = modelView;
            this.normalMat = normalMat;
            this.preDraw = preDraw;
            this.postDraw = postDraw;
        }

        @Override
        public void draw()
        {
            RenderPipeline pipeline = this.shader.get();

            /* Die gewaehlte Pipeline traegt Tiefenschreiben und Cull dieses Befehls. Nur
             * fuer BBS' eigene Modell-Pipelines gibt es die Varianten; eine fremde
             * Pipeline (etwa eine Vanilla-Ebene) bringt ihren Zustand selbst mit. */
            if (ModelVAOPipeline.istModellPipeline(pipeline))
            {
                pipeline = ModelVAOPipeline.waehlenVerschraenkt(this.cull, this.depthWrite);
            }

            if (this.preDraw != null)
            {
                this.preDraw.run();
            }

            GpuBuffer block = ModelVAORenderer.blockFuer(
                this.normalMat != null ? this.normalMat : new Matrix3f(),
                net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                this.passMode, 0);

            this.netz.zeichnen(pipeline, this.modelView, null,
                this.texture == null ? null : SofortZeichner.entityTextur(this.texture), null,
                (durchgang) -> durchgang.setUniform(ModelVAOPipeline.MODELL_BLOCK, block));

            if (this.postDraw != null)
            {
                this.postDraw.run();
            }
        }

        @Override
        public void release()
        {
            this.netz.close();
        }
    }
}
