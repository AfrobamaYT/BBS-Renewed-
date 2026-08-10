package com.afrobama.bbstools.kernel.logik;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Logik hinter {@code FormPropertiesEmptyChannelSkipMixin}: leere Keyframe-Kanaele gar nicht
 * erst anfassen.
 *
 * <p>WOFUER (gemessen 2026-05-17): {@code KeyframeChannel.interpolate()} steigt bei einem
 * leeren Kanal schon frueh aus — der AUFRUFER aber nicht. {@code FormProperties.applyProperty}
 * laeuft vorher {@code FormUtils.getProperty(form, propertyId)}, einen reflektiven Gang durch
 * den Wertebaum der Form, und merkt erst danach, dass es nichts zu tun gibt. Bei 30
 * Knochenkanaelen × 200 Wiedergaben × 20 Takten sind das 120 000 reflektive Gaenge je Sekunde
 * fuer Kanaele, die nichts tun.</p>
 *
 * <p>⚠️ DER ABBRUCH UEBERSPRINGT AUCH DIE BUCHFUEHRUNG — das {@code setRuntimeValue(null)} je
 * Takt auf leeren Nicht-Pose-Kanaelen. Das ist Absicht: {@code resetProperties()} deckt die
 * echten Ruecksetz-Wege ab. Faellt trotzdem etwas beim Zuruecksetzen auf, ist der Schalter
 * {@code BBSPerfConfig.ENABLE_EMPTY_CHANNEL_SKIP} der erste Griff.</p>
 *
 * @return {@code true} = BBS' eigener Rumpf wird uebersprungen
 */
public final class LeerkanalUeberspringenLogik
{
    private LeerkanalUeberspringenLogik() {}

    public static boolean ueberspringen(KeyframeChannel<?> kanal)
    {
        return BBSPerfConfig.ENABLE_EMPTY_CHANNEL_SKIP && kanal != null && kanal.isEmpty();
    }
}
