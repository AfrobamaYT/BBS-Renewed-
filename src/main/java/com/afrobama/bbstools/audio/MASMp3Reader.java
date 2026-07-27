// Coded by Afrobama/Claude — MP3 decoder for BBS audio import.
// BBS's stock AudioReader only handles .wav + .ogg. This decoder
// uses jlayer (javazoom) to decode MP3 -> 16-bit signed PCM, then
// wraps the result in BBS's Wave class so the rest of BBS's audio
// pipeline (SoundBuffer -> SoundPlayer -> AudioRenderer) handles it
// identically to native formats.
//
// Wired into BBS via AudioReaderMixin which adds an `.endsWith(".mp3")`
// branch to AudioReader.read(). Same pattern for FLAC via MASFlacReader.
package com.afrobama.bbstools.audio;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import mchorse.bbs_mod.audio.Wave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASMp3Reader
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/audio/mp3");

    private MASMp3Reader() {}

    /**
     * Decode an MP3 input stream to a Wave (16-bit signed PCM). Reads
     * the full stream, frame-by-frame, accumulates samples to a byte
     * buffer, returns the wrapped Wave. audioFormat=1 (PCM).
     */
    public static Wave read(InputStream input) throws Exception
    {
        Bitstream bitstream = new Bitstream(input);
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream(1 << 16);

        int sampleRate = 0;
        int channels = 0;
        int framesDecoded = 0;

        try
        {
            while (true)
            {
                Header header = bitstream.readFrame();
                if (header == null) break;

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);

                if (sampleRate == 0)
                {
                    sampleRate = output.getSampleFrequency();
                    channels = output.getChannelCount();
                }

                short[] pcm = output.getBuffer();
                int len = output.getBufferLength();
                /* Convert short[] PCM to little-endian byte[] (Wave format). */
                for (int i = 0; i < len; i++)
                {
                    short s = pcm[i];
                    pcmBuffer.write(s & 0xFF);
                    pcmBuffer.write((s >> 8) & 0xFF);
                }

                bitstream.closeFrame();
                framesDecoded++;
            }
        }
        finally
        {
            try { bitstream.close(); } catch (Throwable ignored) {}
        }

        if (sampleRate == 0 || channels == 0)
        {
            throw new IllegalStateException("MP3 decode produced no frames");
        }

        byte[] pcmData = pcmBuffer.toByteArray();
        LOG.info("Decoded MP3: {} frames, {} channels, {} Hz, {} bytes PCM",
            framesDecoded, channels, sampleRate, pcmData.length);

        /* audioFormat=1 (PCM), bitsPerSample=16. Wave ctor computes
         *  byteRate + blockAlign automatically from those. */
        return new Wave(1, channels, sampleRate, 16, pcmData);
    }

}
