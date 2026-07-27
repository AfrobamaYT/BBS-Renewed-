package com.afrobama.bbstools.client.mixin;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code UIMorphingPanel.setForm(Form)} (private upstream) so
 * {@link UIMorphingPanelClickMorphMixin} can route form picks through the
 * EXACT upstream morph flow (sendPlayerForm + list deselect) instead of
 * duplicating its body — stays correct if upstream changes setForm.
 */
@Mixin(value = UIMorphingPanel.class, remap = false)
public interface UIMorphingPanelSetFormInvoker
{
    @Invoker(value = "setForm", remap = false)
    void bbstools$invokeSetForm(Form form);
}
