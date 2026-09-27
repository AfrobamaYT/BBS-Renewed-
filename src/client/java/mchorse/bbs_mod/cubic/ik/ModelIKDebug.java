package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.DebugOverlay;
import mchorse.bbs_mod.cubic.render.RigWidgets;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal IK overlay: each chain is a clean run of wires through joint
 * markers, the effector picked out in a cool accent, and the target bridged to
 * the chain's tip by a dashed relationship line. Every part's look —
 * visibility, colour, size, shape, line thickness, dashing, opacity, drawing
 * through the model — comes from {@link ValueIKDebug}
 * ({@code BBSSettings.ikDebug}), which also holds the overlay's toggle. It
 * reads the model-local pivot frames the renderer already produced — IK is
 * applied to the rig before this runs, so the frames are the solved chain
 * (re-solving would double-apply the pole angle).
 *
 * <p>{@link #renderStencil} mirrors the goal markers into the picking pass so a
 * click on a target selects its bone, exactly as if its (often mesh-less)
 * bone had been clicked directly. The pick geometry is the same shape and size
 * as the visible marker — no oversized hitboxes in the hover highlight, and
 * hidden markers are not pickable at all.
 */
public final class ModelIKDebug
{
    /** Der im Editor gepickte Bone (nur der Name): dessen Widget traegt Blenders
     *  Active-Farbe. Namensvergleich — gleichnamige Ziele in verschachtelten
     *  Model-Forms desselben Frames highlighten doppelt (kosmetischer Rand). */
    private static String selectedBone;

    private ModelIKDebug()
    {
    }

    /** Je Frame vom Film-Controller gesetzt. ⚠️ KEIN Pfad-Zerschneiden an '.': Bone-Namen
     *  selbst tragen Punkte (mixamo/Blender-Rigs: "IK.hand.R") — nur exakter Vergleich. */
    public static void setSelection(String bone)
    {
        selectedBone = bone == null || bone.isEmpty() ? null : bone;
    }

    /**
     * Editor-Pass: die sichtbaren Rig-Widgets des selektierten Replays, gezeichnet im
     * Film-Weltpass (neben MotionPath) — NICHT im Form-Renderer: der laeuft je Rufer in
     * Miniatur-Ziele, und sein Welt-Zweig feuerte nicht verlaesslich (Messrest 2026-08-13).
     * Welt-Matrix je Ziel-Bone ueber {@code getBoneCompositeMatrix}, kamera-relativ, ein
     * TRIANGLES-Blend-Zug ins Weltbild.
     */
    public static void renderEditor(mchorse.bbs_mod.ui.film.controller.UIFilmController controller, net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context)
    {
        /* COLLECT_SUBMITS only queues shadows/text. Immediate control draws there
         * are discarded before the visible main pass, which calls us again. */
        if (mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang)
        {
            return;
        }

        ValueIKDebug config = BBSSettings.ikDebug;
        boolean targets = config.target.visible.get();
        boolean poles = config.pole.visible.get();

        if (!config.enabled.get() || (!targets && !poles))
        {
            return;
        }

        mchorse.bbs_mod.forms.entities.IEntity entity = controller.getCurrentEntity();
        mchorse.bbs_mod.film.replays.Replay replay = controller.panel.replayEditor.getReplay();

        if (entity == null || replay == null
            || !(entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            || !(form.ik.get() instanceof MapType map)
            || !(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form) instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer))
        {
            return;
        }

        mchorse.bbs_mod.cubic.ModelInstance instance = renderer.getModel();

        if (instance == null || instance.model == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(instance.model, map);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        net.minecraft.world.phys.Vec3 kamera = context.levelState().cameraRenderState.pos;
        Matrix4f sicht = modellansicht();

        /* Anzeige-Breite der Baender: das Renderziel (Video) wird aufs Vorschaufeld
         * verkleinert — ohne die Skala schrumpfen die Linien unter 1px (gemessen:
         * 67 Fragment-Pixel am ganzen Rig). */
        float feld = controller.panel.preview.getViewport().w * mchorse.bbs_mod.BBSModClient.getGUIScale();

        RigWidgets.setBreitenSkala(feld <= 0F ? 1F
            : mchorse.bbs_mod.client.BBSRendering.getVideoWidth() / feld);
        float a = config.opacity.get();
        boolean tiefe = !config.xray.get();
        var entities = controller.getEntities();

        /* Mixamo-Skelett mit unseren IK-Bones -> das 1:1 extrahierte Blender-Control-Rig
         * ersetzt Octas UND Klotz-Widgets (Blender zeigt DEF/MCH auch nicht). */
        java.util.List<RigCtrl.Eintrag> rig = instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bm
            ? RigCtrl.fuer(bm.getArmature(), form.model.get()) : null;

        {
            ByteBufferBuilder speicher = mchorse.bbs_mod.cubic.render.ControlVertexBatch.speicher(0);
            BufferBuilder wire = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            float hidden = config.hiddenOpacity.get();

            if (rig != null)
            {
                /* Zwischenzeit wie in FilmBoneWorldProvider: beim laufenden Film die
                 * echte, sonst 0 — sonst zappeln die Widgets im Standbild. */
                float uebergang = controller.panel.getRunner().isRunning()
                    && controller.panel.replayEditor.getContext() != null
                        ? controller.panel.replayEditor.getContext().getTransition() : 0F;

                /* Der Editor-Standard (Unity occluded gizmos, Unreals Control Rig):
                 * sichtbare Baender voll, verdeckte gedimmt — auffindbar hinter dem
                 * Koerper, ohne ihn zu ueberdecken. xray=true bleibt der alte
                 * Voll-Durchschein-Look, dann braucht es keinen zweiten Pass. */
                /* Only this locally created POSITION_COLOR buffer enters the bulk scope. */
                try (mchorse.bbs_mod.cubic.render.ControlVertexBatch batch =
                    mchorse.bbs_mod.cubic.render.ControlVertexBatch.begin(wire))
                {
                    mchorse.bbs_mod.cubic.render.RigCtrlZeichner.weltPass(wire, sicht, kamera,
                        entities, entity, replay, rig, selectedBone, a, uebergang);
                }
            }

            /* FK-Skelett (Blenders Pose Mode): JEDER Bone als Octahedron — grau,
             * Auswahl cyan. Die IK-Ziel/Pol-Bones tragen stattdessen ihre Widgets. */
            if (rig == null && instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj)
            {
                Set<String> ikBones = new HashSet<>();

                for (ModelIKCache.CompiledChain chain : compiled.chains())
                {
                    ikBones.add(chain.target());

                    if (chain.poleTarget() != null)
                    {
                        ikBones.add(chain.poleTarget());
                    }
                }

                Map<String, String> erstesKind = new HashMap<>();

                for (mchorse.bbs_mod.bobj.BOBJBone bone : bobj.getArmature().orderedBones)
                {
                    if (bone.parent != null && !erstesKind.containsKey(bone.parent))
                    {
                        erstesKind.put(bone.parent, bone.name);
                    }
                }

                for (mchorse.bbs_mod.bobj.BOBJBone bone : bobj.getArmature().orderedBones)
                {
                    if (ikBones.contains(bone.name))
                    {
                        continue;
                    }

                    Matrix4f kopf = welt(entities, entity, replay, bone.name);

                    if (kopf == null)
                    {
                        continue;
                    }

                    Vector3f kopfPos = kopf.getTranslation(new Vector3f());
                    String kind = erstesKind.get(bone.name);
                    float laenge = 0.12F;
                    Vector3f richtung = null;

                    if (kind != null && !ikBones.contains(kind))
                    {
                        Matrix4f kindWelt = welt(entities, entity, replay, kind);

                        if (kindWelt != null)
                        {
                            richtung = kindWelt.getTranslation(new Vector3f()).sub(kopfPos);
                            laenge = Math.max(0.03F, richtung.length());
                        }
                    }

                    boolean sel = bone.name.equals(selectedBone);

                    /* Octa-Y muss zum KIND zeigen (Blenders head->tail). Die BBS/Mixamo-
                     * Bone-Matrizen tragen ihr Y NICHT entlang des Knochens — mit ihnen
                     * ragten alle Octas als Stacheln nach Welt-oben (gemessen am T-Pose-
                     * Arm). Also die Rotation explizit aus der Kopf->Kind-Richtung. */
                    Matrix4f ausgerichtet;

                    if (richtung != null && richtung.lengthSquared() > 1.0E-8F)
                    {
                        ausgerichtet = new Matrix4f().rotation(new org.joml.Quaternionf()
                            .rotationTo(0F, 1F, 0F, richtung.x, richtung.y, richtung.z));
                    }
                    else
                    {
                        ausgerichtet = new Matrix4f(kopf).normalize3x3().setTranslation(0F, 0F, 0F);
                    }

                    Matrix4f kombi = new Matrix4f(sicht)
                        .translate((float) (kopf.m30() - kamera.x),
                            (float) (kopf.m31() - kamera.y),
                            (float) (kopf.m32() - kamera.z))
                        .mul(ausgerichtet);

                    /* Blender-Eindruck: matter grauer Knochenkoerper; die 12-Kanten-Outline
                     * nur am GEWAEHLTEN Bone (cyan) — an duennen Bones uebermalen dicke
                     * Kantenbaender sonst den ganzen Koerper schwarz (gemessen). */
                    RigWidgets.emitOctaSolid(wire, kombi, laenge, sel ? 0.95F : 0.72F, a);

                    if (sel)
                    {
                        RigWidgets.emitOcta(wire, kombi, laenge, DebugOverlay.rgb(RigWidgets.POSE_AKTIV), a);
                    }
                }
            }

            for (ModelIKCache.CompiledChain chain : compiled.chains())
            {
                if (rig != null)
                {
                    break;
                }

                List<String> glieder = chain.chainRootToEffector();

                if (glieder == null || glieder.size() < 2)
                {
                    continue;
                }

                /* Widget-Grundmass: die Welt-Segmentlaenge der Kette. */
                Matrix4f wurzel = welt(entities, entity, replay, glieder.get(0));
                Matrix4f spitze = welt(entities, entity, replay, glieder.get(glieder.size() - 1));

                if (wurzel == null || spitze == null)
                {
                    continue;
                }

                float unit = wurzel.getTranslation(new Vector3f())
                    .distance(spitze.getTranslation(new Vector3f())) / (glieder.size() - 1);

                if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
                {
                    mchorse.bbs_mod.graphics.Messpunkt.zaehle("ikdbg.editor",
                        chain.target() + " unit=" + unit
                            + " tWelt=" + (welt(entities, entity, replay, chain.target()) != null)
                            + " tPos=" + welt(entities, entity, replay, chain.target()).getTranslation(new Vector3f()));
                }

                if (targets)
                {
                    zeichneWidget(wire, sicht, kamera, welt(entities, entity, replay, chain.target()),
                        rollenShape(chain.target(), config.target.shape.get()),
                        unit * config.target.size.get(), config.target.color.get(),
                        chain.target().equals(selectedBone), a);
                }

                if (poles && chain.poleTarget() != null && !chain.poleTarget().isEmpty())
                {
                    zeichneWidget(wire, sicht, kamera, welt(entities, entity, replay, chain.poleTarget()),
                        config.pole.shape.get(),
                        unit * config.pole.size.get() * 1.3F, config.pole.color.get(),
                        chain.poleTarget().equals(selectedBone), a);
                }
            }

            /* Verdeckt ZUERST: die Fragmentmengen sind disjunkt (LESS gegen GEQUAL),
             * aber so liegt der gedimmte Anteil sicher unter dem vollen, falls eine
             * Kante doch beide trifft.
             *
             * ⛔ VORAUSSETZUNG IST DIE REIHENFOLGE IN BBSRendering.renderCoolStuff:
             * Figuren vor den Overlays. Andersherum ist beim Widget-Zeichnen die
             * Modell-Tiefe noch nicht im Puffer — der Sichtbar-Pass besteht auch
             * "hinter" dem Koerper, dieser Pass findet dort nichts, und weil die
             * Baender selbst Tiefe schreiben, malt die spaetere Figur nicht mehr
             * drueber: de facto X-Ray, egal was eingestellt ist. Gemessen 2026-08-16:
             * hidden_opacity 0.0/0.35/1.0 lieferten bit-gleiche Bilder. */
            var mesh = wire.build();

            if (mesh == null)
            {
                return;
            }

            try (GehaltenesNetz shared = GehaltenesNetz.kurzlebig(mesh))
            {
                Matrix4f identity = new Matrix4f();

                if (rig != null && tiefe && hidden > 0F)
                {
                    /* position_color.fsh multiplies the vertex alpha by ColorModulator.
                     * Match the old byte-quantized hidden vertex alpha without building
                     * and uploading the identical control geometry a second time. */
                    int visibleAlpha = (int) (a * 255F) & 255;
                    int hiddenAlpha = (int) (a * hidden * 255F) & 255;
                    float ratio = visibleAlpha == 0 ? 0F : hiddenAlpha / (float) visibleAlpha;
                    shared.zeichnen(FormPipelines.hinterGeometrie(PrimitiveTopology.TRIANGLES),
                        identity, new Vector4f(1F, 1F, 1F, ratio), null, null, null);
                }

                shared.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, tiefe, false),
                    identity, null, null, null, null);
            }
        }
    }

    private static Matrix4f welt(io.netty.util.collection.IntObjectMap<mchorse.bbs_mod.forms.entities.IEntity> entities,
        mchorse.bbs_mod.forms.entities.IEntity entity, mchorse.bbs_mod.film.replays.Replay replay, String bone)
    {
        return mchorse.bbs_mod.film.BaseFilmController.getBoneCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, bone, false);
    }

    private static void zeichneWidget(BufferBuilder wire, Matrix4f sicht, net.minecraft.world.phys.Vec3 kamera,
        Matrix4f welt, int shape, float size, int farbe, boolean selektiert, float a)
    {
        if (welt == null)
        {
            return;
        }

        if (shape == mchorse.bbs_mod.settings.values.ui.ValueDebugElement.SHAPE_WIRE_CIRCLE)
        {
            size *= 2.5F;
        }
        else if (shape == mchorse.bbs_mod.settings.values.ui.ValueDebugElement.SHAPE_WIRE_CUBE)
        {
            size *= 1.6F;
        }

        /* normalize3x3: die Welt-Matrix traegt die MODELL-Skalierung (Mixamo-Armaturen:
         * 0.01, cm->m) — ungefiltert schrumpfen die Widgets auf Sprenkel (gemessen). Die
         * Widget-Groesse kommt allein aus size; die Matrix liefert nur Lage+Richtung. */
        Matrix4f kombi = new Matrix4f(sicht).mul(new Matrix4f(welt).normalize3x3().setTranslation(
            (float) (welt.m30() - kamera.x),
            (float) (welt.m31() - kamera.y),
            (float) (welt.m32() - kamera.z)));

        RigWidgets.emit(wire, kombi, shape, size, RigWidgets.wireColor(farbe, selektiert), a);
    }

    public static void render(PoseStack stack, IModel model, MapType ikData, String selectedTip, String modellId)
    {
        ValueIKDebug config = BBSSettings.ikDebug;

        if (!config.enabled.get() || model == null || ikData == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        /* Control-Rig-Modelle: Blender zeigt in Vorschauen KEIN Rig — und dieser
         * Legacy-Pfad laeuft je Miniatur (3x/Frame gemessen) und malt ins Rufer-Ziel. */
        if (model instanceof BOBJModel bm && RigCtrl.fuer(bm.getArmature(), modellId) != null)
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("ikdbg.render", "chains=" + compiled.chains().size() + " frames=" + frames.size() + " von=" + mchorse.bbs_mod.graphics.Messpunkt.rufer(4));
        }

        /* 26.2: Blend/Tiefe/Cull sind keine globalen Schalter mehr — jeder Zug in
         * drawChain waehlt seine Pipeline selbst (ohne Cull, mit Blend, Tiefe nur
         * ohne Roentgen); deshalb hier auch kein Zustands-Aufraeumen mehr. */
        stack.pushPose();

        if (model instanceof BOBJModel)
        {
            stack.mulPose(Axis.YP.rotation(MathUtils.PI));
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, frames, chain, selectedTip, config);
        }

        stack.popPose();
    }

    static Map<String, PivotFrame> collectFrames(IModel model, ModelIKCache.Compiled compiled)
    {
        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        return frames;
    }

    /** The chain's average segment length from the solved positions, the same scale the visual pass draws with. */
    static float chainUnit(Map<String, PivotFrame> frames, List<String> ids)
    {
        float total = 0F;
        int segments = 0;
        Vector3f prev = null;

        for (String id : ids)
        {
            Vector3f p = position(frames, id);

            if (p == null)
            {
                prev = null;

                continue;
            }

            if (prev != null)
            {
                total += prev.distance(p);
                segments++;
            }

            prev = p;
        }

        return segments > 0 ? total / segments : 0.5F;
    }

    private static void drawChain(PoseStack stack, Map<String, PivotFrame> frames, ModelIKCache.CompiledChain chain, String selectedTip, ValueIKDebug config)
    {
        List<String> ids = chain.chainRootToEffector();
        int n = ids.size();

        if (n < 2)
        {
            return;
        }

        List<Vector3f> pts = new ArrayList<>(n);

        for (int i = 0; i < n; i++)
        {
            Vector3f p = position(frames, ids.get(i));

            if (p == null)
            {
                return;
            }

            pts.add(p);
        }

        Vector3f target = position(frames, chain.target());

        if (target == null)
        {
            return;
        }

        Vector3f pole = chain.poleTarget() == null || chain.poleTarget().isEmpty() ? null : position(frames, chain.poleTarget());
        Vector3f tip = pts.get(n - 1);

        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            total += pts.get(i).distance(pts.get(i + 1));
        }

        float unit = total / (n - 1);
        boolean sel = selectedTip == null || selectedTip.isEmpty() || chain.tip().equals(selectedTip);
        float a = (sel ? 1F : 0.4F) * config.opacity.get();

        boolean joints = config.joints.visible.get();
        boolean tipDot = config.tip.visible.get();
        boolean targetDot = config.target.visible.get();
        boolean poleDot = pole != null && config.pole.visible.get();

        /* Blender-Widgets zweigen aus dem Marker-Weg ab: Kurven statt Koerper. */
        boolean targetWire = targetDot && RigWidgets.isWire(config.target.shape.get());
        boolean poleWire = poleDot && RigWidgets.isWire(config.pole.shape.get());

        boolean anyLine = config.lines.visible.get() || targetDot || poleDot;
        float thickness = unit * config.lines.size.get();
        boolean boxes = anyLine && thickness > 0F;
        boolean anyDot = joints || tipDot || (targetDot && !targetWire) || (poleDot && !poleWire);

        Matrix4f matrix = stack.last().pose();
        float dash = unit * 0.12F;

        /* Der Zustand, den render() frueher global setzte, gehoert in 26.2 zum Zug: mit
         *  Blend (config.opacity), ohne Cull (sonst fehlen Marker-Flaechen), Tiefe nur
         *  ausserhalb der Roentgen-Option. */
        boolean tiefe = !config.xray.get();

        /* Lines: hairline GL lines by default, boxes once a thickness is set. */
        if (anyLine && !boxes)
        {
            try (ByteBufferBuilder speicher = new ByteBufferBuilder(1024))
            {
                BufferBuilder lines = SofortZeichner.beginnen(speicher, PrimitiveTopology.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

                emitLines(lines, matrix, 0F, dash, pts, target, pole, a, config);

                SofortZeichner.zeichnen(
                    FormPipelines.holen(PrimitiveTopology.DEBUG_LINES, true, tiefe, false),
                    lines.build(),
                    modellansicht(),
                    null,
                    null);
            }
        }

        /* Blenders Rig-Widgets: weiche, bildschirm-konstante Kurven-Bänder in der gelösten
         * Bone-Orientierung. Farbwahl ist die Wire-Regel aus overlay_armature.cc —
         * selektiert traegt die Active-Farbe des Sets, unselektiert Solid −50. Die Ecken
         * entstehen im KAMERA-Raum (Ansicht x PoseStack), gezeichnet mit Identitäts-
         * Modellansicht — die Bänder richten sich dort am Blickstrahl aus. */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("ikdbg.chain", chain.tip() + " n=" + n + " tw=" + targetWire + " pw=" + poleWire + " a=" + a + " tFrame=" + (frames.get(chain.target()) != null));
        }

        if (targetWire || poleWire)
        {
            Matrix4f kameraRaum = modellansicht().mul(matrix);

            try (ByteBufferBuilder speicher = new ByteBufferBuilder(65536))
            {
                BufferBuilder wire = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

                if (targetWire)
                {
                    float[] col = RigWidgets.wireColor(config.target.color.get(), chain.target().equals(selectedBone));
                    int shape = rollenShape(chain.target(), config.target.shape.get());

                    /* DCC-Groessen: der Fussring UMSCHLIESST den Fuss, der Handwuerfel ist
                     * greifbar — unit allein macht Widgets an kurzgliedrigen Rigs (Mixamo)
                     * unsichtbar klein. */
                    float faktor = shape == mchorse.bbs_mod.settings.values.ui.ValueDebugElement.SHAPE_WIRE_CIRCLE ? 2.5F : 1.6F;

                    RigWidgets.emit(wire, kameraRaum, shape, frames.get(chain.target()), unit * config.target.size.get() * faktor, col, a);
                }

                if (poleWire)
                {
                    float[] col = RigWidgets.wireColor(config.pole.color.get(), chain.poleTarget().equals(selectedBone));

                    RigWidgets.emit(wire, kameraRaum, config.pole.shape.get(), frames.get(chain.poleTarget()), unit * config.pole.size.get() * 1.3F, col, a);
                }

                SofortZeichner.zeichnen(
                    FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, tiefe, false),
                    wire.build(),
                    new Matrix4f(),
                    null,
                    null);
            }
        }

        if (!anyDot && !boxes)
        {
            return;
        }

        /* Solid geometry: joint/accent markers, plus the thick lines. */
        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4096))
        {
            BufferBuilder dots = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            if (boxes)
            {
                emitLines(dots, matrix, thickness, dash, pts, target, pole, a, config);
            }

            if (joints)
            {
                float[] color = DebugOverlay.rgb(config.joints.color.get());
                float radius = unit * config.joints.size.get();
                int shape = config.joints.shape.get();

                for (int i = 0; i < n - 1; i++)
                {
                    DebugOverlay.marker(dots, stack, shape, pts.get(i), radius, color, a);
                }
            }

            if (tipDot)
            {
                DebugOverlay.marker(dots, stack, config.tip.shape.get(), tip, unit * config.tip.size.get(), DebugOverlay.rgb(config.tip.color.get()), a);
            }

            if (targetDot && !targetWire)
            {
                DebugOverlay.marker(dots, stack, config.target.shape.get(), target, unit * config.target.size.get(), DebugOverlay.rgb(config.target.color.get()), a);
            }

            if (poleDot && !poleWire)
            {
                DebugOverlay.marker(dots, stack, config.pole.shape.get(), pole, unit * config.pole.size.get(), DebugOverlay.rgb(config.pole.color.get()), a);
            }

            SofortZeichner.zeichnen(
                FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, tiefe, false),
                dots.build(),
                modellansicht(),
                null,
                null);
        }
    }

    /**
     * Die Modellansicht fuer die Zuege dieses Overlays.
     *
     * <p>⚠️ NICHT {@code ModelVAORenderer.captureModelView(stack)} — das ist die Formel fuer
     * Netze mit ROHEN Modellecken. Hier sind die Ecken schon durch die Pose gelaufen:
     * {@code DebugOverlay.segment/line} schreiben mit {@code addVertex(matrix, ...)}, und
     * {@code DebugOverlay.marker} baut ueber den PoseStack. Was fehlt, ist genau der Teil,
     * den frueher {@code BufferUploader.drawWithShader} beisteuerte — die globale
     * Modellansicht der Kamera. Wer hier die Pose noch einmal dazumultipliziert, zeichnet
     * das Overlay ein zweites Mal transformiert an eine falsche Stelle.
     */
    static Matrix4f modellansicht()
    {
        /* Gleiche Schreibweise wie ModelVAORenderer.captureModelView — nur ohne die Pose,
         *  die hier schon in den Ecken steckt. */
        return new Matrix4f(RenderSystem.getModelViewMatrixCopy());
    }

    /** The chain's wires plus the bridges to the goal and the pole — the bridges are always dashed relationship lines. */
    private static void emitLines(BufferBuilder builder, Matrix4f matrix, float thickness, float dash, List<Vector3f> pts, Vector3f target, Vector3f pole, float a, ValueIKDebug config)
    {
        if (config.lines.visible.get())
        {
            float[] wire = DebugOverlay.rgb(config.lines.color.get());
            boolean dashed = config.dashed.get();

            for (int i = 0; i < pts.size() - 1; i++)
            {
                DebugOverlay.segment(builder, matrix, thickness, dashed, dash, pts.get(i), pts.get(i + 1), wire, 0.9F * a);
            }
        }

        if (config.target.visible.get())
        {
            DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(pts.size() - 1), target, DebugOverlay.rgb(config.target.color.get()), 0.4F * a);
        }

        if (pole != null && config.pole.visible.get())
        {
            DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(1), pole, DebugOverlay.rgb(config.pole.color.get()), 0.4F * a);
        }
    }

    static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }

    /** Rigify-Konvention automatisch: Fuss-Ziele als flacher Ring (der Bodenkreis der
     *  DCC-Rigs), Haende behalten den Wuerfel. Greift nur auf dem Wire-Wuerfel-Standard —
     *  eine ausdrueckliche Formwahl im Menue bleibt unangetastet. */
    private static int rollenShape(String bone, int standard)
    {
        if (standard == mchorse.bbs_mod.settings.values.ui.ValueDebugElement.SHAPE_WIRE_CUBE && bone != null)
        {
            String name = bone.toLowerCase(java.util.Locale.ROOT);

            if (name.contains("foot") || name.contains("fuss") || name.contains("fuß"))
            {
                return mchorse.bbs_mod.settings.values.ui.ValueDebugElement.SHAPE_WIRE_CIRCLE;
            }
        }

        return standard;
    }
}
