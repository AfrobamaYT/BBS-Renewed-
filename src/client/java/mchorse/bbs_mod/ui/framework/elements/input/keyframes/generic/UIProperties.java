package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.camera.utils.TimeUtils;
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
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.generic.factories.IGenericKeyframeFactory;
import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;
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

public class UIProperties extends UIBaseKeyframes<GenericKeyframe>
{
    public boolean selected;
    public List<UIProperty> properties = new ArrayList<>();

    private IUIClipsDelegate delegate;

    public UIProperties(IUIClipsDelegate delegate, Consumer<GenericKeyframe> callback)
    {
        super(callback);

        this.delegate = delegate;
    }

    public IUIClipsDelegate getDelegate()
    {
        return this.delegate;
    }

    /* Implementation of setters */

    public void setInstant(boolean instant)
    {
        for (UIProperty property : this.properties)
        {
            property.setInstant(instant);
        }
    }

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
        GenericKeyframe current = this.getCurrent();

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

    public void setInterpolation(IInterpolation interp)
    {
        for (UIProperty property : this.properties)
        {
            property.setInterpolation(interp);
        }
    }

    /* Graphing code */

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
                GenericKeyframe frame = (GenericKeyframe) object;

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

    public GenericKeyframe getCurrent()
    {
        UIProperty current = this.getCurrentSheet();

        return current == null ? null : current.getKeyframe();
    }

    public List<UIProperty> getProperties()
    {
        return this.properties;
    }

    public UIProperty getProperty(GenericKeyframe keyframe)
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

    public UIProperty getCurrentSheet()
    {
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
        IInterpolation interp = Interpolation.LINEAR;
        GenericKeyframe frame = this.getCurrent();
        IGenericKeyframeFactory factory = property.channel.getFactory();
        long oldTick = tick;

        if (frame != null)
        {
            interp = frame.getInterpolation();
            oldTick = frame.getTick();
        }

        Object value;
        GenericKeyframeSegment segment = property.channel.find(tick);

        if (segment == null)
        {
            value = factory.copy(property.property.get());
        }
        else
        {
            if (segment.isSame())
            {
                value = factory.copy(segment.a.getValue());
            }
            else
            {
                value = segment.createInterpolated();
            }

            if (segment.a != null)
            {
                interp = segment.a.getInterpolation();
            }
        }

        property.clearSelection();
        property.addToSelection(property.channel.insert(tick, value));

        frame = this.getCurrent();

        if (oldTick != tick)
        {
            frame.setInterpolation(interp);
        }
    }

    @Override
    public void removeCurrent()
    {
        GenericKeyframe frame = this.getCurrent();

        if (frame == null)
        {
            return;
        }

        UIProperty current = this.getCurrentSheet();

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
                GenericKeyframe frame = (GenericKeyframe) object;
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
                    GenericKeyframe keyframe = (GenericKeyframe) object;

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

        super.resetMouseReleased(context);
    }

    /* Rendering */

    @Override
    protected void renderGraph(UIContext context)
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
            COLOR.set(property.color, false);

            /* Render same values */
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            Object previous = null;

            for (Object object : property.channel.getKeyframes())
            {
                GenericKeyframe frame = (GenericKeyframe) object;

                if (previous != null)
                {
                    GenericKeyframe previousFrame = (GenericKeyframe) previous;

                    if (Objects.equals(previousFrame.getValue(), frame.getValue()))
                    {
                        int c = 0xffff00 | Colors.A50;
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
                GenericKeyframe frame = (GenericKeyframe) object;
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
                GenericKeyframe frame = (GenericKeyframe) object;

                this.renderRect(context, builder, matrix4f, this.toGraphX(frame.getTick()), y + h / 2, 2, property.hasSelected(index) ? Colors.ACTIVE : 0);

                index++;
            }

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            FontRenderer font = context.batcher.getFont();
            int lw = font.getWidth(property.title.get()) + 10;

            context.batcher.gradientHBox(this.area.ex() - lw - 10, y, this.area.ex(), y + h, property.color, Colors.A75 | property.color);
            context.batcher.textShadow(property.title.get(), this.area.ex() - lw + 5, y + (h - font.getHeight()) / 2);

            y += h;
        }
    }

    /* Handling dragging */

    @Override
    protected GenericKeyframe moving(UIContext context, int mouseX, int mouseY)
    {
        GenericKeyframe frame = this.getCurrent();
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
        GenericKeyframe keyframe = this.getCurrent();
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
            GenericKeyframe keyframe = (GenericKeyframe) properties.get(selection.current.x).channel.getKeyframes().get(selection.current.y);

            this.selected = true;
            this.setKeyframe(keyframe);
        }
    }
}