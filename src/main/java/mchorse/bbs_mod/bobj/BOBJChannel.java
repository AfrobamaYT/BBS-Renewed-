package mchorse.bbs_mod.bobj;

import java.util.ArrayList;
import java.util.List;

public class BOBJChannel
{
    public String path;
    public int index;
    public List<BOBJKeyframe> keyframes = new ArrayList<BOBJKeyframe>();

    public BOBJChannel(String path, int index)
    {
        this.path = path;
        this.index = index;
    }

    public float calculate(float frame)
    {
        int c = this.keyframes.size();

        if (c <= 0)
        {
            return 0;
        }

        if (c == 1)
        {
            return this.keyframes.get(0).value;
        }

        BOBJKeyframe keyframe = this.keyframes.get(0);

        if (keyframe.frame > frame)
        {
            return keyframe.value;
        }

        for (int i = 0; i < c; i++)
        {
            keyframe = this.keyframes.get(i);

            if (keyframe.frame > frame && i != 0)
            {
                BOBJKeyframe prev = this.keyframes.get(i - 1);

                float x = (frame - prev.frame) / (keyframe.frame - prev.frame);

                return prev.interpolate(x, keyframe);
            }
        }

        return keyframe.value;
    }

    public BOBJKeyframe get(float frame, boolean next)
    {
        int c = this.keyframes.size();

        if (c == 0)
        {
            return null;
        }

        if (c == 1)
        {
            return this.keyframes.get(0);
        }

        BOBJKeyframe keyframe = null;

        for (int i = 0; i < c; i++)
        {
            keyframe = this.keyframes.get(i);

            if (keyframe.frame > frame && i != 0)
            {
                return next ? keyframe : this.keyframes.get(i - 1);
            }
        }

        return keyframe;
    }

    public void apply(BOBJBone bone, float frame)
    {
        if (this.path.equals("location"))
        {
            if (this.index == 0) bone.transform.translate.x = this.calculate(frame);
            else if (this.index == 1) bone.transform.translate.y = this.calculate(frame);
            else if (this.index == 2) bone.transform.translate.z = this.calculate(frame);
        }
        else if (this.path.equals("rotation"))
        {
            if (this.index == 0) bone.transform.rotate.x = this.calculate(frame);
            else if (this.index == 1) bone.transform.rotate.y = this.calculate(frame);
            else if (this.index == 2) bone.transform.rotate.z = this.calculate(frame);
        }
        else if (this.path.equals("scale"))
        {
            if (this.index == 0) bone.transform.scale.x = this.calculate(frame);
            else if (this.index == 1) bone.transform.scale.y = this.calculate(frame);
            else if (this.index == 2) bone.transform.scale.z = this.calculate(frame);
        }
    }

    public void applyInterpolate(BOBJBone bone, float frame, float x)
    {
        float value = this.calculate(frame);

        if (this.path.equals("location"))
        {
            if (this.index == 0) bone.transform.translate.x = value + (bone.transform.translate.x - value) * x;
            else if (this.index == 1) bone.transform.translate.y = value + (bone.transform.translate.y - value) * x;
            else if (this.index == 2) bone.transform.translate.z = value + (bone.transform.translate.z - value) * x;
        }
        else if (this.path.equals("rotation"))
        {
            if (this.index == 0) bone.transform.rotate.x = value + (bone.transform.rotate.x - value) * x;
            else if (this.index == 1) bone.transform.rotate.y = value + (bone.transform.rotate.y - value) * x;
            else if (this.index == 2) bone.transform.rotate.z = value + (bone.transform.rotate.z - value) * x;
        }
        else if (this.path.equals("scale"))
        {
            if (this.index == 0) bone.transform.scale.x = value + (bone.transform.scale.x - value) * x;
            else if (this.index == 1) bone.transform.scale.y = value + (bone.transform.scale.y - value) * x;
            else if (this.index == 2) bone.transform.scale.z = value + (bone.transform.scale.z - value) * x;
        }
    }
}