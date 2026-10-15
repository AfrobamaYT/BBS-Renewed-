// Coded by Afrobama/Claude — expose ActionManager's private players list.
//
// WHY: BBSTools' /bbstools crowdmode command walks every active film's
// ActionPlayer to flip the crowd-mode flag and re-spawn actors. It used
// to call ActionManager.getPlayers(), a public getter that was a
// BBSTools source-patch on the old BBS-CML fork. The upstream UI/UX
// revamp rebuilt ActionManager and the field is now `private
// List<ActionPlayer> players` with no public accessor. Rather than
// re-patch the BBS source, expose the private list via this accessor
// mixin (additive infra, survives rebases).
//
// remap = false: BBS ships its own (non-Yarn) class/field names in prod,
// identical to the dev names, so the @Accessor target must not be remapped.
package com.afrobama.bbstools.mixin;

import java.util.List;

import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.actions.ActionPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ActionManager.class, remap = false)
public interface ActionManagerAccessor
{
    @Accessor("players")
    List<ActionPlayer> bbstools$getPlayers();
}
