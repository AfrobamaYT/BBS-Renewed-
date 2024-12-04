package mchorse.bbs_mod.cubic.obj;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.model.IModelLoader;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.obj.MeshOBJ;
import mchorse.bbs_mod.obj.MeshesOBJ;
import mchorse.bbs_mod.obj.OBJParser;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
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
        Map<String, MeshesOBJ> compile = new HashMap<>();

        /* Load the base */
        for (Link link : modelsOBJ)
        {
            String path = link.path.substring(model.path.length() + 1);

            if (path.contains("/"))
            {
                continue;
            }

            try (InputStream stream = models.provider.getAsset(link))
            {
                OBJParser parser = new OBJParser(stream, null);

                parser.read();
                compile.putAll(parser.compile());

                break;
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        for (Link link : modelsOBJ)
        {
            if (link.path.contains("/shapes/"))
            {
                continue;
            }

            try (InputStream stream = models.provider.getAsset(link))
            {
                OBJParser parser = new OBJParser(stream, null);
                String name = StringUtils.fileName(StringUtils.removeExtension(link.path));

                parser.read();

                Map<String, MeshesOBJ> compiled = parser.compile();

                for (Map.Entry<String, MeshesOBJ> entry : compiled.entrySet())
                {
                    MeshesOBJ meshesOBJ = compile.get(entry.getKey());

                    if (meshesOBJ == null)
                    {
                        compile.put(entry.getKey(), entry.getValue());
                    }
                    else
                    {
                        meshesOBJ.mergeShape(name, entry.getValue());
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (!compile.isEmpty())
        {
            Model modelModel = new Model(models.parser);

            for (Map.Entry<String, MeshesOBJ> entry : compile.entrySet())
            {
                MeshesOBJ value = entry.getValue();
                ModelGroup group = new ModelGroup(entry.getKey());

                for (MeshOBJ mesh : value.meshes)
                {
                    ModelMesh modelMesh = new ModelMesh();

                    for (int i = 0, c = mesh.triangles; i < c; i++)
                    {
                        modelMesh.baseData.vertices.add(new Vector3f(mesh.posData[i * 3] * 16F, mesh.posData[i * 3 + 1] * 16F, mesh.posData[i * 3 + 2] * 16F));
                        modelMesh.baseData.normals.add(new Vector3f(mesh.normData[i * 3], mesh.normData[i * 3 + 1], mesh.normData[i * 3 + 2]));
                        modelMesh.baseData.uvs.add(new Vector2f(mesh.texData[i * 2], mesh.texData[i * 2 + 1]));
                    }

                    group.meshes.add(modelMesh);
                }

                for (Map.Entry<String, List<MeshOBJ>> shapeEntry : value.shapes.entrySet())
                {
                    int h = 0;

                    for (MeshOBJ mesh : shapeEntry.getValue())
                    {
                        ModelMesh modelMesh = CollectionUtils.getSafe(group.meshes, h);
                        ModelData data = new ModelData();

                        for (int i = 0, c = mesh.triangles; i < c; i++)
                        {
                            data.vertices.add(new Vector3f(mesh.posData[i * 3] * 16F, mesh.posData[i * 3 + 1] * 16F, mesh.posData[i * 3 + 2] * 16F));
                            data.normals.add(new Vector3f(mesh.normData[i * 3], mesh.normData[i * 3 + 1], mesh.normData[i * 3 + 2]));
                            data.uvs.add(new Vector2f(mesh.texData[i * 2], mesh.texData[i * 2 + 1]));
                        }

                        modelMesh.data.put(shapeEntry.getKey(), data);

                        h += 1;
                    }
                }

                modelModel.topGroups.add(group);
            }

            newModel.model = modelModel;

            modelModel.textureWidth = 1;
            modelModel.textureHeight = 1;
            modelModel.initialize();
        }

        if (newModel.model == null || newModel.model.topGroups.isEmpty())
        {
            return null;
        }

        newModel.applyConfig(config);

        return newModel;
    }
}