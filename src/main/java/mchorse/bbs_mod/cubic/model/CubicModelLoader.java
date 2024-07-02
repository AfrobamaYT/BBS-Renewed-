package mchorse.bbs_mod.cubic.model;

import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CubicModelLoader implements IModelLoader
{
    @Override
    public CubicModel load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        List<Link> modelBBS = IModelLoader.getLinks(links, ".bbs.json");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");
        List<InputStream> modelStreams = new ArrayList<>();

        try
        {
            for (Link link : modelBBS)
            {
                modelStreams.add(models.provider.getAsset(link));
            }
        }
        catch (Exception e)
        {
            return null;
        }

        if (modelStreams.isEmpty() || modelStreams.size() != modelBBS.size())
        {
            return null;
        }

        CubicModel newModel = new CubicModel(id, null, new Animations(models.parser), modelTexture);

        for (int i = 0; i < modelStreams.size(); i++)
        {
            CubicLoader loader = new CubicLoader();
            InputStream stream = modelStreams.get(i);
            CubicLoader.LoadingInfo info = loader.load(models.parser, stream, modelBBS.get(i).path);

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

            try
            {
                stream.close();
            }
            catch (IOException e)
            {}
        }

        if (newModel.model == null || newModel.model.topGroups.isEmpty())
        {
            return null;
        }

        Animations animations = this.tryLoadingExternalAnimations(models, model, config);

        for (Animation animation : animations.getAll())
        {
            newModel.animations.add(animation);
        }

        newModel.applyConfig(config);

        return newModel;
    }

    private Animations tryLoadingExternalAnimations(ModelManager models, Link model, MapType config)
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