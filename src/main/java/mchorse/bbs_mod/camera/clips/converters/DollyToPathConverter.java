package mchorse.bbs_mod.camera.clips.converters;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.interps.IInterp;

public class DollyToPathConverter implements IClipConverter<DollyClip, PathClip>
{
    @Override
    public PathClip convert(DollyClip dolly)
    {
        PathClip path = new PathClip();
        Position position = new Position();
        IInterp interpolation = dolly.interp.get();

        dolly.applyLast(new CameraClipContext(), position);

        path.copy(dolly);
        path.points.reset();
        path.points.add(dolly.position.get().copy());
        path.points.add(position);
        path.interpolationPoint.set(interpolation);
        path.interpolationAngle.set(interpolation);

        return path;
    }
}