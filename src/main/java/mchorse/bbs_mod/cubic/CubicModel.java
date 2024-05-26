package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

import java.util.ArrayList;
import java.util.List;

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

    public List<String> itemsMain = new ArrayList<>();
    public List<String> itemsOff = new ArrayList<>();

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

        if (config.has("items_main")) this.itemsMain = DataStorageUtils.stringListFromData(config.get("items_main"));
        if (config.has("items_off")) this.itemsOff = DataStorageUtils.stringListFromData(config.get("items_off"));
    }
}
