package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.parsing.AnimationParser;
import mchorse.bbs_mod.cubic.parsing.ModelParser;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class CubicLoader
{
    public static MapType loadFile(InputStream stream)
    {
        try
        {
            return DataToString.mapFromString(loadStringFile(stream));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static String loadStringFile(InputStream stream) throws IOException
    {
        String content = IOUtils.readText(stream);

        stream.close();

        return content;
    }

    public LoadingInfo load(MolangParser parser, InputStream stream, String path)
    {
        LoadingInfo info = new LoadingInfo();

        try
        {
            MapType root = loadFile(stream);

            if (root.has("model"))
            {
                info.model = ModelParser.parse(parser, root.getMap("model"));
            }

            if (root.has("animations"))
            {
                MapType animations = root.getMap("animations");

                info.animations = new Animations();

                for (String key : animations.keys())
                {
                    info.animations.add(AnimationParser.parse(parser, key, animations.getMap(key)));
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("An error happened when parsing BBS model file: " + path);
            e.printStackTrace();
        }

        return info;
    }

    public static class LoadingInfo
    {
        public Animations animations;
        public Model model;
    }
}