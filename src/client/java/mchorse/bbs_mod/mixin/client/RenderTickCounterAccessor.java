package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.Timer.class)
public interface RenderTickCounterAccessor
{
    @Accessor("partialTick")
    public float bbs$getTickDelta();
}