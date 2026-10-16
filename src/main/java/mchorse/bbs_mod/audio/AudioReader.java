package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioReader
{
    public static Wave read(AssetProvider provider, Link link) throws Exception
    {
        String pathLower = link.path.toLowerCase();

        if (!pathLower.endsWith(".wav") && !pathLower.endsWith(".ogg"))
        {
            return null;
        }

        /* System.out.println("Reading: " + link); */

        try (InputStream asset = provider.getAsset(link))
        {
            if (pathLower.endsWith(".wav"))
            {
                return readWave(asset);
            }
            else if (pathLower.endsWith(".ogg"))
            {
                return VorbisReader.read(link, asset);
            }
        }

        throw new IllegalStateException("Given link " + link + " isn't a Wave or a Vorbis file!");
    }

    private static Wave readWave(InputStream asset) throws Exception
    {
        byte[] encoded = asset.readAllBytes();
        Wave wave = new WaveReader().read(new ByteArrayInputStream(encoded));

        if (wave == null)
        {
            throw new UnsupportedAudioFileException("Invalid WAV audio");
        }
        if (wave.blockAlign <= 0 || wave.data.length % wave.blockAlign != 0)
        {
            throw new EOFException("Incomplete WAV sample frame");
        }

        /* Both the native mixer and OpenAL consume PCM samples, not packed WAV containers. */
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(encoded)))
        {
            AudioFormat source = input.getFormat();
            AudioFormat.Encoding encoding = source.getEncoding();

            if (!encoding.equals(AudioFormat.Encoding.PCM_SIGNED)
                && !encoding.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                && !encoding.equals(AudioFormat.Encoding.PCM_FLOAT))
            {
                throw new UnsupportedAudioFileException("Unsupported WAV encoding: " + source);
            }

            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2,
                source.getSampleRate(), false);

            if (!AudioSystem.isConversionSupported(target, source))
            {
                throw new UnsupportedAudioFileException("Cannot convert WAV to PCM16: " + source);
            }

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, input))
            {
                Wave decoded = new Wave(1, source.getChannels(), wave.sampleRate, 16, pcm.readAllBytes());

                decoded.lists = wave.lists;
                decoded.cues = wave.cues;

                return decoded;
            }
        }
    }
}
