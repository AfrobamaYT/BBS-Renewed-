// Coded by Afrobama/Claude — License-clean reflective shim for the
// Flashback mod (by Moulberry).
//
// ARCHITECTURE RATIONALE
// ----------------------
// Flashback ("All rights reserved") cannot be vendored, mixin-merged, or
// even depended on at compile time without a code-license violation. Yet
// it's the only Fabric replay recorder that captures the
// IntegratedServer state cleanly enough to scrub and re-shoot footage
// inside it later — which is exactly what the BBS editor needs to do
// when we want to "step inside an old recording, re-pose, re-render."
//
// Solution: a RUNTIME shim. The user installs Flashback alongside
// BBSTools as a normal Fabric mod. We never `import com.moulberry.*`
// anywhere — every call is reflective. If Flashback is missing, every
// method here cleanly returns false / no-ops and BBSTools continues to
// run with its own internal recorder (BBSActionRecorder) as the only
// option. If Flashback is present, the user gets a second recording
// pipeline that produces .zip files compatible with Flashback's
// scrubber + BBS-editor-inside-playback workflow.
//
// API surface (all static):
//   isAvailable()    — FabricLoader probe, cached
//   startRecording() — kick off a Flashback recording session
//   stopRecording()  — finalise and write the .zip
//   isRecording()    — best-effort state probe
//   openReplay(zip)  — load an existing .zip in Flashback's player
//
// Every reflective call sits inside try-catch that returns false +
// logs the failure ONCE (per JVM run) so the console isn't spammed
// when method names drift between Flashback releases. When that
// happens, we update the targetMethod constants below — no compile-
// time breakage, the bridge just no-ops until repaired.
//
// Driver UI: FlashbackBridgeCommand (`/bbstools record-world …`).
package com.afrobama.bbstools.client.flashback;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlashbackBridge
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/flashback-bridge");

    /** Flashback's mod id as declared in its fabric.mod.json. */
    private static final String FLASHBACK_MOD_ID = "flashback";

    /** Primary entry-point class. We probe this first; if it's gone the
     *  bridge gives up cleanly. Moulberry has renamed the package once
     *  before (early dev → release), keeping a small candidate list
     *  makes the shim survive minor re-orgs. */
    private static final String[] CANDIDATE_ENTRY_CLASSES =
    {
        "com.moulberry.flashback.Flashback",
        "com.moulberry.flashback.FlashbackClient",
        "com.moulberry.flashback.record.Recorder",
    };

    /** Method-name candidates per logical operation. Reflection probes
     *  each in order and uses the first that resolves on the entry
     *  class. Insulates us from rename churn between Flashback releases. */
    private static final String[] START_METHODS =
    {
        "startRecordingReplay",
        "startRecording",
        "beginRecording",
        "startReplay",
    };
    private static final String[] STOP_METHODS =
    {
        "finishRecordingReplay",
        "finishRecording",
        "stopRecording",
        "endRecording",
    };
    private static final String[] IS_RECORDING_METHODS =
    {
        "isRecording",
        "isRecordingReplay",
        "recording",
    };
    private static final String[] OPEN_REPLAY_METHODS =
    {
        "openReplay",
        "loadReplay",
        "playReplay",
        "openReplayFile",
    };

    /** Cache so we only emit each diagnostic line once per JVM. */
    private static final Set<String> LOGGED_ONCE = new HashSet<>();

    private static volatile Boolean availableCached;
    private static volatile Class<?> entryClassCached;

    private FlashbackBridge() {}

    /* ====================================================================== */
    /* Public API                                                              */
    /* ====================================================================== */

    /**
     * Cheap, cached: returns true iff FabricLoader sees the Flashback
     * mod id loaded. Does not touch reflection beyond the FabricLoader
     * registry lookup, so it's safe to call every tick.
     */
    public static boolean isAvailable()
    {
        Boolean cached = availableCached;
        if (cached != null) return cached;

        boolean v;
        try
        {
            v = FabricLoader.getInstance().isModLoaded(FLASHBACK_MOD_ID);
        }
        catch (Throwable t)
        {
            logOnce("availability-probe-failed",
                () -> LOG.warn("FabricLoader probe for '{}' threw: {}", FLASHBACK_MOD_ID, t.toString()));
            v = false;
        }

        availableCached = v;
        if (v)
        {
            logOnce("flashback-detected",
                () -> LOG.info("Flashback detected — license-clean reflective bridge enabled"));
        }
        else
        {
            logOnce("flashback-missing",
                () -> LOG.info("Flashback mod not loaded — /bbstools record-world will be a no-op"));
        }
        return v;
    }

    /** Reflectively asks Flashback to begin a recording session. */
    public static boolean startRecording()
    {
        if (!isAvailable()) return false;
        return invokeNoArg(START_METHODS, "startRecording");
    }

    /** Reflectively asks Flashback to finalise & flush the .zip. */
    public static boolean stopRecording()
    {
        if (!isAvailable()) return false;
        return invokeNoArg(STOP_METHODS, "stopRecording");
    }

    /**
     * Best-effort probe. If Flashback exposes an is-recording getter
     * (any of the candidate names) we return its value; otherwise
     * returns false. We deliberately don't cache this — recording
     * state changes per call by design.
     */
    public static boolean isRecording()
    {
        if (!isAvailable()) return false;

        Class<?> entry = resolveEntryClass();
        if (entry == null) return false;

        for (String name : IS_RECORDING_METHODS)
        {
            try
            {
                Method m = entry.getMethod(name);
                Object result = m.invoke(null);
                if (result instanceof Boolean b) return b;
            }
            catch (NoSuchMethodException ignored) { /* try next */ }
            catch (Throwable t)
            {
                logOnce("isRecording-" + name,
                    () -> LOG.warn("Flashback {}() reflective call failed: {}", name, t.toString()));
                return false;
            }
        }

        /* No getter found — degrade gracefully. */
        logOnce("isRecording-no-method",
            () -> LOG.info("Flashback has no recognised is-recording getter; treating as unknown=false"));
        return false;
    }

    /**
     * Reflectively asks Flashback to open and play back the given
     * replay zip. No-op if Flashback isn't loaded or the load API
     * isn't recognised.
     */
    public static void openReplay(File replayZip)
    {
        if (replayZip == null)
        {
            LOG.warn("openReplay called with null file");
            return;
        }
        if (!isAvailable()) return;

        Class<?> entry = resolveEntryClass();
        if (entry == null) return;

        /* Try (File) signature first, then (String path). */
        for (String name : OPEN_REPLAY_METHODS)
        {
            try
            {
                Method m = entry.getMethod(name, File.class);
                m.invoke(null, replayZip);
                LOG.info("Flashback openReplay({}) succeeded via {}(File)", replayZip.getName(), name);
                return;
            }
            catch (NoSuchMethodException ignored) { /* try String variant */ }
            catch (Throwable t)
            {
                logOnce("openReplay-" + name + "-file",
                    () -> LOG.warn("Flashback {}(File) call failed: {}", name, t.toString()));
                return;
            }

            try
            {
                Method m = entry.getMethod(name, String.class);
                m.invoke(null, replayZip.getAbsolutePath());
                LOG.info("Flashback openReplay({}) succeeded via {}(String)", replayZip.getName(), name);
                return;
            }
            catch (NoSuchMethodException ignored) { /* try next candidate */ }
            catch (Throwable t)
            {
                logOnce("openReplay-" + name + "-string",
                    () -> LOG.warn("Flashback {}(String) call failed: {}", name, t.toString()));
                return;
            }
        }

        logOnce("openReplay-no-method",
            () -> LOG.warn("No recognised Flashback open-replay method found; "
                + "tried {}", java.util.Arrays.toString(OPEN_REPLAY_METHODS)));
    }

    /* ====================================================================== */
    /* Internals                                                               */
    /* ====================================================================== */

    /** Resolve and cache the Flashback entry-point class. */
    private static Class<?> resolveEntryClass()
    {
        Class<?> cached = entryClassCached;
        if (cached != null) return cached;

        for (String fqcn : CANDIDATE_ENTRY_CLASSES)
        {
            try
            {
                Class<?> c = Class.forName(fqcn, false, FlashbackBridge.class.getClassLoader());
                entryClassCached = c;
                logOnce("entry-class-" + fqcn,
                    () -> LOG.info("Flashback entry class resolved: {}", fqcn));
                return c;
            }
            catch (ClassNotFoundException ignored) { /* try next */ }
            catch (Throwable t)
            {
                logOnce("entry-class-fail-" + fqcn,
                    () -> LOG.warn("Class.forName({}) threw: {}", fqcn, t.toString()));
            }
        }

        logOnce("entry-class-none",
            () -> LOG.warn("No recognised Flashback entry class on classpath; "
                + "tried {}", java.util.Arrays.toString(CANDIDATE_ENTRY_CLASSES)));
        return null;
    }

    /** Shared helper: invoke a no-arg static method from a name list. */
    private static boolean invokeNoArg(String[] candidateNames, String opLabel)
    {
        Class<?> entry = resolveEntryClass();
        if (entry == null) return false;

        for (String name : candidateNames)
        {
            try
            {
                Method m = entry.getMethod(name);
                m.invoke(null);
                LOG.info("Flashback {} -> {}() succeeded", opLabel, name);
                return true;
            }
            catch (NoSuchMethodException ignored) { /* try next */ }
            catch (Throwable t)
            {
                logOnce(opLabel + "-" + name,
                    () -> LOG.warn("Flashback {}() reflective call failed: {}", name, t.toString()));
                return false;
            }
        }

        logOnce(opLabel + "-no-method",
            () -> LOG.warn("No recognised Flashback {} method found; tried {}",
                opLabel, java.util.Arrays.toString(candidateNames)));
        return false;
    }

    /** Emit a log line at most once per JVM per key. Prevents
     *  per-tick spam when Flashback's API drifts. */
    private static void logOnce(String key, Runnable emit)
    {
        synchronized (LOGGED_ONCE)
        {
            if (!LOGGED_ONCE.add(key)) return;
        }
        try { emit.run(); }
        catch (Throwable t) { LOG.warn("logOnce[{}] emit failed: {}", key, t.toString()); }
    }

}
