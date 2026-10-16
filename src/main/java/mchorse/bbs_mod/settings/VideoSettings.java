package mchorse.bbs_mod.settings;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/** Settings of the footage BBS renders for DaVinci Resolve (video category). */
public final class VideoSettings
{
    /** Height of the film BBS renders for Resolve's camera clips (width follows the export aspect). */
    public static ValueInt resolveProxyHeight;

    private VideoSettings()
    {}

    public static void register(SettingsBuilder builder)
    {
        resolveProxyHeight = builder.getInt("resolve_proxy_height", 360, 144, 2160);
    }
}
