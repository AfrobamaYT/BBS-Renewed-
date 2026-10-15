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

        /* ⛔ OHNE DIESEN RUECKFALL GAB ES HIER KEIN MP3 UND KEIN FLAC (2026-08-10 gemessen).
         *
         * KernelBruecke braucht den Hotpatch-Agent. Laeuft keiner — der dokumentierte Normalfall
         * auf einem produktiven Server und bei jedem Boot ohne Hotpatch-Werkzeug —, dann kehrt
         * `handleHolen` mit null zurueck und `rufe` liefert IMMER null. Der Handler sah dann
         * genauso aus wie "die Datei ist keine MP3": BBS' eigenes read() lief weiter, das nur
         * .wav/.ogg kennt, und die Datei blieb stumm. Kein Fehler, keine Zeile im Log.
         *
         * Gemessen am laufenden Spiel, gleicher Aufruf auf zwei Wegen:
         *     ueber die Bruecke: null
         *     direkt aufgeloest: klasse+methode DA
         * Die Logik war also die ganze Zeit im Prozess — nur unerreichbar.
         *
         * Deshalb: Bruecke ZUERST (mit Agent bleibt der Weg hotswapbar, genau wie gedacht),
         * und nur wenn sie nichts liefert, direkt. Bei .wav/.ogg kostet das einen zweiten
         * Aufruf, der auf einen Endungsvergleich hinauslaeuft und ebenfalls null gibt —
         * das passiert beim LADEN einer Audiodatei, nicht je Bild.
         *
         * ⚠️ An `null` allein laesst sich NICHT erkennen, ob die Bruecke tot war oder die Datei
         * schlicht keine MP3 ist. Genau darum wird nicht geraten, sondern in beiden Faellen
         * derselbe zweite Weg genommen — er ist in beiden Faellen richtig. */
        if (wave == null)
        {
            wave = com.afrobama.bbstools.kernel.logik.AudioReaderLogik.read(provider, link);
        }

        if (wave != null) cir.setReturnValue(wave);
    }

}
