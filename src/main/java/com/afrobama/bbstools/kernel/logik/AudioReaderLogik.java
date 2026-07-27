package com.afrobama.bbstools.kernel.logik;

import java.io.InputStream;

import com.afrobama.bbstools.audio.MASFlacReader;
import com.afrobama.bbstools.audio.MASMp3Reader;

import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik hinter {@link AudioReaderMixin} (mixin-paket), hierher verschoben damit sie über
 * {@link com.afrobama.bbstools.kernel.KernelBruecke} hotswapbar bleibt.
 *
 * <p>Fügt BBS' {@code AudioReader.read()} unterstützung für MP3 + FLAC hinzu — die vanilla-
 * methode kennt nur .wav/.ogg.</p>
 */
public final class AudioReaderLogik
{
    private AudioReaderLogik() {}

    private static final Logger BBSTOOLS_AUDIO_LOG =
        LoggerFactory.getLogger("bbstools/audio/reader-mixin");

    /**
     * @return das dekodierte {@link Wave}, oder {@code null} wenn der pfad weder .mp3 noch
     *         .flac ist — dann läuft BBS' eigener rumpf normal weiter.
     */
    public static Wave read(AssetProvider provider, Link link) throws Exception
    {
        String pathLower = link.path.toLowerCase();

        if (pathLower.endsWith(".mp3"))
        {
            BBSTOOLS_AUDIO_LOG.info("Routing {} to MP3 decoder", link.path);

            try (InputStream asset = provider.getAsset(link))
            {
                return MASMp3Reader.read(asset);
            }
        }
        else if (pathLower.endsWith(".flac"))
        {
            BBSTOOLS_AUDIO_LOG.info("Routing {} to FLAC decoder", link.path);

            try (InputStream asset = provider.getAsset(link))
            {
                return MASFlacReader.read(asset);
            }
        }

        /* Other extensions fall through to BBS's native handling. */
        return null;
    }
}
