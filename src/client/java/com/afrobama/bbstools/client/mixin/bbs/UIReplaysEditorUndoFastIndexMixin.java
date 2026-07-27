// Coded by Afrobama/Claude — kills the O(N^2) undo-collection freeze on bulk replay
// ops (e.g. "process replay scatter" over 9000 replays).
//
// BBS UIReplaysEditor.collectUndoData does:
//     int index = this.film.replays.getList().indexOf(this.getReplay());
// List.indexOf walks the list calling Replay.equals — which is ValueGroup.equals, a
// DEEP recursive compare of every keyframe/value. So one call is O(N * keyframes);
// scattering N replays collects undo per change → O(N^2 * keyframes). At 9000
// replays the render thread froze for minutes inside ValueGroup.equals (observed
// stack: collectUndoData → indexOf → ValueGroup.equals → IKeyframeFactory.compare).
//
// The replay we're locating IS the same object already in the list, so a
// reference-identity scan finds it without any deep compare: O(N) cheap pointer
// checks instead of O(N) deep-equals. Behaviour-identical (returns the same index;
// -1 if somehow absent, matching indexOf).
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = UIReplaysEditor.class, remap = false)
public class UIReplaysEditorUndoFastIndexMixin
{
    /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
     * com.afrobama.blendertransform.UIReplaysEditorUndoFastIndexLogik. */
    @Redirect(
        method = "collectUndoData",
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
    private int bbstools$fastReferenceIndexOf(List list, Object self)
    {
        Object ergebnis = MixinBruecke.rufe("UIReplaysEditorUndoFastIndexLogik", "fastReferenceIndexOf", list, self);
        return ergebnis instanceof Integer ? (Integer) ergebnis : -1;
    }

}
