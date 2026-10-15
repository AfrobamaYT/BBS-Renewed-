package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Voice-over while recording a replay (switch in the voice-over window). The microphone opens
 * when the replay recording starts, so the count-in covers the device start; capture begins on
 * the first tick the replay stores, and stopping the replay saves the take at exactly that tick.
 * Like the voice-over window there is no latency calibration.
 */
public final class VoiceoverBeiReplay
{
    private static OpenALRecorder mic;
    private static Film film;
    private static int abTick = -1;
    private static boolean gestoppt;

    private VoiceoverBeiReplay()
    {}

    public static void start(Film aufnahme)
    {
        verwerfen();

        if (!EditorZustaende.voiceoverBeiReplay.get() || aufnahme == null)
        {
            return;
        }

        film = aufnahme;
        mic = new OpenALRecorder(UIAudioRecorder.lastDevice, EditorZustaende.voiceoverRate.get());

        Thread worker = new Thread(mic, "BBS voice-over with replay");

        worker.setDaemon(true);
        worker.start();
    }

    /** True from the start of a replay recording with voice-over until it stops (count-in included). */
    public static boolean nimmtAuf()
    {
        return mic != null && !gestoppt;
    }

    /** Called before {@link Recorder#update()}: the tick checked here is the one it stores next. */
    public static void vorDemTick(Recorder recorder)
    {
        if (mic == null || gestoppt)
        {
            return;
        }

        if (mic.getFailure() != null)
        {
            melden("Voice-over failed: " + mic.getFailure().getMessage(), true);
            verwerfen();
        }
        else if (abTick < 0 && !recorder.hasNotStarted() && recorder.getTick() >= recorder.aufnahmeAb && mic.isReady())
        {
            mic.begin();
            abTick = recorder.getTick();
            melden("Voice-over recording", false);
        }
    }

    public static void stopp()
    {
        if (mic == null)
        {
            return;
        }

        if (abTick < 0)
        {
            verwerfen();

            return;
        }

        mic.stop();
        gestoppt = true;
    }

    /** Every client tick: once the capture thread has finished, the take goes into the film. */
    public static void update()
    {
        if (mic == null || !gestoppt || !mic.isDone())
        {
            return;
        }

        try
        {
            if (mic.getFailure() != null)
            {
                throw new IllegalStateException(mic.getFailure().getMessage());
            }

            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

            VoiceoverTake.save(panel, film, abTick, "Voice-over", null, mic.getResult());
            melden("Voice-over saved at " + abTick / 20F + " s", false);
        }
        catch (Exception fehler)
        {
            melden("Voice-over not saved: " + fehler.getMessage(), true);
        }

        mic = null;
        film = null;
        abTick = -1;
        gestoppt = false;
    }

    private static void verwerfen()
    {
        if (mic != null)
        {
            mic.cancel();
        }

        mic = null;
        film = null;
        abTick = -1;
        gestoppt = false;
    }

    private static void melden(String text, boolean fehler)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            if (fehler) menu.context.notifyError(IKey.raw(text));
            else menu.context.notifyInfo(IKey.raw(text));
        }
        else if (Minecraft.getInstance().player != null)
        {
            Minecraft.getInstance().gui.setOverlayMessage(Component.literal(text), false);
        }
    }
}
