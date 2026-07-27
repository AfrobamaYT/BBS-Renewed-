// Coded by Afrobama/Claude — invalidate broken cached overlay sizes
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Fixes a BBS-CML bug where {@link UIOverlay} persists the last-used
 * size of each {@code UIOverlayPanel} subclass across opens (via the
 * static {@code sizes} map). If the user ever resizes a picker
 * (model / biome / mob / particle / structure — all share class
 * {@code UIListOverlayPanel}) to a tiny size, that size becomes
 * sticky for every future picker of the same class — the popup
 * opens collapsed to a useless sliver.
 *
 * <p>We invalidate the cached entry on every {@code setupPanel}
 * call if it has fallen below a sane minimum. BBS then falls back
 * to the size the call site explicitly requested (e.g. the model
 * picker passes {@code 280} px × {@code 0.5F} ratio).</p>
 *
 * <p>Position cache ({@code offsets}) is intentionally left alone —
 * if the user dragged the panel to a non-default location, we keep
 * that. Only the size is sanitised.</p>
 */
@Mixin(value = UIOverlay.class, remap = false)
public abstract class UIOverlayMinSizeMixin
{
    @Shadow @Final
    private static Map<String, Vector2i> sizes;

    @Inject(method = "setupPanel", at = @At("HEAD"))
    private static void bbstools$invalidateStaleSize(UIContext context,
                                                     UIOverlay overlay,
                                                     UIOverlayPanel panel,
                                                     CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.UiOverlayMinSizeLogik — siehe MixinBruecke. */
        MixinBruecke.rufe("UiOverlayMinSizeLogik", "invalidateStaleSize", panel, sizes);
    }

}
