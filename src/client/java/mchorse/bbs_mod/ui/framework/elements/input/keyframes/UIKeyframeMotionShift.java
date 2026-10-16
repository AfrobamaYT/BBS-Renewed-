package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import java.util.HashMap;
import java.util.Map;

/**
 * Motion shift: a small handle in the middle of every transition wide enough to hold one. Dragging
 * it moves the transition's midpoint in time, so the motion starts slower and finishes faster (or
 * the other way round) without touching values or keys; double-click resets it. A shifted span gets
 * a strip from the handle to its slow side in the dope sheet. From BBS FS 8f64c66b4, 44276bbdd and
 * c52268d11 (Wemppy4/bbs-fs, MIT); BBS FS's setting to hide the handles is not here.
 */
public class UIKeyframeMotionShift
{
    final UIKeyframes view;
    private final DoubleClick<Keyframe<?>> doubleClick = new DoubleClick<>(true);
    private final Map<Keyframe<?>, Float> originals = new HashMap<>();
    Hit dragging;
    private float original;
    private int pressX;

    record Hit(UIKeyframeSheet sheet, Keyframe<?> key, float start, float duration, int y) {}

    public UIKeyframeMotionShift(UIKeyframes view)
    {
        this.view = view;
    }

    public boolean isDragging()
    {
        return this.dragging != null;
    }

    /** The trackpad and the handle edit every selected key that can be shifted. */
    public static void setzen(UIKeyframes view, float shift, boolean dirty)
    {
        for (UIKeyframeSheet sheet : view.getGraph().getSheets())
        {
            for (Keyframe keyframe : sheet.selection.getSelected())
            {
                if (keyframe.supportsMotionShift()) keyframe.setMotionShift(shift, dirty);
            }
        }

        view.triggerChange();
    }

    float mitteTick(Hit hit)
    {
        return hit.start + hit.duration * (0.5F + hit.key.getMotionShift());
    }

    boolean onHandle(UIContext context, Hit hit)
    {
        return Math.abs(context.mouseX - this.view.toGraphX(this.mitteTick(hit))) <= 5 && Math.abs(context.mouseY - this.handleY(hit)) <= 5;
    }

    int handleY(Hit hit)
    {
        if (!(this.view.getGraph() instanceof UIKeyframeGraph graph)) return hit.y;

        return graph.toGraphY(hit.sheet.channel.getFactory().getY(hit.sheet.channel.interpolate(this.mitteTick(hit))));
    }

    boolean dope()
    {
        return this.view.getGraph() == this.view.getDopeSheet();
    }

    Hit hit(UIContext context)
    {
        if (this.view.isInteracting() || !this.view.graphArea.isInside(context) || context.mouseY < this.view.area.y + IUIKeyframeGraph.TOP_MARGIN
            || Window.isCtrlPressed() || Window.isAltPressed() || Window.isShiftPressed()) return null;

        UIKeyframeSheet sheet = this.view.getGraph().getSheet(context.mouseY);
        KeyframeSegment<?> segment = sheet == null ? null : sheet.channel.find((float) this.view.fromGraphX(context.mouseX));

        /* Repeated keys belong to the source pass: edit there, where their handles are drawn. */
        if (segment == null || segment.isSame() || segment.timeOffset != 0F || !segment.a.supportsMotionShift() || sheet.channel.indexOf(segment.a) < 0) return null;

        Hit hit = this.sichtbar(sheet, segment.a, segment.b);
        int rowY = this.dope() ? this.view.getDopeSheet().getDopeSheetY(sheet) : this.view.area.y + IUIKeyframeGraph.TOP_MARGIN;
        int height = this.dope() ? (int) this.view.getDopeSheet().getTrackHeight() : 16;

        if (hit == null) return null;

        /* Loop edges keep their move and resize targets. */
        for (Object entry : sheet.channel.getLoops())
        {
            KeyframeLoop loop = (KeyframeLoop) entry;
            int loopStart = this.view.toGraphX(loop.start()), loopEnd = this.view.toGraphX(sheet.channel.getLoopEnd(loop));

            if (context.mouseY >= rowY && context.mouseY < rowY + height && context.mouseX >= loopStart - 3 && context.mouseX <= loopEnd + 5
                && (context.mouseY < rowY + 4 || context.mouseY >= rowY + height - 3 || Math.abs(context.mouseX - loopEnd) <= 5)) return null;
        }

        return this.view.getGraph().findKeyframe(context.mouseX, context.mouseY) != null ? null : hit;
    }

    /** Drawing and picking use the same rules: wide enough on screen, inside the view, not under a loop. */
    Hit sichtbar(UIKeyframeSheet sheet, Keyframe<?> key, Keyframe<?> next)
    {
        if (!key.supportsMotionShift() || next == null || !key.isEnabled()) return null;

        float start = key.getTick();
        float duration = key.getDuration() > 0F ? key.getDuration() : next.getTick() - start;
        int left = this.view.toGraphX(start), right = this.view.toGraphX(Math.min(start + duration, next.getTick()));

        if (duration <= 0F || right - left < 28 || right < this.view.graphArea.x || left > this.view.graphArea.ex()) return null;

        int y = this.dope() ? this.view.getDopeSheet().getDopeSheetY(sheet) + (int) this.view.getDopeSheet().getTrackHeight() / 2 : 0;

        if (this.dope() && this.view.getDopeSheet().getSheet(y) != sheet) return null;

        Hit hit = new Hit(sheet, key, start, duration, y);
        int x = this.view.toGraphX(this.mitteTick(hit));

        if (x <= left + 6 || x >= right - 6 || x < this.view.graphArea.x + 5 || x > this.view.graphArea.ex() - 5) return null;

        KeyframeSegment<?> actual = sheet.channel.find(this.mitteTick(hit));

        if (actual == null || actual.a != key || actual.timeOffset != 0F) return null;

        y = this.handleY(hit);

        return y < this.view.area.y + IUIKeyframeGraph.TOP_MARGIN + 4 || y >= this.view.area.ey() - 4 ? null : hit;
    }

    public boolean mouseClicked(UIContext context)
    {
        Hit hit = context.mouseButton == 0 ? this.hit(context) : null;

        if (hit == null || !this.onHandle(context, hit))
        {
            this.doubleClick.hit(null);
            return false;
        }

        /* A handle of an already selected key edits the whole selection, like the trackpad does. */
        if (!hit.sheet.selection.has(hit.key))
        {
            this.view.getGraph().clearSelection();
            hit.sheet.selection.add(hit.key);
        }

        this.view.pickKeyframe(hit.key);
        this.view.cacheKeyframes();

        if (this.doubleClick.hit(hit.key))
        {
            setzen(this.view, 0F, false);
            this.view.submitKeyframes();
            this.view.getGraph().pickSelected();
            return true;
        }

        this.originals.clear();
        for (UIKeyframeSheet sheet : this.view.getGraph().getSheets()) for (Keyframe key : sheet.selection.getSelected()) this.originals.put(key, key.getMotionShift());
        this.dragging = hit;
        this.original = hit.key.getMotionShift();
        this.pressX = context.mouseX;
        return true;
    }

    public void handleMouse(UIContext context)
    {
        if (!this.isDragging()) return;

        Hit hit = this.dragging;
        float shift = this.original + (float) (this.view.fromGraphX(context.mouseX) - this.view.fromGraphX(this.pressX)) / hit.duration;
        double pixels = Math.abs(this.view.toGraphX(hit.start + hit.duration * (0.5F + shift)) - this.view.toGraphX(hit.start + hit.duration * 0.5F));

        /* Within 3 px of the middle it snaps back to none. */
        if (context.mouseX != this.pressX && pixels <= 3) shift = 0F;
        if (context.mouseX != this.pressX) this.doubleClick.hit(null);

        setzen(this.view, shift, false);
    }

    public boolean release(boolean cancel)
    {
        if (!this.isDragging()) return false;

        if (cancel) this.originals.forEach((key, shift) -> key.setMotionShift(shift, false));
        this.view.submitKeyframes();
        /* Submitting rebuilds changed channels, so the picked key must be refreshed. */
        this.view.getGraph().pickSelected();
        this.dragging = null;
        return true;
    }

    public boolean keyPressed(UIContext context)
    {
        if (!this.isDragging()) return false;
        if (context.isPressed(InputConstants.KEY_ESCAPE)) this.release(true);
        return true;
    }

    public void render(UIContext context)
    {
        MotionShiftZeichnung.zeichnen(this, context);
    }
}
