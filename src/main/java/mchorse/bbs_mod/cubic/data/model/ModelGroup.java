package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;

public class ModelGroup implements IMapSerializable
{
    public final String id;
    public List<ModelGroup> children = new ArrayList<>();
    public List<ModelCube> cubes = new ArrayList<>();
    public List<ModelMesh> meshes = new ArrayList<>();
    public boolean visible = true;
    public int index = -1;

    public Transform initial = new Transform();
    public Transform current = new Transform();

    public ModelGroup(String id)
    {
        this.id = id;
    }

    @Override
    public void fromData(MapType data)
    {
        /* Setup initial transformations */
        if (data.has("origin"))
        {
            this.initial.translate.set(DataStorageUtils.vector3fFromData(data.getList("origin")));
        }

        if (data.has("rotate"))
        {
            this.initial.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));
        }

        /* Setup cubes and meshes */
        if (data.has("cubes"))
        {
            this.parseCubes(this, data.getList("cubes"));
        }

        if (data.has("meshes"))
        {
            this.parseMeshes(this, data.getList("meshes"));
        }
    }

    private void parseCubes(ModelGroup group, ListType cubes)
    {
        for (BaseType element : cubes)
        {
            ModelCube cube = new ModelCube();

            cube.fromData((MapType) element);

            group.cubes.add(cube);
        }
    }

    private void parseMeshes(ModelGroup group, ListType meshes)
    {
        for (BaseType element : meshes)
        {
            ModelMesh mesh = new ModelMesh();

            mesh.fromData((MapType) element);

            group.meshes.add(mesh);
        }
    }

    @Override
    public void toData(MapType data)
    {

    }
}