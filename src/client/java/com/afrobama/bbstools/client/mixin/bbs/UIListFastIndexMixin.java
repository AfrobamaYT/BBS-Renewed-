// Coded by Afrobama/Claude — kills the O(N^2) deep-equals freeze in UIList selection
// on bulk replay ops (e.g. duplicate one replay 8999×).
//
// Observed freeze stack (render thread, RUNNABLE for minutes):
//     ArrayList.indexOf -> ValueGroup.equals -> ValueList.equals -> ArrayList.equals (recursive)
//       UIList.setCurrent(UIList.java:299)        // this.list.indexOf(element)
//       UIList.setCurrentScroll(UIList.java:346)
//       UIReplaysEditor.setReplay(...)
//       UIReplayList.dupeReplay(UIReplayList.java:1940)
//
// UIList<T>.setCurrent(T) does `this.list.indexOf(element)`. this.list is a plain
// java.util.ArrayList of the list items — for the replay list that's Replay/ValueGroup,
// whose equals is a DEEP recursive keyframe compare. Locating ONE element in a 9000-item
// replay list is O(N * keyframes) of deep compares = a multi-minute render-thread freeze.
//
// BBS already proves identity matching is the intended semantics here: it ships
// setCurrentDirect(T) (UIList.java:307) which scans `this.list.get(i) == element` by
// REFERENCE — but setCurrentScroll routes through the equals-based setCurrent instead.
// We redirect the indexOf in BOTH setCurrent overloads to an identity-first scan: O(N)
// cheap pointer checks, fall back to equals only if the exact instance isn't present
// (preserving List semantics for any caller that passes a data-equal-but-different
// instance — never the dupe/setReplay path, which always passes the contained instance).
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = UIList.class, remap = false)
public class UIListFastIndexMixin
{
    /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
     * com.afrobama.blendertransform.UIListFastIndexLogik, siehe MixinBruecke-javadoc. */
    @Redirect(
        method = {
            "setCurrent(Ljava/lang/Object;)V",
            "setCurrent(Ljava/util/List;)V"
        },
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
    private int bbstools$fastReferenceIndexOf(List list, Object element)
    {
        Object ergebnis = MixinBruecke.rufe("UIListFastIndexLogik", "fastReferenceIndexOf", list, element);
        return ergebnis instanceof Integer ? (Integer) ergebnis : list.indexOf(element);
    }

}
