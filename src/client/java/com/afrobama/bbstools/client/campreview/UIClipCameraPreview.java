// Coded by Afrobama/Claude — Phase 15 v5: free-fly mode + minimalist BBS-Refreshed-style tile
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.clip.BBSToolsAddCameraClip;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Alt-camera preview tile — minimalist BBS-Refreshed style + free-fly
 * mode for stationary camera placement.
 *
 * <p>Header reduced to a thin invisible drag-strip (no chunky colored
 * bar with text). Resize handle is 3 subtle Cyan dots in the corner.
 * Body is the framebuffer blit (replays + grid, no world).</p>
 *
 * <p>Free-fly mode: hover this tile + press {@code C} to toggle. While
 * active, WASD/Space/Shift moves the clip's offset and Q/E + R/F
 * rotates yaw/pitch. Editing is live — the clip's stored pose updates
 * each frame, so when you exit free-fly the camera stays where you
 * placed it. Hold left-Shift while moving for a 4× speed multiplier.</p>
 */
public class UIClipCameraPreview extends UIElement
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/tile");

    /* Visual style — driven by BBSToolsTheme (UI Overhaul Phase 1).
     *  HEADER_H is the invisible top-edge drag-detection band; the
     *  framebuffer renders behind it so there's no visible gap.
     *  Hex constants below are LEGACY — read via BBSToolsTheme. */
    private static final int HEADER_H = 6;
    private static final int RESIZE_HANDLE_PX = 8;
    /* Pure black for the body — the framebuffer fills it; the actual
     *  panel-bg color from theme would only show through transparent
     *  pixels of the framebuffer. We deliberately keep pure 0x000000
     *  here so the alt-cam looks like a "viewport" not a "panel". */
    private static final int COL_BG = 0x000000;

    /* Free-fly tuning. */
    private static final float MOVE_BASE_PER_FRAME = 0.15F;
    private static final float MOVE_FAST_MULT = 4F;
    private static final float ROT_BASE_PER_FRAME = 1.8F;
    /** Mouse-look sensitivity for free-fly. Each pixel of mouse motion
     *  rotates the camera by this many degrees. Tuned so a comfortable
     *  hand-wide drag gives ~90° turn. */
    private static final float MOUSE_LOOK_SENS = 0.35F;

    /** Mode-toggle button geometry — small square right of the resize
     *  dots, doubles as visual indicator of camera vs. free mode. */
    private static final int MODE_BTN_SIZE = 9;
    private static final int MODE_BTN_PAD = 2;
    /* Mode button accents now come from BBSToolsTheme. Kept as
     *  named constants here for readability. */
    private static final int COL_MODE_CAM = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.ACCENT_CAMERA & 0xFFFFFF;
    private static final int COL_MODE_FREE = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.ACCENT_FREEFLY & 0xFFFFFF;

    public final BBSToolsAddCameraClip clip;
    public final UUID clipId;

    /** Set true the moment the user drags or resizes this tile. */
    public boolean manuallyPositioned;

    public boolean freeFlyActive;

    /** True when in Free Mode (camera locked to the per-tile free
     *  pose, ignoring clip-animation). Camera Mode = follow clip. */
    public boolean freeMode;

    /** Local cached pose for Free Mode. {@code [x, y, z, yaw, pitch]}.
     *  Initialized from the registry's clip pose on first entry into
     *  free mode. */
    private final double[] freePose = new double[5];

    /** Mouse-look state. We track the cursor position from frame-to-
     *  frame so we can apply the delta as yaw/pitch rotation while
     *  free-fly is active. {@code -1} = sentinel for "no previous
     *  sample yet". */
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    private boolean dragging;
    private boolean resizing;
    private int dragMouseX, dragMouseY;
    private int dragStartX, dragStartY;
    private int dragStartW, dragStartH;

    public UIClipCameraPreview(BBSToolsAddCameraClip clip, UUID clipId)
    {
        this.clip = clip;
        this.clipId = clipId;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY))
        {
            int relX = context.mouseX - this.area.x;
            int relY = context.mouseY - this.area.y;

            /* Bottom-left = mode-toggle button. */
            if (relX >= MODE_BTN_PAD && relX < MODE_BTN_PAD + MODE_BTN_SIZE
                && relY >= this.area.h - MODE_BTN_PAD - MODE_BTN_SIZE
                && relY < this.area.h - MODE_BTN_PAD)
            {
                if (this.freeMode)
                {
                    this.freeMode = false;
                    this.freeFlyActive = false;
                    LOG.info("Mode toggle: → CAMERA MODE for clip {}", this.clipId);
                }
                else
                {
                    this.enterFreeMode();
                    LOG.info("Mode toggle: → FREE MODE for clip {}", this.clipId);
                }
                return true;
            }

            /* Bottom-right corner = resize. */
            if (relX >= this.area.w - RESIZE_HANDLE_PX && relY >= this.area.h - RESIZE_HANDLE_PX)
            {
                this.resizing = true;
                this.manuallyPositioned = true;
                this.dragMouseX = context.mouseX;
                this.dragMouseY = context.mouseY;
                this.dragStartW = this.area.w;
                this.dragStartH = this.area.h;
                return true;
            }

            /* Top edge thin strip = drag. */
            if (relY < HEADER_H)
            {
                this.dragging = true;
                this.manuallyPositioned = true;
                this.dragMouseX = context.mouseX;
                this.dragMouseY = context.mouseY;
                this.dragStartX = this.area.x;
                this.dragStartY = this.area.y;
                return true;
            }
        }
        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isFocused())
        {
            return false;
        }
        if (this.area.isInside(context.mouseX, context.mouseY) && context.isPressed(GLFW.GLFW_KEY_F))
        {
            if (!this.freeMode)
            {
                this.enterFreeMode();
            }
            this.freeFlyActive = !this.freeFlyActive;
            suppressBBSFlight();
            return true;
        }
        if (this.freeFlyActive)
        {
            int key = context.getKeyCode();
            if (key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S
                || key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_CONTROL
                || key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_T || key == GLFW.GLFW_KEY_G)
            {
                return true;
            }
        }
        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging)
        {
            int nx = this.dragStartX + (context.mouseX - this.dragMouseX);
            int ny = this.dragStartY + (context.mouseY - this.dragMouseY);
            this.xy(nx, ny);
            this.resize();
        }
        else if (this.resizing)
        {
            int nw = Math.max(120, this.dragStartW + (context.mouseX - this.dragMouseX));
            int nh = Math.max(80, this.dragStartH + (context.mouseY - this.dragMouseY));
            this.wh(nw, nh);
            this.resize();
        }

        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;

        boolean hovered = this.area.isInside(context.mouseX, context.mouseY);

        Window handle = Minecraft.getInstance().getWindow();

        if (this.freeFlyActive)
        {
            /* Suppress BBS's main-preview flight so WASD/mouse-look only
             *  affects our alt-tile camera, not the main film cam. */
            suppressBBSFlight();

            tickFreeFly(handle);
            tickMouseLook(context);
        }
        else
        {
            this.lastMouseX = -1;
            this.lastMouseY = -1;
        }

        /* Publish/clear the override pose so the pre-pass mixin uses
         *  the per-tile pose when we're in free mode, and the clip's
         *  pose otherwise. */
        if (this.freeMode)
        {
            AltCamModeOverrides.FREE_POSES.put(this.clipId,
                new double[] { this.freePose[0], this.freePose[1], this.freePose[2],
                               this.freePose[3], this.freePose[4] });
        }
        else
        {
            AltCamModeOverrides.FREE_POSES.remove(this.clipId);
        }

        /* Pure-black panel background. */
        context.batcher.box(x, y, x + w, y + h, Colors.A100 | COL_BG);

        /* Framebuffer blit fills the ENTIRE tile area — the drag-strip
         *  at the top sits invisibly over the framebuffer so there's no
         *  visual gap. */
        TextureTarget fb = null;
        try { fb = ClipViewportFramebuffers.get(this.clipId); }
        catch (Throwable ignored) {}

        /* ⛔ 26.2, OFFENE UEBERGABE — NICHT VON MIR ERFUNDEN:
         *  RenderTarget hat keine GL-Textur-ID mehr (javap: nur noch
         *  getColorTexture() und getColorTextureView()). Eine rohe int-ID
         *  KANN es in 26.2 auch nicht mehr geben, weil Texturen ueber
         *  RenderPass.bindTexture(String, GpuTextureView, GpuSampler)
         *  gebunden werden. Die Framebuffer-Seite steht damit fest; offen ist
         *  die Gegenseite: Batcher2D.texturedBox(int, ...) ist noch nicht
         *  portiert (Batcher2D haengt selbst an Tesselator/BufferUploader/
         *  RenderPipeline und gehoert in ein anderes Manifest). Diese Zeile
         *  uebersetzt erst, wenn Batcher2D eine GpuTextureView-Ueberladung hat. */
        if (fb != null)
        {
            com.mojang.blaze3d.textures.GpuTextureView texId = fb.getColorTextureView();
            if (texId != null)
            {
                /* V-flip framebuffer (bottom-up) into UI space. */
                /* 26.2: Batcher2D nimmt keine rohe Texturkennung mehr. Eine Textursicht wird
                 * ueber TextureSetup samt Abtaster gereicht -- die Filterung haengt jetzt am
                 * Abtaster des Zuges, nicht mehr am Renderziel. GUI_TEXTURED ist die Pipeline,
                 * die Batcher2D auch fuer seine anderen Bild-Kaesten nimmt. */
                context.batcher.texturedBox(
                    () -> net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.client.gui.render.TextureSetup.singleTexture(texId,
                        /* Filter und Randbehandlung haengen in 26.2 am ABTASTER, nicht mehr an
                         * der Textur. Mojangs Zwischenspeicher gibt fuer dieselbe Kombination
                         * dasselbe Objekt zurueck -- hier entsteht nichts pro Bild. NEAREST,
                         * damit die Vorschau pixelig bleibt wie das Spiel. */
                        com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                            com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                            com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                            com.mojang.blaze3d.textures.FilterMode.NEAREST,
                            com.mojang.blaze3d.textures.FilterMode.NEAREST, false)),
                    Colors.WHITE,
                    x, y, w, h,
                    0, ClipViewportFramebuffers.getHeight(),
                    ClipViewportFramebuffers.getWidth(), 0,
                    ClipViewportFramebuffers.getWidth(), ClipViewportFramebuffers.getHeight());
            }
        }

        /* Bone-hover pick: publish a request when the mouse is inside
         *  the body region (excluding header drag-strip + resize
         *  corner). The pre-pass mixin reads this next frame, runs
         *  BBS's stencil pick at the alt-cam POV, writes the result
         *  back. */
        boolean inBody = hovered
            && (context.mouseY - y) >= HEADER_H
            && !(context.mouseX - x >= w - RESIZE_HANDLE_PX
                 && context.mouseY - y >= h - RESIZE_HANDLE_PX);

        if (inBody && !this.freeFlyActive)
        {
            int relX = context.mouseX - x;
            int relY = context.mouseY - y;
            BoneHoverPickService.REQUESTS.put(this.clipId,
                new BoneHoverPickService.Request(relX, relY, w, h));
        }
        else
        {
            BoneHoverPickService.REQUESTS.remove(this.clipId);
        }

        /* Bone-hover blue highlight + label. Uses BBS's picker_preview
         *  shader to tint pixels matching the picked index. Mirrors
         *  what UIFilmController does over its main film preview. */
        BoneHoverPickService.Result pick = BoneHoverPickService.RESULTS.get(this.clipId);
        boolean haveFreshPick = pick != null && inBody
            && (System.currentTimeMillis() - pick.timestamp) < 500
            && pick.form != null
            && pick.stencilTexId != 0
            && pick.pickedIndex > 0;

        if (haveFreshPick)
        {
            mchorse.bbs_mod.ui.utils.StencilFormFramebuffer stencil = BoneHoverPickService.getStencil(this.clipId);
            mchorse.bbs_mod.graphics.texture.Texture texture = stencil.getFramebuffer().getMainTexture();
            mchorse.bbs_mod.graphics.texture.Texture highlight = stencil.hervorhebungsbild();
            mchorse.bbs_mod.graphics.Bildfilter.kennungHervorheben(texture, highlight, pick.pickedIndex,
                mchorse.bbs_mod.BBSSettings.stencilHighlightColor.get());
            context.batcher.texturedBox(() -> net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                highlight, Colors.WHITE, x, y, w, h, 0, texture.height, texture.width, 0, texture.width, texture.height);

            String label = pick.form.getFormIdOrName();
            if (pick.bone != null && !pick.bone.isEmpty())
            {
                label += " — " + pick.bone;
            }
            context.batcher.textCard(label, context.mouseX + 8, context.mouseY + 8);
        }

        /* 1 px subtle border. Orange when free-fly is active so the
         *  state is obvious; camera-accent when hovered; muted gray
         *  otherwise. Colors come from BBSToolsTheme (Phase 1). */
        int borderColor;
        if (this.freeFlyActive)
            borderColor = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.ACCENT_FREEFLY;
        else if (hovered)
            borderColor = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.ACCENT_CAMERA;
        else
            borderColor = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.BORDER;
        context.batcher.outline(x, y, x + w, y + h, borderColor);

        /* Resize handle: 3 subtle dots in the bottom-right corner. */
        int dotColor = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.withAlpha(
            com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.ACCENT_CAMERA, 0.5F);
        context.batcher.box(x + w - 7, y + h - 3, x + w - 5, y + h - 1, dotColor);
        context.batcher.box(x + w - 4, y + h - 3, x + w - 2, y + h - 1, dotColor);
        context.batcher.box(x + w - 4, y + h - 6, x + w - 2, y + h - 4, dotColor);

        /* Mode button: small square bottom-left. Cyan = camera mode,
         *  orange = free mode. Inner dot brightens on hover. */
        int btnX0 = x + MODE_BTN_PAD;
        int btnY0 = y + h - MODE_BTN_PAD - MODE_BTN_SIZE;
        int btnX1 = btnX0 + MODE_BTN_SIZE;
        int btnY1 = btnY0 + MODE_BTN_SIZE;
        boolean btnHover = context.mouseX >= btnX0 && context.mouseX < btnX1
            && context.mouseY >= btnY0 && context.mouseY < btnY1;
        int btnAccent = this.freeMode ? COL_MODE_FREE : COL_MODE_CAM;
        context.batcher.box(btnX0, btnY0, btnX1, btnY1,
            (btnHover ? Colors.A100 : Colors.A50) | btnAccent);
        /* Inner state glyph: filled square for free (locked), hollow ring for camera (animated). */
        if (this.freeMode)
        {
            context.batcher.box(btnX0 + 2, btnY0 + 2, btnX1 - 2, btnY1 - 2,
                Colors.A100 | 0x000000);
            context.batcher.box(btnX0 + 3, btnY0 + 3, btnX1 - 3, btnY1 - 3,
                Colors.A100 | btnAccent);
        }
        else
        {
            context.batcher.box(btnX0 + 2, btnY0 + 2, btnX1 - 2, btnY1 - 2,
                Colors.A100 | 0x000000);
        }

        super.render(context);
    }

    @Override
    public boolean subMouseReleased(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        boolean handled = subMouseReleasedDrag(context);
        if (!handled) handled = subMouseReleasedClick(context);
        return handled || super.subMouseReleased(context);
    }

    private boolean subMouseReleasedDrag(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        if (this.dragging || this.resizing)
        {
            this.dragging = false;
            this.resizing = false;
            return true;
        }
        return false;
    }

    private boolean subMouseReleasedClick(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        if (!this.area.isInside(context.mouseX, context.mouseY)) return false;
        if (this.freeFlyActive) return false;
        int relY = context.mouseY - this.area.y;
        if (relY < HEADER_H) return false;
        int relX = context.mouseX - this.area.x;
        if (relX >= this.area.w - RESIZE_HANDLE_PX && relY >= this.area.h - RESIZE_HANDLE_PX) return false;

        BoneHoverPickService.Result pick = BoneHoverPickService.RESULTS.get(this.clipId);
        if (pick == null || pick.form == null) return true;

        Window handle = Minecraft.getInstance().getWindow();
        boolean shift = InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ctrl = InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);

        mchorse.bbs_mod.ui.film.UIFilmPanel panel = BoneHoverPickRunner.findPanel();
        if (panel == null) return true;

        mchorse.bbs_mod.forms.forms.Form form = pick.form;
        String bone = pick.bone == null ? "" : pick.bone;

        LOG.info("Click apply: shift={} ctrl={} form={} bone={}", shift, ctrl, form.getFormIdOrName(), bone);

        if (shift)
        {
            mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.offerAdjacent(
                context, form, bone, picked -> panel.replayEditor.pickForm(form, picked));
        }
        else if (ctrl)
        {
            mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.offerHierarchy(
                context, form, bone, picked -> panel.replayEditor.pickForm(form, picked));
        }
        else
        {
            panel.replayEditor.pickForm(form, bone);
            if (!panel.replayEditor.isVisible())
            {
            }
        }

        return true;
    }

    /** Force BBS's main-preview flight mode OFF so WASD/mouse-look
     *  only flies our alt-cam, not the main camera too. Called every
     *  frame while our free-fly is active. */
    private static boolean loggedFirstSuppress;
    private static void suppressBBSFlight()
    {
        try
        {
            mchorse.bbs_mod.ui.film.UIFilmPanel panel = BoneHoverPickRunner.findPanel();
            if (panel != null && panel.isFlying())
            {
                panel.setFlight(false);
                if (!loggedFirstSuppress)
                {
                    loggedFirstSuppress = true;
                    LOG.info("Suppressed BBS panel flight to keep main preview still during alt-cam free-fly");
                }
            }
        }
        catch (Throwable ignored) {}
    }

    /** Snapshot the current alt-cam pose into the per-tile freePose
     *  so Free Mode starts where the camera currently is. Called on
     *  the camera→free transition. */
    private void enterFreeMode()
    {
        try
        {
            for (ActiveCameraClipRegistry.Active a :
                ActiveCameraClipRegistry.snapshot())
            {
                if (this.clipId.equals(a.id) && a.poseValid)
                {
                    this.freePose[0] = a.pose[0];
                    this.freePose[1] = a.pose[1];
                    this.freePose[2] = a.pose[2];
                    this.freePose[3] = a.pose[3];
                    this.freePose[4] = a.pose[4];
                    break;
                }
            }
        }
        catch (Throwable ignored) {}
        this.freeMode = true;
    }

    /** Polls movement/rotation keys. In Free Mode writes to the
     *  per-tile {@link #freePose}; in Camera Mode writes back to the
     *  clip's offset+angle properties (live-edit the timeline). */
    private void tickFreeFly(Window handle)
    {
        if (this.freeMode)
        {
            tickFreeFlyFreePose(handle);
            return;
        }

        Point offset = this.clip.offset.get();
        Point angle = this.clip.angle.get();
        if (offset == null || angle == null) return;

        float yawRad = (float) Math.toRadians(angle.y);

        float forwardX = -(float) Math.sin(yawRad);
        float forwardZ =  (float) Math.cos(yawRad);
        float rightX   =  (float) Math.cos(yawRad);
        float rightZ   =  (float) Math.sin(yawRad);

        float speed = MOVE_BASE_PER_FRAME;
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT))
        {
            speed *= MOVE_FAST_MULT;
        }

        double dx = 0, dy = 0, dz = 0;
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_W)) { dx += forwardX * speed; dz += forwardZ * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_S)) { dx -= forwardX * speed; dz -= forwardZ * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_A)) { dx -= rightX   * speed; dz -= rightZ   * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_D)) { dx += rightX   * speed; dz += rightZ   * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_SPACE))      { dy += speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) { dy -= speed; }

        if (dx != 0 || dy != 0 || dz != 0)
        {
            offset.set(offset.x + dx, offset.y + dy, offset.z + dz);
            this.clip.offset.set(offset, 0);
        }

        float rotSpeed = ROT_BASE_PER_FRAME;
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT))
        {
            rotSpeed *= 2F;
        }

        double newPitch = angle.x;
        double newYaw   = angle.y;
        boolean rotChanged = false;

        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_Q)) { newYaw -= rotSpeed; rotChanged = true; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_E)) { newYaw += rotSpeed; rotChanged = true; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_T)) { newPitch -= rotSpeed; rotChanged = true; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_G)) { newPitch += rotSpeed; rotChanged = true; }

        if (rotChanged)
        {
            /* Clamp pitch so we don't somersault. */
            if (newPitch > 89F) newPitch = 89F;
            if (newPitch < -89F) newPitch = -89F;
            angle.set(newPitch, newYaw, angle.z);
            this.clip.angle.set(angle, 0);
        }
    }

    /** Each-frame mouse-delta → yaw/pitch rotation while free-fly is
     *  active. Cursor stays visible (no lock) — user just drags the
     *  mouse and the camera rotates by the delta. */
    private void tickMouseLook(UIContext context)
    {
        if (this.lastMouseX < 0)
        {
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;
            return;
        }
        int dx = context.mouseX - this.lastMouseX;
        int dy = context.mouseY - this.lastMouseY;
        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;
        if (dx == 0 && dy == 0) return;

        if (this.freeMode)
        {
            this.freePose[3] += dx * MOUSE_LOOK_SENS;
            this.freePose[4] += dy * MOUSE_LOOK_SENS;
            if (this.freePose[4] > 89F) this.freePose[4] = 89F;
            if (this.freePose[4] < -89F) this.freePose[4] = -89F;
        }
        else
        {
            mchorse.bbs_mod.camera.data.Point angle = this.clip.angle.get();
            if (angle == null) return;
            double newYaw = angle.y + dx * MOUSE_LOOK_SENS;
            double newPitch = angle.x + dy * MOUSE_LOOK_SENS;
            if (newPitch > 89F) newPitch = 89F;
            if (newPitch < -89F) newPitch = -89F;
            angle.set(newPitch, newYaw, angle.z);
            this.clip.angle.set(angle, 0);
        }
    }

    /** Free-Mode variant: same WASD/space/ctrl/Q-E/T-G layout but
     *  writes to {@link #freePose} instead of mutating the clip. */
    private void tickFreeFlyFreePose(Window handle)
    {
        float yawRad = (float) Math.toRadians(this.freePose[3]);

        float forwardX = -(float) Math.sin(yawRad);
        float forwardZ =  (float) Math.cos(yawRad);
        float rightX   =  (float) Math.cos(yawRad);
        float rightZ   =  (float) Math.sin(yawRad);

        float speed = MOVE_BASE_PER_FRAME;
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT))
        {
            speed *= MOVE_FAST_MULT;
        }

        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_W)) { this.freePose[0] += forwardX * speed; this.freePose[2] += forwardZ * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_S)) { this.freePose[0] -= forwardX * speed; this.freePose[2] -= forwardZ * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_A)) { this.freePose[0] -= rightX   * speed; this.freePose[2] -= rightZ   * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_D)) { this.freePose[0] += rightX   * speed; this.freePose[2] += rightZ   * speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_SPACE)) { this.freePose[1] += speed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) { this.freePose[1] -= speed; }

        float rotSpeed = ROT_BASE_PER_FRAME;
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT))
        {
            rotSpeed *= 2F;
        }

        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_Q)) { this.freePose[3] -= rotSpeed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_E)) { this.freePose[3] += rotSpeed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_T)) { this.freePose[4] -= rotSpeed; }
        if (InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_G)) { this.freePose[4] += rotSpeed; }

        if (this.freePose[4] > 89F) this.freePose[4] = 89F;
        if (this.freePose[4] < -89F) this.freePose[4] = -89F;
    }

}
