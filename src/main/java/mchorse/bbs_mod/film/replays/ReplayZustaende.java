package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * Swimming, flying and gliding channels of a replay, with the lean into a swim and the glide's
 * roll. Vanilla counts the last two up a tick at a time; a timeline is random access, so they are
 * written down rather than derived (BBS FS e383f1930). Kept apart from ReplayKeyframes, which is
 * at its size limit.
 */
public final class ReplayZustaende
{
    static final String[] IDS = {EntityState.SWIMMING, EntityState.FLYING, EntityState.GLIDING, EntityState.LEANING, EntityState.ROLL};

    private ReplayZustaende()
    {}

    static void anlegen(ReplayKeyframes keyframes)
    {
        for (String id : IDS) keyframes.add(new KeyframeChannel<>(id, KeyframeFactories.DOUBLE));
    }

    @SuppressWarnings("unchecked")
    private static KeyframeChannel<Double> kanal(ReplayKeyframes keyframes, String id)
    {
        return (KeyframeChannel<Double>) keyframes.get(id);
    }

    static void aufnehmen(ReplayKeyframes keyframes, IEntity entity, int tick)
    {
        kanal(keyframes, EntityState.SWIMMING).insert(tick, entity.isSwimming() ? 1D : 0D);
        kanal(keyframes, EntityState.FLYING).insert(tick, entity.isFlying() ? 1D : 0D);
        kanal(keyframes, EntityState.GLIDING).insert(tick, entity.isFallFlying() ? 1D : 0D);
        kanal(keyframes, EntityState.LEANING).insert(tick, (double) entity.getLeaningPitch(1F));
        kanal(keyframes, EntityState.ROLL).insert(tick, (double) entity.getRoll());
    }

    static void einfuegenInterpoliert(ReplayKeyframes keyframes, int tick)
    {
        for (String id : IDS) kanal(keyframes, id).insertInterpolated(tick);
    }

    static boolean an(ReplayKeyframes keyframes, String id, float tick)
    {
        return EntityState.isOn(kanal(keyframes, id).interpolate(tick));
    }

    static int roll(ReplayKeyframes keyframes, float tick)
    {
        return kanal(keyframes, EntityState.ROLL).interpolate(tick).intValue();
    }

    /** A mounted or sitting body neither swims nor flies. */
    static void anwenden(ReplayKeyframes keyframes, IEntity entity, float tick, boolean gesetzt)
    {
        entity.setSwimming(!gesetzt && an(keyframes, EntityState.SWIMMING, tick));
        entity.setFlying(!gesetzt && an(keyframes, EntityState.FLYING, tick));
        entity.setFallFlying(!gesetzt && an(keyframes, EntityState.GLIDING, tick));
        entity.setLeaningPitch(gesetzt ? 0F : kanal(keyframes, EntityState.LEANING).interpolate(tick).floatValue());
        entity.setRoll(roll(keyframes, tick));
    }

    /**
     * A real entity played from a replay (an actor, the first person player): the crouch, the
     * swim, the glide flag (what spreads the wings on every client), the pose they add up to and
     * the glide's roll. Creative flight is a permission on a real player and is not written.
     */
    public static void aufFigur(ReplayKeyframes keyframes, net.minecraft.world.entity.LivingEntity entity, float tick, boolean sneaking)
    {
        boolean swimming = an(keyframes, EntityState.SWIMMING, tick), gliding = an(keyframes, EntityState.GLIDING, tick);

        entity.setShiftKeyDown(sneaking);
        entity.setSwimming(swimming);
        ((mchorse.bbs_mod.mixin.EntityFlagInvoker) entity).bbs$setSharedFlag(EntityState.FALL_FLYING_FLAG, gliding);
        entity.setPose(EntityState.pose(gliding, swimming, sneaking));
        ((mchorse.bbs_mod.mixin.LivingEntityFallFlyAccessor) entity).bbs$setFallFlyTicks(roll(keyframes, tick));
    }

    /** Lean and roll onto an unticked vanilla body (a mob form's), which never grows them itself. */
    public static void koerper(net.minecraft.world.entity.LivingEntity body, float lean, int roll)
    {
        var zugang = (mchorse.bbs_mod.mixin.LivingEntityFallFlyAccessor) body;

        zugang.bbs$setSwimAmount(lean);
        zugang.bbs$setSwimAmountO(lean);
        zugang.bbs$setFallFlyTicks(roll);
    }
}
