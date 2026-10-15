package mchorse.bbs_mod.film.replays;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Independent scalar channels for rig controls; bone transforms keep their own timing. */
public record RigPropertyPath(String formPath, String bone, String property)
{
    public static String key(String formPath, String bone, String property)
    {
        return (formPath.isEmpty() ? "" : formPath + "/") + "rig:"
            + URLEncoder.encode(bone, StandardCharsets.UTF_8) + ":"
            + URLEncoder.encode(property, StandardCharsets.UTF_8);
    }

    public static RigPropertyPath parse(String key)
    {
        if (key == null)
        {
            return null;
        }

        int marker = key.lastIndexOf("/rig:");
        String formPath = marker < 0 ? "" : key.substring(0, marker);
        String rest = marker < 0 ? key : key.substring(marker + 1);

        if (!rest.startsWith("rig:"))
        {
            return null;
        }

        String[] fields = rest.substring(4).split(":", -1);

        if (fields.length != 2 || fields[0].isEmpty() || fields[1].isEmpty())
        {
            return null;
        }

        try
        {
            return new RigPropertyPath(formPath,
                URLDecoder.decode(fields[0], StandardCharsets.UTF_8),
                URLDecoder.decode(fields[1], StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException invalid)
        {
            return null;
        }
    }
}
