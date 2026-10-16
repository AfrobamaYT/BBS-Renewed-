package com.afrobama.blendertransform;

import dev.vitrail.pack.source.IncludeExpander.ExpandedUnit;
import dev.vitrail.uniform.UniformCatalog;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.integrations.vitrail.*;
import mchorse.bbs_mod.utils.iris.ShaderCurves;

/** Required hot-swappable handlers for optional pack source and uniform hooks. */
public final class VitrailShaderCurveLogik
{
    private VitrailShaderCurveLogik() {}
    public static void reset()
    {
        if (dev.vitrail.HostReport.otherBackend()) return;
        ShaderCurves.reset(); VitrailCurveSource.reset(); VitrailCurveUniforms.reset();
    }
    public static ExpandedUnit source(ExpandedUnit unit)
    {
        return VitrailCurveSource.transform(unit, VitrailShaderOptions.present() ? VitrailShaderOptions.sliders() : java.util.List.of(),
            BBSSettings.shaderCurvesEnabled.get());
    }
    public static UniformCatalog catalog(UniformCatalog base) { return VitrailCurveUniforms.layer(base); }
    public static void advance() { VitrailCurveUniforms.advance(); }
}
