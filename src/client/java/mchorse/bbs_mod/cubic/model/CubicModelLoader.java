package mchorse.bbs_mod.cubic.model;

import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.obj.MeshOBJ;
import mchorse.bbs_mod.obj.MeshesOBJ;
import mchorse.bbs_mod.obj.OBJParser;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CubicModelLoader implements IModelLoader
{
    @Override
    public CubicModel load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link modelBBS = IModelLoader.getLink(model.combine("model.bbs.json"), links, ".bbs.json");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");
        CubicModel newModel = new CubicModel(id, null, new Animations(models.parser), modelTexture);
        Map<String, MeshesOBJ> compile = this.tryLoadingOBJMeshes(models, model, IModelLoader.getLinks(links, ".obj"));

        try (InputStream stream = models.provider.getAsset(modelBBS))
        {
            CubicLoader loader = new CubicLoader();
            CubicLoader.LoadingInfo info = loader.load(models.parser, stream, modelBBS.path);

            if (info.model != null)
            {
                newModel.model = info.model;
            }

            if (info.animations != null)
            {
                for (Animation animation : info.animations.getAll())
                {
                    newModel.animations.add(animation);
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("Failed to load BBS file: " + modelBBS);
        }

        /* Construct the model from compiled data */
        if (!compile.isEmpty())
        {
            HashSet<String> declined = new HashSet<>();

            if (newModel.model == null)
            {
                newModel.model = new Model(models.parser);
                newModel.model.textureWidth = 1;
                newModel.model.textureHeight = 1;
            }

            for (Map.Entry<String, MeshesOBJ> entry : compile.entrySet())
            {
                MeshesOBJ value = entry.getValue();
                ModelGroup group = newModel.model.getGroup(entry.getKey());

                if (group == null)
                {
                    group = new ModelGroup(entry.getKey());

                    newModel.model.topGroups.add(group);
                }

                for (MeshOBJ mesh : value.meshes)
                {
                    ModelMesh modelMesh = new ModelMesh();

                    modelMesh.baseData.fill(mesh, newModel.model.textureWidth, newModel.model.textureHeight);
                    group.meshes.add(modelMesh);
                }

                this.fillShapes(declined, value.shapes, group, newModel.model.textureWidth, newModel.model.textureHeight);
            }

            newModel.model.initialize();

            for (String s : declined)
            {
                System.out.println("Model \"" + model + "\" has shape keys \"" + s + "\" that have invalid shape keys (triangle count doesn't match)!");
            }
        }

        if (newModel.model == null || newModel.model.topGroups.isEmpty())
        {
            return null;
        }

        for (Animation animation : this.tryLoadingExternalAnimations(models, config).getAll())
        {
            newModel.animations.add(animation);
        }

        newModel.applyConfig(config);

        return newModel;
    }

    private void validateShapeKeys(Link model, Map<String, MeshesOBJ> compile)
    {
        Set<String> toRemove = new HashSet<>();

        main: for (MeshesOBJ value : compile.values())
        {
            List<MeshOBJ> meshes = value.meshes;

            if (value.shapes == null)
            {
                continue;
            }

            for (Map.Entry<String, List<MeshOBJ>> entry : value.shapes.entrySet())
            {
                List<MeshOBJ> shapeMeshes = entry.getValue();

                if (meshes.size() != shapeMeshes.size())
                {
                    toRemove.add(entry.getKey());

                    continue main;
                }

                for (int i = 0; i < meshes.size(); i++)
                {
                    String key = entry.getKey();
                    MeshOBJ shapeMesh = shapeMeshes.get(i);
                    MeshOBJ mesh = meshes.get(i);

                    if (mesh.triangles != shapeMesh.triangles)
                    {
                        toRemove.add(key);

                        continue main;
                    }
                }
            }
        }

        for (String s : toRemove)
        {
            System.err.println("Model " + model + " has shape keys \"" + s + "\" that doesn't match the base OBJ file!");

            for (MeshesOBJ value : compile.values())
            {
                value.shapes.remove(s);
            }
        }
    }

    /**
     * Load OBJ meshes from multiple files
     */
    private Map<String, MeshesOBJ> tryLoadingOBJMeshes(ModelManager models, Link model, List<Link> modelsOBJ)
    {
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

        return compile;
    }

    private void fillShapes(Set<String> declined, Map<String, List<MeshOBJ>> shapes, ModelGroup group, int tw, int th)
    {
        if (shapes == null)
        {
            return;
        }

        for (Map.Entry<String, List<MeshOBJ>> shapeEntry : shapes.entrySet())
        {
            int i = 0;

            for (MeshOBJ mesh : shapeEntry.getValue())
            {
                ModelMesh modelMesh = CollectionUtils.getSafe(group.meshes, i);
                ModelData data = new ModelData();

                data.fill(mesh, tw, th);

                if (
                    data.vertices.size() == modelMesh.baseData.vertices.size() &&
                    data.normals.size() == modelMesh.baseData.normals.size() &&
                    data.uvs.size() == modelMesh.baseData.uvs.size()
                ) {
                    modelMesh.data.put(shapeEntry.getKey(), data);
                }
                else
                {
                    declined.add(shapeEntry.getKey());
                }

                i += 1;
            }
        }
    }

    /**
     * Loading external animations mentioned in the config
     */
    private Animations tryLoadingExternalAnimations(ModelManager models, MapType config)
    {
        Animations animations = new Animations(models.parser);

        if (config == null)
        {
            return animations;
        }

        for (BaseType type : config.getList("animations"))
        {
            if (type.isString())
            {
                Link animationFile = Link.create(type.asString());

                try (InputStream asset = models.provider.getAsset(animationFile))
                {
                    CubicLoader loader = new CubicLoader();
                    CubicLoader.LoadingInfo info = loader.load(models.parser, asset, type.asString());

                    if (info.animations != null)
                    {
                        for (Animation animation : info.animations.getAll())
                        {
                            animations.add(animation);
                        }
                    }
                }
                catch (FileNotFoundException e)
                {
                    return new Animations(models.parser);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }

        return animations;
    }
}