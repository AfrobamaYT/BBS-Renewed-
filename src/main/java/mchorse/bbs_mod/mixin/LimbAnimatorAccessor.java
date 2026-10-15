package mchorse.bbs_mod.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface LimbAnimatorAccessor
{
    @Accessor
    public float getSpeedOld();

    @Accessor
    public void setSpeedOld(float v);

    @Accessor
    public float getSpeed();

    @Accessor
    public void setSpeed(float v);

    @Accessor
    public float getPosition();

    @Accessor
    public void setPosition(float v);
}