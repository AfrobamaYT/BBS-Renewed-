package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.renderer.MobFormPose;

/**
 * Logik hinter {@code ModelFeatureRendererMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Legt die BBS-Pose auf ein Vanilla-Modell auf, nachdem {@code Model.setupAnim} gelaufen ist,
 * und nimmt sie am Ende von {@code prepareModel} wieder herunter.</p>
 *
 * <p>⚠️ DIE BEIDEN GEHOEREN ZUSAMMEN. {@link #auflegen} ohne das spaetere
 * {@link #zuruecksetzen} laesst die Pose auf dem naechsten Modell stehen, das denselben
 * Renderer benutzt — der Fehler zeigt sich dann an einer Figur, die mit BBS nichts zu tun hat.
 * Der Mixin haengt sie deshalb an INVOKE-AFTER beziehungsweise RETURN derselben Methode.</p>
 *
 * <p>Vorher stand hier je ein Einzeiler direkt im gewobenen Handler. Das sah schon aus wie das
 * Brueckenmuster, war es aber nicht: {@code MobFormPose} liegt in {@code mchorse.bbs_mod.*},
 * also ausserhalb der Kernel-Pakete, und der gewobene Aufruf zeigte fest dorthin. Erst der Weg
 * ueber die Bruecke macht die Logik wirklich swapbar.</p>
 */
public final class ModelFeatureRendererLogik
{
    private ModelFeatureRendererLogik() {}

    /**
     * @param zustand der Renderzustand des Auftrags ({@code auftrag.state()})
     */
    public static void auflegen(Object zustand)
    {
        MobFormPose.auflegen(zustand);
    }

    public static void zuruecksetzen()
    {
        MobFormPose.zuruecksetzen();
    }
}
