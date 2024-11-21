package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class PoseManager
{
    private static MapType poses = new MapType();

    public static MapType getPoses(String group)
    {
        MapType newPoses;

        if (poses.has(group))
        {
            return poses.getMap(group);
        }

        newPoses = new MapType();

        try (InputStream stream = BBSMod.getProvider().getAsset(getPosesFile(group)))
        {
            newPoses = DataToString.mapFromString(IOUtils.readText(stream));
        }
        catch (FileNotFoundException e)
        {}
        catch (Exception e)
        {
            e.printStackTrace();
        }

        poses.put(group, newPoses);

        return newPoses;
    }

    public static void savePose(String group, String key, MapType pose)
    {
        if (group.isEmpty())
        {
            System.err.println("Can't save empty pose group!");

            return;
        }

        MapType newPoses = poses.getMap(group);

        newPoses.put(key, pose);

        File file = BBSMod.getProvider().getFile(getPosesFile(group));

        if (file != null)
        {
            file.getParentFile().mkdirs();

            DataToString.writeSilently(file, newPoses, true);
        }
    }

    private static Link getPosesFile(String group)
    {
        return Link.assets(ModelManager.MODELS_PREFIX + group + "/poses.json");
    }
}