// Coded by Afrobama/Claude — UI Overhaul Phase 3: replace BBS toggle skin with our rectangular variant
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hijacks {@link UIToggle#renderSkin(UIContext)} BBS-wide so EVERY
 * toggle in the entire BBS-CML UI gets our AE/Premiere-style
 * rectangular switch instead of BBS's stock chrome.
 *
 * <p>Renders: label on the left, rectangular track + thumb on the
 * right. Track fills with {@link BBSToolsTheme#ACCENT_PRIMARY} when
 * ON, sits at {@link BBSToolsTheme#BG_FIELD} when OFF. Hover gives a
 * brighter border. No rounded pills — sharp edges.</p>
 */
@Mixin(value = UIToggle.class, remap = false)
public abstract class UIToggleSkinMixin
{
    @Inject(method = "renderSkin", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbstools$ourSkin(UIContext context, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.UiToggleSkinLogik — siehe MixinBruecke. */
        MixinBruecke.rufe("UiToggleSkinLogik", "ourSkin", (UIToggle)(Object) this, context);
        ci.cancel();
    }

}
