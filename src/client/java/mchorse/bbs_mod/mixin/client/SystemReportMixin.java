package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.CrashReportDetail;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "BBS Renewed" in the system details of every crash report, the way Fabric adds its mod list. */
@Mixin(SystemReport.class)
public abstract class SystemReportMixin
{
    @Shadow
    public abstract void setDetail(String name, CrashReportDetail<Object> detail);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbs$absturzHinweise(CallbackInfo info)
    {
        this.setDetail("BBS Renewed", () -> MixinBruecke.rufe("AbsturzHinweiseLogik", "text"));
    }
}
