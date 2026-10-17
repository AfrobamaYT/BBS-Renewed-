package mchorse.bbs_mod.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Declare the known generated WAV container without overriding an explicit user input format. */
final class VideoAudioInput
{
    static String[] prepare(String[] arguments, File audio) throws IOException
    {
        if (audio == null) return arguments;
        byte[] header;
        try (java.io.InputStream input = Files.newInputStream(audio.toPath()))
        {
            header = input.readNBytes(12);
        }
        if (header.length != 12 || !new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
            || !new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WAVE")) return arguments;

        List<String> input = Arrays.stream(arguments).filter(value -> !value.isEmpty()).toList();
        List<String> output = new ArrayList<>();
        boolean explicitFormat = false;
        for (int i = 0; i < input.size(); i++)
        {
            String argument = input.get(i);
            if (argument.equals("-f")) explicitFormat = true;
            if (argument.equals("-i"))
            {
                if (!explicitFormat && i + 1 < input.size() && input.get(i + 1).equals("%AUDIO_TRACK%"))
                {
                    output.add("-f");
                    output.add("wav");
                }
                explicitFormat = false;
            }
            output.add(argument);
        }
        return output.toArray(String[]::new);
    }
}
