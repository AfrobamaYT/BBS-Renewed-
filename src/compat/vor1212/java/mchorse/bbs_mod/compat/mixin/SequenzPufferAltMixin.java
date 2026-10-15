package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.SequenzPufferAlt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** vor1212: der Indexpuffer ist ein roher GL-Name, kein GpuBuffer. */
@Mixin(RenderSystem.AutoStorageIndexBuffer.class)
public interface SequenzPufferAltMixin extends SequenzPufferAlt
{
    @Accessor("name")
    int bbs$name();
}
