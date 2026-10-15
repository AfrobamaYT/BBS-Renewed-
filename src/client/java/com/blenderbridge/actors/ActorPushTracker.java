// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 task 1.6: push not poll).
package com.blenderbridge.actors;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import org.slf4j.Logger;

import java.util.List;

/**
 * ActorPushTracker — plan v2 task 1.6: replace the addon's 0.5s actor POLL with a tick-driven
 * PUSH. BBS was the only side that never initiated a sync; Blender had to ask every 0.5s
 * (2 Hz) under a 20 Hz wire protocol — research (R4) found push-on-change everywhere and
 * second-scale polling nowhere. This class is the "on-change" half living on the BBS side.
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS + NO NEW METHODS ON {@code BlenderBridgeClient}</h2>
 * <p>Same deploy-model constraint as {@code EntityBakeHandler} (task 4.3b) right now (W1+,
 * restarts forbidden, {@code canAddMethod=false} measured for already-loaded classes — task
 * 0.2): this class is brand new (lazy-loadable, no boot needed), and {@code BlenderBridgeClient}
 * gains ZERO new members. Wiring is one added call in the ALREADY-EXISTING body of
 * {@code onClientTick(MinecraftClient)} — {@code ActorPushTracker.tick()} — the exact same
 * "ride the already-registered tick handler" shape {@code EntityBakeHandler} uses instead of
 * registering a second {@code ClientTickEvents} listener.</p>
 *
 * <h2>FINGERPRINT, NOT A FULL RE-SERIALISE, EVERY TICK</h2>
 * <p>{@link #tick()} runs 20x/second. Building the full ACTORS_EXPORT_RESULT JSON that often
 * would be the "teure Voll-Serialisierung pro tick" the plan explicitly warns against. Instead
 * {@link #fingerprint(Film)} folds every replay's uuid + every non-empty channel's id and
 * keyframes (tick/value/interp/handles) into one {@code long} — no JSON, no string building, no
 * allocation beyond the channel list already held by BBS. That is cheap enough to run every
 * tick (a handful of replays with a few dozen keyframes each is microseconds of arithmetic), so
 * unlike a throttled "hash once a second" fallback, a real edit is detected on the VERY NEXT
 * tick — the latency budget (task 1.6 gate b: median &lt; 200ms) needs that, not a coarser poll.
 * Only when the fingerprint actually changed does {@link #push(Film)} build the real payload,
 * reusing {@link ActorBridge#export(JsonObject)} — the exact same code path the button/poll
 * export already uses (plan instruction: "bestehenden ActorBridge.export-weg nutzen"), so
 * Blender's {@code _handle_actors_export_result}/{@code _live_sync_actors} need not learn a new
 * payload shape, only a new (unsolicited) arrival mode.</p>
 *
 * <h2>IDLE GATE</h2>
 * <p>No diff, no push — by construction, not a special case. 30s of an unedited film means 30s
 * of {@link #tick()} calls that all return after the fingerprint compare, sending nothing
 * (task 1.6 IDLE-GATE).</p>
 *
 * <h2>FILM-SWITCH / FIRST-TICK BASELINE</h2>
 * <p>The first tick that resolves a given film id ONLY adopts it as the baseline; it never
 * pushes. Without that, opening a film Blender has never seen (or switching between two open
 * films) would look like a "change" to the whole film and blast the entire performance at
 * Blender as a push the instant the user merely selected a different film — the button/poll
 * paths remain the deliberate way to pull a whole new film's data.</p>
 *
 * <h2>ECHO GUARD — see {@link #markSynced()}</h2>
 * <p>An ACTORS_IMPORT from Blender mutates the SAME film state this class fingerprints. Without
 * a guard, the very next {@link #tick()} in that SAME client tick (dispatch runs first, then
 * this) would see the just-applied values as a "change" and push them straight back — an echo.
 * {@link ActorBridge#importActors(JsonObject, com.blenderbridge.BridgeState)} calls
 * {@link #markSynced()} right after the import lands, which re-fingerprints the (now
 * post-import) film and adopts it as the baseline BEFORE this class's own tick() runs later in
 * the same pass — so the diff comes back "unchanged" and nothing is sent. This mirrors what
 * {@code BridgeState.applyingRemote} does for the camera, but needs no new field on that
 * (already-loaded) class: the guard here is "pretend we already knew about this edit", which
 * this brand-new class can hold entirely on its own.</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY, like every other class in this package — {@link #tick()} is called from
 * {@code BlenderBridgeClient.onClientTick}, {@link #markSynced()} from
 * {@code ActorBridge.importActors}, both already main-thread-only call sites.</p>
 */
public final class ActorPushTracker
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Sentinel: "no film has been fingerprinted yet" (distinct from any real fold result is not
     * guaranteed, but paired with {@link #lastFilmId} being {@code null} it is unambiguous —
     * the film-id check below always runs first). */
    private static final long NO_BASELINE = 0L;

    /** FNV-1a-style 64-bit offset basis / prime — arbitrary but FIXED constants; the fingerprint
     * only needs to be a stable function of the film content, never compared across process
     * restarts, so no cryptographic property is required. */
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    /** Last fingerprint successfully associated with {@link #lastFilmId}. Touched only on the
     * main thread (tick() and markSynced() both run there — see class-doc). */
    private static long lastFingerprint = NO_BASELINE;

    /** Film id the baseline above belongs to, or {@code null} when no film has been seen yet /
     * the last-seen film closed. */
    private static String lastFilmId = null;

    private ActorPushTracker()
    {
        /* static-only helper */
    }

    /**
     * Per-tick drain (called from {@code BlenderBridgeClient.onClientTick}'s widened body).
     * Cheap no-op when no film is open or nothing changed — see class-doc for why this is safe
     * to call unconditionally every tick.
     */
    public static void tick()
    {
        Film film = resolveOpenFilm();

        if (film == null)
        {
            /* No film open: forget any baseline so a LATER re-open never diffs against stale
             * state from a previous film/session (task 1.0's film-identity concern, narrowly
             * applied here — this class does not itself gate on film-id changing mid-sync). */
            lastFilmId = null;
            lastFingerprint = NO_BASELINE;

            return;
        }

        String filmId = film.getId() == null ? "" : film.getId();
        long fingerprint = fingerprint(film);

        if (!filmId.equals(lastFilmId))
        {
            /* Film switched (or first tick ever): adopt the baseline SILENTLY. Pushing here
             * would present the whole newly-opened film as a "change" the instant the user
             * merely switched films — see class-doc. */
            lastFilmId = filmId;
            lastFingerprint = fingerprint;

            return;
        }

        if (fingerprint == lastFingerprint)
        {
            /* Nothing changed since last tick — the normal case, IDLE-GATE. */
            return;
        }

        lastFingerprint = fingerprint;

        push(film);
    }

    /**
     * ECHO GUARD hook: called by {@link ActorBridge#importActors} right after a Blender-authored
     * ACTORS_IMPORT lands (main thread, synchronous, same client tick as the subsequent
     * {@link #tick()} call). Re-fingerprints whichever film is open NOW and adopts it as the
     * baseline, so the next {@link #tick()} diff — which runs later in this very tick — sees
     * "unchanged" and never bounces Blender's own edit back to it. See class-doc.
     *
     * <p>Safe to call even when the import applied nothing (unknown uuids, no film open): it
     * simply re-adopts whatever the current state already is, which is a no-op push-wise either
     * way.</p>
     */
    public static void markSynced()
    {
        Film film = resolveOpenFilm();

        if (film == null)
        {
            lastFilmId = null;
            lastFingerprint = NO_BASELINE;

            return;
        }

        lastFilmId = film.getId() == null ? "" : film.getId();
        lastFingerprint = fingerprint(film);
    }

    /* ================================================================= *
     *  Push                                                             *
     * ================================================================= */

    /**
     * Build the ACTORS_EXPORT_RESULT payload via the SAME path the button/poll export uses
     * ({@link ActorBridge#export(JsonObject)}) and send it unsolicited — an ACTORS_EXPORT_RESULT
     * that is not a reply to any request Blender made. {@code BridgeServer.send} is a no-op when
     * no client is connected, exactly like the camera push in {@code pushCameraIfChanged} —
     * calling this unconditionally on every detected change is therefore always safe.
     */
    private static void push(Film film)
    {
        JsonObject fields = new JsonObject();
        JsonObject payload = ActorBridge.export(fields);

        if (!payload.get(ActorProtocol.OK).getAsBoolean())
        {
            /* Film closed between the fingerprint diff and now — cannot happen within a single
             * synchronous main-thread call today, but export() staying well-formed either way
             * means this is a correct no-op rather than sending a broken payload. */
            return;
        }

        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject envelope = BridgeProtocol.actorsExportResult(seq, payload);

        BlenderBridgeClient.SERVER.send(envelope);

        LOGGER.debug("[blenderbridge] actor push: film='{}' replays={}",
            film.getId(), film.replays.getList().size());
    }

    /* ================================================================= *
     *  Fingerprint                                                      *
     * ================================================================= */

    /**
     * Fold every replay's identity and every non-empty numeric channel's keyframes into one
     * {@code long}. Mirrors exactly what {@link ActorExporter#exportActors()} would SEND (uuid,
     * enabled, channel id, tick/value/interp/handles) without building any JSON — see class-doc.
     */
    private static long fingerprint(Film film)
    {
        long h = FNV_OFFSET;
        List<Replay> replays = film.replays.getList();

        for (Replay replay : replays)
        {
            if (replay == null)
            {
                continue;
            }

            h = mixString(h, replay.uuid.get());
            h = mixLong(h, replay.enabled.get() ? 1L : 0L);
            h = mixString(h, replay.form.get() == null ? "" : replay.form.get().getFormId());
            if (replay.form.get() instanceof mchorse.bbs_mod.forms.forms.ModelForm model)
            {
                h = mixString(h, model.model.get());
                var asset = BBSModClient.getModels().getModel(model.model.get());
                h = mixLong(h, asset == null ? 0 : System.identityHashCode(asset.model));
            }
            if (replay.form.get() instanceof mchorse.bbs_mod.forms.forms.BillboardForm)
            {
                h = mixString(h, replay.form.get().toData().toString());
            }

            for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
            {
                if (channel == null || channel.isEmpty())
                {
                    continue;
                }

                h = mixString(h, channel.getId());

                for (Keyframe<?> keyframe : channel.getKeyframes())
                {
                    h = mixLong(h, Float.floatToIntBits(keyframe.getTick()));
                    h = mixValue(h, keyframe.getValue());
                    h = mixString(h, interpKey(keyframe));
                    h = mixLong(h, Float.floatToIntBits(keyframe.lx));
                    h = mixLong(h, Float.floatToIntBits(keyframe.ly));
                    h = mixLong(h, Float.floatToIntBits(keyframe.rx));
                    h = mixLong(h, Float.floatToIntBits(keyframe.ry));
                }
            }

            /* PROPERTIES-topf (2026-07-18): der fingerprint überwachte bis hier NUR replay.keyframes
             * (transform/status). Damit folgte Blender bei MATERIAL-animation (color/glow/paint) und
             * PER-BONE-pose (pose:<bone>) NICHT live — die reisten zwar im payload, lösten aber keinen
             * push aus. Jetzt foldet der fingerprint AUCH replay.properties.properties (die
             * FormProperties-kanäle), sodass JEDE rig-änderung (bewegung, material, pose) live nach
             * Blender pusht. Der value-typ ist hier Color/GlowSettings/PaintSettings/PoseTransform
             * (nicht Number) — mixValue faellt fuer non-Number auf value.toString() zurueck (siehe
             * unten), was fuer die aenderungs-ERKENNUNG genügt (jeder edit ändert die toString). */
            if (replay.properties != null)
            {
                for (java.util.Map.Entry<String, KeyframeChannel> e : replay.properties.properties.entrySet())
                {
                    KeyframeChannel channel = e.getValue();

                    if (channel == null || channel.isEmpty())
                    {
                        continue;
                    }

                    h = mixString(h, e.getKey());

                    for (Object obj : channel.getKeyframes())
                    {
                        Keyframe<?> keyframe = (Keyframe<?>) obj;
                        h = mixLong(h, Float.floatToIntBits(keyframe.getTick()));
                        h = mixValue(h, keyframe.getValue());
                        h = mixString(h, interpKey(keyframe));
                        h = mixLong(h, Float.floatToIntBits(keyframe.lx));
                        h = mixLong(h, Float.floatToIntBits(keyframe.ly));
                        h = mixLong(h, Float.floatToIntBits(keyframe.rx));
                        h = mixLong(h, Float.floatToIntBits(keyframe.ry));
                    }
                }
            }
        }

        return h;
    }

    /**
     * Interpolation key of a keyframe, defaulting to "" when unresolvable — mirrors
     * {@code ActorExporter}'s own helper (duplicated rather than shared, matching the existing
     * convention in this package of small private per-class copies, e.g. {@code getEditorFilm}/
     * {@code getPlayingFilm} in both {@code ActorExporter} and {@code ActorImporter}).
     */
    private static String interpKey(Keyframe<?> keyframe)
    {
        Interpolation interp = keyframe.getInterpolation();

        if (interp == null || interp.getInterp() == null)
        {
            return "";
        }

        String key = interp.getKey();

        return key == null ? "" : key;
    }

    private static long mixLong(long h, long v)
    {
        h ^= v;
        h *= FNV_PRIME;

        return h;
    }

    private static long mixString(long h, String s)
    {
        if (s == null)
        {
            return mixLong(h, 0L);
        }

        for (int i = 0; i < s.length(); i++)
        {
            h = mixLong(h, s.charAt(i));
        }

        return h;
    }

    /** Numeric keyframe value (the only kind this bridge exports — see {@code ActorExporter}). */
    private static long mixValue(long h, Object value)
    {
        if (value instanceof Number number)
        {
            return mixLong(h, Double.doubleToLongBits(number.doubleValue()));
        }

        if (value == null)
        {
            return mixLong(h, 0L);
        }

        /* Non-Number-werte aus dem properties-topf (2026-07-18): über ihre FELDER folden, NICHT
         * toString (gemessen: Color/GlowSettings/PaintSettings ueberschreiben toString NICHT →
         * identity-hash waere WERTBLIND, eine reine farb-aenderung wuerde den push nicht ausloesen).
         * Typ-spezifisch die float-felder mischen, damit jede material/pose-wert-aenderung den hash
         * aendert = live-push. Unbekannte typen → getARGBColor/toString-fallback bzw. 0L (kein regress). */
        if (value instanceof mchorse.bbs_mod.utils.colors.Color color)
        {
            /* getARGBColor packt r/g/b/a in einen int → aendert sich bei jeder farb/alpha-aenderung. */
            return mixLong(h, color.getARGBColor());
        }

        /* GlowSettings/PaintSettings via MaterialCompat: both classes are absent from the BBS-CML
         * jar this fork compiles against (measured). When absent these branches never match and
         * the value falls through to the toData()-fallback below — same behaviour as any other
         * unknown type, so the fingerprint stays correct either way. */
        if (com.blenderbridge.actors.compat.MaterialCompat.isGlow(value))
        {
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "r")));
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "g")));
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "b")));
            return mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "intensity")));
        }

        if (com.blenderbridge.actors.compat.MaterialCompat.isPaint(value))
        {
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "r")));
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "g")));
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "b")));
            h = mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "intensity")));
            return mixLong(h, Float.floatToIntBits(com.blenderbridge.actors.compat.MaterialCompat.readFloat(value, "shaderShadow")));
        }

        /* Sonstige (PoseTransform per-bone, Illusion, TextureBlend, ...): ihr toData() serialisiert
         * ALLE felder in eine BaseType-datenstruktur, deren toString wert-sensitiv ist (im gegensatz
         * zum objekt-toString). Das faengt per-bone-pose + material generisch. Wirft die klasse kein
         * toData (kein IMapSerializable), fallback auf objekt-toString (schlimmstenfalls identity =
         * kein regress ggue. dem alten 0L). */
        try
        {
            if (value instanceof mchorse.bbs_mod.data.IMapSerializable ser)
            {
                return mixString(h, ser.toData().toString());
            }
        }
        catch (Throwable ignored)
        {
            /* toData warf (unerwartet) → best-effort weiter unten */
        }

        return mixString(h, value.toString());
    }

    /* ================================================================= *
     *  Film resolution — identical precedence to ActorExporter/ActorImporter *
     * ================================================================= */

    /** The film loaded in the film editor panel, else the first playing film, else null. */
    private static Film resolveOpenFilm()
    {
        Film editorFilm = getEditorFilm();

        if (editorFilm != null)
        {
            return editorFilm;
        }

        return getPlayingFilm();
    }

    /** peekDashboard() — NOT getDashboard(): the latter constructs a dashboard as a side effect. */
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
}
