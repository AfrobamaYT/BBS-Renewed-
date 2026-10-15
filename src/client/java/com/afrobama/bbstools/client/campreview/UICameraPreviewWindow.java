// Coded by Afrobama/Claude — BBSTools Camera Preview Phase 5: header controls
package com.afrobama.bbstools.client.campreview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Draggable + resizable window that displays one {@link CameraPreview}'s
 * framebuffer. Blender-style: header bar = drag, bottom-right corner =
 * resize. Header buttons: [X] close, [R] cycle resolution. Lives in the
 * dashboard's overlay so it floats over the editor.
 *
 * <p>Window position/size + resolution choice persist in the underlying
 * {@link CameraPreview} fields, so re-opening the dashboard restores
 * the same layout.</p>
 *
 * @deprecated will be removed in Phase 11 — see new clip-driven design
 */
@Deprecated
public class UICameraPreviewWindow extends UIElement
{
    private static final int HEADER_H = 14;
    private static final int RESIZE_HANDLE_PX = 10;
    private static final int BUTTON_W = 12;

    /**
     * Die vorschau-textur als GUI-bindung.
     *
     * <p>26.2 nimmt keine rohe GL-nummer mehr entgegen, sondern ansicht + abtaster.
     * NEAREST und CLAMP_TO_EDGE sind bewusst gewaehlt: das fenster zeigt ein
     * kamerabild fuer machinima, da soll nichts weichgezeichnet und am rand nichts
     * wiederholt werden.
     */
    private static TextureSetup vorschauTextur(GpuTextureView ansicht)
    {
        return TextureSetup.singleTexture(ansicht, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    private final CameraPreview preview;

    private boolean dragging;
    private boolean resizing;
    private int dragMouseX, dragMouseY;
    private int dragStartWinX, dragStartWinY;
    private int dragStartW, dragStartH;

    public UICameraPreviewWindow(CameraPreview preview)
    {
        this.preview = preview;
        this.applyWindowRect();
    }

    public CameraPreview getPreview()
    {
        return this.preview;
    }

    public void applyWindowRect()
    {
        this.xy(this.preview.windowX, this.preview.windowY).wh(this.preview.windowW, this.preview.windowH);
        this.resize();
    }

    /** Hit rect for the close button (rightmost slot in header). */
    private int closeBtnX() { return this.area.x + this.area.w - BUTTON_W; }

    /** Hit rect for the resolution-cycle button (next to close). */
    private int resBtnX() { return this.area.x + this.area.w - BUTTON_W * 2; }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY))
        {
            int relX = context.mouseX - this.area.x;
            int relY = context.mouseY - this.area.y;

            /* Header button hits — checked BEFORE the drag-on-header
             *  path so clicking [X] or [R] doesn't also start a drag. */
            if (relY < HEADER_H)
            {
                /* Close button. */
                if (context.mouseX >= this.closeBtnX() && context.mouseX < this.closeBtnX() + BUTTON_W)
                {
                    if (com.blenderbridge.camera.RenderCameraPreview.owns(this.preview.id))
                        com.blenderbridge.camera.RenderCameraPreview.dismiss();
                    else
                    {
                        CameraPreviewRegistry.remove(this.preview.id);
                        this.removeFromParent();
                    }
                    return true;
                }

                /* Resolution-cycle button. */
                if (context.mouseX >= this.resBtnX() && context.mouseX < this.resBtnX() + BUTTON_W)
                {
                    CameraPreviewRegistry.cycleResolution(this.preview);
                    return true;
                }
            }

            /* Bottom-right corner = resize (checked before drag so the
             *  resize zone takes priority in the overlap area). */
            if (relX >= this.area.w - RESIZE_HANDLE_PX && relY >= this.area.h - RESIZE_HANDLE_PX)
            {
                this.resizing = true;
                this.dragMouseX = context.mouseX;
                this.dragMouseY = context.mouseY;
                this.dragStartW = this.preview.windowW;
                this.dragStartH = this.preview.windowH;
                return true;
            }

            /* Header bar = drag. */
            if (relY < HEADER_H)
            {
                this.dragging = true;
                this.dragMouseX = context.mouseX;
                this.dragMouseY = context.mouseY;
                this.dragStartWinX = this.preview.windowX;
                this.dragStartWinY = this.preview.windowY;
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragging || this.resizing)
        {
            this.dragging = false;
            this.resizing = false;
            return true;
        }
        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging)
        {
            this.preview.windowX = this.dragStartWinX + (context.mouseX - this.dragMouseX);
            this.preview.windowY = this.dragStartWinY + (context.mouseY - this.dragMouseY);
            this.applyWindowRect();
        }
        else if (this.resizing)
        {
            this.preview.windowW = Math.max(80, this.dragStartW + (context.mouseX - this.dragMouseX));
            this.preview.windowH = Math.max(60, this.dragStartH + (context.mouseY - this.dragMouseY));
            this.applyWindowRect();
        }

        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;

        /* Frame. */
        context.batcher.box(x, y, x + w, y + h, Colors.A100 | 0x101010);
        context.batcher.outline(x, y, x + w, y + h, Colors.A100 | 0x4ec1ff);

        /* Header bar. */
        context.batcher.box(x, y, x + w, y + HEADER_H, Colors.A100 | 0x1b3a52);

        /* Label clamped so the buttons on the right have room. */
        String label = this.preview.name + "  " + this.preview.fbWidth + "x" + this.preview.fbHeight;
        context.batcher.textShadow(label, x + 4, y + 3);

        /* Header buttons. Hover highlight via mouse-pos check. */
        int rx = this.resBtnX();
        int cx = this.closeBtnX();
        boolean hoverRes = context.mouseY >= y && context.mouseY < y + HEADER_H
            && context.mouseX >= rx && context.mouseX < rx + BUTTON_W;
        boolean hoverClose = context.mouseY >= y && context.mouseY < y + HEADER_H
            && context.mouseX >= cx && context.mouseX < cx + BUTTON_W;

        context.batcher.box(rx, y, rx + BUTTON_W, y + HEADER_H,
            hoverRes ? (Colors.A100 | 0x2a5f88) : 0);
        context.batcher.textShadow("R", rx + 3, y + 3);

        context.batcher.box(cx, y, cx + BUTTON_W, y + HEADER_H,
            hoverClose ? (Colors.A100 | 0x883030) : 0);
        context.batcher.textShadow("X", cx + 3, y + 3);

        /* Framebuffer texture — only if allocated AND attachment is valid. */
        if (this.preview.framebuffer != null)
        {
            GpuTextureView texAnsicht = this.preview.framebuffer.getColorTextureView();
            if (texAnsicht != null)
            {
                int top = y + HEADER_H;
                int bottom = y + h;
                int drawW = Math.min(w, Math.round((bottom-top) * (float)this.preview.fbWidth / this.preview.fbHeight));
                int drawH = Math.round(drawW * (float)this.preview.fbHeight / this.preview.fbWidth);
                /* Flip V because framebuffer is bottom-up vs UI top-down. */
                context.batcher.texturedBox(
                    () -> RenderPipelines.GUI_TEXTURED,
                    vorschauTextur(texAnsicht),
                    Colors.WHITE,
                    x + (w-drawW)/2, top + (bottom-top-drawH)/2,
                    drawW, drawH,
                    0, this.preview.fbHeight,
                    this.preview.fbWidth, 0,
                    this.preview.fbWidth, this.preview.fbHeight);
            }
        }
        else
        {
            context.batcher.text("Allocating framebuffer...", x + 6, y + HEADER_H + 6);
        }

        /* Resize handle visual hint. */
        int rgx = x + w - RESIZE_HANDLE_PX;
        int rgy = y + h - RESIZE_HANDLE_PX;
        context.batcher.box(rgx, rgy, x + w, y + h, Colors.A50 | 0x4ec1ff);

        super.render(context);
    }

}
