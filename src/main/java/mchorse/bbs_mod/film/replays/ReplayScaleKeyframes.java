package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Actor-local scale, outside the form hierarchy: world = T * R * S * form. */
public final class ReplayScaleKeyframes extends ReplayKeyframes
{
    private static final Map<IEntity, Sample> SAMPLES = Collections.synchronizedMap(new WeakHashMap<>());

    public final KeyframeChannel<Double> scaleX = new KeyframeChannel<>("scale_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> scaleY = new KeyframeChannel<>("scale_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> scaleZ = new KeyframeChannel<>("scale_z", KeyframeFactories.DOUBLE);

    public ReplayScaleKeyframes(String id)
    {
        super(id);
        this.add(this.scaleX);
        this.add(this.scaleY);
        this.add(this.scaleZ);
    }

    @Override
    public void apply(int tick, IEntity entity, List<String> groups)
    {
        super.apply(tick, entity, groups);

        if (this.scaleX.isEmpty() && this.scaleY.isEmpty() && this.scaleZ.isEmpty())
        {
            SAMPLES.remove(entity);
            return;
        }

        /* Match form properties: evaluate at the replay tick plus render transition.
         * Paused editor/seek passes transition zero; no previous-tick lag or smoothing
         * of constant keys. The renderer extracts the resulting vector before submit. */
        SAMPLES.put(entity, new Sample(this, tick));
    }

    public static Vector3f sample(IEntity entity, float partialTick, Vector3f target)
    {
        Sample value = SAMPLES.get(entity);

        if (value == null)
        {
            return target.set(1F);
        }

        return target.set(value.x(partialTick), value.y(partialTick), value.z(partialTick));
    }

    public static Matrix4f applyScale(Matrix4f matrix, IEntity entity, float partialTick)
    {
        Sample value = SAMPLES.get(entity);

        if (value != null)
        {
            matrix.scale(value.x(partialTick), value.y(partialTick), value.z(partialTick));
        }

        return matrix;
    }

    private record Sample(ReplayScaleKeyframes frames, int tick)
    {
        float x(float partialTick)
        {
            return this.frames.scaleX.interpolate(this.tick + partialTick, 1D).floatValue();
        }

        float y(float partialTick)
        {
            return this.frames.scaleY.interpolate(this.tick + partialTick, 1D).floatValue();
        }

        float z(float partialTick)
        {
            return this.frames.scaleZ.interpolate(this.tick + partialTick, 1D).floatValue();
        }
    }
}
