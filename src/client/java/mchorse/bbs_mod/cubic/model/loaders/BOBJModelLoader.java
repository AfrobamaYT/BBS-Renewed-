package mchorse.bbs_mod.cubic.model.loaders;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

import java.io.InputStream;
import java.util.Collection;

public class BOBJModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link modelBOBJ = IModelLoader.getLink(model.combine("model.bobj"), links, ".bobj");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");

        try (InputStream stream = models.provider.getAsset(modelBOBJ))
        {
            BOBJLoader.BOBJData bobjData = BOBJLoader.readData(stream);

            if (bobjData.armatures.isEmpty())
            {
                System.err.println("Model \"" + model + "\" doesn't have an armature!");

                return null;
            }

            BOBJArmature armature = bobjData.armatures.values().iterator().next();
            BOBJLoader.BOBJMesh finalMesh = null;

            for (BOBJLoader.BOBJMesh mesh : bobjData.meshes)
            {
                if (mesh.armature == armature)
                {
                    finalMesh = mesh;

                    break;
                }
            }

            if (finalMesh != null)
            {
                BOBJLoader.CompiledData compiledData = BOBJLoader.compileMesh(bobjData, finalMesh);
                BOBJModel bobjModel = new BOBJModel(armature, compiledData);

                bobjData.initiateArmatures();

                return new ModelInstance(id, bobjModel, new Animations(models.parser), modelTexture);
            }

            System.err.println("Model \"" + model + "\" doesn't have a mesh connected to one of the armatures!");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }
}