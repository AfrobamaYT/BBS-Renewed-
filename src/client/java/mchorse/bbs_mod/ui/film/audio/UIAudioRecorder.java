package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUITreeEventListener;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/** Native controls keep the scene visible, with a fixed origin for repeatable takes. */
public class UIAudioRecorder extends UIOverlayPanel implements IUITreeEventListener
{
    private static String lastDevice;
    private static int lastRate = 48000;
    private static boolean lastCountdown = true;
    public final UIButton microphone;
    public final UIButton rate;
    public final UIButton record;
    public final UIButton discard;
    public final UIToggle countdown;
    public final UITextbox name;
    public final UIText status;
    private final UIFilmPanel filmPanel;
    private final Film film;
    private final int originCursor;
    private OpenALRecorder recorder;
    private AudioClientClip previous;
    private float[] waveform;
    private long deadline;
    private boolean playing;
    private boolean finishing;
    private boolean closed;
    private String message = "Opening microphone…";

    public static void addOption(UIFilmPanel filmPanel, ContextMenuManager menu)
    {
        menu.action(Icons.SOUND, UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE, () -> open(filmPanel));
    }

    public static void open(UIFilmPanel panel)
    {
        UIOverlay.addOverlay(panel.getContext(), new UIAudioRecorder(panel), 350, 228);
    }

    public UIAudioRecorder(UIFilmPanel panel)
    {
        super(IKey.raw("Voice-over"));
        this.filmPanel = panel;
        this.film = panel.getData();
        this.originCursor = panel.getCursor();
        this.microphone = new UIButton(IKey.raw("Microphone"), (button) -> this.chooseMicrophone());
        this.rate = new UIButton(IKey.raw(lastRate + " Hz · Mono / 16-bit"), (button) ->
        {
            lastRate = lastRate == 48000 ? 44100 : 48000;
            button.label = IKey.raw(lastRate + " Hz · Mono / 16-bit");
            this.arm();
        });
        this.countdown = new UIToggle(IKey.raw("3-second count-in"), lastCountdown,
            (button) -> lastCountdown = button.getValue());
        this.name = new UITextbox(100, null);
        this.name.setText("Voice-over");
        this.record = new UIButton(IKey.raw("Record"), (button) -> this.toggleRecord());
        this.record.color(0xB94D55);
        this.discard = new UIButton(IKey.raw("Discard take"), (button) -> this.cancelTake());
        this.status = new UIText().text(this.message);
        UIElement meter = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                UIAudioRecorder.this.renderMeter(context, this);
            }
        };
        this.microphone.relative(this.content).xy(12, 8).w(1F, -24).h(22);
        this.rate.relative(this.content).xy(12, 38).w(0.5F, -18).h(22);
        this.countdown.relative(this.content).x(0.5F, 6).y(42).w(0.5F, -18).h(14);
        this.name.relative(this.content).xy(12, 68).w(1F, -24).h(22);
        this.status.relative(this.content).xy(12, 137).w(1F, -24).h(24);
        meter.relative(this.content).xy(12, 101).w(1F, -24).h(32);
        this.record.relative(this.content).xy(12, 168).w(0.5F, -18).h(24);
        this.discard.relative(this.content).x(0.5F, 6).y(168).w(0.5F, -18).h(24);
        this.content.add(this.microphone, this.rate, this.countdown, this.name, meter, this.status, this.record, this.discard);
        this.onClose((event) -> this.shutdown());
        this.arm();
    }

    private void arm()
    {
        if (this.recorder != null)
        {
            this.recorder.cancel();
        }

        this.microphone.label = IKey.raw("Mic: " + (lastDevice == null ? "System default ▾" : lastDevice + " ▾"));
        this.message = "Opening microphone…";
        this.recorder = new OpenALRecorder(lastDevice, lastRate);
        Thread worker = new Thread(this.recorder, "BBS voice-over input");

        worker.setDaemon(true);
        worker.start();
    }

    private void chooseMicrophone()
    {
        try
        {
            List<String> devices = OpenALRecorder.devices();
            UIListOverlayPanel picker = new UIListOverlayPanel(IKey.raw("Microphone"), null);

            picker.addValues(devices);
            picker.callback((values) ->
            {
                lastDevice = values.get(0);
                picker.close();
                this.arm();
            });
            UIOverlay.addOverlay(this.getContext(), picker, 340, 220);

            if (devices.isEmpty())
            {
                this.message = "No capture devices available";
            }
        }
        catch (Exception error)
        {
            this.message = "ERROR: " + error.getMessage();
        }
    }

    private void toggleRecord()
    {
        if (this.deadline != 0L)
        {
            this.cancelTake();

            return;
        }
        if (this.playing)
        {
            this.recorder.stop();
            this.finishing = true;
            this.stopPlayback();

            return;
        }
        if (!this.recorder.isReady() || this.film == null || this.filmPanel.getData() != this.film)
        {
            this.message = "ERROR: microphone or original film is not ready";

            return;
        }

        if (this.filmPanel.isRunning())
        {
            this.filmPanel.togglePlayback();
        }

        this.filmPanel.setCursor(this.originCursor);
        this.deadline = System.nanoTime() + (lastCountdown ? 3000000000L : 1L);
    }

    private void startPlayback()
    {
        this.deadline = 0L;

        try
        {
            this.recorder.begin();
            this.filmPanel.setCursor(this.originCursor);
            this.filmPanel.togglePlayback();
            this.playing = true;
        }
        catch (Exception error)
        {
            this.recorder.cancel();
            this.message = "ERROR: " + error.getMessage();
        }
    }

    private void stopPlayback()
    {
        if (this.playing && this.filmPanel.getData() == this.film && this.filmPanel.isRunning())
        {
            this.filmPanel.togglePlayback();
        }
        if ((this.playing || this.deadline != 0L) && this.filmPanel.getData() == this.film)
        {
            this.filmPanel.setCursor(this.originCursor);
        }

        this.playing = false;
    }

    private void cancelTake()
    {
        this.recorder.cancel();
        this.stopPlayback();
        this.deadline = 0L;
        this.finishing = false;
        this.arm();
        this.message = "Take discarded · start " + this.originCursor / 20F + " s";
    }

    private void shutdown()
    {
        this.closed = true;
        this.stopPlayback();
        this.deadline = 0L;
        this.recorder.cancel();
    }

    private void updateRecording()
    {
        if (this.closed)
        {
            return;
        }
        if (this.filmPanel.getData() != this.film)
        {
            this.shutdown();
            this.message = "ERROR: film changed; recording cancelled";
        }
        else if (this.recorder.getFailure() != null)
        {
            this.stopPlayback();
            this.deadline = 0L;
            this.finishing = false;
            this.message = "ERROR: " + this.recorder.getFailure().getMessage();
        }
        else if (this.finishing && this.recorder.isDone())
        {
            this.finishing = false;

            try
            {
                this.previous = VoiceoverTake.save(this.filmPanel, this.film, this.originCursor,
                    this.name.getText(), this.previous, this.recorder.getResult());
                this.arm();
                this.message = "Take saved · start " + this.originCursor / 20F + " s · Record for another take";
            }
            catch (Exception error)
            {
                this.arm();
                this.message = "ERROR: " + error.getMessage();
            }
        }
        else if (this.deadline != 0L)
        {
            long remaining = this.deadline - System.nanoTime();

            this.message = "Starting in " + Math.max(0, (int) Math.ceil(remaining / 1000000000D));

            if (remaining <= 0L)
            {
                this.startPlayback();
            }
        }
        else if (this.playing)
        {
            this.message = "Recording · " + this.recorder.getTime() / 1000F + " s · start " + this.originCursor / 20F + " s";
        }
        else if (this.recorder.isReady() && this.message.equals("Opening microphone…"))
        {
            this.message = "Ready · start " + this.originCursor / 20F + " s";
        }

        boolean busy = this.playing || this.finishing || this.deadline != 0L;

        this.microphone.setEnabled(!this.closed && !busy);
        this.rate.setEnabled(!this.closed && !busy);
        this.countdown.setEnabled(!this.closed && !busy);
        this.name.setEnabled(!this.closed && !busy);
        this.record.setEnabled(!this.closed && !this.finishing && (busy || this.recorder.isReady()));
        this.record.label = IKey.raw(this.playing ? "Stop & save" : this.deadline != 0L ? "Cancel count-in" : "Record");
        this.discard.setEnabled(busy && !this.finishing);
        if (!this.status.getText().get().equals(this.message))
        {
            this.status.text(this.message);
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.updateRecording();
        super.render(context);
    }

    private void renderMeter(UIContext context, UIElement meter)
    {
        int x = meter.area.x;
        int y = meter.area.y;
        int width = meter.area.w;
        float volume = this.recorder.getVolume();

        context.batcher.box(x, y, x + width, y + 10, 0xFF272A30);
        context.batcher.box(x, y, x + (int) (width * Math.min(1F, volume)), y + 10,
            volume >= 0.98F ? 0xFFDC6068 : 0xFF54BFA7);
        this.waveform = this.recorder.getWaveform(this.waveform);

        for (int px = 0; px < width; px++)
        {
            int sample = Math.min(this.waveform.length - 1, px * this.waveform.length / width);
            int half = (int) (this.waveform[sample] * 9F);

            context.batcher.box(x + px, y + 23 - half, x + px + 1, y + 23 + half, 0xFF54BFA7);
        }
    }

    @Override
    public void onAddedToTree(UIElement root) {}

    @Override
    public void onRemovedFromTree(UIElement root)
    {
        this.shutdown();
    }
}
