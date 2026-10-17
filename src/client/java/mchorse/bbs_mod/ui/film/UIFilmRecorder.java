package mchorse.bbs_mod.ui.film;

import java.util.Locale;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import java.util.function.Supplier;

/**
 * Thin UI wrapper around a {@link PanelVideoExportSession}. Owns the overlay
 * presence (warm-up countdown, ESC-to-cancel) and delegates the export
 * lifecycle to the session.
 */
public class UIFilmRecorder extends UIElement
{
    private long attachedAtMs;
    private long finishedAtMs;
    private String finishedName;

    public UIFilmPanel editor;

    public boolean resetReplays = true;

    private final PanelVideoExportSession session;
    private final UIExit exit = new UIExit(this);

    public UIFilmRecorder(UIFilmPanel editor)
    {
        super();

        this.editor = editor;
        this.session = new PanelVideoExportSession(this, editor);

        this.noCulling();
    }

    public boolean isRecording()
    {
        return this.session.isRecording();
    }

    public boolean isExporting()
    {
        return this.session.isExporting();
    }

    public void setFinishedListener(VideoExportSession.FinishedListener listener)
    {
        this.session.setFinishedListener(listener);
    }

    public void cancel()
    {
        this.session.cancel();
    }

    public void stop()
    {
        this.session.stop();
    }

    public void fail(Exception failure)
    {
        this.session.fail(failure);
    }

    public void openMovies()
    {
        UIUtils.openFolder(BBSRendering.getVideoFolder());
    }

    public void startRecording(int duration, Texture texture)
    {
        this.startRecording(duration, texture::gpu, texture.width, texture.height);
    }

    public void startRecording(int duration, GpuTexture texture, int w, int h)
    {
        this.startRecording(duration, () -> texture, w, h);
    }

    public void startRecording(int duration, Supplier<GpuTexture> textureSource, int w, int h)
    {
        if (this.editor.isRunning() || duration <= 0)
        {
            return;
        }

        this.session.start(duration, textureSource, w, h);
    }

    /** A silent full-film render for DaVinci Resolve (see {@link PanelVideoExportSession#startProxy}). */
    public void startProxy(int duration, Supplier<GpuTexture> textureSource, int w, int h)
    {
        if (this.editor.isRunning() || duration <= 0)
        {
            return;
        }

        this.session.startProxy(duration, textureSource, w, h);
    }

    /**
     * Add the recorder to the overlay and disable the main UI. Called by the
     * session as recording is set up.
     */
    void attachOverlay()
    {
        UIContext context = this.editor.getContext();

        this.attachedAtMs = System.currentTimeMillis();
        this.finishedAtMs = 0L;
        context.menu.main.setEnabled(false);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);
    }

    /**
     * Remove the recorder from the overlay and re-enable the main UI. Called by
     * the session during teardown.
     */
    void detachOverlay()
    {
        UIContext context = this.editor.getContext();

        context.render.postRunnable(this.exit::removeFromParent);
        context.menu.main.setEnabled(true);
        context.render.postRunnable(this::removeFromParent);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        Area viewport = this.editor.preview.getViewport();

        this.dim(context, viewport);

        if (this.session.isExporting() && !this.session.isWarmingUp())
        {
            float progress = this.session.getProgress();
            long remaining = this.session.getRemainingMs(progress);
            float pulse = 0.6F + 0.4F * (float) Math.cos((System.currentTimeMillis() % 1400L) / 1400D * Math.PI * 2D);

            this.linie(context, viewport, progress, Colors.RED, 1F);
            this.anzeige(context, viewport, Icons.SPHERE, Colors.setA(Colors.RED, pulse), (int) (progress * 100F) + "%", remaining < 0L ? "" : rest(remaining), 1F);
        }

        this.session.update();
    }

    /**
     * Everything but the video frame goes dark: the editor can't be used while exporting, and the
     * frame is the one thing worth watching. The fade in is all there is of the warm-up (BBS FS
     * 79172021c, which replaced the countdown with it).
     */
    private void dim(UIContext context, Area viewport)
    {
        int color = Colors.setA(0, 0.6F * Math.min(1F, (System.currentTimeMillis() - this.attachedAtMs) / 300F));
        int w = context.menu.width, h = context.menu.height;

        context.batcher.box(0, 0, w, viewport.y, color);
        context.batcher.box(0, viewport.ey(), w, h, color);
        context.batcher.box(0, viewport.y, viewport.x, viewport.ey(), color);
        context.batcher.box(viewport.ex(), viewport.y, w, viewport.ey(), color);
    }

    /** Marks the export as finished with this file for a moment; from the session's teardown. */
    void showFinished(String name)
    {
        this.finishedAtMs = System.currentTimeMillis();
        this.finishedName = name == null ? "" : name;
    }

    /** For two seconds after a finished export, then fading: a full green line and the file's name. */
    public void renderFertig(UIContext context, Area viewport)
    {
        long since = System.currentTimeMillis() - this.finishedAtMs;

        if (this.isExporting() || this.finishedAtMs <= 0L || since >= 2500L) return;

        float alpha = since < 2000L ? 1F : 1F - (since - 2000L) / 500F;

        this.linie(context, viewport, 1F, Colors.GREEN, alpha);
        this.anzeige(context, viewport, Icons.CHECKMARK, Colors.setA(Colors.GREEN, alpha), this.finishedName, "", alpha);
    }

    private void linie(UIContext context, Area viewport, float progress, int color, float alpha)
    {
        context.batcher.box(viewport.x, viewport.ey() - 2, viewport.ex(), viewport.ey(), Colors.setA(0, 0.25F * alpha));
        context.batcher.box(viewport.x, viewport.ey() - 2, viewport.x + Math.round(viewport.w * progress), viewport.ey(), Colors.setA(color, alpha));
    }

    /** An icon, then the text: percent and time left, or the saved file. BBS FS puts it top left. */
    private void anzeige(UIContext context, Area viewport, Icon icon, int iconColor, String label, String secondary, float alpha)
    {
        FontRenderer font = context.batcher.getFont();
        /* Bottom left above the line: the top left holds this tree's tool column and view label */
        int x = viewport.x + 26, y = viewport.ey() - 8 - icon.h; /* past the axis gizmo */
        int textY = y + icon.h / 2 - font.getHeight() / 2;

        context.batcher.icon(icon, iconColor, x, y);
        context.batcher.text(label, x + icon.w + 4, textY, Colors.setA(Colors.WHITE, alpha), true);
        if (!secondary.isEmpty()) context.batcher.text(secondary, x + icon.w + 12 + font.getWidth(label), textY, Colors.setA(Colors.LIGHTER_GRAY, alpha), true);
    }

    private static String rest(long ms)
    {
        long seconds = (ms + 999L) / 1000L, hours = seconds / 3600L, minutes = seconds % 3600L / 60L;

        return hours > 0L ? String.format(Locale.ROOT, "~%d:%02d:%02d", hours, minutes, seconds % 60L) : String.format(Locale.ROOT, "~%d:%02d", minutes, seconds % 60L);
    }

    public static class UIExit extends UIElement
    {
        private UIFilmRecorder recorder;

        public UIExit(UIFilmRecorder recorder)
        {
            this.recorder = recorder;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(InputConstants.KEY_ESCAPE))
            {
                this.recorder.cancel();

                return true;
            }

            return super.subKeyPressed(context);
        }
    }
}
