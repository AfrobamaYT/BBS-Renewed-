package mchorse.bbs_mod.integrations.vitrail;

import java.util.List;
import java.util.Map;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailShaderOptions
{
    private VitrailShaderOptions() {}
    public static boolean present() { return false; }
    public static List<String> sliders() { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static String getOption(String name) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static Map<String, String> language(String language) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void setOption(String name, String value) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
}
