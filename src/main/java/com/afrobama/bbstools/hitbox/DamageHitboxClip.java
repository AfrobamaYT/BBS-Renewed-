package com.afrobama.bbstools.hitbox;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * BBS film clip that produces a keyframed damage AABB. On each apply tick the
 * current interpolated values are pushed onto {@link FilmHitboxBridge} so the
 * server tick handler can pick them up and damage living entities inside.
 *
 * Ported from NeoForge BBSTools.
 */
public class DamageHitboxClip extends CameraClip
{
    public final KeyframeChannel<Double> posX = new KeyframeChannel<>("posX", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> posY = new KeyframeChannel<>("posY", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> posZ = new KeyframeChannel<>("posZ", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> sizeX = new KeyframeChannel<>("sizeX", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> sizeY = new KeyframeChannel<>("sizeY", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> sizeZ = new KeyframeChannel<>("sizeZ", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> damage = new KeyframeChannel<>("damage", KeyframeFactories.DOUBLE);

    public DamageHitboxClip()
    {
        super();

        this.add(this.posX);
        this.add(this.posY);
        this.add(this.posZ);
        this.add(this.sizeX);
        this.add(this.sizeY);
        this.add(this.sizeZ);
        this.add(this.damage);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        float t = context.relativeTick + context.transition;

        FilmHitboxBridge.activeBoxes.add(new double[]
        {
            this.posX.interpolate(t, 0.0D),
            this.posY.interpolate(t, 0.0D),
            this.posZ.interpolate(t, 0.0D),
            this.sizeX.interpolate(t, 1.0D),
            this.sizeY.interpolate(t, 1.0D),
            this.sizeZ.interpolate(t, 1.0D),
            /* Damage only while the film plays: in the paused editor the cursor parks inside the
             * clip and mobs in the box died within a second (audit 2026-10-14 #109). */
            context.playing ? this.damage.interpolate(t, 4.0D) : 0D
        });
    }

    @Override
    protected Clip create()
    {
        return new DamageHitboxClip();
    }

}
