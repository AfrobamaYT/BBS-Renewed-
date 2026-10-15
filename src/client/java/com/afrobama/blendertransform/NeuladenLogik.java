package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;

/**
 * Logik hinter den beiden Mixins, die auf ein RESSOURCEN-NEULADEN reagieren —
 * {@code LanguageManagerMixin} und {@code ResourceReloadLoggerMixin}.
 *
 * <p>Zwei Mixins, eine Logik-Klasse: beide haengen am selben Ereignis und muessen bei einer
 * Aenderung ohnehin zusammen betrachtet werden. Die Reihenfolge zwischen ihnen bestimmt
 * Vanilla, nicht wir.</p>
 *
 * <p>Herausgeloest, damit sie hotswapbar ist.</p>
 */
public final class NeuladenLogik
{
    private NeuladenLogik() {}

    /** {@code LanguageManager.onResourceManagerReload} — Sprachdateien sind neu eingelesen. */
    public static void spracheNeuladen()
    {
        BBSModClient.reloadLanguage(BBSModClient.getLanguageKey());
    }

    /**
     * {@code ResourceLoadStateTracker.finishReload} — der ganze Ressourcen-Durchgang ist durch.
     *
     * <p>Die Klaenge werden verworfen, nicht neu geladen: BBS zieht sie beim naechsten Bedarf
     * selbst nach. Ein Neuladen an dieser Stelle wuerde jeden Ressourcen-Reload um die volle
     * Audio-Dekodierung verlaengern.</p>
     */
    public static void klaengeVerwerfen()
    {
        BBSModClient.getSounds().deleteSounds();
    }
}
