package mchorse.bbs_mod.integrations.vitrail;

import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

/** Camera and light submissions own independent snapshots; their nested FBO contents own neither. */
public final class VitrailAttachmentCapture
{
    private VitrailAttachmentCapture() {}
    public static boolean traversing() { return VitrailShadowFrame.collecting() || VitrailActorFrame.traversing(); }
    public static boolean ownedWorld() { return VitrailShadowFrame.collecting() ? VitrailShadowFrame.ownedWorld() : VitrailActorFrame.ownedWorld(); }
    public static boolean earlyWorld() { return VitrailShadowFrame.collecting() ? VitrailShadowFrame.earlyWorld() : VitrailActorFrame.earlyWorld(); }
    public static boolean replaying() { return VitrailShadowFrame.collecting() ? VitrailShadowFrame.replaying() : VitrailActorFrame.replaying(); }
    public static boolean mesh(String kind) { return VitrailShadowFrame.collecting() ? VitrailShadowFrame.mesh(kind) : VitrailActorFrame.mesh(kind); }
    public static void enter(Form form, FormRenderingContext context)
    {
        if (VitrailShadowFrame.collecting()) VitrailShadowFrame.enter(form, context);
        else VitrailActorFrame.enter(form, context);
    }
    public static void leave(boolean completed)
    {
        if (VitrailShadowFrame.collecting()) VitrailShadowFrame.leave(completed);
        else VitrailActorFrame.leave(completed);
    }
    public static void retain(FormTranslucentQueue.NetzBefehl mesh)
    {
        if (VitrailShadowFrame.collecting()) VitrailShadowFrame.retainEarlyMesh(mesh);
        else VitrailActorFrame.retainEarlyMesh(mesh);
    }
}
