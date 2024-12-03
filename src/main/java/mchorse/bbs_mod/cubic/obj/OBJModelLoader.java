package mchorse.bbs_mod.cubic.obj;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.model.IModelLoader;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.obj.MeshOBJ;
import mchorse.bbs_mod.obj.MeshesOBJ;
import mchorse.bbs_mod.obj.OBJParser;
import mchorse.bbs_mod.resources.Link;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class OBJModelLoader implements IModelLoader
{
    @Override
    public CubicModel load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        List<Link> modelsOBJ = IModelLoader.getLinks(links, ".obj");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");
        CubicModel newModel = new CubicModel(id, null, new Animations(models.parser), modelTexture);

        for (Link link : modelsOBJ)
        {
            try (InputStream stream = models.provider.getAsset(link))
            {
                OBJParser parser = new OBJParser(stream, null);
                Model modelModel = new Model(models.parser);

                parser.read();

                Map<String, MeshesOBJ> compile = parser.compile();

                for (Map.Entry<String, MeshesOBJ> entry : compile.entrySet())
                {
                    MeshesOBJ value = entry.getValue();
                    ModelGroup group = new ModelGroup(entry.getKey());

                    for (MeshOBJ mesh : value.meshes)
                    {
                        ModelMesh modelMesh = new ModelMesh();

                        for (int i = 0, c = mesh.triangles; i < c; i++)
                        {
                            modelMesh.vertices.add(new Vector3f(mesh.posData[i * 3] * 16F, mesh.posData[i * 3 + 1] * 16F, mesh.posData[i * 3 + 2] * 16F));
                            modelMesh.normals.add(new Vector3f(mesh.normData[i * 3], mesh.normData[i * 3 + 1], mesh.normData[i * 3 + 2]));
                            modelMesh.uvs.add(new Vector2f(mesh.texData[i * 2], mesh.texData[i * 2 + 1]));
                        }

                        group.meshes.add(modelMesh);
                    }

                    modelModel.topGroups.add(group);
                }

                newModel.model = modelModel;

                modelModel.textureWidth = 1;
                modelModel.textureHeight = 1;
                modelModel.initialize();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (newModel.model == null || newModel.model.topGroups.isEmpty())
        {
            return null;
        }

        newModel.applyConfig(config);

        return newModel;
    }
}