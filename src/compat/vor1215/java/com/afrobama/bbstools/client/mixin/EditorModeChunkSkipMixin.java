package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vor 1.21.6 (ersetzt die gemeinsame Fassung): Chunk-Schichten im Editor-Modus auslassen, je Schicht. */
@Mixin(LevelRenderer.class)
public abstract class EditorModeChunkSkipMixin
{
    @Inject(method = mchorse.bbs_mod.compat.alt.MixinZiele.ABSCHNITT_EBENE, at = @At("HEAD"), cancellable = true, require = 0)
    private void bbstools$skipChunksInEditorMode(CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("EditorModeChunkSkipLogik", "shouldSkipChunks")) ci.cancel();
    }
}
