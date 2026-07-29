package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import java.util.Collection;

/**
 * Logik der Kanten-Zieh-Wache in der Zeitleiste — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIClipsDragEdgeCrashFixMixin} (2026-07-29, Aufgabe #56).</p>
 *
 * <p><b>Wogegen:</b> ein alter BBS-Fehler. Zieht man die linke oder rechte Kante eines Clips,
 * während die Greif-Listen leer sind, rechnet BBS {@code grabbedData.get(size() - 1)} — bei
 * {@code size() == 0} also Index −1 → {@code IndexOutOfBoundsException} mitten in der
 * Aufnahme-Sitzung. Der Ausweg ist, das Ziehen für diesen Frame ausfallen zu lassen: das ist
 * dasselbe, als hätte der Nutzer nichts gegriffen. Kein kaputter Zustand, kein Absturz — Maus
 * loslassen und noch einmal greifen genügt.</p>
 *
 * <p>⛔ DIE FELDER WERDEN REFLEKTIV GELESEN, nicht über ein {@code @Shadow}-Interface. Der
 * Kernel darf keine Mixin-Schnittstelle in der Signatur führen — ein Cast darauf wäre das
 * Klassenlader-Identitätsproblem, an dem hier schon einmal ein Feature gestorben ist.
 * {@link MixinBruecke#feldLesen} geht über {@code objekt.getClass()} und ist damit
 * loader-unabhängig.</p>
 *
 * <p>⚠ {@code feldLesen} liefert {@code null} für ZWEI verschiedene Dinge: „das Feld steht
 * wirklich auf null" und „das Feld gibt es nicht mehr" (Umbenennung im Upstream). Hier wird
 * bewusst NICHT versucht, das auseinanderzuhalten — die Wache verhält sich exakt wie vorher
 * ({@code null} ODER leer → abbrechen), denn genau dieses Verhalten verhindert den Absturz,
 * wenn das Feld echt null ist. Den Umbenennungs-Fall meldet {@link MixinBruecke} von sich aus
 * laut („feld in keiner oberklasse gefunden"), er bleibt also nicht unbemerkt.</p>
 */
public final class UIClipsDragEdgeGuardLogik
{
    private UIClipsDragEdgeGuardLogik() {}

    private static final String DATEN = "grabbedData";
    private static final String CLIPS = "grabbedClips";

    /** {@code true} = der Handler soll das Ziehen für diesen Frame abbrechen. */
    public static boolean abbrechen(Object clipsPanel)
    {
        return leer(MixinBruecke.feldLesen(clipsPanel, DATEN))
            || leer(MixinBruecke.feldLesen(clipsPanel, CLIPS));
    }

    /** Wie das Original: {@code null} zählt als leer — siehe Klassen-Javadoc. */
    private static boolean leer(Object wert)
    {
        return wert == null || (wert instanceof Collection && ((Collection<?>) wert).isEmpty());
    }
}
