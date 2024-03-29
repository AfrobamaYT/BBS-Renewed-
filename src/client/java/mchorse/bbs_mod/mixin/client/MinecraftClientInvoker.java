package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientInvoker
{
    @Invoker("handleInputEvents")
    public void bbs$handleInputEvents();

    @Invoker("handleBlockBreaking")
    public void bbs$handleBlockBreaking(boolean attack);

    @Accessor("attackCooldown")
    public void bbs$setAttackCooldown(int cooldown);

    @Accessor("attackCooldown")
    public int bbs$getAttackCooldown();
}