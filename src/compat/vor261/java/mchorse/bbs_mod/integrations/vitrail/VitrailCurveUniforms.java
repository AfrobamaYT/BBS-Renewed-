package mchorse.bbs_mod.integrations.vitrail;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailCurveUniforms
{
    private VitrailCurveUniforms() {}
    public static void reset() {}
    public static void setValue(String name, float value) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
}
