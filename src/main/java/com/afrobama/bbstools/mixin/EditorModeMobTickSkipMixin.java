// Coded by Afrobama/Claude — Phase 17: short-circuit mob ticks during editor mode
package com.afrobama.bbstools.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;

import com.afrobama.bbstools.editormode.EditorMode;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the user opts into {@code /bbsdebug editor-mode on}, this mixin
 * short-circuits {@link Mob#tick()} for all mobs. AI + pathfinding
 * + brain + LivingEntity bookkeeping all skip. Net effect: a thousand
 * villagers cost almost nothing to have around during scene setup.
 *
 * <p>Self-disables during BBS video export
 * ({@code BBSRendering.isCustomSize()}) so the recorded film captures
 * everything as if editor mode were off.</p>
 *
 * <p>Note: other mixins may hit the same
 * inject point with different conditions. Both call
 * {@link CallbackInfo#cancel()}; whichever fires first wins. Both want
 * the same outcome (skip vanilla tick), so the stacking is harmless.</p>
 */
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
