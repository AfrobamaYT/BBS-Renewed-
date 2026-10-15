package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The microphone picked in the voice-over window, kept across restarts. It lived in a static
 * field only, so every start went back to the system default (owner, 2026-10-13). A device that
 * is no longer there falls back to the default instead of failing the take.
 */
final class VoiceOverMikrofon
{
    private VoiceOverMikrofon()
    {}

    private static File datei()
    {
        return new File(BBSMod.getGameFolder(), "config/bbs/settings/voiceover-microphone.txt");
    }

    static String laden()
    {
        try
        {
            File file = datei();
            String name = file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8).strip() : "";

            return !name.isEmpty() && OpenALRecorder.devices().contains(name) ? name : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    static void speichern(String name)
    {
        try
        {
            File file = datei();

            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), name == null ? "" : name, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            System.err.println("[voiceover] could not keep the microphone choice: " + e);
        }
    }
}
