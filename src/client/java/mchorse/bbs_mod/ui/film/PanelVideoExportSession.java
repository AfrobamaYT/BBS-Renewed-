package mchorse.bbs_mod.ui.film;

import java.util.ArrayList;
import mchorse.bbs_mod.ui.utils.Scale;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.util.function.Supplier;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import org.joml.Vector2i;

import java.io.File;
import java.util.List;

/**
 * Video export of the film panel's preview texture. Renders the audio track,
 * restarts the film to the (loop-aware) start, pauses/resumes the editor around
 * the warm-up, and drives the {@link UIFilmRecorder} overlay.
 */
public class PanelVideoExportSession extends VideoExportSession
{
    private final List<double[]> ansichten = new ArrayList<>();
    private int start;
    private long startedAtMs;
    private float[] nanoAnsicht;

    private final UIFilmRecorder ui;
    private final UIFilmPanel editor;

    private int duration;
    private int end;
    private boolean restorePaused;

    /** Proxy renders are written under this name, then moved out of the movies folder. */
    public static final String PROXY_PREFIX = "bbs-resolve-proxy-";

    private boolean proxy;

    public PanelVideoExportSession(UIFilmRecorder ui, UIFilmPanel editor)
    {
        this.ui = ui;
        this.editor = editor;
    }

    public boolean start(int duration, GpuTexture texture, int width, int height)
    {
        return this.start(duration, () -> texture, width, height);
    }

    public boolean start(int duration, Supplier<GpuTexture> textureSource, int width, int height)
    {
        this.proxy = false;
        this.duration = duration;

        long delayMs = (long) (Math.max(0F, BBSSettings.videoDelay.get()) * 1000F);

        return this.begin(textureSource, width, height, delayMs);
    }

    /**
     * The whole film, silently and without sound, for DaVinci Resolve's camera clips: no export
     * delay, no loop range, no completion sound or folder.
     */
    public boolean startProxy(int duration, Supplier<GpuTexture> textureSource, int width, int height)
    {
        this.proxy = true;
        this.duration = duration;

        return this.begin(textureSource, width, height, 0L);
    }

    @Override
    protected boolean finishEffects()
    {
        return !this.proxy;
    }

    @Override
    protected boolean prepare()
    {
        try
        {
            if (BBSSettings.videoExportAudio.get() && !this.proxy)
            {
                Clips camera = this.editor.getData().camera;
                List<AudioClip> audioClips = camera.getClips(AudioClip.class);

                String name = StringUtils.createTimestampFilename() + ".wav";
                File file = new File(BBSRendering.getVideoFolder(), name);
                Vector2i range = BBSSettings.editorLoop.get() ? this.editor.getLoopingRange() : new Vector2i();

                if (AudioRenderer.renderAudio(file, audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
                {
                    this.audioFile = file;
                }
            }
        }
        catch (Exception e)
        {
            UIOverlay.addOverlay(this.editor.getContext(), new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, IKey.constant(e.getMessage())));

            return false;
        }

        this.restorePaused = this.editor.getController().isPaused();

        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;
        boolean looping = BBSSettings.editorLoop.get() && !this.proxy;

        this.end = looping && min != max ? Math.max(min, max) : this.duration;

        this.editor.setCursor(looping ? Math.min(min, max) : 0);
        this.editor.notifyServer(ActionState.RESTART);
        this.start = looping ? Math.min(min, max) : 0;
        this.ansichtenAnpassen(this.start, this.end);

        if (this.ui.resetReplays)
        {
            this.editor.getController().createEntities();
        }

        this.ui.attachOverlay();

        return true;
    }

    @Override
    protected String getMovieName()
    {
        Film film = this.editor.getData();

        if (this.proxy)
        {
            return uniqueName(BBSRendering.getVideoFolder(), PROXY_PREFIX + StringUtils.createTimestampFilename());
        }

        String base = StringUtils.resolveExportFilename(
            BBSSettings.videoExportFilenameFormat.get(),
            film == null ? "" : film.getId(),
            this.width,
            this.height,
            BBSRendering.getVideoFrameRate(),
            film == null ? 0 : film.camera.calculateDuration()
        );

        return uniqueName(BBSRendering.getVideoFolder(), base);
    }

    /**
     * Avoid overwriting a previous export - and, with it, hanging ffmpeg (whose default args
     * carry no {@code -y}): when a file with this base name already exists, append " (n)".
     */
    private static String uniqueName(File folder, String base)
    {
        String candidate = base;

        for (int i = 1; nameTaken(folder, candidate); i++)
        {
            candidate = base + " (" + i + ")";
        }

        return candidate;
    }

    private static boolean nameTaken(File folder, String base)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return false;
        }

        String prefix = (base + ".").toLowerCase();

        for (File file : files)
        {
            String name = file.getName().toLowerCase();

            /* Only a video of an earlier export takes the name: the audio track this
             * export just rendered sits in the same folder under the same base name and
             * must not bump every audio export to a "(1)" */
            if (name.startsWith(prefix) && !isExportArtifact(name.substring(prefix.length())))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onWarmupStarted()
    {
        this.editor.getController().setPaused(true);
    }

    @Override
    protected void onRecordingStarted()
    {
        this.startedAtMs = System.currentTimeMillis();
        this.editor.getController().setPaused(false);
        this.editor.togglePlayback();
    }

    @Override
    protected boolean isFinished()
    {
        return !this.editor.isRunning() || this.editor.getCursor() >= this.end;
    }

    /**
     * The timelines show the exported range edge to edge while the video records, so the playhead
     * crosses them once; {@link #teardown} puts their own views back. From BBS FS 2d54428ca
     * (Wemppy4/bbs-fs, MIT); here the clip and key axes and the film's nano timeline.
     */
    private void ansichtenAnpassen(int start, int end)
    {
        this.ansichten.clear();

        if (end <= start) return;

        for (Scale achse : this.achsen())
        {
            this.ansichten.add(new double[] {achse.getMinValue(), achse.getMaxValue()});
            achse.view(start, end);
        }

        if (this.editor.zeitleisteNano instanceof UIZeitleisteNano nano) this.nanoAnsicht = nano.sichtSetzen(start, end);
    }

    private List<Scale> achsen()
    {
        List<Scale> achsen = new ArrayList<>(List.of(this.editor.cameraEditor.clips.scale));

        if (this.editor.replayEditor.keyframeEditor != null) achsen.add(this.editor.replayEditor.keyframeEditor.view.getXAxis());

        return achsen;
    }

    private void ansichtenZurueck()
    {
        List<Scale> achsen = this.achsen();

        for (int i = 0; i < Math.min(achsen.size(), this.ansichten.size()); i++)
        {
            if (this.ansichten.get(i)[0] < this.ansichten.get(i)[1]) achsen.get(i).view(this.ansichten.get(i)[0], this.ansichten.get(i)[1]);
        }

        if (this.nanoAnsicht != null && this.editor.zeitleisteNano instanceof UIZeitleisteNano nano) nano.sichtSetzen(this.nanoAnsicht[0], this.nanoAnsicht[1]);

        this.ansichten.clear();
        this.nanoAnsicht = null;
    }

    /** Recorded share of the exported range, 0..1; zero until recording starts (BBS FS 79172021c). */
    public float getProgress()
    {
        if (this.state != State.RECORDING || this.end <= this.start) return 0F;

        return Math.max(0F, Math.min(1F, (this.editor.getCursor() - this.start) / (float) (this.end - this.start)));
    }

    /** Rough time left, from how long the recorded part took; -1 until there is enough to go on. */
    public long getRemainingMs(float progress)
    {
        long elapsed = System.currentTimeMillis() - this.startedAtMs;

        return this.state != State.RECORDING || progress < 0.01F || elapsed < 1000L ? -1L : (long) (elapsed * (1F - progress) / progress);
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        this.ansichtenZurueck();
        if (!cancelled) this.ui.showFinished(this.getRecordingName());
        this.editor.getController().setPaused(this.restorePaused);
        this.editor.restorePreviewSize();

        if (this.editor.isRunning())
        {
            this.editor.togglePlayback();
        }

        this.ui.detachOverlay();
    }
}
