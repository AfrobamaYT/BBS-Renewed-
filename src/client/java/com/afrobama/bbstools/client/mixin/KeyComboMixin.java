package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.client.keybinds.KeybindOverrides;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors every BBS {@link KeyCombo} into our {@link KeybindOverrides}
 * registry. BBS's stock {@code KeyCombo(String, IKey, int...)} ctor
 * calls its own internal {@code KeybindOverrides.register} — that is a
 * different class than ours. Without this mixin our registry stays
 * empty and {@code UIKeybindEditor} shows "No keybinds match filter."
 * for every search.
 */
@Mixin(KeyCombo.class)
public abstract class KeyComboMixin
{
    @Inject(method = "<init>(Ljava/lang/String;Lmchorse/bbs_mod/l10n/keys/IKey;[I)V",
            at = @At("RETURN"),
            require = 1)
    private void bbstools$mirrorRegister(String id, IKey label, int[] keys, CallbackInfo ci)
    {
        /* Der try/catch von frueher entfaellt: MixinBruecke.rufe schluckt Throwable bereits
         * selbst und meldet es EINMAL — genau die Zusicherung, die hier gebraucht wurde
         * ("nie die BBS-Klasseninitialisierung abbrechen, die das Kuerzel gebaut hat"),
         * nur ohne den stillen Verlust des Grundes. */
        MixinBruecke.rufe("EditorVorschauLogik", "kuerzelSpiegeln", this);
    }

}
