package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Model
{
    public int textureWidth;
    public int textureHeight;

    public final MolangParser parser;

    /**
     * This list contains only the root groups of the model (and not all of the groups)
     */
    public List<ModelGroup> topGroups = new ArrayList<>();

    private Map<String, ModelGroup> namedGroups = new HashMap<>();
    private List<ModelGroup> orderedGroups = new ArrayList<>();
    private int nextIndex;

    public Model(MolangParser parser)
    {
        this.parser = parser;
    }

    public void initialize()
    {
        this.fillGroups(this.topGroups);

        this.orderedGroups = Collections.unmodifiableList(this.orderedGroups);
    }

    private void fillGroups(List<ModelGroup> groups)
    {
        for (ModelGroup group : groups)
        {
            this.namedGroups.put(group.id, group);
            this.orderedGroups.add(group);

            group.index = this.nextIndex;
            this.nextIndex += 1;

            this.fillGroups(group.children);
        }
    }

    public void apply(Pose pose)
    {
        if (pose.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform transform = entry.getValue();
            ModelGroup group = this.getGroup(entry.getKey());

            if (group == null)
            {
                continue;
            }

            if (pose.staticPose)
            {
                group.current.copy(group.initial);
            }
            else if (transform.fix > 0F)
            {
                group.current.lerp(group.initial, transform.fix);
            }

            if (group != null)
            {
                group.current.translate.add(transform.translate);
                group.current.scale.add(transform.scale).sub(1, 1, 1);
                group.current.rotate.add(
                    (float) Math.toDegrees(transform.rotate.x),
                    (float) Math.toDegrees(transform.rotate.y),
                    (float) Math.toDegrees(transform.rotate.z)
                );
                group.current.rotate2.add(
                    (float) Math.toDegrees(transform.rotate2.x),
                    (float) Math.toDegrees(transform.rotate2.y),
                    (float) Math.toDegrees(transform.rotate2.z)
                );
            }
        }
    }

    public List<ModelGroup> getOrderedGroups()
    {
        return this.orderedGroups;
    }

    public Set<String> getAllGroupKeys()
    {
        return this.namedGroups.keySet();
    }

    public Collection<ModelGroup> getAllGroups()
    {
        return this.namedGroups.values();
    }

    public ModelGroup getGroup(String id)
    {
        return this.namedGroups.get(id);
    }
}