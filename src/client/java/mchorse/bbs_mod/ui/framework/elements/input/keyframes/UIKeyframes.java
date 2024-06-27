package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * - Editing inside
 */
public class UIKeyframes extends UIElement
{
    /* Keyframes */

    private List<UIKeyframeSheet> sheets = new ArrayList<>();

    /* Editing states */

    private boolean selecting;
    private boolean navigating;
    private int dragging = -1;

    private int lastX;
    private int lastY;
    private int originalX;
    private int originalY;
    private int originalT;

    /* Fields */

    private final UIKeyframeGraph graph = new UIKeyframeGraph(this);
    private IUIKeyframeGraph currentGraph = this.graph;

    private final Scale xAxis = new Scale(this.area, ScrollDirection.HORIZONTAL);
    private final Scale yAxis = new Scale(this.area, ScrollDirection.VERTICAL);

    private final Consumer<Keyframe> callback;
    private Consumer<UIContext> backgroundRender;
    private Supplier<Integer> duration;

    private IAxisConverter converter;

    public UIKeyframes(Consumer<Keyframe> callback)
    {
        this.callback = callback;

        /* Context menu items */
        this.context((menu) ->
        {
            /* TODO: if (this.isEditing())
            {
                menu.action(Icons.CLOSE, UIKeys.KEYFRAMES_CONTEXT_EXIT_TRACK, () -> this.properties.editSheet(null));
            }
            else
            {
                UIProperty sheet = this.getSheet(this.getContext().mouseY);

                if (sheet != null)
                {
                    menu.action(Icons.EDIT, UIKeys.KEYFRAMES_CONTEXT_EDIT_TRACK.format(sheet.id), () -> this.editSheet(sheet));
                }
            } */

            menu.action(Icons.MAXIMIZE, UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, this::resetView);
            menu.action(Icons.FULLSCREEN, UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, () -> this.currentGraph.selectAll());

            if (this.currentGraph.getSelected() != null)
            {
                menu.action(Icons.REMOVE, UIKeys.KEYFRAMES_CONTEXT_REMOVE, () -> this.currentGraph.removeSelected());
                menu.action(Icons.COPY, UIKeys.KEYFRAMES_CONTEXT_COPY, this::copyKeyframes);
            }

            Map<String, PastedKeyframes> pasted = this.parseKeyframes();

            if (pasted != null)
            {
                UIContext context = this.getContext();
                final Map<String, PastedKeyframes> keyframes = pasted;
                double offset = this.fromGraphX(context.mouseX);
                int mouseY = context.mouseY;

                menu.action(Icons.PASTE, UIKeys.KEYFRAMES_CONTEXT_PASTE, () -> this.pasteKeyframes(keyframes, (long) offset, mouseY));
            }
        });

        /* Keys */
        IKey category = UIKeys.KEYFRAMES_KEYS_CATEGORY;

        this.keys().register(Keys.KEYFRAMES_MAXIMIZE, this::resetView).inside().category(category);
        this.keys().register(Keys.KEYFRAMES_SELECT_ALL, () -> this.currentGraph.selectAll()).inside().category(category);
        this.keys().register(Keys.COPY, this::copyKeyframes).inside().category(category);
        this.keys().register(Keys.PASTE, () ->
        {
            Map<String, PastedKeyframes> pasted = this.parseKeyframes();

            if (pasted != null)
            {
                UIContext context = this.getContext();
                double offset = this.fromGraphX(context.mouseX);
                int mouseY = context.mouseY;

                this.pasteKeyframes(pasted, (long) offset, mouseY);
            }
        }).inside().category(category);
        this.keys().register(Keys.DELETE, () -> this.currentGraph.removeSelected()).inside().category(category);
    }

    /* Copy-pasting */

    /**
     * Parse keyframes from clipboard
     */
    private Map<String, PastedKeyframes> parseKeyframes()
    {
        return this.parseKeyframes(Window.getClipboardMap("_CopyKeyframes"));
    }

    private Map<String, PastedKeyframes> parseKeyframes(MapType data)
    {
        if (data == null)
        {
            return null;
        }

        Map<String, PastedKeyframes> temp = new HashMap<>();

        for (String key : data.keys())
        {
            MapType map = data.getMap(key);
            ListType list = map.getList("keyframes");
            IKeyframeFactory serializer = KeyframeFactories.FACTORIES.get(map.getString("type"));

            for (int i = 0, c = list.size(); i < c; i++)
            {
                PastedKeyframes pastedKeyframes = temp.computeIfAbsent(key, k -> new PastedKeyframes(serializer));
                Keyframe keyframe = new Keyframe("", serializer);

                keyframe.fromData(list.getMap(i));
                pastedKeyframes.keyframes.add(keyframe);
            }
        }

        return temp.isEmpty() ? null : temp;
    }

    /**
     * Copy keyframes to clipboard
     */
    private void copyKeyframes()
    {
        Window.setClipboard(this.serializeKeyframes(), "_CopyKeyframes");
    }

    private MapType serializeKeyframes()
    {
        MapType keyframes = new MapType();

        for (UIKeyframeSheet property : this.currentGraph.getSheets())
        {
            List<Keyframe> selected = property.selection.getSelected();

            if (selected.isEmpty())
            {
                continue;
            }

            MapType data = new MapType();
            ListType list = new ListType();

            data.putString("type", CollectionUtils.getKey(KeyframeFactories.FACTORIES, property.channel.getFactory()));
            data.put("keyframes", list);

            for (Keyframe keyframe : selected)
            {
                list.add(keyframe.toData());
            }

            if (!list.isEmpty())
            {
                keyframes.put(property.id, data);
            }
        }

        return keyframes;
    }

    /**
     * Paste copied keyframes to clipboard
     */
    protected void pasteKeyframes(Map<String, PastedKeyframes> keyframes, long offset, int mouseY)
    {
        List<UIKeyframeSheet> sheets = this.currentGraph.getSheets();

        this.currentGraph.clearSelection();

        if (keyframes.size() == 1)
        {
            UIKeyframeSheet current = this.currentGraph.getSheet(mouseY);

            if (current == null)
            {
                current =  sheets.get(0);
            }

            this.pasteKeyframesTo(current, keyframes.get(keyframes.keySet().iterator().next()), offset);
        }
        else
        {
            for (Map.Entry<String, PastedKeyframes> entry : keyframes.entrySet())
            {
                for (UIKeyframeSheet property : sheets)
                {
                    if (!property.id.equals(entry.getKey()))
                    {
                        continue;
                    }

                    this.pasteKeyframesTo(property, entry.getValue(), offset);
                }
            }
        }

        this.pickSelected();
    }

    private void pasteKeyframesTo(UIKeyframeSheet sheet, PastedKeyframes pastedKeyframes, long offset)
    {
        if (sheet.channel.getFactory() != pastedKeyframes.factory)
        {
            return;
        }

        long firstX = pastedKeyframes.keyframes.get(0).getTick();
        List<Keyframe> toSelect = new ArrayList<>();

        for (Keyframe keyframe : pastedKeyframes.keyframes)
        {
            keyframe.setTick(keyframe.getTick() - firstX + offset);

            int index = sheet.channel.insert(keyframe.getTick(), keyframe.getValue());
            Keyframe inserted = sheet.channel.get(index);

            inserted.copy(keyframe);
            toSelect.add(inserted);
        }

        for (Keyframe select : toSelect)
        {
            sheet.selection.add(sheet.channel.getKeyframes().indexOf(select));
        }

        this.pickSelected();
    }

    /* Getters & setters */

    public UIKeyframes backgroundRenderer(Consumer<UIContext> backgroundRender)
    {
        this.backgroundRender = backgroundRender;

        return this;
    }

    public UIKeyframes duration(Supplier<Integer> duration)
    {
        this.duration = duration;

        return this;
    }

    public UIKeyframes axisConverter(IAxisConverter converter)
    {
        this.converter = converter;

        return this;
    }

    public IAxisConverter getConverter()
    {
        return this.converter;
    }

    public IUIKeyframeGraph getGraph()
    {
        return this.currentGraph;
    }

    public Scale getXAxis()
    {
        return this.xAxis;
    }

    public Scale getYAxis()
    {
        return this.yAxis;
    }

    public int getDuration()
    {
        return this.duration == null ? 0 : this.duration.get();
    }

    public boolean isSelecting()
    {
        return this.selecting;
    }

    public boolean isNavigating()
    {
        return this.navigating;
    }

    /* Sheet management */

    public void removeAllSheets()
    {
        this.graph.removeAllSheets();
    }

    public void addSheet(UIKeyframeSheet sheet)
    {
        this.graph.addSheet(sheet);
    }

    public void pickSelected()
    {
        this.pickKeyframe(this.currentGraph.getSelected());
    }

    public void pickKeyframe(Keyframe keyframe)
    {
        if (this.callback != null)
        {
            this.callback.accept(keyframe);
        }
    }

    /* Graphing */

    public int toGraphX(double tick)
    {
        return (int) this.xAxis.to(tick);
    }

    public double fromGraphX(int mouseX)
    {
        return this.xAxis.from(mouseX);
    }

    public int toGraphY(double value)
    {
        return (int) this.yAxis.to(value);
    }

    public double fromGraphY(int mouseY)
    {
        return this.yAxis.from(mouseY);
    }

    public void resetView()
    {
        this.resetViewX();
    }

    public void resetViewX()
    {
        int c = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        /* Find minimum and maximum */
        for (UIKeyframeSheet property : this.sheets)
        {
            List keyframes = property.channel.getKeyframes();

            for (Object object : keyframes)
            {
                Keyframe frame = (Keyframe) object;

                min = Integer.min((int) frame.getTick(), min);
                max = Integer.max((int) frame.getTick(), max);
            }

            c = Math.max(c, keyframes.size());
        }

        if (c <= 1)
        {
            min = 0;
            max = this.getDuration();
        }

        if (Math.abs(max - min) > 0.01F)
        {
            this.xAxis.viewOffset(min, max, this.area.w, 30);
        }
        else
        {
            this.xAxis.set(0, 2);
        }
    }

    public void resetViewY(UIKeyframeSheet current)
    {
        this.yAxis.set(0, 2);

        KeyframeChannel channel = current.channel;
        List<Keyframe> keyframes = channel.getKeyframes();
        int c = keyframes.size();

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        if (c > 1)
        {
            for (int i = 0; i < c; i++)
            {
                Keyframe frame = keyframes.get(i);

                minY = Math.min(minY, frame.getY(i));
                maxY = Math.max(maxY, frame.getY(i));
            }
        }
        else
        {
            minY = -10;
            maxY = 10;

            if (c == 1)
            {
                minY = maxY = channel.get(0).getY(0);
            }
        }

        if (Math.abs(maxY - minY) < 0.01F)
        {
            /* Centerize */
            this.yAxis.setShift(minY);
        }
        else
        {
            /* Spread apart vertically */
            this.yAxis.viewOffset(minY, maxY, this.area.h, 20);
        }
    }

    public Area getGrabbingArea(UIContext context)
    {
        Area area = new Area();

        area.setPoints(this.originalX, this.originalY, context.mouseX, context.mouseY, 3);

        return area;
    }

    /* User input */

    @Override
    public void resize()
    {
        super.resize();

        this.currentGraph.resize();
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.currentGraph.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context))
        {
            this.lastX = this.originalX = context.mouseX;
            this.lastY = this.originalY = context.mouseY;

            if (Window.isCtrlPressed() && context.mouseButton == 0)
            {
                this.removeOrCreateKeyframe(context);
            }
            else if (Window.isAltPressed() && context.mouseButton == 0)
            {
                this.duplicateOrSelectColumn(context);
            }
            else if (context.mouseButton == 0)
            {
                this.pickOrStartSelectingKeyframes(context);
            }
            else if (context.mouseButton == 2)
            {
                this.navigating = true;
            }

            return context.mouseButton != 1;
        }

        return super.subMouseClicked(context);
    }

    private void removeOrCreateKeyframe(UIContext context)
    {
        Keyframe keyframe = this.currentGraph.findKeyframe(context.mouseX, context.mouseY);

        if (keyframe != null)
        {
            this.currentGraph.removeKeyframe(keyframe);
        }
        else
        {
            this.currentGraph.addKeyframe(context.mouseX, context.mouseY);
        }
    }

    private void duplicateOrSelectColumn(UIContext context)
    {
        if (this.currentGraph.getSelected() != null)
        {
            /* Duplicate */
            int tick = (int) Math.round(this.fromGraphX(context.mouseX));

            this.pasteKeyframes(this.parseKeyframes(this.serializeKeyframes()), tick, context.mouseY);

            return;
        }

        /* Select a column */
        this.currentGraph.selectByX(context.mouseX);
        this.pickSelected();
    }

    private void pickOrStartSelectingKeyframes(UIContext context)
    {
        /* Picking keyframe or initiating selection */
        Keyframe found = this.currentGraph.findKeyframe(context.mouseX, context.mouseY);
        boolean shift = Window.isShiftPressed();

        if (shift && found == null)
        {
            this.selecting = true;
        }

        if (found != null)
        {
            UIKeyframeSheet sheet = this.currentGraph.getSheet(found);

            if (!shift && !sheet.selection.has(found))
            {
                this.currentGraph.clearSelection();
            }

            sheet.selection.add(found);

            found = this.currentGraph.getSelected();

            this.pickKeyframe(found);
        }
        else if (!this.selecting)
        {
            this.currentGraph.clearSelection();
            this.pickKeyframe(null);
        }

        if (!this.selecting)
        {
            this.dragging = 0;

            if (found != null)
            {
                this.originalT = (int) found.getTick();
            }
        }
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.currentGraph.mouseReleased(context);

        if (this.selecting)
        {
            this.currentGraph.selectInArea(this.getGrabbingArea(context));
            this.pickSelected();
        }

        if (this.dragging > 0)
        {
            for (UIKeyframeSheet sheet : this.currentGraph.getSheets())
            {
                sheet.sort();
            }
        }

        this.navigating = false;
        this.selecting = false;
        this.dragging = -1;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.navigating)
        {
            this.currentGraph.mouseScrolled(context);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        this.handleMouse(context);

        context.batcher.clip(this.area, context);

        this.renderBackground(context);
        this.currentGraph.render(context);

        if (this.selecting)
        {
            context.batcher.normalizedBox(this.originalX, this.originalY, context.mouseX, context.mouseY, Colors.setA(Colors.ACTIVE, 0.25F));
        }

        this.currentGraph.postRender(context);

        context.batcher.unclip(context);
    }

    /**
     * Handle any related mouse logic during rendering
     */
    protected void handleMouse(UIContext context)
    {
        this.currentGraph.handleMouse(context, this.lastX, this.lastY);

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        boolean mouseHasMoved = mouseX != this.lastX || mouseY != this.lastY;

        if (this.dragging == 0 && mouseHasMoved)
        {
            this.dragging = 1;
        }
        else if (this.dragging == 1)
        {
            if (this.currentGraph.getSelected() != null)
            {
                int offset = (int) (Math.round(this.fromGraphX(this.originalX)) - this.originalT);

                this.currentGraph.setTick(Math.round(this.fromGraphX(mouseX)) - offset);
            }
            else
            {
                this.moveNoKeyframes(context);
            }
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    protected void moveNoKeyframes(UIContext context)
    {}

    /**
     * Render background, specifically backdrop and borders if the duration is present
     */
    protected void renderBackground(UIContext context)
    {
        this.area.render(context.batcher, Colors.A50);

        int duration = this.getDuration();

        if (duration > 0)
        {
            int leftBorder = this.toGraphX(0);
            int rightBorder = this.toGraphX(duration);

            if (leftBorder > this.area.x) context.batcher.box(this.area.x, this.area.y, Math.min(this.area.ex(), leftBorder), this.area.y + this.area.h, Colors.A50);
            if (rightBorder < this.area.ex()) context.batcher.box(Math.max(this.area.x, rightBorder), this.area.y, this.area.ex() , this.area.y + this.area.h, Colors.A50);
        }

        if (this.backgroundRender != null)
        {
            this.backgroundRender.accept(context);
        }
    }

    /* Caching state */

    public KeyframeState cacheState()
    {
        KeyframeState state = new KeyframeState();

        state.extra.putDouble("x_min", this.xAxis.getMinValue());
        state.extra.putDouble("x_max", this.xAxis.getMaxValue());
        this.currentGraph.saveState(state.extra);

        for (UIKeyframeSheet property : this.sheets)
        {
            state.selected.add(new ArrayList<>(property.selection.getIndices()));
        }

        return state;
    }

    public void applyState(KeyframeState state)
    {
        this.xAxis.view(state.extra.getDouble("x_min"), state.extra.getDouble("x_max"));
        this.currentGraph.restoreState(state.extra);

        List<UIKeyframeSheet> properties = this.sheets;

        for (int i = 0; i < properties.size(); i++)
        {
            if (CollectionUtils.inRange(state.selected, i))
            {
                properties.get(i).selection.clear();
                properties.get(i).selection.addAll(state.selected.get(i));
            }
        }

        this.pickSelected();
    }

    private static class PastedKeyframes
    {
        public IKeyframeFactory factory;
        public List<Keyframe> keyframes = new ArrayList<>();

        public PastedKeyframes(IKeyframeFactory factory)
        {
            this.factory = factory;
        }
    }
}