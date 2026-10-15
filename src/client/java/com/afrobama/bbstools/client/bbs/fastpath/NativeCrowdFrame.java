package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;

import java.util.HashSet;
import java.util.Set;

/** One actual world frame: capture before preparation, cancel only actors captured in that frame. */
public final class NativeCrowdFrame
{
    private enum Phase { INACTIVE, COLLECTING, PREPARED }
    private static Phase phase = Phase.INACTIVE;
    private static final Set<Actor> captured = new HashSet<>();
    private static final java.util.Map<Actor, Appearance> appearances = new java.util.HashMap<>();

    private NativeCrowdFrame()
    {}

    public static void begin()
    {
        captured.clear();
        appearances.clear();
        phase = Phase.COLLECTING;
    }

    public static boolean collecting()
    {
        return phase == Phase.COLLECTING;
    }

    public static void capture(Form form, IEntity entity)
    {
        if (!collecting()) throw new IllegalStateException("Native crowd capture must precede GPU preparation");
        captured.add(new Actor(form, entity));
    }

    public static void capture(FastPathBatch.Submission submission)
    {
        capture(submission.form, submission.entity);
        Link texture = submission.form.texture.get();
        if (texture == null) texture = submission.instance.getTexture();
        appearances.put(new Actor(submission.form, submission.entity),
            new Appearance(Tier2hVertexPacking.resolvedColor(submission), texture));
    }

    static Appearance appearance(FastPathBatch.Submission submission)
    {
        return appearances.get(new Actor(submission.form, submission.entity));
    }

    static float[] palette(FastPathBatch.Submission submission,
        java.util.function.Function<FastPathBatch.Submission, mchorse.bbs_mod.cubic.render.CubicMatrixRenderer> extract)
    {
        submission.form.applyStates(submission.transition);
        try
        {
            if (com.afrobama.bbstools.perf.BBSPerfConfig.ENABLE_ARMY_BAKE && submission.entity != null)
            {
                float[] cached = AnimationBakeCache.get(submission.entity,
                    Math.round(submission.entity.getAge() + submission.transition));
                if (cached != null) return cached;
            }
            var renderer = extract.apply(submission);
            return renderer == null || renderer.matrices == null || renderer.matrices.isEmpty()
                ? null : AnimationBakeCache.flatten(renderer.matrices);
        }
        finally
        {
            submission.form.unapplyStates();
        }
    }

    record Appearance(int color, Link texture)
    {}

    public static void prepared()
    {
        if (!collecting()) throw new IllegalStateException("Native crowd prepared outside the collection phase");
        phase = Phase.PREPARED;
    }

    public static boolean cancels(Form form, IEntity entity)
    {
        return phase == Phase.PREPARED && captured.contains(new Actor(form, entity));
    }

    private record Actor(Form form, IEntity entity)
    {
        @Override
        public boolean equals(Object value)
        {
            return value instanceof Actor actor && actor.form == this.form && actor.entity == this.entity;
        }

        @Override
        public int hashCode()
        {
            return System.identityHashCode(this.form) * 31 + System.identityHashCode(this.entity);
        }
    }
}
