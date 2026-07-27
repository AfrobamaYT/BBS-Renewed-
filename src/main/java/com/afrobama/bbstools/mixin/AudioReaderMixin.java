// Coded by Afrobama/Claude — Adds MP3 + FLAC support to BBS's AudioReader.
// BBS's stock AudioReader.read() returns null if path isn't .wav/.ogg.
// We inject HEAD, delegate to AudioReaderLogik (kernel, hotswapbar) and
// short-circuit the return value via CallbackInfoReturnable if it decoded.
//
// Zielklasse mchorse.bbs_mod.audio.AudioReader ist server-relevant (BBS lädt
// audio-assets auch serverseitig für replays) — deshalb KernelBruecke
// (src/main, keine client-abhängigkeit), nicht MixinBruecke.
package com.afrobama.bbstools.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;

import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AudioReader.class, remap = false)
public class AudioReaderMixin
{
    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private static void bbstools$readMp3Flac(AssetProvider provider, Link link, CallbackInfoReturnable<Wave> cir) throws Exception
    {
        Wave wave = (Wave) KernelBruecke.rufe("AudioReaderLogik", "read", provider, link);
        if (wave != null) cir.setReturnValue(wave);
    }

}
