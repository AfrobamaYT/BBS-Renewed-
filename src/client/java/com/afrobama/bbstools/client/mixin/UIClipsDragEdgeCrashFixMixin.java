package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.clips.Clip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Workaround for a long-standing BBS bug: dragging a clip's left or
 * right edge in the timeline crashes with
 * {@code IndexOutOfBoundsException: Index -1 out of bounds for length 0}
 * when the {@code grabbedData} / {@code grabbedClips} state lists
 * have been emptied (typically after the clip has been on the
 * timeline a while + interaction state desynced). The crash is at
 * {@code grabbedData.get(grabbedData.size() - 1)} where
 * {@code size() == 0} so the index becomes -1.
 *
 * <p>Cancel both methods at HEAD when the state lists are empty —
 * the drag operation simply does nothing for that frame, which is
 * the same as the user not having grabbed a clip. No state corruption,
 * no crash. The user releases the mouse + retries; works fine.</p>
 *
 * <p>This is a BBS-internal bug. Reported to McHorse but easier to
 * patch on our side via this mixin so the user's flagship recording
 * session doesn't get bricked by an empty-list crash.</p>
 */
@Mixin(UIClips.class)
public abstract class UIClipsDragEdgeCrashFixMixin
{
    /* BRÜCKEN-MUSTER (Aufgabe #56): logik in
     * com.afrobama.blendertransform.UIClipsDragEdgeGuardLogik.
     *
     * ⛔ DIE @Shadow-FELDER SIND WEG. Der kernel darf keine mixin-schnittstelle in der
     * signatur führen (klassenlader-identität) — er liest `grabbedData`/`grabbedClips`
     * reflektiv über MixinBruecke.feldLesen, das über objekt.getClass() geht und damit
     * loader-unabhängig ist. Deshalb reicht der handler nur `this` als Object hinüber. */
    @Inject(method = "dragLeftEdge", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbstools$guardDragLeftEdge(List<Clip> clipList, int dx, int dy, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("UIClipsDragEdgeGuardLogik", "abbrechen", this))
        {
            ci.cancel();
        }
    }

    @Inject(method = "dragRightEdge", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbstools$guardDragRightEdge(List<Clip> clipList, int dx, int dy, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("UIClipsDragEdgeGuardLogik", "abbrechen", this))
        {
            ci.cancel();
        }
    }

}
