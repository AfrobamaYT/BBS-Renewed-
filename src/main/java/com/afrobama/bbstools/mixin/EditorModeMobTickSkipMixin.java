// Coded by Afrobama/Claude — Phase 17: short-circuit mob ticks during editor mode
package com.afrobama.bbstools.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;

import com.afrobama.bbstools.editormode.EditorMode;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Mob.class)
public abstract class EditorModeMobTickSkipMixin
{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bbstools$skipForEditorMode(CallbackInfo ci)
    {
        if (KernelBruecke.rufeUndAbbrechen("EditorModeTickSkipLogik", "ueberspringen", this))
        {
            ci.cancel();
        }
    }

}
