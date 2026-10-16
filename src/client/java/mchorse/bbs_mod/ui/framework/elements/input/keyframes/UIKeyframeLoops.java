package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.overlays.UIKeyframeLoopOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoops;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Interaction and drawing of finite loop blocks, shared by every keyframe timeline. */
public class UIKeyframeLoops
{
    final UIKeyframes view;
    private final DoubleClick<String> doubleClick = new DoubleClick<>(false);
    private final UIKeyframeMotionShift schub;
    KeyframeChannel<?> selectedChannel;
    String selectedId;
    private Map<KeyframeChannel<?>, BaseType> before;
    private List<KeyframeChannel<?>> draggingChannels;
    private float pressTick;
    private float originalStart;
    private float originalEnd;
    boolean resizing;

    public UIKeyframeLoops(UIKeyframes view)
    {
        this.view = view;
        this.schub = new UIKeyframeMotionShift(view);
    }

    record Hit(UIKeyframeSheet sheet, KeyframeLoop loop, int y, int height) {}

    Hit hit(UIContext context)
    {
        if (!this.view.graphArea.isInside(context) || context.mouseY < this.view.area.y + IUIKeyframeGraph.TOP_MARGIN) return null;
        UIKeyframeSheet sheet = this.view.getGraph().getSheet(context.mouseY);
        if (sheet == null) return null;
        boolean dope = this.view.getGraph() == this.view.getDopeSheet();
        int y = dope ? this.view.getDopeSheet().getDopeSheetY(sheet) : this.view.area.y + IUIKeyframeGraph.TOP_MARGIN;
        int height = dope ? (int) this.view.getDopeSheet().getTrackHeight() : 16;

        for (Object entry : sheet.channel.getLoops())
        {
            KeyframeLoop loop = (KeyframeLoop) entry;
            int start = this.view.toGraphX(loop.start()), end = this.view.toGraphX(sheet.channel.getLoopEnd(loop));
            if (context.mouseX >= start - 3 && context.mouseX <= end + 4) return new Hit(sheet, loop, y, height);
        }
        return null;
    }

    public void menu(ContextMenuManager menu, UIContext context)
    {
        KeyframeLoopMenue.fuellen(this, menu, context);
    }

    public void open(KeyframeChannel<?> channel, String id)
    {
        this.selectedChannel = channel;
        this.selectedId = id;
        UIOverlay.addOverlay(this.view.getContext(), new UIKeyframeLoopOverlay(this, channel, id), 240, 164);
    }

    public void setEnd(KeyframeChannel<?> channel, String id, float end)
    {
        List<KeyframeChannel<?>> members = KeyframeLoops.members(channel, id);
        this.edit(members, () -> KeyframeLoops.resize(members, id, end));
    }

    public void remove(KeyframeChannel<?> channel, String id)
    {
        List<KeyframeChannel<?>> members = KeyframeLoops.members(channel, id);
        this.edit(members, () -> members.forEach(member -> member.removeLoop(id)));
        this.selectedId = null;
    }

    private Map<KeyframeChannel<?>, BaseType> snapshot(List<KeyframeChannel<?>> channels)
    {
        Map<KeyframeChannel<?>, BaseType> result = new LinkedHashMap<>();
        for (KeyframeChannel<?> channel : channels) result.put(channel, channel.toData());
        return result;
    }

    void edit(List<KeyframeChannel<?>> channels, Runnable edit)
    {
        for (KeyframeChannel<?> channel : channels) channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        edit.run();
        for (KeyframeChannel<?> channel : channels) channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        this.view.triggerChange();
    }

    public boolean mouseClicked(UIContext context)
    {
        if (this.schub.mouseClicked(context)) return true;
        if (context.mouseButton != 0) return false;
        Hit hit = this.hit(context);
        if (hit == null)
        {
            this.selectedId = null;
            return false;
        }
        int endX = this.view.toGraphX(hit.sheet.channel.getLoopEnd(hit.loop));
        boolean handle = Math.abs(context.mouseX - endX) <= 5 && context.mouseY >= hit.y && context.mouseY < hit.y + hit.height;
        boolean bar = context.mouseY >= hit.y && context.mouseY < hit.y + hit.height
            && (context.mouseY < hit.y + 4 || context.mouseY >= hit.y + hit.height - 3);
        if (!handle && (Window.isCtrlPressed() || Window.isAltPressed() || Window.isShiftPressed())) return false;

        /* Original keys retain ordinary selection and dragging. */
        if (!handle && !bar && this.view.getGraph().findKeyframe(context.mouseX, context.mouseY) != null)
        {
            this.selectedId = null;
            return false;
        }

        this.view.getGraph().clearSelection();
        this.selectedChannel = hit.sheet.channel;
        this.selectedId = hit.loop.id();
        if (this.doubleClick.hit(this.selectedId))
        {
            this.open(this.selectedChannel, this.selectedId);
            return true;
        }

        if (!handle && !bar)
        {
            float local = hit.loop.sourceTick((float) this.view.fromGraphX(context.mouseX));
            for (Object entry : hit.sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) entry;
                int y = this.view.getGraph() instanceof UIKeyframeGraph graph ? graph.toGraphY(key.getY(0)) : hit.y + hit.height / 2;
                if (hit.loop.containsSource(key.getTick()) && Math.abs(this.view.toGraphX(local) - this.view.toGraphX(key.getTick())) <= 5 && Math.abs(context.mouseY - y) <= 5)
                {
                    hit.sheet.selection.add(key);
                    this.view.pickKeyframe(key);
                    return true;
                }
            }
        }

        this.draggingChannels = KeyframeLoops.members(this.selectedChannel, this.selectedId);
        this.before = this.snapshot(this.draggingChannels);
        this.pressTick = (float) this.view.fromGraphX(context.mouseX);
        this.originalStart = hit.loop.start();
        this.originalEnd = hit.sheet.channel.getLoopEnd(hit.loop);
        this.resizing = handle;
        return true;
    }

    /** A loop or a motion shift handle is being dragged: both answer to the same editor hooks. */
    public boolean isDragging()
    {
        return this.before != null || this.schub.isDragging();
    }

    boolean ziehtSchleife()
    {
        return this.before != null;
    }

    public void handleMouse(UIContext context)
    {
        this.schub.handleMouse(context);
        if (!this.ziehtSchleife()) return;
        KeyframeLoop loop = this.selectedChannel.getLoop(this.selectedId);
        if (loop == null) return;
        float delta = (float) this.view.fromGraphX(context.mouseX) - this.pressTick;
        if (!Window.isShiftPressed()) delta = Math.round(delta);
        if (this.resizing)
        {
            float end = this.originalEnd + delta;
            if (Window.isCtrlPressed()) end = loop.start() + Math.max(1, Math.round((end - loop.start()) / loop.period())) * loop.period();
            KeyframeLoops.resize(this.draggingChannels, this.selectedId, end);
        }
        else KeyframeLoops.move(this.draggingChannels, this.selectedId, this.originalStart + delta - loop.start());
        this.view.triggerChange();
    }

    public boolean release(boolean cancel)
    {
        if (this.schub.release(cancel)) return true;
        if (!this.ziehtSchleife()) return false;
        Map<KeyframeChannel<?>, BaseType> after = this.snapshot(this.draggingChannels);
        boolean changed = !this.before.equals(after);
        if (cancel || changed)
        {
            this.before.forEach(KeyframeChannel::fromData);
            if (!cancel) this.edit(this.draggingChannels, () -> after.forEach(KeyframeChannel::fromData));
        }
        this.before = null;
        this.draggingChannels = null;
        this.view.triggerChange();
        return true;
    }

    public boolean keyPressed(UIContext context)
    {
        if (this.schub.keyPressed(context)) return true;
        if (this.ziehtSchleife()) return context.isPressed(InputConstants.KEY_ESCAPE) ? this.release(true) : true;
        if (this.view.area.isInside(context) && this.selectedId != null && this.view.getGraph().getSelected() == null && context.isPressed(InputConstants.KEY_DELETE))
        {
            this.remove(this.selectedChannel, this.selectedId);
            return true;
        }
        return false;
    }

    public void reset()
    {
        this.release(false);
        this.selectedId = null;
        this.selectedChannel = null;
    }

    /** Loops under the topmost keyframes, the drag status above them. */
    public void render(UIContext context, Runnable topmostKeyframes)
    {
        KeyframeLoopZeichnung.zeichnen(this, context);
        this.schub.render(context);
        topmostKeyframes.run();
        KeyframeLoopZeichnung.status(this, context);
    }
}
