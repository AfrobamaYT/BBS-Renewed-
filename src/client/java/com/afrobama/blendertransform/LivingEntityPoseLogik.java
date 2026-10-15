package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.renderer.MobFormPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Logik hinter {@code LivingEntityRendererMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Dieselben drei Schritte wie in {@code ModelFeatureRendererLogik}, nur am anderen Renderer:
 * Vanilla zeichnet Lebewesen ueber zwei Wege, und BBS' Pose muss an beiden aufgelegt werden.</p>
 *
 * <p>⚠️ DREI HANDLER, EINE KLAMMER. {@link #stempeln} haelt den Zustand am Anfang fest,
 * {@link #auflegen} setzt die Pose NACH {@code setupAnim} (vorher wuerde Vanilla sie gleich
 * wieder ueberschreiben), {@link #zuruecksetzen} nimmt sie am Ende herunter. Faellt der letzte
 * Schritt aus, traegt das naechste Lebewesen mit demselben Renderer noch die fremde Pose.</p>
 */
public final class LivingEntityPoseLogik
{
    private LivingEntityPoseLogik() {}

    /**
     * @param zustand der Renderzustand, den Vanilla gerade aufbaut
     *
     * <p>Anders als {@link #auflegen} nimmt {@code MobFormPose.stempeln} den konkreten
     * {@link EntityRenderState} und nicht {@code Object} — der Typ steht hier deshalb
     * ausgeschrieben.</p>
     */
    public static void stempeln(EntityRenderState zustand)
    {
        MobFormPose.stempeln(zustand);
    }

    /** Nach {@code EntityModel.setupAnim} — vorher waere die Pose sofort wieder weg. */
    public static void auflegen(Object zustand)
    {
        MobFormPose.auflegen(zustand);
    }

    public static void zuruecksetzen()
    {
        MobFormPose.zuruecksetzen();
    }
}
