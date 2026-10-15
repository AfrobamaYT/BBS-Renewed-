package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.integrations.vitrail.VitrailShaderOptions;
import net.fabricmc.loader.api.FabricLoader;
import java.util.List;
import java.util.Map;

/** Existing BBS slider/label discovery follows whichever optional shader engine loaded the pack. */
public final class ShaderOptionBackend
{
    private ShaderOptionBackend() {}

    private static boolean vitrail()
    {
        return FabricLoader.getInstance().isModLoaded("vitrail") && VitrailShaderOptions.present();
    }

    public static List<String> sliders(boolean iris)
    {
        if (vitrail()) return VitrailShaderOptions.sliders();
        return iris ? IrisUtils.getSliderProperties() : List.of();
    }

    public static Map<String, String> language(boolean iris, String language)
    {
        if (vitrail()) return VitrailShaderOptions.language(language);
        return iris ? IrisUtils.getShadersLanguageMap(language) : Map.of();
    }
}
