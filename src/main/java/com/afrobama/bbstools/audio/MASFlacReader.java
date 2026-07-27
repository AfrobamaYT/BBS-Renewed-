// Coded by Afrobama/Claude — FLAC decoder for BBS audio import.
// Uses jflac to decode FLAC -> PCM, wraps in BBS Wave. Same pattern as MP3.
package com.afrobama.bbstools.audio;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import mchorse.bbs_mod.audio.Wave;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASFlacReader
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/audio/flac");

    private MASFlacReader() {}

    public static Wave read(InputStream input) throws Exception
    {
        FLACDecoder decoder = new FLACDecoder(input);
        ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream(1 << 16);
        final int[] meta = new int[3]; /* [sampleRate, channels, bitsPerSample] */

        decoder.addPCMProcessor(new PCMProcessor()
        {
            @Override
            public void processStreamInfo(StreamInfo streamInfo)
            {
                meta[0] = streamInfo.getSampleRate();
                meta[1] = streamInfo.getChannels();
                meta[2] = streamInfo.getBitsPerSample();
            }

            @Override
            public void processPCM(ByteData pcm)
            {
                pcmBuffer.write(pcm.getData(), 0, pcm.getLen());
            }
        });

        decoder.decode();

        if (meta[0] == 0)
        {
            throw new IllegalStateException("FLAC decode produced no stream info");
        }

        byte[] pcmData = pcmBuffer.toByteArray();
        LOG.info("Decoded FLAC: {} channels, {} Hz, {} bit, {} bytes PCM",
            meta[1], meta[0], meta[2], pcmData.length);

        /* audioFormat=1 (PCM). bitsPerSample may be 16 or 24. Wave ctor
         *  computes byteRate + blockAlign automatically. */
        return new Wave(1, meta[1], meta[0], meta[2], pcmData);
    }

}
