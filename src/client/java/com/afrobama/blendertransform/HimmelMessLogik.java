package com.afrobama.blendertransform;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.SkyRenderState;

/**
 * Logik hinter {@code HimmelMessMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>⚗️ MESSFUEHLER, kein Feature — laeuft nur mit eingeschaltetem {@code Messpunkt}.</p>
 *
 * <p>⛔ WARUM GERADE HIER DIE BRUECKE AM MEISTEN BRINGT. Im Mixin stand woertlich:
 * <em>"Mixin-KLASSEN sind nicht hotswapbar. Jede Messung hier kostet einen Boot, darum nimmt sie
 * gleich mehrere Werte mit."</em> Genau das ist der Grund, aus dem die Bruecke gebaut wurde. Ab
 * hier kostet eine geaenderte Messung keinen Boot mehr, sondern einen Swap — und der Zwang,
 * vorsorglich moeglichst viel auf einmal mitzunehmen, faellt weg.</p>
 *
 * <p>Die Frage, die dieser Fuehler beantwortet: wird der Himmelszustand ueberhaupt EINGESAMMELT?
 * Zaehler bleibt 0 → der Pass laeuft gar nicht, die Suche geht in den Bildgraphen. Zaehler
 * laeuft hoch → der Zustand wird gesammelt und beim Zeichnen verworfen, dann ist das Ziel oder
 * die Pipeline schuld. Von aussen sehen beide Faelle gleich aus (schwarze Flaeche).</p>
 */
public final class HimmelMessLogik
{
    private HimmelMessLogik() {}

    /**
     * @param teilbild Teilbild-Fortschritt, den Vanilla hereinreicht
     * @param kamera   die Kamera des Durchgangs — nur auf Vorhandensein geprueft
     * @param zustand  der einzusammelnde Himmelszustand
     */
    public static void himmelGemessen(float teilbild, Camera kamera, SkyRenderState zustand)
    {
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("sky.extract",
            "zustand=" + (zustand == null ? "null" : zustand.getClass().getSimpleName())
            + " | teilbild=" + teilbild
            + " | kamera=" + (kamera == null ? "null" : "da"));
    }
}
