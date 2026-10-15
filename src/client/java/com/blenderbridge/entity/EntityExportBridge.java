// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export, task 4.1 wire-anschluss, W7-B1).
package com.blenderbridge.entity;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import com.mojang.brigadier.StringReader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EntityExportBridge — plan v2 task 4.1 wire-anschluss (W7-B1): the {@code ENTITY_EXPORT} message.
 * Blender asks for a mob model (typically the {@link MobForm} of a REPLAY, which has no live world
 * entity), this class drives the existing, proven {@link EntityExporter} (OBJ+MTL+composited PNG,
 * task 4.1 grundgerüst + villager layer stack) and additionally writes the RIG manifest
 * ({@code rig.json}, see below) the addon needs to build a real armature + vertex groups.
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS (unchanged deploy reasoning)</h2>
 * <p>Same constraint as every sibling ({@code ReplayPoseReader}'s class doc): brand-new lazy class,
 * zero new members on loaded classes. Wiring is one raw-string case in
 * {@code BlenderBridgeClient.dispatch} ({@code "ENTITY_EXPORT"} in, {@code "ENTITY_EXPORT_RESULT"}
 * out, no new {@code BridgeProtocol.TYPE_*}), plus two BODY-only hooks in {@code EntityExporter}
 * that consult the new {@link EntityExportTarget} holder (its class doc carries the full why).</p>
 *
 * <h2>REQUEST fields</h2>
 * <ul>
 *   <li>{@code replayUuid} (preferred): resolve the replay in the open/playing film, require a
 *       {@link MobForm}, construct a throwaway posing entity from {@code mobID}+{@code mobNBT} —
 *       the NBT is applied (mirrors {@code MobFormRenderer.ensureEntity()}: parse, put "id",
 *       {@code readNbt}) so profession/type texture layers match what BBS renders for this form.
 *       Deliberately RICHER than {@code ReplayPoseReader.createPosingEntity} (which skips NBT):
 *       pose math never needed it, the TEXTURE stack does.</li>
 *   <li>{@code entityUuid}: a live world entity instead (EXEC/diagnostic path); blank/absent both →
 *       {@code EntityExporter}'s nearest-villager fallback.</li>
 *   <li>{@code mobId + targetUuid}: construct a throwaway default mob directly from its registry
 *       id and echo {@code targetUuid} as {@code replayUuid}. The live-recording bridge uses this
 *       for a disposable Blender morph preview whose current MobForm is not the replay's base form.</li>
 *   <li>{@code requestId}: echoed verbatim in the result — solicited-korrelation for the addon
 *       (dossier merkregel: JEDER result-handler braucht pending-korrelation; the java bridge
 *       broadcasts results to ALL clients). Absent/empty → no key echoed (old-caller shape).</li>
 * </ul>
 *
 * <h2>RESULT fields</h2>
 * <p>Everything {@link EntityExporter#export} returns (ok/entityType/entityUuid/objPath/mtlPath/
 * texPath/verts/faces/visibleParts/exportedGroups/uMin..vMax/textureLayers/error) PLUS:
 * {@code mobId} (registry id string), {@code replayUuid} (echo, "" when not a replay request),
 * {@code rigPath} (absolute path of the written {@code rig.json}), {@code boneCount},
 * {@code zeroRotation:true} (capture convention marker), {@code requestId} (echo, see above).</p>
 *
 * <h2>rig.json — the armature contract (disk-based like world_meta.json, NOT sent over the wire)</h2>
 * <pre>{@code
 * { "formatVersion": 1, "entityType": "minecraft_villager", "mobId": "minecraft:villager",
 *   "replayUuid": "...", "zeroRotation": true, "pivotUnit": "px16",
 *   "bones": [ { "path": "root"|"/head"|"/head/hat"|..., "name": "root"|"head"|"hat"|...,
 *               "objGroup": "root"|"head"|"head_hat"|..., "parent": ""|"root"|"/head"|...,
 *               "pivotPx": [x,y,z], "bindRotation": [pitch,yaw,roll], "visible": true|false }, ... ] }
 * }</pre>
 * <ul>
 *   <li>{@code path} uses the SAME convention the pose results already speak ({@code capturePose}/
 *       {@code ReplayPoseReader.frame}): root literal {@code "root"}, children {@code "/parent/child"}.
 *       Tree order = depth-first pre-order, parents ALWAYS before children (walkTree contract) —
 *       the addon may accumulate pivot positions in one forward pass.</li>
 *   <li>{@code pivotPx} are RAW {@code ModelPart.pivotX/Y/Z} (PIXELS, parent-relative, Y-DOWN java
 *       model space) — divide by 16 for block units, exactly what vanilla's own
 *       {@code ModelPart.rotate} does ({@code matrices.translate(pivotX/16,...)}). Kept raw for
 *       consistency with the 9-float pose convention ({@code EntityProtocol}: raw part fields).</li>
 *   <li>{@code bindRotation} is the part's TRUE default rotation (radians) — for reference only:
 *       the OBJ geometry is captured with rotations ZEROED ({@code zeroRotation:true}, see
 *       {@link EntityExportTarget}'s class doc "WHY ZERO ROTATIONS"), so the addon does NOT need
 *       to compensate binds; recorded anyway so nothing about the model shape is thrown away.</li>
 *   <li>{@code objGroup} is the OBJ {@code g}-group this part's cuboids were written under — MUST
 *       mirror {@code EntityExporter.sanitizeGroupName} (private there, deliberately duplicated
 *       here as {@link #sanitizeGroupName}, same house convention as the film-resolution helpers
 *       below; a drift would only mis-link vertex groups, loudly visible in the addon's logs).</li>
 * </ul>
 *
 * <h2>KNOWN LIMIT — one export dir per entity TYPE</h2>
 * <p>{@code EntityExporter} writes to {@code entity_<type>/} — two mob replays of the same type but
 * different NBT (e.g. two villager professions) share that dir, last export wins. Documented, not
 * hidden; per-NBT dirs are a follow-up if it ever bites.</p>
 *
 * <h2>Film/replay resolution — deliberately duplicated</h2>
 * <p>Same established convention as {@code ActorExporter}/{@code FilmCameraExporter}/{@code FilmBackup}/
 * {@code ReplayPoseReader} (see any of those class docs): a future change to one must not silently
 * change the others.</p>
 *
 * <h2>read-mostly, main-thread-only</h2>
 * <p>Runs synchronously inside dispatch on the client-tick thread (same as {@code ReplayPoseReader}).
 * World/film state is only READ; the only writes are the export files under
 * {@code run/blenderbridge_export/} and the transient part-tree transforms of the SHARED renderer
 * model (reset to true bind pose before returning, see {@link #buildRigJson}).</p>
 */
public final class EntityExportBridge
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Request field: which replay's mob form to export. Preferred path. */
    private static final String REQ_REPLAY_UUID = "replayUuid";

    /** Request field: a live world entity's uuid instead (EXEC/diagnostic path). */
    private static final String REQ_ENTITY_UUID = "entityUuid";

    /** Request field: construct a throwaway entity directly from this registry id. */
    private static final String REQ_MOB_ID = "mobId";

    /** Identity echoed as replayUuid for a disposable Blender preview object. */
    private static final String REQ_TARGET_UUID = "targetUuid";

    /** Request/result field: solicited-korrelation echo (see class doc). */
    private static final String KEY_REQUEST_ID = "requestId";

    private EntityExportBridge()
    {
        /* static-only helper */
    }

    /**
     * Handle an ENTITY_EXPORT request (raw-string case in {@code BlenderBridgeClient.dispatch}).
     * Never throws — every failure path answers with {@code ok:false} rather than leaving the
     * caller waiting.
     */
    public static void handle(JsonObject message)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String requestId = BridgeProtocol.getFieldString(fields, KEY_REQUEST_ID, "");
        JsonObject payload;

        try
        {
            payload = export(fields);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] entity export (wire) failed", t);
            payload = failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        if (requestId != null && !requestId.isEmpty())
        {
            payload.addProperty(KEY_REQUEST_ID, requestId);
        }

        BlenderBridgeClient.SERVER.send(resultEnvelope(payload));
    }

    private static JsonObject export(JsonObject fields)
    {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.level == null)
        {
            return failure("no world loaded (join a world before exporting an entity)");
        }

        String replayUuid = BridgeProtocol.getFieldString(fields, REQ_REPLAY_UUID, "");
        String entityUuid = BridgeProtocol.getFieldString(fields, REQ_ENTITY_UUID, "");
        String requestedMobId = BridgeProtocol.getFieldString(fields, REQ_MOB_ID, "");
        LivingEntity target;

        if (replayUuid != null && !replayUuid.isBlank())
        {
            Replay replay = resolveReplay(replayUuid);

            if (replay == null)
            {
                return failure("replay not found for uuid " + replayUuid);
            }

            if (replay.isGroup.get())
            {
                return failure("replay " + replayUuid + " is a group folder — no form of its own");
            }

            Form form = replay.form.get();

            if (!(form instanceof MobForm mobForm))
            {
                return failure("replay " + replayUuid + "'s form is not a MobForm (task 4.1 exports mob rigs; "
                    + "model-form actors already import via .bobj)");
            }

            target = createPosingEntity(client, mobForm);

            if (target == null)
            {
                return failure("could not construct a poseable entity for mobID '" + mobForm.mobID.get() + "'");
            }
        }
        else if (requestedMobId != null && !requestedMobId.isBlank())
        {
            MobForm mobForm = new MobForm();
            mobForm.mobID.set(requestedMobId);
            target = createPosingEntity(client, mobForm);
            replayUuid = BridgeProtocol.getFieldString(fields, REQ_TARGET_UUID, "");

            if (target == null)
            {
                return failure("could not construct a poseable entity for mobID '" + requestedMobId + "'");
            }
        }
        else
        {
            replayUuid = "";
            target = EntityExporter.resolveEntity(client, entityUuid);

            if (target == null)
            {
                return failure(entityUuid == null || entityUuid.isBlank()
                    ? "no villager found near the player"
                    : "entity not found for uuid " + entityUuid);
            }
        }

        /* Rig-neutral capture through the UNCHANGED EntityExporter.export entry point: the armed
         * holder makes resolveEntity return our target and resetBindPose zero every rotation.
         * clear() in finally — a leaked target would hijack the next ENTITY_BAKE resolve (holder doc). */
        JsonObject result;

        EntityExportTarget.arm(target, true);

        try
        {
            result = EntityExporter.export(null);
        }
        finally
        {
            EntityExportTarget.clear();
        }

        result.addProperty("replayUuid", replayUuid);
        result.addProperty("mobId", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        result.addProperty("zeroRotation", true);

        if (!result.has("ok") || !result.get("ok").getAsBoolean())
        {
            return result;
        }

        /* The rig manifest. resolveRootPart again (same shared model instance the export used);
         * buildRigJson restores TRUE bind pose first (holder is cleared, so resetBindPose is the
         * plain vanilla reset again) — the shared model is left in a vanilla-clean state AND the
         * recorded bindRotation values are the real defaults, while the OBJ on disk stays neutral. */
        ModelPart root = EntityExporter.resolveRootPart(client, target);

        if (root == null)
        {
            /* Shouldn't happen — export above only succeeds for a resolvable SinglePartEntityModel. */
            return failure("rig walk failed: root part unresolvable after successful export");
        }

        String objPath = result.get("objPath").getAsString();
        File rigFile = new File(new File(objPath).getParentFile(), "rig.json");
        JsonObject rig = buildRigJson(root, result, replayUuid);

        try
        {
            Files.write(rigFile.toPath(), rig.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            LOGGER.warn("[blenderbridge] entity export: could not write rig.json: {}", e.toString());

            /* Without the rig manifest the addon cannot build the armature — fail LOUD, the OBJ
             * alone would silently degrade task 4.1 back to a static mesh. */
            return failure("rig.json write failed: " + e.getMessage());
        }

        result.addProperty("rigPath", rigFile.getAbsolutePath().replace('\\', '/'));
        result.addProperty("boneCount", rig.getAsJsonArray("bones").size());

        LOGGER.info("[blenderbridge] entity export (wire) DONE: mobId={} replayUuid={} bones={} rig={}",
            result.get("mobId").getAsString(), replayUuid, rig.getAsJsonArray("bones").size(), rigFile.getAbsolutePath());

        return result;
    }

    /**
     * Restore TRUE bind pose (holder is already cleared → plain vanilla reset), then walk the tree
     * into the rig manifest (schema: class doc). Uses {@code EntityExporter}'s package-private
     * {@code resetBindPose}/{@code walkTree} — the exact same tree/path convention the OBJ groups
     * and the pose results use, never a re-derivation.
     */
    private static JsonObject buildRigJson(ModelPart root, JsonObject result, String replayUuid)
    {
        EntityExporter.resetBindPose(root);

        LinkedHashMap<String, EntityExporter.PartInfo> parts = EntityExporter.walkTree(root);
        JsonArray bones = new JsonArray();

        for (Map.Entry<String, EntityExporter.PartInfo> entry : parts.entrySet())
        {
            String rawPath = entry.getKey();
            EntityExporter.PartInfo info = entry.getValue();

            /* Path convention = capturePose/ReplayPoseReader.frame: root "" is sent as literal
             * "root", every other path keeps its leading slash. Parent = path minus last segment
             * ("" cut down to "root"); the root itself has parent "". */
            String path = rawPath.isEmpty() ? "root" : rawPath;
            String name = rawPath.isEmpty() ? "root" : rawPath.substring(rawPath.lastIndexOf('/') + 1);
            String parent;

            if (rawPath.isEmpty())
            {
                parent = "";
            }
            else
            {
                int cut = rawPath.lastIndexOf('/');
                parent = cut <= 0 ? "root" : rawPath.substring(0, cut);
            }

            JsonObject bone = new JsonObject();
            bone.addProperty("path", path);
            bone.addProperty("name", name);
            bone.addProperty("objGroup", sanitizeGroupName(rawPath));
            bone.addProperty("parent", parent);

            JsonArray pivot = new JsonArray();
            pivot.add(info.pivotX);
            pivot.add(info.pivotY);
            pivot.add(info.pivotZ);
            bone.add("pivotPx", pivot);

            JsonArray rotation = new JsonArray();
            rotation.add(info.pitch);
            rotation.add(info.yaw);
            rotation.add(info.roll);
            bone.add("bindRotation", rotation);

            bone.addProperty("visible", info.visible);
            bones.add(bone);
        }

        JsonObject rig = new JsonObject();
        rig.addProperty("formatVersion", 1);
        rig.addProperty("entityType", result.get("entityType").getAsString());
        rig.addProperty("mobId", result.get("mobId").getAsString());
        rig.addProperty("replayUuid", replayUuid);
        rig.addProperty("zeroRotation", true);
        rig.addProperty("pivotUnit", "px16");
        rig.add("bones", bones);

        return rig;
    }

    /**
     * MUST mirror {@code EntityExporter.sanitizeGroupName} (private there — deliberately duplicated,
     * class doc): root path {@code ""} → {@code "root"}, otherwise strip the leading slash, slashes →
     * underscores, any non-[letter|digit|_|-] → underscore.
     */
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

    /**
     * Throwaway, never-spawned, never-ticked posing entity for a mob form — mirrors
     * {@code MobFormRenderer.ensureEntity()} INCLUDING the NBT application (class doc: the texture
     * layer stack needs it; {@code ReplayPoseReader.createPosingEntity} skips it because pose math
     * doesn't). NBT parse failures degrade silently to an empty compound — the same silent catch
     * {@code ensureEntity()} itself uses.
     */
    private static LivingEntity createPosingEntity(Minecraft client, MobForm mobForm)
    {
        ResourceLocation mobId;

        try
        {
            mobId = new ResourceLocation(mobForm.mobID.get());
        }
        catch (RuntimeException e)
        {
            return null;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(mobId);

        /* Defaulted registry falls back to minecraft:pig — round-trip the id so an unknown mobID
         * fails loudly instead of silently exporting a pig (same guard as ReplayPoseReader). */
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(mobId))
        {
            return null;
        }

        /* ⚠️ 26.2: create() nimmt kein blosses Level mehr, sondern Level + EntitySpawnRequest.
         * ignoreChecks=TRUE ist hier Absicht und kein Bequemlichkeits-Flag: die 1-arg-Variante
         * create(Level, EntitySpawnReason) laeuft intern durch canSpawn(level), und canSpawn
         * prueft in 26.2 ZUSAETZLICH die Schwierigkeit — ein nicht-friedlicher Mob liefert auf
         * PEACEFUL jetzt null. In 1.21.1 gab es diese Huerde nicht (create(Level) pruefte nur
         * die Feature-Flags). Da diese Entity NIE gespawnt/geticked wird (reines Posier-/
         * Export-Objekt), waere ein "auf Friedlich exportiert kein Zombie" eine echte
         * Verhaltens-Regression. ignoreChecks springt canSpawn komplett und geht direkt an die
         * Factory. Preis: eine per Feature-Flag abgeschaltete Entity-Art laesst sich jetzt
         * konstruieren statt null zu liefern — fuer ein reines Anzeige-Objekt folgenlos.
         * Der EntitySpawnReason selbst hat hier KEINE Wirkung (Bytecode geprueft: er wandert
         * nie in die Factory, nur ignoreChecks wird gelesen). */
        Entity entity = type.create(client.level);

        /* 26.2: Wegwerf-Entities brauchen eine Kennung, sonst stirbt der Zeichenweg.
         * Siehe mchorse.bbs_mod.forms.renderers.AnzeigeEntity. */
        mchorse.bbs_mod.forms.renderers.AnzeigeEntity.kennungGeben(entity);

        if (!(entity instanceof LivingEntity living))
        {
            return null;
        }

        CompoundTag compound = new CompoundTag();

        try
        {
            /* ⚠️ 26.2: TagParser ist generisch (TagParser<T>) und hat keinen oeffentlichen
             * Konstruktor mehr. parseCompoundAsArgument ist das exakte Gegenstueck zum alten
             * readStruct(): es liest EIN Compound aus dem Reader und meckert NICHT ueber
             * Resttext dahinter. (parseCompoundFully waere strenger und wuerde bei Resttext
             * werfen — das waere hier eine stille Verhaltensaenderung.) */
            compound = mchorse.bbs_mod.compat.NbtAlt.parseCompoundAsArgument(new StringReader(mobForm.mobNBT.get()));
        }
        catch (Exception e)
        {
            /* mirrors MobFormRenderer.ensureEntity: unparseable NBT = plain default entity */
        }

        try
        {
            compound.putString("id", mobForm.mobID.get());

            /* ⚠️ 26.2: Entity.load nimmt keinen CompoundTag mehr, sondern ein ValueInput.
             * TagValueInput.create(...) ist die Bruecke Compound -> ValueInput (so macht es
             * Vanilla selbst, siehe BaseSpawner-Bytecode). ProblemReporter.DISCARDING = Fehler
             * im NBT werden verschluckt statt geloggt — deckt sich mit dem stillen catch, den
             * dieser Pfad schon immer hatte. registryAccess() liefert den HolderLookup.Provider
             * (RegistryAccess erweitert ihn), den Codec-basierte Felder brauchen. */
            living.load(compound);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("[blenderbridge] entity export: mobNBT not applicable, using defaults: {}", e.toString());
        }

        living.noPhysics = true;

        return living;
    }

    /* ================================================================= *
     *  Replay / film resolution — deliberately duplicated, see class-doc *
     * ================================================================= */

    private static Replay resolveReplay(String replayUuid)
    {
        Film film = getEditorFilm();

        if (film == null)
        {
            film = getPlayingFilm();
        }

        if (film == null)
        {
            return null;
        }

        for (Replay replay : film.replays.getList())
        {
            if (replay != null && replayUuid.equals(replay.uuid.get()))
            {
                return replay;
            }
        }

        return null;
    }

    /** peekDashboard() — NOT getDashboard(): a read-only lookup must not build UI as a side effect. */
    private static Film getEditorFilm()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();

        if (dashboard == null)
        {
            return null;
        }

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel == null)
        {
            return null;
        }

        return panel.getData();
    }

    /** The first film currently playing in-world, or null when none is. */
    private static Film getPlayingFilm()
    {
        if (BBSModClient.getFilms() == null)
        {
            return null;
        }

        List<BaseFilmController> controllers = BBSModClient.getFilms().getControllers();

        if (controllers == null)
        {
            return null;
        }

        for (BaseFilmController controller : controllers)
        {
            if (controller != null && controller.film != null)
            {
                return controller.film;
            }
        }

        return null;
    }

    /* ================================================================= *
     *  Payload skeleton + wire helper                                    *
     * ================================================================= */

    /** Failure payload — same key skeleton EntityExporter.failure() emits (ok/error). */
    private static JsonObject failure(String reason)
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", false);
        out.addProperty("error", reason == null ? "" : reason);
        return out;
    }

    private static JsonObject resultEnvelope(JsonObject payload)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();

        /* Raw string type, no BridgeProtocol.TYPE_* constant — same convention as
         * FILM_CAMERA_EXPORT/PLAYHEAD/REPLAY_POSE_EXPORT (see class doc). */
        JsonObject envelope = BridgeProtocol.envelope("ENTITY_EXPORT_RESULT", "entity-export", seq, BridgeProtocol.ORIGIN_BBS);
        envelope.add(BridgeProtocol.KEY_FIELDS, payload);

        return envelope;
    }
}
