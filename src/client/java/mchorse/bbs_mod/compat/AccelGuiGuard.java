package mchorse.bbs_mod.compat;

import java.lang.reflect.Method;

/**
 * Schaltet die GUI-beschleunigung von <i>acceleratedrendering</i> für einen render-abschnitt ab.
 *
 * <p>WOFÜR: beim 4K-export rutschen die status-bars (herzen/hunger/XP) aus dem rahmen. Ursache
 * ist, dass die mod MCs {@code BufferSource} gegen ihren GPU-beschleunigten tauscht
 * ({@code core.mixins.buffers.BufferSourceMixin#initAcceleration}). Die HUD-quads landen dann in
 * IHREN puffern und rastern erst später — unter der editor-ortho, die mit einer override-breite
 * positioniert wurde. Ergebnis: verschoben und zu klein.</p>
 *
 * <p>WARUM NICHT FLUSHEN: der erste versuch (2026-07-24, commit 31436f3e7) rief
 * {@code drawBuffers(LayerDrawType.ALL)} an den flush-stellen. Das rastert ALLE offenen puffer,
 * auch die des welt-renders, und riss mitten im tracker-frame den kamera-state mit → viewport-flip
 * bei hand+HUD. Revertet in c28140bed.</p>
 *
 * <p>WARUM DIESER WEG: die mod bringt selbst eine API dafür mit — ihre eigene config-doku sagt
 * "<i>unless mods explicitly disable it temporarily when rendering their own item models</i>".
 * {@code dontAccelerateInGui()} legt einen wert auf einen internen stack, {@code resetGuiAcceleration()}
 * nimmt ihn wieder herunter. Die quads werden damit gar nicht erst beschleunigt — es gibt nichts
 * zu flushen, also auch keinen state, der verlorengehen kann.</p>
 *
 * <p>SOFT-DEP: alles reflektiv, gecached. Ohne die mod sind alle aufrufe no-ops (muster wie
 * {@code FracturePhysicsBridge}) — kein harter link, kein crash wenn die mod fehlt.</p>
 *
 * <p>PAARWEISE BENUTZEN, sonst wächst der stack der mod:</p>
 * <pre>
 *   AccelGuiGuard.push();
 *   try { ...HUD zeichnen... }
 *   finally { AccelGuiGuard.pop(); }
 * </pre>
 */
public final class AccelGuiGuard
{
    private AccelGuiGuard() {}

    private static final String ENTITY_FEATURE =
        "com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature";
    private static final String ITEM_FEATURE =
        "com.github.argon4w.acceleratedrendering.features.items.AcceleratedItemRenderingFeature";

    private static Method entityOff, entityReset, itemOff, itemReset;
    private static boolean resolved;
    private static boolean available;

    /** Wie tief wir selbst geschachtelt sind — schützt vor unpaarigem pop(). */
    private static int depth;

    private static synchronized void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;

        try
        {
            Class<?> entity = Class.forName(ENTITY_FEATURE);
            Class<?> item = Class.forName(ITEM_FEATURE);

            entityOff = entity.getMethod("dontAccelerateInGui");
            entityReset = entity.getMethod("resetGuiAcceleration");
            itemOff = item.getMethod("dontAccelerateInGui");
            itemReset = item.getMethod("resetGuiAcceleration");

            available = true;
        }
        catch (Throwable e)
        {
            /* Mod nicht installiert oder API umbenannt — dann bleibt alles ein no-op.
             * Kein log-spam: das ist der normale fall in der dev-instanz, wo die mod nur
             * modCompileOnly ist und zur laufzeit fehlt. */
            available = false;
        }
    }

    /** Ist die mod da und die API auflösbar? */
    public static boolean isAvailable()
    {
        resolve();

        return available;
    }

    /**
     * GUI-beschleunigung für den folgenden abschnitt abschalten.
     *
     * <p>Schachtelt sauber: die mod führt selbst einen stack, jeder {@link #push} braucht genau
     * einen {@link #pop}.</p>
     */
    public static void push()
    {
        resolve();

        if (!available)
        {
            return;
        }

        try
        {
            entityOff.invoke(null);
            itemOff.invoke(null);
            depth++;
        }
        catch (Throwable e)
        {
            /* Rendern darf daran nicht sterben — im zweifel bleibt die beschleunigung an und der
             * 4K-bug tritt auf, statt dass der frame abbricht. */
        }
    }

    /** Den zustand von vor dem passenden {@link #push} wiederherstellen. */
    public static void pop()
    {
        resolve();

        if (!available || depth <= 0)
        {
            return;
        }

        try
        {
            itemReset.invoke(null);
            entityReset.invoke(null);
            depth--;
        }
        catch (Throwable e)
        {
            /* siehe push() */
        }
    }
}
