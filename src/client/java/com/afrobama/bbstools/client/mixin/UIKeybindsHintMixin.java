package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybinds;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a one-line "Press Shift+F9 to open the Keybind Editor" hint at
 * the bottom of the F9 keybind viewer. Drawn at @TAIL of preRender so
 * it overlays after the categories without interfering with their
 * scroll calculation.
 */
@Mixin(UIKeybinds.class)
public abstract class UIKeybindsHintMixin
{
    @Inject(method = "preRender", at = @At("TAIL"))
    private void bbstools$drawEditorHint(UIContext context, CallbackInfo ci)
    {
        /* Logik → com.afrobama.blendertransform.UiKeybindsHintLogik. */
        com.afrobama.bbstools.client.blender.MixinBruecke.rufe(
            "UiKeybindsHintLogik", "drawEditorHint", (UIKeybinds) (Object) this, context);
    }

}
