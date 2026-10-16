package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.IPlaceableClip;
import mchorse.bbs_mod.camera.clips.misc.OverlayBox;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The screen manipulator of overlay clips (subtitle, image, video): while such
 * a clip is selected and under the timeline's cursor, its on-screen rectangle
 * gets a draggable frame in the preview - the body moves the placement's
 * offset, the corner handles scale around the anchor.
 *
 * Works in the frame units the overlay renderers draw in. The box AND the unit
 * frame width come from what was actually rendered last frame ({@link OverlayBox}) -
 * never recomputed here, so the frame cannot drift away from the drawn overlay.
 *
 * From BBS FS 894caa1eb (Wemppy4/bbs-fs, MIT); an element of its own over the preview here.
 */
public class UIPlacementGizmo extends mchorse.bbs_mod.ui.framework.elements.UIElement
{

    static final int MODE_NONE = 0;
    static final int MODE_MOVE = 1;
    static final int MODE_SCALE = 2;
    static final int MODE_SCALE_X = 3;
    static final int MODE_SCALE_Y = 4;

    private final UIFilmPanel panel;

    private int mode = MODE_NONE;
    private float startUnitX;
    private float startUnitY;
    private Placement startPlacement;
    private ValuePlacement editedValue;

    /** Some gizmo is mid-drag: the film's undo keeps the gesture as one entry until release */
    public static boolean ziehtGerade;

    public UIPlacementGizmo(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    public boolean isDragging()
    {
        return this.mode != MODE_NONE;
    }

    /**
     * The selected clip's placement value, but only when the gizmo can edit it:
     * an overlay clip that is enabled, covers the timeline's cursor, and is not
     * stretched over the whole screen.
     */
    private ValuePlacement getPlacement()
    {
        if (!this.panel.cameraEditor.isVisible())
        {
            return null;
        }

        Clip clip = this.panel.cameraEditor.getClip();

        if (clip == null || !clip.enabled.get())
        {
            return null;
        }

        int cursor = this.panel.getCursor();

        if (cursor < clip.tick.get() || cursor >= clip.tick.get() + clip.duration.get())
        {
            return null;
        }

        return clip instanceof IPlaceableClip placeable ? placeable.getPlacement() : null;
    }

    private OverlayBox getBox()
    {
        Clip clip = this.panel.cameraEditor.getClip();
        OverlayBox box = clip instanceof IPlaceableClip placeable ? placeable.getOverlayBox() : null;

        return box == null || box.unitWidth <= 0F ? null : box;
    }

    /* Unit space <-> screen space (the unit frame is stretched over the viewport) */

    private float toUnitX(UIContext context, Area viewport, float unitWidth)
    {
        return (context.mouseX - viewport.x) / (float) viewport.w * unitWidth;
    }

    private float toUnitY(UIContext context, Area viewport)
    {
        return (context.mouseY - viewport.y) / (float) viewport.h * Placement.HEIGHT;
    }

    static float toScreenX(float unitX, Area viewport, float unitWidth)
    {
        return viewport.x + unitX / unitWidth * viewport.w;
    }

    static float toScreenY(float unitY, Area viewport)
    {
        return viewport.y + unitY / Placement.HEIGHT * viewport.h;
    }

    private boolean klicken(UIContext context, Area viewport)
    {
        if (context.mouseButton != 0)
        {
            return false;
        }

        ValuePlacement value = this.getPlacement();
        OverlayBox box = value == null ? null : this.getBox();

        if (box == null)
        {
            return false;
        }

        int x1 = (int) this.toScreenX(box.x, viewport, box.unitWidth);
        int y1 = (int) this.toScreenY(box.y, viewport);
        int x2 = (int) this.toScreenX(box.x + box.w, viewport, box.unitWidth);
        int y2 = (int) this.toScreenY(box.y + box.h, viewport);
        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;
        int handle = PlacementRahmen.HANDLE_SIZE + 2;

        boolean corner = this.isNear(context, x1, y1, handle) || this.isNear(context, x2, y1, handle)
            || this.isNear(context, x1, y2, handle) || this.isNear(context, x2, y2, handle);
        boolean sideX = this.isNear(context, x1, my, handle) || this.isNear(context, x2, my, handle);
        boolean sideY = this.isNear(context, mx, y1, handle) || this.isNear(context, mx, y2, handle);

        if (!corner && !sideX && !sideY && !(context.mouseX >= x1 && context.mouseX < x2 && context.mouseY >= y1 && context.mouseY < y2))
        {
            return false;
        }

        this.mode = corner ? MODE_SCALE : (sideX ? MODE_SCALE_X : (sideY ? MODE_SCALE_Y : MODE_MOVE));
        this.startUnitX = this.toUnitX(context, viewport, box.unitWidth);
        this.startUnitY = this.toUnitY(context, viewport);
        this.startPlacement = value.get().copy();
        this.editedValue = value;

        return true;
    }

    private boolean isNear(UIContext context, int x, int y, int distance)
    {
        return Math.abs(context.mouseX - x) <= distance && Math.abs(context.mouseY - y) <= distance;
    }

    private boolean loslassen(UIContext context)
    {
        if (this.mode == MODE_NONE)
        {
            return false;
        }

        this.mode = MODE_NONE;
        this.startPlacement = null;
        this.editedValue = null;
        this.panel.cameraEditor.markLastUndoNoMerging();

        return true;
    }

    private void updateDrag(UIContext context, Area viewport, OverlayBox box)
    {
        ValuePlacement value = this.getPlacement();

        if (value != this.editedValue || value == null)
        {
            /* The clip got deselected or edited away mid-drag */
            this.mode = MODE_NONE;

            return;
        }

        float unitX = this.toUnitX(context, viewport, box.unitWidth);
        float unitY = this.toUnitY(context, viewport);
        Placement edited = this.startPlacement.copy();

        if (this.mode == MODE_MOVE)
        {
            edited.offsetX = this.startPlacement.offsetX + (unitX - this.startUnitX);
            edited.offsetY = this.startPlacement.offsetY + (unitY - this.startUnitY);
        }
        else if (!PlacementSkalieren.skalieren(this.mode, this.startPlacement, edited, this.startUnitX, this.startUnitY, unitX, unitY, box.unitWidth))
        {
            return;
        }

        if (!value.get().equals(edited))
        {
            this.panel.cameraEditor.editMultiple(value, (v) -> v.set(edited.copy()));
            this.panel.cameraEditor.fillData();
        }
    }

    public void render(UIContext context, Area viewport)
    {
        ValuePlacement value = this.getPlacement();
        OverlayBox box = value == null ? null : this.getBox();

        if (box == null)
        {
            return;
        }

        if (this.mode != MODE_NONE)
        {
            this.updateDrag(context, viewport, box);
        }

        PlacementRahmen.zeichnen(context, viewport, box, value.get(), this.mode == MODE_NONE);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        boolean genommen = this.klicken(context, this.panel.preview.getViewport());

        ziehtGerade = this.isDragging();

        return genommen || super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        ziehtGerade = false;

        return this.loslassen(context) || super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);
        this.render(context, this.panel.preview.getViewport());
    }
}
