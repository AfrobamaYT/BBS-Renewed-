// Coded by Afrobama/Claude — see UndoSnapshotMemo. Replaces ValueChangeUndo.cacheAfter's
// full-tree collectAllUndoData() with the per-batch memo, so a bulk edit's N identical
// "after" snapshots collapse to ONE (kills the 20k-scatter freeze + OOM).
//
// HEAD-inject + cancel (not @Redirect): getRoot() returns UIBaseMenu$UIRootElement, not
// UIElement, so an @At INVOKE redirect keyed to UIElement.collectAllUndoData found 0
// targets and crashed the mixin transform. uiAfter is a public field, so we just set it.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.client.bbs.fastpath.UndoSnapshotMemo;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ValueChangeUndo.class, remap = false)
public class ValueChangeUndoMemoMixin
{
    @Inject(method = "cacheAfter", at = @At("HEAD"), cancellable = true)
    private void bbstools$memoizeCacheAfter(UIElement editor, CallbackInfo ci)
    {
        MixinBruecke.rufe("UndoMemoLogik", "aufnehmen", this, editor);
        ci.cancel();
    }

}
