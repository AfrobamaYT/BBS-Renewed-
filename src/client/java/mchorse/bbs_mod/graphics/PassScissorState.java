package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderPass;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks actual RenderPass setter calls; null means the pass's default render-area scissor. */
public final class PassScissorState
{
    private static final Map<RenderPass, RenderPass.RenderArea> states = new WeakHashMap<>();
    private PassScissorState() {}
    public static synchronized RenderPass.RenderArea get(RenderPass pass) { return states.get(pass); }
    public static synchronized void set(RenderPass pass, RenderPass.RenderArea area) { states.put(pass, area); }
    public static synchronized void retire(RenderPass pass) { states.remove(pass); }
    public static void apply(RenderPass pass, RenderPass.RenderArea area)
    {
        if (area == null) pass.disableScissor();
        else pass.enableScissor(area.x(), area.y(), area.width(), area.height());
        set(pass, area);
    }
}
