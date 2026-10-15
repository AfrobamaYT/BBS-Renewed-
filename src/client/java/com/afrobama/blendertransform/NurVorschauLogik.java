package com.afrobama.blendertransform;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.VideoExportSession;

/**
 * Entscheidet, ob ein Akteur beim Zeichnen uebersprungen wird, weil er auf
 * "nur Vorschau" steht und gerade ein Export laeuft.
 *
 * <p>Fuer tutorial- und making-of-aufnahmen: hilfs-akteure, platzhalter und
 * referenz-posen bleiben im viewport sichtbar und fehlen im fertigen video.
 * Unterschied zu {@code Replay.enabled}: das schaltet den akteur UEBERALL ab.
 *
 * <p>⛔ EIGENE KLASSE, nicht inline im {@code BaseFilmController}: der steht bei
 * 1538 zeilen und darf laut arbeitsweise-gate nur noch schrumpfen. Die begruendung
 * gehoert trotzdem irgendwohin — also hierher.
 *
 * <p>⚠️ Der aufruf sitzt in {@code canUpdate(.., RENDER)} und NICHT in
 * {@code render()}: durch canUpdate laufen ALLE zeichenwege, sonst rutschte der
 * akteur ueber einen zweiten pfad doch ins bild.
 */
public final class NurVorschauLogik
{
    private NurVorschauLogik() {}

    /** True = diesen akteur JETZT nicht zeichnen. */
    public static boolean ueberspringen(Replay replay, boolean zeichnet)
    {
        return zeichnet
            && replay != null
            && replay.nurVorschau.get()
            && VideoExportSession.laeuftExport();
    }
}
