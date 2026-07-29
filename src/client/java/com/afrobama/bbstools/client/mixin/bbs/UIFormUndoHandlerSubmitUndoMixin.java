// Coded by Afrobama/Claude — see UndoSnapshotMemo. Brackets submitUndo() so the per-batch
// undo-snapshot memo is opened/closed exactly around the loop that builds one
// ValueChangeUndo per changed value.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIFormUndoHandler.class, remap = false)
public class UIFormUndoHandlerSubmitUndoMixin
{
    @Inject(method = "submitUndo", at = @At("HEAD"))
    private void bbstools$beginUndoBatch(CallbackInfo ci)
    {
        /* BRUECKEN-MUSTER: klammer-logik liegt in UndoStapelLogik (kernel-paket, swapbar). */
        MixinBruecke.rufe("UndoStapelLogik", "beginne");
    }

    @Inject(method = "submitUndo", at = @At("RETURN"))
    private void bbstools$endUndoBatch(CallbackInfo ci)
    {
        MixinBruecke.rufe("UndoStapelLogik", "beende");
    }

}
