package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlReadBufferMixin
{
    @Inject(method = "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_readPixels(IIIIIIJ)V"))
    private void bbs$selectReadBuffer(GpuTexture source, GpuBuffer destination, long offset,
        Runnable callback, int mipLevel, int x, int y, int width, int height, CallbackInfo info) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeBackendLogik", "selectReadBuffer", source);
    }
}
