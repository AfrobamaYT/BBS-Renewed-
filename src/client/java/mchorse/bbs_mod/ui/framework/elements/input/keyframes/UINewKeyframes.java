package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollArea;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
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
import java.util.function.Supplier;

public class UINewKeyframes extends UIElement
{
    /* Constants */

    public static final int TOP_MARGIN = 25;
    public static final int TRACK_HEIGHT = 16;

    public static final Color COLOR = new Color();
    public static final double MIN_ZOOM = 0.01D;
    public static final double MAX_ZOOM = 1000D;

    /* Keyframes */

    private List<UIKeyframeSheet> sheets = new ArrayList<>();

    /* Editing states */

    private boolean selecting;
    private boolean navigating;

    private int lastX;
    private int lastY;
    private int originalX;
    private int originalY;

    /* Fields */

    private Scale xAxis = new Scale(this.area, ScrollDirection.HORIZONTAL);
    private Scale yAxis = new Scale(this.area, ScrollDirection.VERTICAL);
    private ScrollArea dopeSheet = new ScrollArea(this.area);

    private Consumer<Keyframe> callback;
    private Consumer<UIContext> backgroundRender;
    private Supplier<Integer> duration;

    private IAxisConverter converter;

    public UINewKeyframes(Consumer<Keyframe> callback)
    {
        this.callback = callback;
    }

    /* Setters and getters */

    public UINewKeyframes backgroundRenderer(Consumer<UIContext> backgroundRender)
    {
        this.backgroundRender = backgroundRender;

        return this;
    }

    public UINewKeyframes duration(Supplier<Integer> duration)
    {
        this.duration = duration;

        return this;
    }

    public UINewKeyframes axisConverter(IAxisConverter converter)
    {
        this.converter = converter;

        return this;
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

        this.pickKeyframe(null);
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

    public int getDopeSheetY()
    {
        return this.area.y + TOP_MARGIN - (int) this.dopeSheet.scroll;
    }

    public int getDopeSheetY(int sheet)
    {
        return this.getDopeSheetY() + sheet * TRACK_HEIGHT;
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
            this.xAxis.viewOffset(min, max, this.area.w, 20);
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
            if (context.mouseButton == 0)
            {
                boolean shift = Window.isShiftPressed();

                if (!shift)
                {
                    this.clearSelection();
                }

                Keyframe found = this.findKeyframe(context);

                if (shift && found == null)
                {
                    this.selecting = true;
                }

                if (found != null)
                {
                    this.pickKeyframe(found);

                    return true;
                }
            }
            else if (context.mouseButton == 2)
            {
                this.navigating = true;
            }

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
            this.originalX = context.mouseX;
            this.originalY = context.mouseY;
        }

        return super.subMouseClicked(context);
    }

    private Keyframe findKeyframe(UIContext context)
    {
        List<UIKeyframeSheet> sheets = this.getSheets();
        Keyframe found = null;
        boolean selectInARow = Window.isAltPressed();

        for (int i = 0; i < sheets.size(); i++)
        {
            UIKeyframeSheet sheet = sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(j);
                int x = this.toGraphX(keyframe.getTick());
                int y = this.getDopeSheetY(i) + TRACK_HEIGHT / 2;

                if (!this.isNear(x, y, context.mouseX, context.mouseY, selectInARow))
                {
                    continue;
                }

                sheet.selection.add(j);

                if (!selectInARow)
                {
                    return keyframe;
                }

                found = keyframe;
            }
        }

        return found;
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
                    int y = this.getDopeSheetY(i) + TRACK_HEIGHT / 2;

                    if (area.isInside(x, y))
                    {
                        sheet.selection.add(j);
                    }
                }
            }
        }

        this.navigating = false;
        this.selecting = false;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.navigating)
        {
            this.xAxis.zoomAnchor(Scale.getAnchorX(context, this.area), Math.copySign(this.xAxis.getZoomFactor(), context.mouseWheel), MIN_ZOOM, MAX_ZOOM);
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

        context.batcher.unclip(context);

        this.dopeSheet.renderScrollbar(context.batcher);
    }

    /**
     * Handle any related mouse logic during rendering
     */
    private void handleMouse(UIContext context)
    {
        this.dopeSheet.drag(context);

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.navigating)
        {
            this.xAxis.setShift(this.xAxis.getShift() - (mouseX - this.lastX) / this.xAxis.getZoom());

            this.dopeSheet.scrollBy(-(mouseY - this.lastY));
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    /**
     * Render background, specifically backdrop and borders if the duration is present
     */
    private void renderBackground(UIContext context)
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

    /**
     * Render grid that allows easier to see where are specific ticks
     */
    private void renderGrid(UIContext context)
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
    private void renderGraph(UIContext context)
    {
        if (this.sheets.isEmpty())
        {
            return;
        }

        this.dopeSheet.scrollSize = TRACK_HEIGHT * this.sheets.size() + TOP_MARGIN;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        for (int i = 0; i < this.sheets.size(); i++)
        {
            int y = this.getDopeSheetY(i);

            if (y + TRACK_HEIGHT < this.area.y || y > this.area.ey())
            {
                continue;
            }

            UIKeyframeSheet sheet = this.sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();
            LineBuilder<Void> line = new LineBuilder<>(0.75F);

            boolean hover = this.area.isInside(context) && context.mouseY >= y && context.mouseY < y + TRACK_HEIGHT;
            int my = y + TRACK_HEIGHT / 2;

            COLOR.set(sheet.color, false);
            COLOR.a = hover ? 1F : 0.45F;

            /* Render track bars (horizontal lines) */
            line.add(this.area.x, my);
            line.add(this.area.ex(), my);
            line.render(context.batcher, SolidColorLineRenderer.get(COLOR));

            /* Render bars indicating same values */
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (int j = 1; j < keyframes.size(); j++)
            {
                Keyframe previous = (Keyframe) keyframes.get(j - 1);
                Keyframe frame = (Keyframe) keyframes.get(j);

                if (Objects.equals(previous.getValue(), frame.getValue()))
                {
                    int c = 0xffff00 | Colors.A25;
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

                if (this.selecting)
                {
                    isPointHover = isPointHover || this.getGrabbingArea(context).isInside(x1, my);
                }

                this.renderSquare(context, builder, matrix, x1, my, 3, sheet.selection.has(j) || isPointHover ? Colors.WHITE : sheet.color);
            }

            /* Render keyframe handles (inner) */
            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe frame = (Keyframe) keyframes.get(j);

                this.renderSquare(context, builder, matrix, this.toGraphX(frame.getTick()), my, 2, sheet.selection.has(j) ? Colors.ACTIVE : 0);
            }

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            FontRenderer font = context.batcher.getFont();
            int lw = font.getWidth(sheet.title.get()) + 10;

            if (hover)
            {
                context.batcher.gradientHBox(this.area.x, y, this.area.x + lw + 10, y + TRACK_HEIGHT, Colors.A75 | sheet.color, sheet.color);
                context.batcher.textShadow(sheet.title.get(), this.area.x + 5, my - font.getHeight() / 2);
            }
        }
    }

    protected void renderSquare(UIContext context, BufferBuilder builder, Matrix4f matrix, int x, int y, int offset, int c)
    {
        c = Colors.A100 | c;

        context.batcher.fillRect(builder, matrix, x - offset, y - offset, offset * 2, offset * 2, c, c, c, c);
    }
}