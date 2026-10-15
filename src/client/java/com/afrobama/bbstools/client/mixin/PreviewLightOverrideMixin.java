// Coded by Afrobama/Claude — Phase 15 v6: force full-bright during alt-cam pre-pass
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.client.util.PreviewPassState;
//? if >=26.1 {
import net.minecraft.util.LightCoordsUtil;
//?} else {
/*import net.minecraft.client.renderer.LightTexture;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides {@link LightTexture#pack(int, int)} so any light
 * lookup during {@link PreviewPassState#PREVIEW_PASS_ACTIVE} returns
 * the max-bright lightmap UV. Result: replays/forms shown in the
 * alt-camera preview always render at full brightness regardless of
 * world light at the camera's pose. The user wants the preview to
 * stay readable during dark scenes (caves, night).
 *
 * <p>Outside the preview pass this is a no-op — vanilla light wins for
 * the main world render and BBS export.</p>
 */
//? if >=26.1 {
@Mixin(LightCoordsUtil.class)
//?} else {
/*@Mixin(LightTexture.class)
*///?}
public abstract class PreviewLightOverrideMixin
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/light-override");
    private static boolean loggedFirstOverride;

    @Inject(method = "pack(II)I", at = @At("RETURN"), cancellable = true, require = 0)
    private static void bbstools$forceBrightInPreview(int blockLight, int skyLight,
                                                       CallbackInfoReturnable<Integer> cir)
    {
        Object hell = MixinBruecke.rufe("VorschauLichtLogik", "volleHelligkeit");

        if (hell != null)
        {
            cir.setReturnValue((Integer) hell);
        }
    }

}
