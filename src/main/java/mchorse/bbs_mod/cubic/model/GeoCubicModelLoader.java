package mchorse.bbs_mod.cubic.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.geo.GeoAnimationParser;
import mchorse.bbs_mod.cubic.geo.GeoModelParser;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

public class GeoCubicModelLoader implements IModelLoader
{
    @Override
    public CubicModel load(String id, ModelManager models, Link model, Collection<Link> links) throws Exception
    {
        List<Link> modelGeo = IModelLoader.getLinks(links, ".geo.json");
        List<Link> modelAnimation = IModelLoader.getLinks(links, ".animation.json");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");
        InputStream geoStream = null;
        InputStream animationStream = null;
        MapType config = null;

        try
        {
            InputStream asset = models.provider.getAsset(model.combine("config.json"));
            String string = IOUtils.readText(asset);

            config = (MapType) DataToString.fromString(string);
        }
        catch (Exception e)
        {}

        try
        {
            if (!modelGeo.isEmpty())
            {
                geoStream = BBSMod.getProvider().getAsset(modelGeo.get(0));
            }
        }
        catch (Exception e)
        {
            System.err.println("Failed to load Bedrock entity .geo.json for model: " + model);
        }

        try
        {
            if (!modelAnimation.isEmpty())
            {
                animationStream = BBSMod.getProvider().getAsset(modelAnimation.get(0));
            }
        }
        catch (Exception e)
        {
            System.err.println("Failed to load Bedrock entity .animation.json for model: " + model);
        }

        if (geoStream == null)
        {
            return null;
        }

        try
        {
            JsonObject modelJson = JsonParser.parseString(IOUtils.readText(geoStream)).getAsJsonObject();
            Animations modelAnimations = new Animations();

            if (animationStream != null)
            {
                JsonObject animationJson = JsonParser.parseString(IOUtils.readText(animationStream)).getAsJsonObject().get("animations").getAsJsonObject();

                for (String key : animationJson.keySet())
                {
                    JsonObject animation = animationJson.getAsJsonObject(key);

                    modelAnimations.animations.put(key, GeoAnimationParser.parse(models.parser, key, animation));
                }
            }

            Model modelModel = GeoModelParser.parse(modelJson, models.parser);

            CubicModel newModel = new CubicModel(id, modelModel, modelAnimations, modelTexture);

            if (newModel.model == null || newModel.model.topGroups.isEmpty())
            {
                return null;
            }

            newModel.applyConfig(config);

            return newModel;
        }
        catch (Exception e)
        {
            System.err.println("Failed to load Bedrock entity model: " + model);

            e.printStackTrace();
        }

        return null;
    }
}