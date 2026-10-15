package com.afrobama.blendertransform;

/**
 * Logik des sprint-ramp-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code TrackerSprintRampMixin} (2026-07-26, brücken-umbau). Der handler
 * dort ist jetzt ein einzeiler, der hierher delegiert; dieser code kann bei jedem kernel-swap
 * getauscht werden, ohne dass {@code TrackerClientClip} neu gewoben werden muss.</p>
 *
 * <p>Ersetzt BBS' sprint-FOV-ramp (0.2/tick, "mushy") durch vanilla MCs 0.5/tick ("snappy").
 * Feuert einmal pro tick (nicht pro entity in einer schleife) — brücken-overhead unkritisch.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. Alle felder von {@code TrackerClientClip} werden
 * über reflection gelesen/geschrieben (wie im original) — {@code TrackerClientClip} selbst ist
 * ein BBS-typ und dürfte ohnehin in der signatur stehen, wird hier aber gar nicht gebraucht.</p>
 */
public final class TrackerSprintRampLogik
{
    private TrackerSprintRampLogik() {}

    /**
     * Wird vom gewobenen {@code TrackerClientClip.updateSprintFov}-handler pro tick gerufen.
     *
     * @return {@code true} = original-rumpf abbrechen (wir haben die felder schon gesetzt),
     *         {@code false} = original weiterlaufen lassen (reflection ist gescheitert)
     */
    public static boolean vanillaRamp(boolean entitySprinting, int tick)
    {
        try
        {
            Class<?> cls = Class.forName("mchorse.bbs_mod.camera.clips.misc.TrackerClientClip");

            /* Match BBS's tick-skip guard: only one update per tick. */
            java.lang.reflect.Field lastTickField = cls.getDeclaredField("lastFovTick");
            lastTickField.setAccessible(true);
            int lastTick = lastTickField.getInt(null);
            if (tick == lastTick) return true;
            lastTickField.setInt(null, tick);

            java.lang.reflect.Field factorF = cls.getDeclaredField("sprintFovFactor");
            java.lang.reflect.Field lastFactorF = cls.getDeclaredField("lastSprintFovFactor");
            java.lang.reflect.Field isSprintingF = cls.getDeclaredField("isSprinting");

            factorF.setAccessible(true);
            lastFactorF.setAccessible(true);
            isSprintingF.setAccessible(true);

            float currentFactor = factorF.getFloat(null);
            lastFactorF.setFloat(null, currentFactor);
            isSprintingF.setBoolean(null, entitySprinting);

            /* Vanilla sprint multiplier + vanilla ramp step. */
            float target = entitySprinting ? 1.15F : 1.0F;
            float newFactor = currentFactor + (target - currentFactor) * 0.5F;
            factorF.setFloat(null, newFactor);

            return true;
        }
        catch (Throwable t)
        {
            /* Let upstream run if reflection fails. */
            return false;
        }
    }
}
