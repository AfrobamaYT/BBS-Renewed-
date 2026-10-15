package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.pack.target.TargetPlan;
import dev.vitrail.render.PackValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(targets = "dev.vitrail.render.EntityProgram", remap = false)
public abstract class BbsEntityProgramMixin
{
    @ModifyVariable(method = "of(Ldev/vitrail/glsl/PackProgram$Loaded;Ldev/vitrail/render/EntityDraw$Element;Ldev/vitrail/render/PackValues;ILjava/util/List;Ldev/vitrail/pack/target/TargetPlan;Ldev/vitrail/render/ColorTargets;Z)Ldev/vitrail/render/EntityProgram;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static PackProgram.Loaded bbs$nativeProgram(PackProgram.Loaded value, PackProgram.Loaded original,
        @Coerce Object row, PackValues values, int load, List<?> writes, TargetPlan targets,
        @Coerce Object colours, boolean runs) throws Throwable
    { return (PackProgram.Loaded) MixinBruecke.rufePflicht("VitrailGeometryLogik", "program", value, row); }
}
