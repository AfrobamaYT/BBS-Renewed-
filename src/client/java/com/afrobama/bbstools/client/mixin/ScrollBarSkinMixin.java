// Coded by Afrobama/Claude — UI Overhaul Phase 3 part 3: scrollbar tint
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.utils.Scroll;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Tints BBS's scrollbar fill color to our accent palette via
 * {@link Scroll#bar(mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D, int, int, int, int, int)}.
 * Every scrollbar (form picker, replay list, keybind editor, etc.)
 * inherits this color shift.
 *
 * <p>Only the explicit color int passed in gets re-targeted to
 * {@link BBSToolsTheme#ACCENT_PRIMARY}. Track/shadow rendering still
 * uses BBS's own logic.</p>
 */
@Mixin(value = Scroll.class, remap = false)
public abstract class ScrollBarSkinMixin
{
    @ModifyVariable(
        method = "bar(Lmchorse/bbs_mod/ui/framework/elements/utils/Batcher2D;IIIII)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 4
    )
    private static int bbstools$tintScrollbar(int color)
    {
        /* BRUECKEN-MUSTER: die farbrechnung liegt in OberflaechenFarbenLogik (kernel-paket,
         * swapbar), hier bleibt nur das durchreichen. Nur primitive in der signatur. */
        Object farbe = MixinBruecke.rufe("OberflaechenFarbenLogik", "scrollbalken", color);

        return farbe instanceof Integer wert ? wert : color;
    }

}
