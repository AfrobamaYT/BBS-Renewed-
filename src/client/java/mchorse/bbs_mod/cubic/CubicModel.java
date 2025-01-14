package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.client.render.ModelVAO;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Pose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CubicModel implements ICubicModel
{
    public final String id;
    public Model model;
    public Animations animations;
    public Link texture;
    public long loadTime;

    /* Model's additional properties */
    public String poseGroup;
    public boolean procedural;
    public boolean culling = true;
    public String anchorGroup = "";

    public Vector3f scale = new Vector3f(1F);
    public float uiScale = 1F;
    public Pose sneakingPose = new Pose();

    public List<String> itemsMain = new ArrayList<>();
    public List<String> itemsOff = new ArrayList<>();
    public Map<String, String> flippedParts = new HashMap<>();
    public Map<ArmorType, ArmorSlot> armorSlots = new HashMap<>();

    private Map<ModelGroup, ModelVAO> vaos = new HashMap<>();

    public CubicModel(String id, Model model, Animations animations, Link texture)
    {
        this.id = id;
        this.model = model;
        this.animations = animations;
        this.texture = texture;

        this.loadTime = System.currentTimeMillis();
        this.poseGroup = id;
    }

    @Override
    public Model getModel()
    {
        return this.model;
    }

    @Override
    public Pose getSneakingPose()
    {
        return this.sneakingPose;
    }

    @Override
    public Animations getAnimations()
    {
        return this.animations;
    }

    public Map<ModelGroup, ModelVAO> getVaos()
    {
        return this.vaos;
    }

    public String getAnchor()
    {
        if (this.anchorGroup.isEmpty() && this.model.topGroups.size() == 1)
        {
            return this.model.topGroups.get(0).id;
        }

        return this.anchorGroup;
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
        if (config.has("ui_scale")) this.uiScale = config.getFloat("ui_scale");
        if (config.has("scale")) this.scale = DataStorageUtils.vector3fFromData(config.getList("scale"), new Vector3f(1F));
        if (config.has("sneaking_pose", BaseType.TYPE_MAP))
        {
            this.sneakingPose = new Pose();
            this.sneakingPose.fromData(config.getMap("sneaking_pose"));
        }
        if (config.has("anchor")) this.anchorGroup = config.getString("anchor");
        if (config.has("flipped_parts"))
        {
            MapType map = config.getMap("flipped_parts");

            for (String key : map.keys())
            {
                String string = map.getString(key);

                if (!string.trim().isEmpty())
                {
                    this.flippedParts.put(key, string);
                }
            }
        }
        if (config.has("armor_slots"))
        {
            MapType map = config.getMap("armor_slots");

            for (String key : map.keys())
            {
                try
                {
                    ArmorType type = ArmorType.valueOf(key.toUpperCase());
                    ArmorSlot slot = new ArmorSlot();

                    slot.fromData(map.getMap(key));
                    this.armorSlots.put(type, slot);
                }
                catch (Exception e)
                {}
            }
        }
    }

    public void setup()
    {
        /* VAOs should be only generated if there are no shape keys */
        if (!this.model.getShapeKeys().isEmpty())
        {
            return;
        }

        MinecraftClient.getInstance().execute(() ->
        {
            CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new MatrixStack(), this.model);
        });
    }

    public void delete()
    {
        for (ModelVAO value : this.vaos.values())
        {
            value.delete();
        }

        this.vaos.clear();
    }
}
