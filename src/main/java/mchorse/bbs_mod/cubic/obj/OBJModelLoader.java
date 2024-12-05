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

        /* Load the base OBJ file */
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

        /* Load shapes from shapes folder */
        for (Link link : modelsOBJ)
        {
            if (!link.path.contains("/shapes/"))
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

        /* Construct the model from compiled data */
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

                    modelMesh.baseData.fill(mesh);
                    group.meshes.add(modelMesh);
                }

                this.fillShapes(value.shapes, group);
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

    private void fillShapes(Map<String, List<MeshOBJ>> shapes, ModelGroup group)
    {
        if (shapes == null)
        {
            return;
        }

        for (Map.Entry<String, List<MeshOBJ>> shapeEntry : shapes.entrySet())
        {
            int h = 0;

            for (MeshOBJ mesh : shapeEntry.getValue())
            {
                ModelMesh modelMesh = CollectionUtils.getSafe(group.meshes, h);
                ModelData data = new ModelData();

                data.fill(mesh);
                modelMesh.data.put(shapeEntry.getKey(), data);

                h += 1;
            }
        }
    }
}