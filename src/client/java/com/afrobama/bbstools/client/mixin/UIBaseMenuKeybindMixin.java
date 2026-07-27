// Coded by Afrobama/Claude — wires BBSTools keybinds into every UIBaseMenu
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wires BBSToolsKeys.KEYBIND_EDITOR_OPEN (Shift+F9 default) into every
 * UIBaseMenu so the user can open the BBSTools UIKeybindEditor from
 * anywhere in the BBS UI.
 *
 * <p>Approach: inject at {@code @TAIL} of UIBaseMenu's constructor; pull
 * the {@code root} element via {@link UIBaseMenuAccessor} and register
 * a fresh keybind on a new child element. This sidesteps BBS upstream
 * not having any keybind for our editor.</p>
 */
@Mixin(UIBaseMenu.class)
public abstract class UIBaseMenuKeybindMixin
{
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbstools$wireKeybindEditor(CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in UIBaseMenu
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Das
         * gesamte keybind-wiring liegt jetzt in
         * `com.afrobama.blendertransform.UIBaseMenuKeybindLogik` und wird bei jedem swap
         * mitgetauscht. */
        MixinBruecke.rufe("UIBaseMenuKeybindLogik", "wireKeybindEditor", (UIBaseMenu) (Object) this);
    }

}
