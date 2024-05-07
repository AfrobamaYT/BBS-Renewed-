package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

public class CubicModel
{
    public final String id;
    public Model model;
    public Animations animations;
    public Link texture;
    public long loadTime;

    public String poseGroup;
    public boolean procedural;
    public boolean culling = true;

    public CubicModel(String id, Model model, Animations animations, Link texture)
    {
        this.id = id;
        this.model = model;
        this.animations = animations;
        this.texture = texture;

        this.loadTime = System.currentTimeMillis();
        this.poseGroup = id;
    }

    public void applyConfig(MapType config)
    {
        if (config == null)
        {
            return;
        }

        this.procedural = config.getBool("procedural", this.procedural);
        this.culling = config.getBool("culling", this.culling);
        this.poseGroup = config.getString("pose_group", this.poseGroup);
    }
}
