package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.SequenzPufferAlt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderSystem.AutoStorageIndexBuffer.class)
public interface SequenzPufferAltMixin extends SequenzPufferAlt
{
    @Invoker("ensureStorage")
    void bbs$sicherstellen(int anzahl);

    @Accessor("buffer")
    GpuBuffer bbs$puffer();
}
