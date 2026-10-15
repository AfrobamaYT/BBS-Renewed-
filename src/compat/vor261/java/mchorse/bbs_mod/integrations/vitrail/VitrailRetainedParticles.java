package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.alt.RenderArea;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Matrix4f;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailRetainedParticles
{
    private VitrailRetainedParticles() {}
    public static boolean active() { return false; }
    public static boolean eligible(RenderPipeline pipeline) { return false; }
    public static Matrix4f view() { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void draw(GehaltenesNetz mesh, Matrix4f view, Texture texture, RenderArea area) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
}
