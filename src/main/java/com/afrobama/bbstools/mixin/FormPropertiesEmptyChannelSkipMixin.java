// Coded by Afrobama/Claude — skip per-tick reflective property walk
// for empty keyframe channels.
//
// Verified via deep-research agent 2026-05-17:
//   `KeyframeChannel.interpolate()` is already lazy for empty channels
//   (early-out at `findSegment` line 109-115). But the CALLER —
//   `FormProperties.applyProperty(...)` at FormProperties.java:131-249 —
//   runs `FormUtils.getProperty(form, propertyId)` (a reflective walk
//   over the form's value tree) BEFORE realising the channel has no
//   keyframes. With 30 bone channels in the pose track × 200 replays ×
//   20 TPS = 120 000 reflective property walks/sec for channels doing
//   nothing.
//
//   This mixin cancels `applyProperty` at HEAD when the channel is
//   empty, skipping both the reflective lookup AND the per-channel
//   bookkeeping (per-tick `setRuntimeValue(null)` at line 247 on empty
//   non-pose channels — defensive overkill; `resetProperties()` at
//   line 251-283 covers real reset paths).
//
//   Knob: BBSPerfConfig.ENABLE_EMPTY_CHANNEL_SKIP (volatile, default ON).
//   Flip false if anything regresses around channel reset behaviour.
//
// remap=false because BBS is in its own intermediate namespace; we
// reference the BBS class + method by their (non-yarn) original names.
package com.afrobama.bbstools.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FormProperties.class, remap = false)
public abstract class FormPropertiesEmptyChannelSkipMixin
{
    @Inject(
        method = "applyProperty",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void bbstools$skipEmptyChannel(
        float tick,
        Form form,
        KeyframeChannel value,
        float blend,
        CallbackInfo ci)
    {
        if (KernelBruecke.rufeUndAbbrechen("LeerkanalUeberspringenLogik", "ueberspringen", value))
        {
            ci.cancel();
        }
    }

}
