package mchorse.bbs_mod.compat.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.shaders.CompiledShader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Vor 1.21.5: BBS' Shader stehen im Format von 1.21.6+ (#moj_import der Uniform-Bloecke) und
 * werden von der eigenen GL-Schicht geladen (ShaderQuellen). 1.21.4s ShaderManager wuerde sie
 * beim Ressourcenladen vorverarbeiten, an den fehlenden Includes scheitern und daraufhin alle
 * Ressourcenpakete abwerfen (gemessen am ersten Start). Darum laesst er den Namensraum bbs aus.
 */
@Mixin(net.minecraft.client.renderer.ShaderManager.class)
public abstract class ShaderManagerBbsMixin
{
    @Inject(method = "loadShader", at = @At("HEAD"), cancellable = true)
    private static void bbs$eigeneShaderAuslassen(ResourceLocation ort, Resource quelle, CompiledShader.Type art,
        Map<ResourceLocation, Resource> alle, ImmutableMap.Builder<?, String> ziel, CallbackInfo info)
    {
        if ("bbs".equals(ort.getNamespace()))
        {
            info.cancel();
        }
    }
}
