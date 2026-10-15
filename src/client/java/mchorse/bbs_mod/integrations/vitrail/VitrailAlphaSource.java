package mchorse.bbs_mod.integrations.vitrail;

import dev.vitrail.glsl.GlslLexer;
import dev.vitrail.pack.model.AlphaTest;

/** BBS's texture/form alpha classification precedes the unchanged pack fragment body. */
public final class VitrailAlphaSource
{
    private VitrailAlphaSource() {}

    public static String compose(String translated)
    {
        return compose(translated, false);
    }

    public static String compose(String translated, boolean captureEmission)
    {
        if (translated.contains("BbsAlphaTexture") || translated.contains("bbs_native_alpha_main")
            || translated.contains("BbsEmission") || translated.contains("bbs_emission_"))
            throw new IllegalArgumentException("Shaderpack collides with BBS alpha names");
        StringBuilder body = new StringBuilder(translated.length());
        int entries = 0;
        boolean writesDepth = false;
        boolean earlyDepth = false;
        for (GlslLexer.Token token : GlslLexer.lex(translated))
        {
            if (token.directive() == null && token.identifier("gl_FragDepth")) writesDepth = true;
            if (token.directive() == null && token.identifier("early_fragment_tests")) earlyDepth = true;
            if (token.directive() == null && token.identifier("main"))
            {
                body.append("bbs_native_alpha_main");
                entries++;
            }
            else body.append(token.text());
        }
        if (entries == 0) throw new IllegalArgumentException("Translated fragment has no entry point");
        return body + "\nuniform sampler2D BbsAlphaTexture;\nin vec3 BbsAlphaCoordinate;\nflat in int BbsPassMode;\n"
            + (captureEmission ? "#define BBS_CAPTURE_PACK_DEPTH " + (writesDepth && earlyDepth ? "16u\n" : "0u\n")
                + VitrailEmissionSource.captureInclude() : "")
            + "void main() { float bbsSampleAlpha = texture(BbsAlphaTexture, BbsAlphaCoordinate.xy).a;\n"
            + new AlphaTest(AlphaTest.Function.GEQUAL, .1F).discard("bbsSampleAlpha") + "\n"
            + "float bbsAlpha = bbsSampleAlpha * BbsAlphaCoordinate.z;\n"
            + new AlphaTest(AlphaTest.Function.GREATER, 0F).discard("bbsAlpha") + "\n"
            + "if (BbsPassMode == 1) { " + new AlphaTest(AlphaTest.Function.GEQUAL, .999F).discard("bbsAlpha") + " }\n"
            + "if (BbsPassMode == 2) { " + new AlphaTest(AlphaTest.Function.LESS, .999F).discard("bbsAlpha") + " }\n"
            /* Same depth seed as Vitrail Emitter.coveragePrologue: inactive depth-writing branches must not yield undefined data. */
            + (captureEmission && writesDepth ? "gl_FragDepth = gl_FragCoord.z; " : "")
            + "bbs_native_alpha_main(); " + (captureEmission ? "bbs_emission_capture(bbsAlpha, "
                + (writesDepth ? "gl_FragDepth" : "gl_FragCoord.z") + "); " : "") + "}\n";
    }
}
