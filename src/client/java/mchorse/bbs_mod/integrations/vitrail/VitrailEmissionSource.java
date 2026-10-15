package mchorse.bbs_mod.integrations.vitrail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Appends a raw-colour fragment list without replacing or assigning meaning to any pack output. */
public final class VitrailEmissionSource
{
    private VitrailEmissionSource() {}

    public static String captureInclude()
    {
        try (java.io.InputStream source = VitrailEmissionSource.class.getResourceAsStream(
            "/assets/bbs/shaders/include/vitrail_emission_capture.glsl"))
        {
            if (source == null) throw new IllegalStateException("BBS emission capture shader is missing");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException error) { throw new IllegalStateException("Cannot read BBS emission capture shader", error); }
    }
}
