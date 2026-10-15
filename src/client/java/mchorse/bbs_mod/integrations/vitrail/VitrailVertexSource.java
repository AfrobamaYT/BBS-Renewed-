package mchorse.bbs_mod.integrations.vitrail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/** Adds native GPU deformation to Vitrail's translated entity vertex stage, never its pack parser. */
public final class VitrailVertexSource
{
    public enum Kind { MODEL, SKIN, CROWD }

    private static final Set<String> ENTITY_INPUTS = Set.of("Position", "Color", "UV0", "UV1", "UV2",
        "Normal", "EntityIds", "MidTexCoord", "Tangent");
    private static final Pattern INPUT = Pattern.compile("(?m)^in (\\w+) (\\w+);$");

    private VitrailVertexSource() {}

    public static String compose(String translated, Kind kind, boolean shadow)
    {
        return compose(translated, kind, shadow, false);
    }

    public static String compose(String translated, Kind kind, boolean shadow, boolean captureEmission)
    {
        if (shadow && captureEmission) throw new IllegalArgumentException("Emission cannot capture a light-space pass");
        if (translated.contains("bbs_native_") || translated.contains("BbsCorner"))
            throw new IllegalArgumentException("Shaderpack collides with BBS native deformation names");
        var matcher = INPUT.matcher(translated);
        var seen = new java.util.HashSet<String>();
        StringBuilder globals = new StringBuilder(translated.length());
        while (matcher.find())
        {
            if (ENTITY_INPUTS.contains(matcher.group(2)))
            {
                if (!seen.add(matcher.group(2))) throw new IllegalArgumentException("Duplicate Vitrail entity input");
                matcher.appendReplacement(globals, matcher.group(1) + " " + matcher.group(2) + ";");
            }
        }
        matcher.appendTail(globals);
        if (!seen.equals(ENTITY_INPUTS))
            throw new IllegalArgumentException("Vitrail entity prologue changed: actual inputs " + seen);
        String renamed = renameEntrypoints(globals.toString());

        return renamed + "\n#define BBS_NATIVE_" + kind.name() + "\n"
            + (captureEmission ? "#define BBS_NATIVE_EMISSION_CAPTURE\n" : "")
            + (shadow ? "#define BBS_NATIVE_SHADOW\n" : "") + support()
            + "\nvoid main() { bbs_native_prepare(); bbs_native_pack_main(); }\n";
    }

    private static String renameEntrypoints(String source)
    {
        /* Inactive shader branches remain in translated text. The compiler resolves them. */
        java.util.List<dev.vitrail.glsl.GlslLexer.Token> tokens = dev.vitrail.glsl.GlslLexer.lex(source);
        int names = 0;
        StringBuilder result = new StringBuilder(source.length());
        for (dev.vitrail.glsl.GlslLexer.Token token : tokens)
        {
            if (token.directive() == null && token.identifier("main"))
            {
                result.append("bbs_native_pack_main");
                names++;
            }
            else result.append(token.text());
        }
        if (names == 0) throw new IllegalArgumentException("Translated Vitrail vertex stage has no main");
        return result.toString();
    }

    private static String support()
    {
        try (var source = VitrailVertexSource.class.getResourceAsStream(
            "/assets/bbs/shaders/include/vitrail_native_deformation.glsl"))
        {
            if (source == null) throw new IllegalStateException("BBS native Vitrail deformation shader is missing");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException error)
        {
            throw new IllegalStateException("Cannot read BBS native Vitrail deformation shader", error);
        }
    }
}
