// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export/bake, task 4.1/4.3b).
package com.blenderbridge.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import com.blenderbridge.mixin.ModelPartChildrenAccessor;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * EntityExporter — plan v2 task 4.1: export ONE living entity's model as a standalone OBJ/MTL/PNG
 * set Blender can import, using the SAME "ask the real renderer" philosophy as
 * {@code world.WorldExporter}/{@code world.StreamingWorldExporter} (Phase 2/3): we do not
 * re-implement Minecraft's cuboid geometry or pose math by hand, we drive the actual client-side
 * model classes and capture what they hand a {@link VertexConsumer}.
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS</h2>
 * <p>Same deploy-model reasoning as {@code StreamingWorldExporter}'s class doc: this JVM is
 * currently W1+ (restarts forbidden except crash/freeze/system — project hard rule), and on this
 * JVM {@code canAddMethod=false} even for already-loaded classes (measured, task 0.2). So this is a
 * brand-new class (lazy-loadable, no boot needed) rather than an addition to any existing one.</p>
 *
 * <h2>GEOMETRY — {@code ModelPart.forEachCuboid} + a capturing {@link VertexConsumer}</h2>
 * <p>Research fundament (plan v2 phase 4, doubly code-verified against mc-world-export AND Figura):
 * {@code ModelPart.forEachCuboid(MatrixStack, CuboidConsumer)} is PUBLIC and walks the ENTIRE part
 * tree (own cuboids, then every child, recursively), handing each cuboid's already-pose-transformed
 * vertices to whatever {@link VertexConsumer} {@link ModelPart.Cube#compile} is pointed at —
 * exactly the same capture trick {@code world.ObjVertexConsumer} already uses for fluids. VERIFIED
 * by decompiling the 1.21.1+build.3 bytecode (this project's yarn mappings, see the entity/ package
 * dossier notes): {@code forEachCuboid} builds each cuboid's callback {@code path} argument as
 * {@code parentPath + "/" + childName} recursing from the root, whose own path is {@code ""} — so
 * {@code path} IS the owning {@link ModelPart}'s dotted/slashed identity, letting us match a
 * captured cuboid back to the part it came from WITHOUT re-deriving that mapping by hand.</p>
 *
 * <h2>VISIBILITY GATE (task 4.1's "geometrie-gate")</h2>
 * <p>Bytecode-verified: {@code forEachCuboid} does NOT consult {@link ModelPart#visible} — it walks
 * every cuboid in the tree regardless. Vanilla's own {@code ModelPart.render} DOES check it (skips a
 * part's own cuboids when {@code visible==false} but still recurses into its children). We replicate
 * that ourselves: {@link #walkTree} independently records {@code visible} per path (using the SAME
 * path convention as {@code forEachCuboid}, proven above), and the capture callback drops any cuboid
 * whose {@code path} maps to a part we did not mark visible. This is what lets the "exported part
 * count == visible ModelPart count" proof gate hold BY CONSTRUCTION: every visible part gets exactly
 * one OBJ {@code g} group (even if it happens to own zero cuboids of its own), every invisible one
 * gets none.</p>
 *
 * <h2>{@code ModelPart.children} — WIEDER PRIVAT, DER MIXIN-ACCESSOR IST DER WEG (port 26.2)</h2>
 * <p>Bis zum port las diese klasse {@code part.children} direkt: {@code
 * src/main/resources/bbs.accesswidener} trug {@code accessible field
 * net/minecraft/client/model/geom/ModelPart children Ljava/util/Map;} und machte das feld public.
 * Im 26.2-baum ist der access-widener STILLGELEGT ({@code build.gradle} zeile 61 auskommentiert,
 * kommentar "P3-26.2-AUS" — 26.2 will das neue {@code classTweaker}-format), also ist {@code
 * children} wieder {@code private} (mit {@code javap -p} am 26.2-jar geprüft). Deshalb geht der
 * zugriff jetzt über {@link ModelPartChildrenAccessor} — genau den fallback, für den dieser
 * accessor-mixin im baum liegt. Der doppel-cast {@code (Accessor) (Object) part} ist pflicht:
 * {@code ModelPart} ist {@code final}, ein direkter cast auf ein fremdes interface wäre ein
 * compile-fehler.</p>
 *
 * <p>⚠ Der accessor wirkt erst, wenn der mixin GEWOBEN ist (eintrag in {@code
 * blenderbridge.mixins.json}). Ist er es nicht, fliegt hier ein {@code ClassCastException} —
 * laut, nicht still. Zweiter, sauberer weg: den access-widener im 26.2-format reaktivieren,
 * dann kann diese eine methode wieder auf das feld zeigen.</p>
 *
 * <h2>POSE — der render-state des spiels, nicht selbst gerechnete animation (port 26.2)</h2>
 * <p>{@link #poseEntity} rief bis 1.21.1 die einzelnen model-felder direkt an ({@code riding},
 * {@code young}, {@code attackTime}, {@code prepareMobModel}, {@code setupAnim} mit sechs
 * argumenten). 26.2 hat die entity-darstellung auf RENDER-STATES umgestellt: die felder sind weg,
 * {@code EntityModel.setupAnim} nimmt nur noch EIN argument, den render-state. Der wird vom spiel
 * selbst gefüllt — {@code EntityRenderer.createRenderState(entity, partialTick)} ist {@code public
 * final} und ruft intern {@code extractRenderState}, das genau die werte einsammelt, die der alte
 * code von hand aus der entity zog (walk-animation, body/head-yaw, pitch, alter, angriffs-fortschritt,
 * baby-flag, passagier-flag). Wir rechnen also weiterhin NICHTS selbst nach, wir fragen nur eine
 * ebene höher. {@code tickDelta=1.0F} bleibt (ein voller, gesetzter tick — dies ist ein
 * per-tick-sampler, kein per-frame-sampler). Task 4.1 ruft zusätzlich {@link #resetBindPose} vorher.</p>
 *
 * <h2>TEXTURE — full layer-stack composite (villager task, 2026-07-17 — supersedes v1 base-only scope)</h2>
 * <p>{@code EntityRenderer.getTexture(entity)} is public and returns a full resource path directly
 * — unlike block sprites (see {@code WorldExporter}), entity textures are NOT atlas-packed, so no
 * "textures/"+path+".png" reconstruction is needed and cuboid UVs are already 0..1 in THIS texture's
 * own space. <b>CORRECTION to this section's original v1 claim</b> (decompiled ground truth, see
 * {@link VillagerTextureLayers}'s class doc): for {@code VillagerEntityRenderer} specifically,
 * {@code getTexture} is a HARDCODED CONSTANT ({@code textures/entity/villager/villager.png}), NOT
 * biome/type-specific — the biome-specific look and the profession/level clothing are a SEPARATE,
 * always-or-conditionally active texture LAYER STACK
 * ({@code textures/entity/villager/{type,profession,profession_level}/...}, panel H34) drawn on
 * top by {@code VillagerClothingFeatureRenderer}. The v1 grundgerüst exported the base layer alone,
 * which was therefore incomplete for EVERY villager (not just employed ones). {@link #doExport}
 * now resolves the full active layer set via {@link VillagerTextureLayers#resolveAndComposite} and
 * bakes it into ONE alpha-composited {@code base.png} (see that class's doc for why compositing,
 * not separate files, is the correct semantic match here) — the OBJ/MTL/UV contract, output paths,
 * and JSON field names are all UNCHANGED, only what gets written into {@code textures/base.png}
 * changed, so nothing downstream (there is no addon consumer yet) needs updating.</p>
 *
 * <h2>FALLBACK</h2>
 * <p>Unterstützt wird, was einen {@link LivingEntityRenderer} mit einem {@link EntityModel} hat.
 * PORT 26.2: {@code HierarchicalModel} (das interface mit dem einen {@code getPart()}-root) gibt
 * es nicht mehr — der wurzel-part hängt jetzt an der basisklasse selbst: {@code Model.root()} ist
 * {@code public final} (am jar geprüft), also hat JEDES entity-model einen root. Die alte
 * "nur SinglePartEntityModel"-schranke ist damit ersatzlos gefallen, nicht heimlich erweitert:
 * derselbe wurzel-part, nur ohne den zwischen-typ. Alles ohne {@code LivingEntityRenderer} kommt
 * weiterhin als sauberes {@code ok:false} zurück, nie als crash oder stiller leer-export.</p>
 */
public final class EntityExporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cuboid faces are always quads — same convention as every other exporter in this package. */
    private static final int QUAD_VERTICES = 4;

    /** Output subdirectory, relative to the game's run directory (mirrors WorldExportBridge.DEFAULT_SUBDIR). */
    private static final String DEFAULT_SUBDIR = "blenderbridge_export";

    private EntityExporter()
    {
        /* static-only helper */
    }

    /* ==================== public entry point ==================== */

    /**
     * Export one entity's model as {@code <runDir>/blenderbridge_export/entity_<type>/entity.obj}
     * (+ .mtl + textures/base.png), in BIND POSE (every {@link ModelPart#resetPose()}).
     *
     * @param uuidOrNull entity UUID string (as returned by {@code Entity.getUuidAsString()}), or
     *                   null/blank to pick the nearest {@link Villager} to the player
     * @return a well-formed result object; never null, never throws. Check {@code ok}.
     */
    public static JsonObject export(String uuidOrNull)
    {
        try
        {
            return doExport(uuidOrNull);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] entity export crashed", t);
            return failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static JsonObject doExport(String uuidOrNull) throws IOException
    {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.level == null)
        {
            return failure("no world loaded (join a world before exporting)");
        }

        LivingEntity entity = resolveEntity(client, uuidOrNull);

        if (entity == null)
        {
            return failure(uuidOrNull == null || uuidOrNull.isBlank()
                ? "no villager found near the player"
                : "entity not found for uuid " + uuidOrNull);
        }

        ModelPart root = resolveRootPart(client, entity);

        if (root == null)
        {
            return failure("unsupported model shape for entity type " + entity.getType()
                + " (this grundgerüst only supports SinglePartEntityModel — no fallback empty-export built yet)");
        }

        resetBindPose(root);

        Map<String, PartInfo> parts = walkTree(root);
        int visiblePartCount = 0;

        for (PartInfo info : parts.values())
        {
            if (info.visible)
            {
                visiblePartCount++;
            }
        }

        /* Pre-seed a bucket per VISIBLE part, in tree order, so every one of them gets its own OBJ
         * "g" group even if it turns out to own zero cuboids — this is what makes the "exported
         * group count == visible ModelPart count" proof gate hold by construction (class doc). */
        LinkedHashMap<String, List<CapturedQuad>> byPart = new LinkedHashMap<>();

        for (Map.Entry<String, PartInfo> entry : parts.entrySet())
        {
            if (entry.getValue().visible)
            {
                byPart.put(entry.getKey(), new ArrayList<>());
            }
        }

        PoseStack matrixStack = new PoseStack();

        root.visit(matrixStack, (matrixEntry, path, index, cuboid) ->
        {
            List<CapturedQuad> bucket = byPart.get(path);

            if (bucket == null)
            {
                /* Either an invisible part (gate) or a path we failed to see in walkTree (shouldn't
                 * happen — same tree, same convention) — either way, dropping beats guessing. */
                return;
            }

            EntityQuadCapture capture = new EntityQuadCapture();
            /* PORT 26.2: LightTexture ist weg, die pack/entpack-mathematik der lichtkoordinaten
             * sitzt jetzt in net.minecraft.util.LightCoordsUtil — FULL_BRIGHT dort, gleicher wert. */
            cuboid.compile(matrixEntry, capture, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            bucket.addAll(capture.finish());
        });

        String typeName = safeName(EntityType.getKey(entity.getType()));
        File runDir = client.gameDirectory;

        if (runDir == null)
        {
            return failure("no run directory (client not ready)");
        }

        File outDir = new File(new File(runDir, DEFAULT_SUBDIR), "entity_" + typeName);
        File texDir = new File(outDir, "textures");

        if (!texDir.exists() && !texDir.mkdirs())
        {
            return failure("could not create texture directory: " + texDir.getAbsolutePath());
        }

        File objFile = new File(outDir, "entity.obj");
        File mtlFile = new File(outDir, "entity.mtl");

        /* Task: villager texture layer-stack (2026-07-17). Base texture id is still resolved the
         * same way (resolveTexture, unchanged below); VillagerTextureLayers now takes it from
         * there — resolves the full active layer set (ground truth: its class doc), decodes+
         * alpha-composites them, and hands back the ready-to-write image plus which layer ids
         * actually loaded (for the JSON diagnostics below). A non-villager entity, or a villager
         * whose layers all fail to decode, degrades to exactly the old base-only behaviour. */
        Identifier textureId = resolveTexture(client, entity);
        VillagerTextureLayers.Result textureResult = textureId == null
            ? null
            : VillagerTextureLayers.resolveAndComposite(client, entity, textureId);

        int textureWritten = 0;
        List<String> textureLayerIds = List.of();

        if (textureResult != null)
        {
            File textureFile = new File(texDir, "base.png");

            try
            {
                ImageIO.write(textureResult.image, "png", textureFile);
                textureWritten = 1;
                textureLayerIds = textureResult.layerIds;
            }
            catch (IOException e)
            {
                LOGGER.warn("[blenderbridge] entity export: could not write composited texture: {}", e.toString());
            }
        }

        WriteResult written = writeObjAndMtl(objFile, mtlFile, byPart, textureWritten > 0);

        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.addProperty("entityType", typeName);
        out.addProperty("entityUuid", entity.getStringUUID());
        out.addProperty("objPath", portablePath(objFile));
        out.addProperty("mtlPath", portablePath(mtlFile));
        out.addProperty("texPath", textureWritten > 0 ? portablePath(new File(texDir, "base.png")) : "");
        out.addProperty("verts", written.vertCount);
        out.addProperty("faces", written.faceCount);
        out.addProperty("visibleParts", visiblePartCount);
        out.addProperty("exportedGroups", byPart.size());
        out.addProperty("uMin", written.uMin);
        out.addProperty("uMax", written.uMax);
        out.addProperty("vMin", written.vMin);
        out.addProperty("vMax", written.vMax);

        JsonArray textureLayersJson = new JsonArray();

        for (String layerId : textureLayerIds)
        {
            textureLayersJson.add(layerId);
        }

        out.add("textureLayers", textureLayersJson);
        out.addProperty("error", "");

        LOGGER.info("[blenderbridge] entity export DONE: type={} uuid={} verts={} faces={} visibleParts={} exportedGroups={} -> {}",
            typeName, entity.getStringUUID(), written.vertCount, written.faceCount, visiblePartCount, byPart.size(), objFile.getAbsolutePath());

        return out;
    }

    private static JsonObject failure(String reason)
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", false);
        out.addProperty("error", reason);
        return out;
    }

    /* ==================== entity resolution (package-private, shared with EntityBakeHandler) ==================== */

    /**
     * Resolve the target entity: by uuid string if given (non-blank), else the nearest
     * {@link Villager} to the player. Returns null (never throws) when nothing matches.
     */
    static LivingEntity resolveEntity(Minecraft client, String uuidOrNull)
    {
        /* Task 4.1 wire-anschluss (W7-B1, BODY-only hook): the ENTITY_EXPORT path exports a
         * THROWAWAY posing entity (a replay's MobForm mob is not in the world — neither the uuid
         * scan nor the nearest-villager fallback could ever find it). EntityExportBridge arms the
         * holder immediately before export() and clears it in a finally, all inside ONE synchronous
         * dispatch on the client-tick thread — EntityBakeHandler's own per-tick calls here can
         * never observe an armed target (see EntityExportTarget's class doc). */
        LivingEntity armedTarget = EntityExportTarget.peekTarget();

        if (armedTarget != null)
        {
            return armedTarget;
        }

        if (client == null || client.level == null)
        {
            return null;
        }

        if (uuidOrNull != null && !uuidOrNull.isBlank())
        {
            for (Entity candidate : client.level.entitiesForRendering())
            {
                if (uuidOrNull.equals(candidate.getStringUUID()) && candidate instanceof LivingEntity living)
                {
                    return living;
                }
            }

            return null;
        }

        if (client.player == null)
        {
            return null;
        }

        Villager nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Entity candidate : client.level.entitiesForRendering())
        {
            if (!(candidate instanceof Villager villager))
            {
                continue;
            }

            double distSq = villager.distanceToSqr(client.player);

            if (distSq < nearestDistSq)
            {
                nearestDistSq = distSq;
                nearest = villager;
            }
        }

        return nearest;
    }

    /**
     * Resolve the entity's root {@link ModelPart} via its LIVE renderer/model, exactly the object
     * instance Minecraft itself renders — never a synthesised copy. Raw types are used deliberately:
     * {@code EntityRenderDispatcher.getRenderer(T)}'s wildcard-captured return type cannot be called
     * back into with a concrete {@code entity} argument through normal generics (the classic
     * invariant-generics wall every "call setAngles from outside the renderer" mod hits) — the
     * standard, pragmatic fix is a raw-type call, same as Figura/mc-world-export do it.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static ModelPart resolveRootPart(Minecraft client, Entity entity)
    {
        EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(entity);

        if (!(renderer instanceof LivingEntityRenderer livingRenderer))
        {
            return null;
        }

        EntityModel model = livingRenderer.getModel();

        if (model == null)
        {
            return null;
        }

        /* PORT 26.2: HierarchicalModel ist weg — root() sitzt an Model selbst (public final). */
        return model.root();
    }

    /**
     * Resolve the entity's base texture identifier via its live renderer.
     *
     * <p>PORT 26.2: {@code EntityRenderer.getTextureLocation(Entity)} gibt es nicht mehr — die
     * textur hängt jetzt am RENDER-STATE ({@code LivingEntityRenderer.getTextureLocation(S)},
     * abstrakt, am jar geprüft). Den state baut das spiel selbst über {@code
     * createRenderState(entity, partialTick)}; wir raten ihn nicht zusammen. Für den villager
     * ist das ergebnis weiterhin die konstante basis-textur (siehe klassendoc TEXTURE) — der
     * layer-stack darüber macht {@link VillagerTextureLayers}, unverändert.</p>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Identifier resolveTexture(Minecraft client, Entity entity)
    {
        if (!(entity instanceof LivingEntity living))
        {
            /* Nur living entities haben einen LivingEntityRenderer; alles andere hatte auch
             * vorher keine textur, die dieser exporter schreiben konnte. */
            return null;
        }

        EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(living);

        if (!(renderer instanceof LivingEntityRenderer livingRenderer))
        {
            return null;
        }

        Object state = livingRenderer.createRenderState(living, 1.0F);

        if (!(state instanceof LivingEntityRenderState livingState))
        {
            return null;
        }

        return livingRenderer.getTextureLocation(livingState);
    }

    /**
     * Drive the model's pose through the game's OWN pipeline (class doc POSE section) —
     * {@code tickDelta=1.0F}, a settled per-tick sample, not an interpolated per-frame one.
     * Mutates the SHARED (per-renderer) model instance's {@link ModelPart} tree in place; callers
     * must read whatever they need out of it (e.g. via {@link #capturePose}) before posing a
     * different entity of the same type.
     *
     * <p>PORT 26.2 — WAS SICH HIER GEÄNDERT HAT UND WARUM ES KEINE NEUE MATHEMATIK IST: bis 1.21.1
     * setzte diese methode {@code model.riding/young/attackTime} von hand und rief {@code
     * prepareMobModel} + {@code setupAnim(entity, limbAngle, limbDistance, age, headYaw, pitch)}.
     * Diese felder und überladungen existieren in 26.2 nicht mehr (am jar geprüft: {@code
     * EntityModel} hat keine öffentlichen felder, {@code Model.setupAnim} nimmt genau EIN argument).
     * Der ersatz ist der render-state: {@code createRenderState(entity, tickDelta)} ist die
     * öffentliche, finale methode, mit der das spiel selbst genau diese werte aus der entity zieht
     * ({@code extractRenderState}), und {@code setupAnim(state)} ist der aufruf, den {@code
     * LivingEntityRenderer.submit} macht. Wir haben also KEINEN eigenen animations-pfad gebaut,
     * sondern nur den vom spiel bereitgestellten eingang benutzt — dieselbe regel wie beim
     * fluid-renderer-mitschnitt.</p>
     *
     * <p>⚠ OFFEN (nicht am laufenden spiel gemessen): {@code createRenderState} macht mehr als die
     * alten feld-zuweisungen (nametags, schatten, item-modelle). Für eine WEGWERF-entity, die nie
     * in der welt war ({@code ReplayPoseReader}/{@code EntityExportBridge} bauen genau solche),
     * kann darin etwas fehlschlagen, was der alte, engere pfad nie anfasste. Der beweis dafür ist
     * ein export einer replay-gebundenen mob-form im laufenden spiel.</p>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void poseEntity(Minecraft client, LivingEntity entity)
    {
        EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(entity);

        if (!(renderer instanceof LivingEntityRenderer livingRenderer))
        {
            return;
        }

        EntityModel model = livingRenderer.getModel();

        if (model == null)
        {
            return;
        }

        Object state = livingRenderer.createRenderState(entity, 1.0F);

        if (!(state instanceof LivingEntityRenderState livingState))
        {
            return;
        }

        model.setupAnim(livingState);
    }

    /* ==================== tree walk / bind pose (package-private, shared with EntityBakeHandler) ==================== */

    /**
     * Recursively reset every part in the tree to its {@code defaultTransform} (bind/rest pose) —
     * task 4.1 wants a STATIC export, not whatever transient pose the shared model instance last
     * happened to carry from an unrelated render call.
     */
    static void resetBindPose(ModelPart part)
    {
        part.resetPose();

        /* Task 4.1 wire-anschluss (W7-B1, BODY-only hook): no-op unless EntityExportTarget is armed
         * with neutralRotations=true (the ENTITY_EXPORT wire path) — then the part's rotation is
         * zeroed on top of the vanilla reset (pivots untouched), giving the rig-neutral capture the
         * armature/pose pipeline needs. Direct/EXEC callers keep the unchanged true bind pose.
         * Full reasoning: EntityExportTarget's class doc "WHY ZERO ROTATIONS". */
        EntityExportTarget.neutralizeRotation(part);

        for (ModelPart child : children(part).values())
        {
            resetBindPose(child);
        }
    }

    /**
     * Die kind-parts eines {@link ModelPart}, mit ihren namen — die EINE nicht-öffentliche stelle
     * des model-baums.
     *
     * <p>PORT 26.2: {@code children} ist wieder {@code private} (mit {@code javap -p} am 26.2-jar
     * geprüft), weil der access-widener im 26.2-build stillgelegt ist. Der mixin-accessor, der
     * genau dafür seit 2026-07-16 im baum liegt, ist jetzt der aktive weg. Doppel-cast über
     * {@link Object}, weil {@code ModelPart} {@code final} ist. Siehe klassendoc.</p>
     *
     * <p>WARUM ES KEINEN ERSATZ AUS DER PUBLIC-API GIBT: {@code getAllParts()} liefert eine FLACHE
     * liste ohne namen, {@code createPartLookup()} nur name→part ohne aufzählbarkeit,
     * {@code getChild(String)} braucht den namen schon. Der pfad-schlüssel dieses exporters
     * ({@code /kopf/hut}) braucht aber beides: struktur UND namen.</p>
     */
    private static Map<String, ModelPart> children(ModelPart part)
    {
        return ((ModelPartChildrenAccessor) (Object) part).blenderbridge$getChildren();
    }

    /**
     * Walk the whole part tree, keyed by the EXACT path convention {@code ModelPart.forEachCuboid}
     * itself uses internally (verified by bytecode, class doc GEOMETRY section): root path is
     * {@code ""}, each child's path is {@code parentPath + "/" + childName}. This is what lets a
     * cuboid capture callback's {@code path} argument be looked up directly against this map.
     */
    static LinkedHashMap<String, PartInfo> walkTree(ModelPart root)
    {
        LinkedHashMap<String, PartInfo> out = new LinkedHashMap<>();
        walkTree(root, "", out);
        return out;
    }

    private static void walkTree(ModelPart part, String path, Map<String, PartInfo> out)
    {
        out.put(path, new PartInfo(part.visible, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot));

        for (Map.Entry<String, ModelPart> child : children(part).entrySet())
        {
            walkTree(child.getValue(), path + "/" + child.getKey(), out);
        }
    }

    /**
     * Sample the current 9-float pose (pivot xyz, rotation xyz in radians, scale xyz) of every part
     * in the tree, keyed by the same path convention as {@link #walkTree}. Used by
     * {@code EntityBakeHandler} once per sampled tick, right after {@link #poseEntity} — the caller
     * must copy these values out before touching a different entity of the same type (the model
     * instance backing them is shared).
     */
    static LinkedHashMap<String, float[]> capturePose(ModelPart root)
    {
        LinkedHashMap<String, float[]> out = new LinkedHashMap<>();
        capturePose(root, "", out);
        return out;
    }

    private static void capturePose(ModelPart part, String path, Map<String, float[]> out)
    {
        out.put(path, new float[] {
            part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale
        });

        for (Map.Entry<String, ModelPart> child : children(part).entrySet())
        {
            capturePose(child.getValue(), path + "/" + child.getKey(), out);
        }
    }

    /** One {@link ModelPart} node's static tree-shape data, keyed by path in {@link #walkTree}. */
    static final class PartInfo
    {
        final boolean visible;
        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float pitch;
        final float yaw;
        final float roll;

        PartInfo(boolean visible, float pivotX, float pivotY, float pivotZ, float pitch, float yaw, float roll)
        {
            this.visible = visible;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
        }
    }

    /* ==================== OBJ / MTL / texture writing ==================== */

    private static WriteResult writeObjAndMtl(File objFile, File mtlFile, Map<String, List<CapturedQuad>> byPart, boolean hasTexture) throws IOException
    {
        int vertexIndex = 1;
        int uvIndex = 1;
        int normalIndex = 1;
        int faceCount = 0;
        float uMin = Float.MAX_VALUE;
        float uMax = -Float.MAX_VALUE;
        float vMin = Float.MAX_VALUE;
        float vMax = -Float.MAX_VALUE;

        BufferedWriter obj = Files.newBufferedWriter(objFile.toPath(), StandardCharsets.UTF_8);

        try
        {
            obj.write("# Minecraft entity export — BBS Blender bridge (Task 4.1)\n");
            obj.write("# BIND POSE — every ModelPart.resetTransform() applied before capture\n");
            obj.write("# vertices are entity-local model-space units, entity root at origin\n");
            obj.write("mtllib " + mtlFile.getName() + "\n");
            obj.write("o entity\n");
            obj.write(hasTexture ? "usemtl base\n" : "");

            for (Map.Entry<String, List<CapturedQuad>> entry : byPart.entrySet())
            {
                obj.write("g " + sanitizeGroupName(entry.getKey()) + "\n");

                for (CapturedQuad quad : entry.getValue())
                {
                    for (int v = 0; v < QUAD_VERTICES; v++)
                    {
                        obj.write("v " + fmt(quad.x[v]) + " " + fmt(quad.y[v]) + " " + fmt(quad.z[v]) + "\n");
                    }

                    for (int v = 0; v < QUAD_VERTICES; v++)
                    {
                        /* V-FLIP: MC texture-space V has origin top-left, OBJ/Blender expects
                         * bottom-left — same convention world.WorldExporter/StreamingWorldExporter
                         * already apply for block textures. */
                        float u = quad.u[v];
                        float flippedV = 1F - quad.v[v];

                        obj.write("vt " + fmt(u) + " " + fmt(flippedV) + "\n");

                        uMin = Math.min(uMin, u);
                        uMax = Math.max(uMax, u);
                        vMin = Math.min(vMin, flippedV);
                        vMax = Math.max(vMax, flippedV);
                    }

                    obj.write("vn " + fmt(quad.nx) + " " + fmt(quad.ny) + " " + fmt(quad.nz) + "\n");

                    int vBase = vertexIndex;
                    int uvBase = uvIndex;
                    int nBase = normalIndex;

                    vertexIndex += QUAD_VERTICES;
                    uvIndex += QUAD_VERTICES;
                    normalIndex += 1;

                    obj.write("f");

                    for (int v = 0; v < QUAD_VERTICES; v++)
                    {
                        obj.write(" " + (vBase + v) + "/" + (uvBase + v) + "/" + nBase);
                    }

                    obj.write("\n");
                    faceCount++;
                }
            }
        }
        finally
        {
            obj.close();
        }

        BufferedWriter mtl = Files.newBufferedWriter(mtlFile.toPath(), StandardCharsets.UTF_8);

        try
        {
            mtl.write("# Minecraft entity export — BBS Blender bridge (Task 4.1)\n");
            mtl.write("newmtl base\n");
            mtl.write("Ka 1.000 1.000 1.000\n");
            mtl.write("Kd 1.000 1.000 1.000\n");
            mtl.write("Ks 0.000 0.000 0.000\n");
            mtl.write("d 1.0\n");
            mtl.write("illum 1\n");

            if (hasTexture)
            {
                mtl.write("map_Kd textures/base.png\n");
                mtl.write("map_d textures/base.png\n");
            }
        }
        finally
        {
            mtl.close();
        }

        WriteResult result = new WriteResult();
        result.vertCount = vertexIndex - 1;
        result.faceCount = faceCount;
        result.uMin = faceCount == 0 ? 0F : uMin;
        result.uMax = faceCount == 0 ? 0F : uMax;
        result.vMin = faceCount == 0 ? 0F : vMin;
        result.vMax = faceCount == 0 ? 0F : vMax;

        return result;
    }

    private static int extractTexture(Minecraft client, Identifier textureId, File target)
    {
        try
        {
            Optional<Resource> resource = client.getResourceManager().getResource(textureId);

            if (resource.isEmpty())
            {
                LOGGER.warn("[blenderbridge] entity export: texture not found in resources: {}", textureId);
                return 0;
            }

            InputStream stream = resource.get().open();

            try
            {
                Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            finally
            {
                stream.close();
            }

            return 1;
        }
        catch (IOException e)
        {
            LOGGER.warn("[blenderbridge] entity export: could not extract texture {}: {}", textureId, e.toString());
            return 0;
        }
    }

    private static String sanitizeGroupName(String path)
    {
        String raw = path.isEmpty() ? "root" : path.substring(1).replace('/', '_');
        StringBuilder builder = new StringBuilder(raw.length());

        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            builder.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }

        return builder.length() == 0 ? "root" : builder.toString();
    }

    private static String safeName(Identifier id)
    {
        String raw = id.getNamespace() + "_" + id.getPath();
        StringBuilder builder = new StringBuilder(raw.length());

        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            builder.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }

        return builder.toString();
    }

    private static String portablePath(File file)
    {
        return file.getAbsolutePath().replace('\\', '/');
    }

    private static String fmt(float value)
    {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    /* ==================== capture plumbing ==================== */

    private static final class WriteResult
    {
        int vertCount;
        int faceCount;
        float uMin;
        float uMax;
        float vMin;
        float vMax;
    }

    /** One captured cuboid face: four local-space positions, four raw (un-flipped) UVs, one normal. */
    static final class CapturedQuad
    {
        final float[] x;
        final float[] y;
        final float[] z;
        final float[] u;
        final float[] v;
        final float nx;
        final float ny;
        final float nz;

        CapturedQuad(float[] x, float[] y, float[] z, float[] u, float[] v, float nx, float ny, float nz)
        {
            this.x = x.clone();
            this.y = y.clone();
            this.z = z.clone();
            this.u = u.clone();
            this.v = v.clone();
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }


}
