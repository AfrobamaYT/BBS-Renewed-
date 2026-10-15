// Coded by Afrobama/Claude — UI Overhaul Phase 3 part 2: BBS button skin override
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hijacks {@link UIButton#renderSkin(UIContext)} so EVERY button in
 * the entire BBS-CML UI gets our AE/Premiere-style rectangular look:
 *
 * <ul>
 *   <li>Sharp 2px corners (vs Refreshed's pills)</li>
 *   <li>Subtle 1px border, brightens on hover</li>
 *   <li>Custom-color buttons (when {@code custom == true}) keep their
 *       user-supplied tint as accent fill</li>
 *   <li>Disabled state honored via UIClickable.isEnabled() — handled
 *       in render() not renderSkin, so this mixin doesn't see it</li>
 * </ul>
 */
@Mixin(value = UIButton.class, remap = false)
public abstract class UIButtonSkinMixin
{
    @Inject(method = "renderSkin", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbstools$ourSkin(UIContext context, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in UIButton
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Das
         * gesamte zeichnen liegt jetzt in `com.afrobama.blendertransform.UIButtonSkinLogik`
         * und wird bei jedem swap mitgetauscht. */
        MixinBruecke.rufe("UIButtonSkinLogik", "zeichneSkin", (UIButton) (Object) this, context);
        ci.cancel();
    }

}
