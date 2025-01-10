package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.utils.pose.Pose;

public interface ICubicModel
{
    public Model getModel();

    public Pose getSneakingPose();

    public Animations getAnimations();
}