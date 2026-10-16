package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vitrail.pack.source.IncludeExpander.ExpandedUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.vitrail.pack.source.IncludeExpander", remap = false)
public abstract class VitrailCurveSourceMixin
{
    @ModifyReturnValue(method = "expand(Ljava/nio/file/Path;)Ldev/vitrail/pack/source/IncludeExpander$ExpandedUnit;", at = @At("RETURN"))
    private ExpandedUnit bbs$curves(ExpandedUnit unit) throws Throwable
    { return (ExpandedUnit) MixinBruecke.rufePflicht("VitrailShaderCurveLogik", "source", unit); }
}
