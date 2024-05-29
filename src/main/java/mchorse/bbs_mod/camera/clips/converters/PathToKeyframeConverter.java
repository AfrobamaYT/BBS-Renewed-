package mchorse.bbs_mod.camera.clips.converters;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.math.IInterpolation;

public class PathToKeyframeConverter implements IClipConverter<PathClip, KeyframeClip>
{
    @Override
    public KeyframeClip convert(PathClip path)
    {
        int c = path.size();

        long duration = path.duration.get();
        KeyframeClip keyframe = new KeyframeClip();

        keyframe.copy(path);
        IInterpolation pos = path.interpolationPoint.get();
        IInterpolation angle = path.interpolationAngle.get();

        long x;

        for (int i = 0; i < path.size(); i++)
        {
            Position point = path.points.get(i);

            x = (int) (i / (c - 1F) * duration);

            int index = keyframe.x.insert(x, (float) point.point.x);
            keyframe.y.insert(x, (float) point.point.y);
            keyframe.z.insert(x, (float) point.point.z);
            keyframe.yaw.insert(x, point.angle.yaw);
            keyframe.pitch.insert(x, point.angle.pitch);
            keyframe.roll.insert(x, point.angle.roll);
            keyframe.fov.insert(x, point.angle.fov);

            keyframe.x.get(index).setInterpolation(pos);
            keyframe.y.get(index).setInterpolation(pos);
            keyframe.z.get(index).setInterpolation(pos);
            keyframe.yaw.get(index).setInterpolation(angle);
            keyframe.pitch.get(index).setInterpolation(angle);
            keyframe.roll.get(index).setInterpolation(angle);
            keyframe.fov.get(index).setInterpolation(angle);
        }

        return keyframe;
    }
}