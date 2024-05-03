package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.ArrayList;
import java.util.List;

public class CameraClipContext extends ClipContext<CameraClip, Position>
{
    public List<IEntity> entities = new ArrayList<>();

    @Override
    public boolean apply(Clip clip, Position position)
    {
        if (clip instanceof CameraClip)
        {
            this.currentLayer = clip.layer.get();
            this.relativeTick = this.ticks - clip.tick.get();

            ((CameraClip) clip).apply(this, position);

            this.count += 1;

            return true;
        }

        return false;
    }

    public void shutdown()
    {
        if (this.clips == null)
        {
            return;
        }

        for (Clip clip : this.clips.get())
        {
            if (clip instanceof CameraClip)
            {
                ((CameraClip) clip).shutdown(this);
            }
        }
    }
}