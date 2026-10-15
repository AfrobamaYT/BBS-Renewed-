package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** vor1215: 1.21.4s Lichtrichtungen fuer den Lighting-Block der eigenen GL-Schicht. */
@Mixin(RenderSystem.class)
public interface RenderSystemLichtAccessor
{
    @Accessor("shaderLightDirections")
    static Vector3f[] bbs$lichtrichtungen()
    {
        throw new AssertionError();
    }
}
