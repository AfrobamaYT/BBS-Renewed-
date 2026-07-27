// Coded by Afrobama/Claude — UI Overhaul Phase 3 part 4: icon hover/active accent
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Retunes every {@link UIIcon}'s default hover + active color to our
 * accent palette. BBS-CML's defaults are light gray (0xFFCCCCCC);
 * we want primary-blue feedback on hover so the dashboard feels
 * cohesive with the rest of the overhaul.
 *
 * <p>Icons that explicitly set their own colors via
 * {@code .hoverColor(...)} or {@code .activeColor(...)} keep
 * whatever the caller passed — those overrides happen AFTER the
 * constructor returns.</p>
 */
@Mixin(value = UIIcon.class, remap = false)
public abstract class UIIconColorMixin
{
    @Inject(method = "<init>*", at = @At("RETURN"), require = 0)
    private void bbstools$retuneDefaults(CallbackInfo ci)
    {
        UIIcon self = (UIIcon) (Object) this;
        self.hoverColor = BBSToolsTheme.ACCENT_PRIMARY;
        self.activeColor = BBSToolsTheme.ACCENT_PRIMARY;
    }

}
