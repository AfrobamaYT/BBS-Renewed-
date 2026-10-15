package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Matrix4f;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailActorFrame
{
    private VitrailActorFrame() {}
    public static boolean collecting() { return false; }
    public static boolean traversing() { return false; }
    public static boolean replaying() { return false; }
    public static boolean ownedWorld() { return false; }
    public static boolean earlyWorld() { return false; }
    public static boolean mesh(String kind) { return false; }
    public static boolean world(FormRenderingContext context) { return false; }
    public static boolean collected(Form form, IEntity entity) { return false; }
    public static boolean collect(Form form, FormRenderingContext context) { return false; }
    public static boolean suppressModel(RenderPipeline pipeline, IModelVAO vao, Matrix4f pose,
        float r, float g, float b, float a, int light, int overlay, int pass, Texture texture, float emission) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void enter(Form form, FormRenderingContext context) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void leave(boolean completed) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void retainEarlyMesh(FormTranslucentQueue.NetzBefehl mesh) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static boolean retainParticle(FormTranslucentQueue.NetzBefehl mesh) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void original(Form form, IEntity entity, Runnable submission) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void replay(Form form, IEntity entity, Runnable submission) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
}
