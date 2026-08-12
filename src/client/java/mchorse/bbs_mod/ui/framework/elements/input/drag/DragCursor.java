package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.graphics.window.Window;

/**
 * The cursor a running drag reads, as opposed to the one the mouse is at.
 *
 * <p>Two things separate them. Shift slows the gesture down: the virtual cursor then advances
 * at {@link DragStrategy#FINE_DRAG_FACTOR} of the real one and the rest of the motion piles
 * into a lag offset, so releasing Shift resumes 1:1 without a jump. And a drag can be started
 * on a frame that has no cursor at all — controlling an actor pins the UI pointer to -1/-1 and
 * only releases it once an edit is running — in which case the anchor has to be planted again
 * on the first frame that carries one.
 *
 * <p>Every ray gesture reads through {@link #x}/{@link #y}, so they all slow uniformly without
 * a line of per-mode code.
 */
public class DragCursor
{
    private float offsetX;
    private float offsetY;
    private int lastX;
    private int lastY;
    private boolean hasLast;
    private boolean reanchor;

    /** Drop the tracking; the next frame anchors afresh. */
    public void forget()
    {
        this.hasLast = false;
        this.reanchor = false;
    }

    /** Plant the cursor here — on start, after a screen-edge wrap, and when numeric input ends. */
    public void anchor(int mouseX, int mouseY)
    {
        this.offsetX = 0F;
        this.offsetY = 0F;
        this.lastX = mouseX;
        this.lastY = mouseY;
        this.hasLast = true;
    }

    /** Carry the virtual cursor into this frame, damping it while Shift is held.
     *  {@code fineFactor} is the gesture's precision speed (Blender: 1/10, but
     *  1/30 for angle/trackball gestures). */
    public void advance(int mouseX, int mouseY, float fineFactor)
    {
        if (!this.hasLast)
        {
            this.anchor(mouseX, mouseY);

            return;
        }

        if (Window.isShiftPressed())
        {
            float keep = 1F - fineFactor;

            this.offsetX += (mouseX - this.lastX) * keep;
            this.offsetY += (mouseY - this.lastY) * keep;
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    public int x(int mouseX)
    {
        return Math.round(mouseX - this.offsetX);
    }

    public int y(int mouseY)
    {
        return Math.round(mouseY - this.offsetY);
    }

    /**
     * Note whether the drag was anchored without a cursor. Left alone, the whole distance
     * between that placeholder and the pointer's real position arrives as one step on the first
     * frame that has one (measured: 7.8 radians before the mouse had moved at all).
     */
    public void armReanchor(int mouseX, int mouseY)
    {
        this.reanchor = mouseX < 0 || mouseY < 0;
    }

    /**
     * Whether this frame must re-plant the anchor instead of advancing the gesture. True at most
     * once per drag, on the first frame that carries a cursor.
     */
    public boolean claimReanchor(int mouseX, int mouseY)
    {
        if (!this.reanchor || mouseX < 0 || mouseY < 0)
        {
            return false;
        }

        this.reanchor = false;
        this.anchor(mouseX, mouseY);

        return true;
    }
}
