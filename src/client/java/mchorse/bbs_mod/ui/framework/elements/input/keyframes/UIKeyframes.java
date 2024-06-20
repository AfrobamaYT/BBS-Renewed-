package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * - Editing inside
 */
public class UIKeyframes extends UIElement
{
    /* Constants */

    public static final int TOP_MARGIN = 25;

    public static final Color COLOR = new Color();
    public static final double MIN_ZOOM = 0.01D;
    public static final double MAX_ZOOM = 1000D;

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

    private Scale xAxis = new Scale(this.area, ScrollDirection.HORIZONTAL);
    private Scale yAxis = new Scale(this.area, ScrollDirection.VERTICAL);
    private Scroll dopeSheet = new Scroll(this.area);

    private Consumer<Keyframe> callback;
    private Consumer<UIContext> backgroundRender;
    private Supplier<Integer> duration;

    private IAxisConverter converter;

    private double trackHeight;

    public UIKeyframes(Consumer<Keyframe> callback)
    {
        this.callback = callback;

        this.setTrackHeight(16);

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
            menu.action(Icons.FULLSCREEN, UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, this::selectAll);

            if (this.getSelected() != null)
            {
                menu.action(Icons.REMOVE, UIKeys.KEYFRAMES_CONTEXT_REMOVE, this::removeSelected);
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
        this.keys().register(Keys.KEYFRAMES_SELECT_ALL, this::selectAll).inside().category(category);
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
        this.keys().register(Keys.DELETE, this::removeSelected).inside().category(category);
    }

    /* Setters and getters */

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

    private void setTrackHeight(double height)
    {
        this.trackHeight = MathUtils.clamp(height, 8D, 100D);
        this.dopeSheet.scrollSpeed = (int) this.trackHeight * 2;
        this.dopeSheet.scrollSize = (int) this.trackHeight * this.sheets.size() + TOP_MARGIN;

        this.dopeSheet.clamp();
    }

    /* Sheet management */

    public List<UIKeyframeSheet> getSheets()
    {
        return this.sheets;
    }

    public void addSheet(UIKeyframeSheet sheet)
    {
        this.sheets.add(sheet);
    }

    /* Selection */

    public void clearSelection()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.clear();
        }
    }

    public void selectAll()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.all();
        }

        this.pickSelected();
    }

    /* Keyframes */

    public Keyframe getSelected()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            Keyframe first = sheet.selection.getFirst();

            if (first != null)
            {
                return first;
            }
        }

        return null;
    }

    public UIKeyframeSheet getSheet(Keyframe keyframe)
    {
        KeyframeChannel channel = (KeyframeChannel) keyframe.getParent();

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (sheet.channel == channel)
            {
                return sheet;
            }
        }

        return null;
    }

    public UIKeyframeSheet getSheet(int mouseY)
    {
        int dopeSheetY = this.getDopeSheetY();
        int index = (mouseY - dopeSheetY) / (int) this.trackHeight;

        if (CollectionUtils.inRange(this.sheets, index))
        {
            return this.sheets.get(index);
        }

        return null;
    }

    public boolean addKeyframe(int mouseX, int mouseY)
    {
        long tick = Math.round(this.fromGraphX(mouseX));
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (sheet != null)
        {
            this.addKeyframe(sheet, tick);
        }

        return sheet != null;
    }

    public void addKeyframe(UIKeyframeSheet sheet, long tick)
    {
        KeyframeSegment segment = sheet.channel.find(tick);
        Interpolation interpolation = null;
        IFormProperty property = sheet.property;
        Object value;

        if (segment != null)
        {
            value = segment.createInterpolated();
            interpolation = segment.a.getInterpolation();
        }
        else if (property != null)
        {
            value = sheet.channel.getFactory().copy(property.get());
        }
        else
        {
            value = sheet.channel.getFactory().createEmpty();
        }

        int index = sheet.channel.insert(tick, value);
        Keyframe keyframe = sheet.channel.get(index);

        if (interpolation != null)
        {
            keyframe.getInterpolation().copy(interpolation);
        }

        this.clearSelection();
        this.pickKeyframe(keyframe);
        sheet.selection.add(index);
    }

    public void removeKeyframe(Keyframe keyframe)
    {
        UIKeyframeSheet sheet = this.getSheet(keyframe);

        sheet.remove(keyframe);
        this.clearSelection();
        this.pickKeyframe(null);
    }

    public void removeSelected()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.removeSelected();
        }

        this.pickKeyframe(null);
    }

    public Keyframe findKeyframe(int mouseX, int mouseY)
    {
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (sheet == null)
        {
            return null;
        }

        List keyframes = sheet.channel.getKeyframes();
        int i = this.sheets.indexOf(sheet);

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(j);
            int x = this.toGraphX(keyframe.getTick());
            int y = this.getDopeSheetY(i) + (int) this.trackHeight / 2;

            if (this.isNear(x, y, mouseX, mouseY, false))
            {
                return keyframe;
            }
        }

        return null;
    }

    public void pickSelected()
    {
        this.pickKeyframe(this.getSelected());
    }

    public void pickKeyframe(Keyframe keyframe)
    {
        if (this.callback != null)
        {
            this.callback.accept(keyframe);
        }
    }

    public void selectKeyframe(Keyframe keyframe)
    {
        this.clearSelection();

        UIKeyframeSheet sheet = this.getSheet(keyframe);

        if (sheet != null)
        {
            sheet.selection.add(keyframe);
            this.pickKeyframe(keyframe);

            double x = keyframe.getTick();
            int y = (int) (this.sheets.indexOf(sheet) * this.trackHeight) + TOP_MARGIN;

            this.getXAxis().shiftIntoMiddle(x);

            if (y < this.area.y || y > this.area.y)
            {
                this.dopeSheet.scrollIntoView(y, (int) (this.trackHeight * 2), (int) (this.trackHeight * 2));
            }
        }
    }

    public void setTick(long tick)
    {
        Keyframe selected = this.getSelected();
        long diff = tick - selected.getTick();

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setTickBy(diff);
        }
    }

    public void setInterpolation(Interpolation interpolation)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setInterpolation(interpolation);
        }
    }

    /**
     * Set value for all selected keyframes
     */
    public void setValue(Object value)
    {
        IKeyframeFactory factory = this.getSelected().getFactory();

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.channel.getFactory() == factory)
            {
                sheet.setValue(value);
            }
        }
    }

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

        for (UIKeyframeSheet property : this.getSheets())
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
        List<UIKeyframeSheet> sheets = this.getSheets();

        this.clearSelection();

        if (keyframes.size() == 1)
        {
            UIKeyframeSheet current = this.getSheet(mouseY);

            if (current == null)
            {
                current =  sheets.get(0);
            }

            this.pasteKeyframesTo(current, keyframes.get(keyframes.keySet().iterator().next()), offset);

            return;
        }

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

    public int getDopeSheetY()
    {
        return this.area.y + TOP_MARGIN - (int) this.dopeSheet.scroll;
    }

    public int getDopeSheetY(int sheet)
    {
        return this.getDopeSheetY() + sheet * (int) this.trackHeight;
    }

    public int getDopeSheetY(UIKeyframeSheet sheet)
    {
        return this.getDopeSheetY(this.sheets.indexOf(sheet));
    }

    /**
     * Whether given mouse coordinates are near the given point?
     */
    private boolean isNear(double x, double y, int mouseX, int mouseY, boolean checkOnlyX)
    {
        if (checkOnlyX)
        {
            return Math.pow(mouseX - x, 2) < 25D;
        }

        return Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2) < 25D;
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

        this.dopeSheet.clamp();
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.dopeSheet.mouseClicked(context))
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
        Keyframe keyframe = this.findKeyframe(context.mouseX, context.mouseY);

        if (keyframe != null)
        {
            this.removeKeyframe(keyframe);
        }
        else
        {
            this.addKeyframe(context.mouseX, context.mouseY);
        }
    }

    private void duplicateOrSelectColumn(UIContext context)
    {
        if (this.getSelected() != null)
        {
            /* Duplicate */
            int tick = (int) Math.round(this.fromGraphX(context.mouseX));

            this.pasteKeyframes(this.parseKeyframes(this.serializeKeyframes()), tick, context.mouseY);

            return;
        }

        /* Select a column */
        for (int i = 0; i < sheets.size(); i++)
        {
            UIKeyframeSheet sheet = sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(j);
                int x = this.toGraphX(keyframe.getTick());
                int y = this.getDopeSheetY(i) + (int) this.trackHeight / 2;

                if (this.isNear(x, y, context.mouseX, context.mouseY, true))
                {
                    sheet.selection.add(j);
                }
            }
        }
    }

    private void pickOrStartSelectingKeyframes(UIContext context)
    {
        /* Picking keyframe or initiating selection */
        Keyframe found = this.findKeyframe(context.mouseX, context.mouseY);
        boolean shift = Window.isShiftPressed();

        if (shift && found == null)
        {
            this.selecting = true;
        }

        if (found != null)
        {
            UIKeyframeSheet sheet = this.getSheet(found);

            if (!shift && !sheet.selection.has(found))
            {
                this.clearSelection();
            }

            sheet.selection.add(found);

            found = this.getSelected();

            this.pickKeyframe(found);
        }
        else if (!this.selecting)
        {
            this.clearSelection();
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
        this.dopeSheet.mouseReleased(context);

        if (this.selecting)
        {
            Area area = this.getGrabbingArea(context);
            List<UIKeyframeSheet> sheets = this.getSheets();

            for (int i = 0; i < sheets.size(); i++)
            {
                UIKeyframeSheet sheet = sheets.get(i);
                List keyframes = sheet.channel.getKeyframes();

                for (int j = 0; j < keyframes.size(); j++)
                {
                    Keyframe keyframe = (Keyframe) keyframes.get(j);
                    int x = this.toGraphX(keyframe.getTick());
                    int y = this.getDopeSheetY(i) + (int) this.trackHeight / 2;

                    if (area.isInside(x, y))
                    {
                        sheet.selection.add(j);
                    }
                }
            }

            this.pickSelected();
        }

        if (this.dragging > 0)
        {
            for (UIKeyframeSheet sheet : this.getSheets())
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
            if (Window.isShiftPressed())
            {
                this.dopeSheet.mouseScroll(context);
            }
            else if (Window.isAltPressed())
            {
                this.setTrackHeight(this.trackHeight - context.mouseWheel);
            }
            else
            {
                this.xAxis.zoomAnchor(Scale.getAnchorX(context, this.area), Math.copySign(this.xAxis.getZoomFactor(), context.mouseWheel), MIN_ZOOM, MAX_ZOOM);
            }

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
        this.renderGrid(context);
        this.renderGraph(context);

        if (this.selecting)
        {
            context.batcher.normalizedBox(this.originalX, this.originalY, context.mouseX, context.mouseY, Colors.setA(Colors.ACTIVE, 0.25F));
        }

        this.dopeSheet.renderScrollbar(context.batcher);

        context.batcher.unclip(context);
    }

    /**
     * Handle any related mouse logic during rendering
     */
    protected void handleMouse(UIContext context)
    {
        this.dopeSheet.drag(context);

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        boolean mouseHasMoved = mouseX != this.lastX || mouseY != this.lastY;

        if (this.navigating)
        {
            double offset = (mouseX - this.lastX) / this.xAxis.getZoom();

            this.xAxis.setShift(this.xAxis.getShift() - offset);

            this.dopeSheet.scrollBy(-(mouseY - this.lastY));
        }

        if (this.dragging == 0 && mouseHasMoved)
        {
            this.dragging = 1;
        }
        else if (this.dragging == 1)
        {
            if (this.getSelected() != null)
            {
                int offset = (int) (Math.round(this.fromGraphX(this.originalX)) - this.originalT);

                this.setTick(Math.round(this.fromGraphX(mouseX)) - offset);
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

        boolean alt = Window.isAltPressed();

        if (this.area.isInside(context) && (Window.isCtrlPressed() || alt))
        {
            UIKeyframeSheet sheet = this.getSheet(context.mouseY);

            if (sheet != null)
            {
                int x = this.toGraphX(Math.round(this.fromGraphX(context.mouseX)));
                int y = this.getDopeSheetY(sheet) + (int) this.trackHeight / 2;
                float a = (float) Math.sin(context.getTickTransition() / 2D) * 0.1F + 0.5F;

                context.batcher.box(x - 3, y - 3, x + 3, y + 3, Colors.setA(alt ? Colors.YELLOW : Colors.WHITE, a));
            }
        }

        if (this.backgroundRender != null)
        {
            this.backgroundRender.accept(context);
        }
    }

    /**
     * Render grid that allows easier to see where are specific ticks
     */
    protected void renderGrid(UIContext context)
    {
        /* Draw horizontal grid */
        int mult = this.xAxis.getMult();
        int hx = this.getDuration() / mult;
        int ht = (int) this.fromGraphX(this.area.x);

        for (int j = Math.max(ht / mult, 0); j <= hx; j++)
        {
            int x = this.toGraphX(j * mult);

            if (x >= this.area.ex())
            {
                break;
            }

            String label = this.converter == null ? String.valueOf(j * mult) : this.converter.format(j * mult);

            context.batcher.box(x, this.area.y, x + 1, this.area.ey(), Colors.setA(Colors.WHITE, 0.25F));
            context.batcher.text(label, x + 4, this.area.y + 4);
        }
    }

    /**
     * Render the graph
     */
    @SuppressWarnings({"rawtypes", "IntegerDivisionInFloatingPointContext"})
    protected void renderGraph(UIContext context)
    {
        if (this.sheets.isEmpty())
        {
            return;
        }

        this.dopeSheet.scrollSize = (int) this.trackHeight * this.sheets.size() + TOP_MARGIN;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        for (int i = 0; i < this.sheets.size(); i++)
        {
            int y = this.getDopeSheetY(i);

            if (y + this.trackHeight < this.area.y || y > this.area.ey())
            {
                continue;
            }

            UIKeyframeSheet sheet = this.sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();
            LineBuilder<Void> line = new LineBuilder<>(0.75F);

            boolean hover = this.area.isInside(context) && context.mouseY >= y && context.mouseY < y + this.trackHeight;
            int my = y + (int) this.trackHeight / 2;

            COLOR.set(sheet.color, false);
            COLOR.a = hover ? 1F : 0.45F;

            /* Render track bars (horizontal lines) */
            line.add(this.area.x, my);
            line.add(this.area.ex(), my);
            line.render(context.batcher, SolidColorLineRenderer.get(COLOR));

            /* Render bars indicating same values */
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            if (sheet.separator)
            {
                int c = COLOR.getRGBColor();

                context.batcher.fillRect(builder, matrix, this.area.x, y, this.area.w, (int) this.trackHeight, c | Colors.A25, c | Colors.A25, c, c);
            }

            for (int j = 1; j < keyframes.size(); j++)
            {
                Keyframe previous = (Keyframe) keyframes.get(j - 1);
                Keyframe frame = (Keyframe) keyframes.get(j);

                if (Objects.equals(previous.getValue(), frame.getValue()))
                {
                    int c = Colors.YELLOW | Colors.A25;
                    int xx = this.toGraphX(previous.getTick());

                    context.batcher.fillRect(builder, matrix, xx, my - 2, this.toGraphX(frame.getTick()) - xx, 4, c, c, c, c);
                }
            }

            /* Draw keyframe handles (outer) */
            int forcedIndex = 0;

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe frame = (Keyframe) keyframes.get(j);
                long tick = frame.getTick();
                int x1 = this.toGraphX(tick);
                int x2 = this.toGraphX(tick + frame.getDuration());

                /* Render custom duration markers */
                if (x1 != x2)
                {
                    int y1 = my - 8 + (forcedIndex % 2 == 1 ? -4 : 0);
                    int color = sheet.selection.has(j) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(sheet.color, 0.9F), 0.75F);

                    context.batcher.fillRect(builder, matrix, x1, y1 - 2, 1, 5, color, color, color, color);
                    context.batcher.fillRect(builder, matrix, x2, y1 - 2, 1, 5, color, color, color, color);
                    context.batcher.fillRect(builder, matrix, x1 + 1, y1, x2 - x1, 1, color, color, color, color);

                    forcedIndex += 1;
                }

                boolean isPointHover = this.isNear(this.toGraphX(frame.getTick()), my, context.mouseX, context.mouseY, false);
                boolean toRemove = Window.isCtrlPressed() && isPointHover;

                if (this.selecting)
                {
                    isPointHover = isPointHover || this.getGrabbingArea(context).isInside(x1, my);
                }

                int c = (sheet.selection.has(j) || isPointHover ? Colors.WHITE : sheet.color) | Colors.A100;

                if (toRemove)
                {
                    c = Colors.RED | Colors.A100;
                }

                this.renderSquare(context, builder, matrix, x1, my, toRemove ? 4 : 3, c);
            }

            /* Render keyframe handles (inner) */
            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe frame = (Keyframe) keyframes.get(j);
                int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;

                this.renderSquare(context, builder, matrix, this.toGraphX(frame.getTick()), my, 2, c | Colors.A100);
            }

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            FontRenderer font = context.batcher.getFont();
            int lw = font.getWidth(sheet.title.get());

            context.batcher.gradientHBox(this.area.ex() - lw - 10, y, this.area.ex(), y + (int) this.trackHeight, sheet.color, sheet.color | (hover ? Colors.A75 : Colors.A25));

            if (hover)
            {
                context.batcher.textShadow(sheet.title.get(), this.area.ex() - lw - 5, my - font.getHeight() / 2);
            }
            else
            {
                context.batcher.text(sheet.title.get(), this.area.ex() - lw - 5, my - font.getHeight() / 2, Colors.WHITE & 0x88ffffff);
            }

            Icon icon = sheet.getIcon();

            if (icon != null && this.trackHeight >= 12D)
            {
                context.batcher.box(this.area.x, y, this.area.x + 6, y + (int) this.trackHeight, Colors.A75);
                context.batcher.gradientHBox(this.area.x + 6, y, this.area.x + 4 + icon.w, y + (int) this.trackHeight, Colors.A75, 0);
                context.batcher.icon(icon, this.area.x + 2, my - icon.h / 2);
            }
        }
    }

    protected void renderSquare(UIContext context, BufferBuilder builder, Matrix4f matrix, int x, int y, int offset, int c)
    {
        context.batcher.fillRect(builder, matrix, x - offset, y - offset, offset * 2, offset * 2, c, c, c, c);
    }

    /* Caching state */

    public KeyframeState cacheState()
    {
        KeyframeState state = new KeyframeState();

        state.min = this.xAxis.getMinValue();
        state.max = this.xAxis.getMaxValue();
        state.scroll = this.dopeSheet.scroll;

        for (UIKeyframeSheet property : this.sheets)
        {
            state.selected.add(new ArrayList<>(property.selection.getIndices()));
        }

        return state;
    }

    public void applyState(KeyframeState state)
    {
        this.xAxis.view(state.min, state.max);
        this.dopeSheet.scroll = state.scroll;

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