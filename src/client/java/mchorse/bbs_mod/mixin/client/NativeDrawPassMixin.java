package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class NativeDrawPassMixin
{
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void bbs$beginNativeCapture(CallbackInfo info) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeDrawPassLogik", "beginNativeFrame");
    }

    @WrapMethod(method = "executeSolid")
    private void bbs$existingSolidPass(ChunkSectionsToRender sections,
        FeatureRenderDispatcher.PreparedFrame prepared, RenderPass pass, Operation<Void> original) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeDrawPassLogik", "executeSolid", sections, prepared, pass, original);
    }
}
