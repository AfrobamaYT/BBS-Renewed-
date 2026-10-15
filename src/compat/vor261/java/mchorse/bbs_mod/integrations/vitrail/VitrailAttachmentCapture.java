package mchorse.bbs_mod.integrations.vitrail;

import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailAttachmentCapture
{
    private VitrailAttachmentCapture() {}
    public static boolean traversing() { return false; }
    public static boolean ownedWorld() { return false; }
    public static boolean earlyWorld() { return false; }
    public static boolean replaying() { return false; }
    public static boolean mesh(String kind) { return false; }
    public static void enter(Form form, FormRenderingContext context) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void leave(boolean completed) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static void retain(FormTranslucentQueue.NetzBefehl mesh) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
}
