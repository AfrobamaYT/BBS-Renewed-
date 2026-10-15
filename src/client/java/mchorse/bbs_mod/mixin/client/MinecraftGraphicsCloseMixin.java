package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftGraphicsCloseMixin
{
    @Inject(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;close()V", ordinal = 0))
    private void bbs$closeGraphics(CallbackInfo info) throws Throwable
    {
        MixinBruecke.rufePflicht("GrafikLebensendeLogik", "schliessen");
    }
}
