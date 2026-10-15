package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerWorldMixin
{
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    public void onSetBlockBreakingInfo(int entityId, BlockPos pos, int progress, CallbackInfo info)
    {
        KernelBruecke.rufe("SpielerAktionenLogik", "blockAbbauFortschritt", this, entityId, pos, progress);
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    public void onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> info)
    {
        KernelBruecke.rufe("SpielerAktionenLogik", "entitaetErschienen", entity);
    }
}
