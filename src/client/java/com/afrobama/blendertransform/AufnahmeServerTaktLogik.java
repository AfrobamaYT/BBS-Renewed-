package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.VideoRecorder;

/**
 * Logik hinter {@code IntegratedServerMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Waehrend einer Aufnahme laeuft der Server nicht mehr in Echtzeit, sondern so schnell (oder
 * langsam), wie der Rekorder Bilder wegschreibt: der Rekorder zaehlt {@code serverTicks} hoch,
 * diese Stelle holt die Differenz nach. Ohne das haette ein Video bei 20 Bildern je Sekunde
 * Aufnahmetempo die Welt in Zeitlupe — die Weltzeit haengt am echten Takt, das Video am
 * gezaehlten.</p>
 *
 * <p>⚠️ DIE SCHLEIFE DARF NULLMAL LAUFEN. Ist der Rekorder noch nicht so weit, wird in diesem
 * Aufruf gar nicht getaktet — das ist der Sinn der Sache, kein Fehler. Wer hier ein
 * "mindestens einmal" einbaut, laesst die Welt der Aufnahme davonlaufen.</p>
 *
 * <p>⚠️ DIE BEDINGUNG WIRD JE RUNDE NEU GELESEN, nicht vorher ausgerechnet. {@code serverTicks}
 * steigt von aussen; eine vorab bestimmte Anzahl wuerde das verpassen.</p>
 */
public final class AufnahmeServerTaktLogik
{
    private AufnahmeServerTaktLogik() {}

    /**
     * @param original der urspruengliche {@code MinecraftServer.tickServer}-Aufruf. Kommt als
     *                 {@link Runnable} herein, damit MixinExtras' {@code Operation} im Mixin
     *                 bleibt und dieser Kernel sie nicht kennen muss.
     */
    public static void takten(Runnable original)
    {
        VideoRecorder rekorder = BBSModClient.getVideoRecorder();

        if (rekorder == null || !rekorder.isRecording())
        {
            original.run();

            return;
        }

        while (rekorder.lastServerTicks < rekorder.serverTicks)
        {
            original.run();

            rekorder.lastServerTicks += 1;
        }
    }
}
