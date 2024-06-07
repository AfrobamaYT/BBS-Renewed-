package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.graphics.line.Line;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.utils.undo.FilmEditorUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIBaseKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class UIProperties extends UIBaseKeyframes<Keyframe>
{
    public boolean selected;
    public List<UIProperty> properties = new ArrayList<>();

    private Scale scaleY;
    private List<UIProperty> currentSheet = new ArrayList<>();
    private UIProperty current;

    private IUIClipsDelegate delegate;

    public UIProperties(IUIClipsDelegate delegate, Consumer<Keyframe> callback)
    {
        super(callback);

        this.delegate = delegate;

        this.scaleY = new Scale(this.area, ScrollDirection.VERTICAL);
        this.scaleY.inverse().anchor(0.5F);
    }

    public IUIClipsDelegate getDelegate()
    {
        return this.delegate;
    }

    @Override
    public boolean canScroll()
    {
        return this.current == null;
    }

    public Scale getScaleY()
    {
        return this.scaleY;
    }

    public boolean isEditing()
    {
        return this.current != null;
    }

    public void editSheet(UIProperty sheet)
    {
        this.clearSelection();

        this.current = sheet;

        this.currentSheet.clear();
        this.currentSheet.add(sheet);

        this.resetViewY();
    }

    public void resetViewY()
    {
        if (this.current == null)
        {
            return;
        }

        this.scaleY.set(0, 2);

        KeyframeChannel channel = this.current.channel;
        List<Keyframe> keyframes = channel.getKeyframes();
        int c = keyframes.size();

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        if (c > 1)
        {
            for (int i = 0; i < c; i++)
            {
                Keyframe frame = keyframes.get(i);

                minY = Math.min(minY, frame.getFactory().getY(frame.getValue(), i));
                maxY = Math.max(maxY, frame.getFactory().getY(frame.getValue(), i));
            }
        }
        else
        {
            minY = -10;
            maxY = 10;

            if (c == 1)
            {
                Keyframe first = channel.get(0);

                minY = maxY = first.getFactory().getY(first.getValue(), 0);
            }
        }

        if (Math.abs(maxY - minY) < 0.01F)
        {
            /* Centerize */
            this.scaleY.setShift(minY);
        }
        else
        {
            /* Spread apart vertically */
            this.scaleY.viewOffset(minY, maxY, this.area.h, 20);
        }
    }

    /* Implementation of setters */

    public void setTick(double tick)
    {
        if (this.isMultipleSelected())
        {
            tick = (long) tick;

            double dx = tick - this.getCurrent().getTick();

            for (UIProperty property : this.properties)
            {
                property.setTick(dx);
            }
        }
        else
        {
            this.getCurrent().setTick((long) tick);
        }

        this.sliding = true;
    }

    public void setValue(Object value)
    {
        Keyframe current = this.getCurrent();

        if (this.isMultipleSelected())
        {
            for (UIProperty property : this.properties)
            {
                if (current.getFactory() == property.channel.getFactory())
                {
                    property.setValue(current.getValue());
                }
            }
        }
        else
        {
            current.setValue(value);
        }
    }

    public void setInterpolation(Interpolation interp)
    {
        for (UIProperty property : this.properties)
        {
            property.setInterpolation(interp);
        }
    }

    /* Graphing code */

    public int toGraphY(double value)
    {
        return (int) this.scaleY.to(value);
    }

    public int toGraphY(IKeyframeFactory factory, Object value, int index)
    {
        return (int) this.scaleY.to(factory.getY(value, index));
    }

    public int toGraphY(Keyframe keyframe, int index)
    {
        return (int) this.scaleY.to(keyframe.getFactory().getY(keyframe.getValue(), index));
    }

    public double fromGraphY(int mouseY)
    {
        return this.scaleY.from(mouseY);
    }

    @Override
    public void resetView()
    {
        int c = 0;

        this.scaleX.set(0, 2);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        /* Find minimum and maximum */
        for (UIProperty property : this.properties)
        {
            for (Object object : property.channel.getKeyframes())
            {
                Keyframe frame = (Keyframe) object;

                min = Integer.min((int) frame.getTick(), min);
                max = Integer.max((int) frame.getTick(), max);
            }

            c = Math.max(c, property.channel.getKeyframes().size());
        }

        if (c <= 1)
        {
            if (c == 0)
            {
                min = 0;
            }

            max = this.duration;
        }

        if (Math.abs(max - min) > 0.01F)
        {
            this.scaleX.viewOffset(min, max, this.area.w, 20);
        }
    }

    public Keyframe getCurrent()
    {
        UIProperty current = this.getCurrentProperty();

        return current == null ? null : current.getKeyframe();
    }

    public List<UIProperty> getProperties()
    {
        return this.current == null ? this.properties : this.currentSheet;
    }

    public UIProperty getProperty(Keyframe keyframe)
    {
        for (UIProperty property : this.getProperties())
        {
            for (Object object : property.channel.getKeyframes())
            {
                if (object == keyframe)
                {
                    return property;
                }
            }
        }

        return null;
    }

    public UIProperty getProperty(int mouseY)
    {
        if (this.current != null)
        {
            return this.current;
        }

        List<UIProperty> properties = this.properties;
        int sheetCount = properties.size();
        int h = LANE_HEIGHT;
        int i = (mouseY - (this.area.y + TOP_MARGIN - (int) this.scroll.scroll)) / h;

        return i < 0 || i >= sheetCount ? null : properties.get(i);
    }

    @Override
    public void selectAll()
    {
        for (UIProperty property : this.properties)
        {
            property.selectAll();
        }

        this.selected = true;
        this.setKeyframe(this.getCurrent());
    }

    public UIProperty getCurrentProperty()
    {
        if (this.current != null)
        {
            return this.current;
        }

        for (UIProperty property : this.properties)
        {
            if (property.hasSelected())
            {
                return property;
            }
        }

        return null;
    }

    @Override
    public int getSelectedCount()
    {
        int i = 0;

        for (UIProperty property : this.properties)
        {
            i += property.getSelectedCount();
        }

        return i;
    }

    @Override
    public void clearSelection()
    {
        this.selected = false;

        for (UIProperty property : this.properties)
        {
            property.clearSelection();
        }
    }

    @Override
    public void addCurrent(int mouseX, int mouseY)
    {
        UIProperty property = this.getProperty(mouseY);

        if (property == null)
        {
            return;
        }

        this.addCurrent(property, Math.round(this.fromGraphX(mouseX)));
    }

    public void addCurrent(UIProperty property, long tick)
    {
        Interpolation interp = null;
        Keyframe frame = this.getCurrent();
        IKeyframeFactory factory = property.channel.getFactory();
        long oldTick = tick;

        if (frame != null)
        {
            interp = frame.getInterpolation();
            oldTick = frame.getTick();
        }

        Object value;
        KeyframeSegment segment = property.channel.find(tick);

        if (segment == null)
        {
            value = factory.copy(property.property.get());
        }
        else
        {
            value = segment.createInterpolated();

            if (segment.a != null)
            {
                interp = segment.a.getInterpolation();
            }
        }

        property.clearSelection();
        property.addToSelection(property.channel.insert(tick, value));

        frame = this.getCurrent();

        if (oldTick != tick && interp != null)
        {
            frame.getInterpolation().copy(interp);
        }
    }

    @Override
    public void removeCurrent()
    {
        Keyframe frame = this.getCurrent();

        if (frame == null)
        {
            return;
        }

        UIProperty current = this.getCurrentProperty();

        current.channel.remove(current.getSelected(0));
        current.clearSelection();

        this.selected = false;
    }

    @Override
    public void removeSelectedKeyframes()
    {
        for (UIProperty property : this.properties)
        {
            property.removeSelectedKeyframes();
        }

        this.setKeyframe(null);

        this.selected = false;
    }

    /* Mouse input handling */

    @Override
    public boolean isSelected()
    {
        return this.selected;
    }

    @Override
    public void doubleClick(int mouseX, int mouseY)
    {
        if (!this.selected)
        {
            this.addCurrent(mouseX, mouseY);
        }
        else if (!this.isMultipleSelected())
        {
            this.removeCurrent();
        }
    }

    @Override
    protected void duplicateKeyframe(UIContext context, int mouseX, int mouseY)
    {
        long offset = (long) this.fromGraphX(mouseX);

        for (UIProperty property : this.properties)
        {
            property.duplicate(offset);
        }

        this.setKeyframe(this.getCurrent());
    }

    @Override
    protected boolean pickKeyframe(UIContext context, int mouseX, int mouseY, boolean shift)
    {
        return this.current == null
            ? this.pickKeyframeDopeSheet(context, mouseX, mouseY, shift)
            : this.pickKeyframeGraph(context, mouseX, mouseY, shift);
    }

    private boolean pickKeyframeDopeSheet(UIContext context, int mouseX, int mouseY, boolean shift)
    {
        int h = LANE_HEIGHT;
        int y = this.area.y + TOP_MARGIN - (int) this.scroll.scroll;
        boolean alt = Window.isAltPressed();
        boolean finished = false;
        boolean isMultiSelect = this.isMultipleSelected();

        for (UIProperty property : this.properties)
        {
            int index = 0;

            for (Object object : property.channel.getKeyframes())
            {
                Keyframe frame = (Keyframe) object;
                boolean point = this.isInside(this.toGraphX(frame.getTick()), alt ? mouseY : y + h / 2, mouseX, mouseY);

                if (point)
                {
                    int key = property.getSelection().indexOf(index);

                    if (!shift && key == -1 && !alt)
                    {
                        this.clearSelection();
                    }

                    if (!shift)
                    {
                        this.selected = true;

                        if (key == -1)
                        {
                            property.addToSelection(index);
                            frame = isMultiSelect ? this.getCurrent() : frame;
                        }
                        else
                        {
                            frame = this.getCurrent();
                        }

                        this.setKeyframe(frame);
                    }

                    if (frame != null)
                    {
                        this.lastT = frame.getTick();
                    }

                    if (alt)
                    {
                        if (frame != null)
                        {
                            finished = true;
                        }
                    }
                    else
                    {
                        return true;
                    }
                }

                index++;
            }

            y += h;
        }

        return finished;
    }

    private boolean pickKeyframeGraph(UIContext context, int mouseX, int mouseY, boolean shift)
    {
        UIProperty property = this.current;
        List<Keyframe> keyframes = property.channel.getKeyframes();
        int index = 0;
        boolean isMultiSelect = this.isMultipleSelected();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = keyframes.get(i);
            boolean point = this.isInsideTickValue(frame.getTick(), frame, index, mouseX, mouseY);

            if (point)
            {
                int key = property.getSelection().indexOf(index);

                if (!shift && key == -1)
                {
                    this.clearSelection();
                }

                if (!shift)
                {
                    this.selected = true;

                    if (key == -1)
                    {
                        property.addToSelection(index);
                        frame = isMultiSelect ? this.getCurrent() : frame;
                    }
                    else
                    {
                        frame = this.getCurrent();
                    }

                    this.setKeyframe(frame);
                }

                if (frame != null)
                {
                    this.lastT = frame.getTick();
                }

                return true;
            }

            index++;
        }

        return false;
    }

    private boolean isInsideTickValue(double tick, Keyframe keyframe, int index, int mouseX, int mouseY)
    {
        int x = this.toGraphX(tick);
        int y = this.toGraphY(keyframe, index);
        double d = Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2);

        return d < 25;
    }

    @Override
    protected void zoom(UIContext context, int scroll)
    {
        if (this.current == null)
        {
            super.zoom(context, scroll);

            return;
        }

        boolean x = Window.isShiftPressed();
        boolean y = Window.isCtrlPressed();
        boolean none = !x && !y;

        /* Scaling X */
        if (x && !y || none)
        {
            this.scaleX.zoomAnchor(Scale.getAnchorX(context, this.area), Math.copySign(this.scaleX.getZoomFactor(), scroll), MIN_ZOOM, MAX_ZOOM);
        }

        /* Scaling Y */
        if (y && !x || none)
        {
            this.scaleY.zoomAnchor(Scale.getAnchorY(context, this.area), Math.copySign(this.scaleY.getZoomFactor(), scroll), MIN_ZOOM, MAX_ZOOM);
        }
    }

    @Override
    protected void resetMouseReleased(UIContext context)
    {
        if (this.selected)
        {
            if (this.sliding)
            {
                /* Resort after dragging the tick thing */
                for (UIProperty property : this.getProperties())
                {
                    if (property.hasSelected())
                    {
                        property.sort();
                    }
                }

                this.sliding = false;
            }
        }

        if (this.isGrabbing())
        {
            if (this.current == null)
            {
                /* Multi select */
                Area area = this.getGrabbingArea(context);
                int h = LANE_HEIGHT;
                int y = this.area.y + TOP_MARGIN - (int) this.scroll.scroll;
                int c = 0;

                for (UIProperty property : this.properties)
                {
                    int i = 0;

                    for (Object object : property.channel.getKeyframes())
                    {
                        Keyframe keyframe = (Keyframe) object;

                        if (area.isInside(this.toGraphX(keyframe.getTick()), y + h / 2) && !property.getSelection().contains(i))
                        {
                            property.getSelection().add(i);
                            c++;
                        }

                        i++;
                    }

                    y += h;
                }

                if (c > 0)
                {
                    this.selected = true;
                    this.setKeyframe(this.getCurrent());
                }
            }
            else
            {
                /* Multi select */
                UIProperty property = this.current;
                Area area = this.getGrabbingArea(context);
                KeyframeChannel channel = property.channel;

                for (int i = 0, c = channel.getKeyframes().size(); i < c; i ++)
                {
                    Keyframe keyframe = channel.get(i);

                    if (area.isInside(this.toGraphX(keyframe.getTick()), this.toGraphY(keyframe, i)) && !property.hasSelected(i))
                    {
                        property.addToSelection(i);
                    }
                }

                if (!property.hasSelected())
                {
                    this.selected = true;
                    this.setKeyframe(this.getCurrent());
                }
            }
        }

        super.resetMouseReleased(context);
    }

    /* Rendering */

    @Override
    protected void renderGrid(UIContext context)
    {
        super.renderGrid(context);

        if (this.current == null)
        {
            return;
        }

        /* Draw vertical grid */
        int ty = (int) this.fromGraphY(this.area.ey());
        int by = (int) this.fromGraphY(this.area.y - 12);

        int min = Math.min(ty, by) - 1;
        int max = Math.max(ty, by) + 1;
        int mult = this.scaleY.getMult();

        min -= min % mult + mult;
        max -= max % mult - mult;

        for (int j = 0, c = (max - min) / mult; j < c; j++)
        {
            int y = this.toGraphY(min + j * mult);

            if (y > this.area.ey())
            {
                continue;
            }

            context.batcher.box(this.area.x, y, this.area.ex(), y + 1, Colors.setA(Colors.WHITE, 0.25F));
            context.batcher.text(String.valueOf(min + j * mult), this.area.x + 4, y + 4);
        }
    }

    @Override
    protected void renderGraph(UIContext context)
    {
        if (this.current == null)
        {
            this.renderDopeSheetGraph(context);
        }
        else
        {
            this.renderGraphGraph(context, this.current);
        }
    }

    private void renderDopeSheetGraph(UIContext context)
    {
        /* Draw dope property */
        int propertyCount = this.properties.size();

        if (propertyCount == 0)
        {
            return;
        }

        this.scroll.scrollSize = LANE_HEIGHT * propertyCount + TOP_MARGIN;

        int h = LANE_HEIGHT;
        int y = this.area.y + TOP_MARGIN - (int) this.scroll.scroll;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        for (UIProperty property : this.properties)
        {
            if (y + h < this.area.y || y > this.area.ey())
            {
                y += h;

                continue;
            }

            COLOR.set(property.color, false);

            /* Render same values */
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            Object previous = null;

            for (Object object : property.channel.getKeyframes())
            {
                Keyframe frame = (Keyframe) object;

                if (previous != null)
                {
                    Keyframe previousFrame = (Keyframe) previous;

                    if (Objects.equals(previousFrame.getValue(), frame.getValue()))
                    {
                        int c = 0xffff00 | Colors.A25;
                        int xx = this.toGraphX(previousFrame.getTick());
                        int yy = y + h / 2;

                        context.batcher.fillRect(builder, matrix4f, xx, yy - 2, this.toGraphX(frame.getTick()) - xx, 4, c, c, c, c);
                    }
                }

                previous = object;
            }

            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            /* Render the line */
            LineBuilder line = new LineBuilder(0.75F);
            boolean hover = this.area.isInside(context) && context.mouseY >= y && context.mouseY < y + h;

            line.add(this.area.x, y + h / 2);
            line.add(this.area.ex(), y + h / 2);
            line.render(context.batcher, SolidColorLineRenderer.get(COLOR.r, COLOR.g, COLOR.b, hover ? 1F : 0.45F));

            /* Draw points */
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            int index = 0;
            int forcedIndex = 0;

            for (Object object : property.channel.getKeyframes())
            {
                Keyframe frame = (Keyframe) object;
                int duration = frame.getDuration();
                long tick = frame.getTick();
                int x1 = this.toGraphX(tick);

                if (duration > 0)
                {
                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                    BufferRenderer.drawWithGlobalProgram(builder.end());

                    int x2 = this.toGraphX(tick + duration);
                    int y1 = y + h / 2 - 8 + (forcedIndex % 2 == 1 ? -4 : 0);
                    int color = property.hasSelected(index) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(property.color, 0.9F), 0.75F);

                    context.batcher.box(x1, y1 - 2, x1 + 1, y1 + 3, color);
                    context.batcher.box(x2 - 1, y1 - 2, x2, y1 + 3, color);
                    context.batcher.box(x1 + 1, y1, x2 - 1, y1 + 1, color);

                    forcedIndex += 1;

                    builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                }

                boolean isPointHover = this.isInside(this.toGraphX(frame.getTick()), y + h / 2, context.mouseX, context.mouseY);

                if (this.isGrabbing())
                {
                    isPointHover = isPointHover || this.getGrabbingArea(context).isInside(this.toGraphX(frame.getTick()), y + h / 2);
                }

                this.renderRect(context, builder, matrix4f, x1, y + h / 2, 3, property.hasSelected(index) || isPointHover ? Colors.WHITE : property.color);

                index++;
            }

            index = 0;

            for (Object object : property.channel.getKeyframes())
            {
                Keyframe frame = (Keyframe) object;

                this.renderRect(context, builder, matrix4f, this.toGraphX(frame.getTick()), y + h / 2, 2, property.hasSelected(index) ? Colors.ACTIVE : 0);

                index++;
            }

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            FontRenderer font = context.batcher.getFont();
            int lw = font.getWidth(property.title.get()) + 10;

            if (hover)
            {
                context.batcher.gradientHBox(this.area.x, y, this.area.x + lw + 10, y + h, Colors.A75 | property.color, property.color);
                context.batcher.textShadow(property.title.get(), this.area.x + 5, y + (h - font.getHeight()) / 2);
            }

            y += h;
        }
    }

    private void renderGraphGraph(UIContext context, UIProperty sheet)
    {
        if (sheet == null || sheet.channel == null || sheet.channel.isEmpty())
        {
            return;
        }

        KeyframeChannel channel = sheet.channel;
        LineBuilder lines = new LineBuilder(0.75F);
        Line main = new Line();

        /* Colorize the graph for given channel */
        COLOR.set(sheet.color, false);

        /* Draw the graph */
        for (int i = 1; i < channel.getKeyframes().size(); i++)
        {
            Keyframe prev = (Keyframe) channel.getKeyframes().get(i - 1);
            Keyframe frame = (Keyframe) channel.getKeyframes().get(i);

            if (prev != null)
            {
                int px = this.toGraphX(prev.getTick());
                int fx = this.toGraphX(frame.getTick());

                /* Main line */
                IInterp interp = prev.getInterpolation().getInterp();

                if (interp == Interpolations.LINEAR)
                {
                    main.add(px, this.toGraphY(prev, i))
                        .add(fx, this.toGraphY(frame, i));
                }
                else
                {
                    float seg = 10;

                    if (interp.getKey().startsWith("bounce_") || interp.getKey().startsWith("elastic_"))
                    {
                        seg = 30;
                    }

                    for (int j = 0; j < seg; j++)
                    {
                        int a = this.toGraphY(prev.getFactory(), KeyframeSegment.interpolate(prev, frame, j / seg), i);
                        int b = this.toGraphY(prev.getFactory(), KeyframeSegment.interpolate(prev, frame, (j + 1) / seg), i);

                        main.add(px + (fx - px) * (j / seg), a)
                            .add(px + (fx - px) * ((j + 1) / seg), b);
                    }
                }
            }
            else
            {
                /* Left edge line */
                main.add(0, this.toGraphY(frame, i))
                    .add(this.toGraphX(frame.getTick()), this.toGraphY(frame, 0));
            }
        }

        /* Right edge line */
        int index = channel.getKeyframes().size() - 1;
        Keyframe prev = (Keyframe) channel.getKeyframes().get(index);

        main.add(this.toGraphX(prev.getTick()), this.toGraphY(prev, index))
            .add(this.area.ex(), this.toGraphY(prev, index));

        lines.push(main).render(context.batcher, SolidColorLineRenderer.get(COLOR.r, COLOR.g, COLOR.b, 0.65F));

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        /* Draw points */
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < channel.getKeyframes().size(); i++)
        {
            Keyframe frame = (Keyframe) channel.getKeyframes().get(i);
            boolean isPointHover = this.isInside(this.toGraphX(frame.getTick()), this.toGraphY(frame, i), context.mouseX, context.mouseY);

            if (this.isGrabbing())
            {
                isPointHover = isPointHover || this.getGrabbingArea(context).isInside(this.toGraphX(frame.getTick()), this.toGraphY(frame, i));
            }

            this.renderRect(context, builder, matrix4f, this.toGraphX(frame.getTick()), this.toGraphY(frame, i), 3, sheet.hasSelected(i) || isPointHover ? Colors.WHITE : sheet.color);
        }

        for (int i = 0; i < channel.getKeyframes().size(); i++)
        {
            Keyframe frame = (Keyframe) channel.getKeyframes().get(i);
            boolean has = sheet.getSelection().contains(i);

            this.renderRect(context, builder, matrix4f, this.toGraphX(frame.getTick()), this.toGraphY(frame, i), 2, has && this.selected ? Colors.ACTIVE : 0);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /* Handling dragging */

    @Override
    protected Keyframe moving(UIContext context, int mouseX, int mouseY)
    {
        Keyframe frame = this.getCurrent();
        double x = this.fromGraphX(mouseX);

        if (!this.selected)
        {
            this.moveNoKeyframe(context, x, 0);
        }
        else
        {
            if (this.isMultipleSelected())
            {
                int dx = mouseX - this.lastX;
                int xx = this.toGraphX(this.lastT);

                x = this.fromGraphX(xx + dx);
            }

            this.setTick(x);
        }

        return frame;
    }

    /* ... */

    @Override
    protected void moveNoKeyframe(UIContext context, double x, double y)
    {
        if (this.delegate != null)
        {
            this.delegate.setCursor((int) x);
        }
    }

    @Override
    protected void renderCursor(UIContext context)
    {
        if (this.delegate != null)
        {
            int cx = this.toGraphX(this.delegate.getCursor());
            String label = TimeUtils.formatTime(this.delegate.getCursor()) + "/" + TimeUtils.formatTime(this.duration);

            UIClips.renderCursor(context, label, this.area, cx - 1);
        }
    }

    /* Undo/redo */

    @Override
    public FilmEditorUndo.KeyframeSelection createSelection()
    {
        FilmEditorUndo.KeyframeSelection selection = super.createSelection();
        Keyframe keyframe = this.getCurrent();
        List<UIProperty> properties = this.getProperties();

        for (UIProperty property : properties)
        {
            selection.selected.add(new ArrayList<>(property.getSelection()));
        }

        if (keyframe != null)
        {
            main:
            for (int i = 0; i < properties.size(); i++)
            {
                UIProperty property = properties.get(i);

                for (int j = 0; j < property.channel.getKeyframes().size(); j++)
                {
                    if (property.channel.getKeyframes().get(j) == keyframe)
                    {
                        selection.current.set(i, j);

                        break main;
                    }
                }
            }
        }

        return selection;
    }

    @Override
    public void applySelection(FilmEditorUndo.KeyframeSelection selection)
    {
        super.applySelection(selection);

        this.clearSelection();

        List<UIProperty> properties = this.getProperties();

        for (int i = 0; i < properties.size(); i++)
        {
            if (CollectionUtils.inRange(selection.selected, i))
            {
                properties.get(i).getSelection().addAll(selection.selected.get(i));
            }
        }

        if (
            CollectionUtils.inRange(properties, selection.current.x) &&
            CollectionUtils.inRange(properties.get(selection.current.x).channel.getKeyframes(), selection.current.y)
        ) {
            Keyframe keyframe = (Keyframe) properties.get(selection.current.x).channel.getKeyframes().get(selection.current.y);

            this.selected = true;
            this.setKeyframe(keyframe);
        }
    }
}