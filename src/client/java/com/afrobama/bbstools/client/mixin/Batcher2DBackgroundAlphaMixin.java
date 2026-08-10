// Coded by Afrobama/Claude — UI Overhaul Phase 1: background-only alpha tint
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency;

import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Multiplies the alpha channel of dark/neutral box draws in BBS's
 * {@link Batcher2D} by {@link BBSToolsTransparency#PANEL_BG_ALPHA}.
 *
 * <p>The {@code looksLikeBackground} heuristic in {@link BBSToolsTheme}
 * targets only low-brightness near-neutral colors — i.e. panel
 * backgrounds and dividers. Text, icon textures, accent colors, and
 * gradient fills are not affected, so legibility stays intact even at
 * 0% panel-bg alpha.</p>
 *
 * <p>If the user hasn't dialled transparency below 100% the mixin is
 * a no-op (returns the same int).</p>
 */
@Mixin(value = Batcher2D.class, remap = false)
public abstract class Batcher2DBackgroundAlphaMixin
{
    @ModifyVariable(
        method = "box(FFFFI)V",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    private int bbstools$tintBoxBackground(int color)
    {
        Object neu = MixinBruecke.rufe("FarbFilterLogik", "hintergrundAlpha", color);
        return neu instanceof Integer i ? i : color;
    }

}
