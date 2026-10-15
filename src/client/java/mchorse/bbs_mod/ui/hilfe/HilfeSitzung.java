package mchorse.bbs_mod.ui.hilfe;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;

/**
 * The one video that plays, whatever shows it (the Help window now, the mini-player later): one
 * browser, so the picture runs on when it changes place. Keeps where the guide stood and how much
 * of each chapter was seen (state_help_position / state_help_watched).
 */
final class HilfeSitzung
{
    private static HilfeSitzung aktiv;

    final HilfeKanal kanal;
    final Guides.Guide guide;
    final double[] gesehen;
    private long gespeichert;
    double tempo = 1D;
    /** The one-time note before the guide is up; the player is made when it is answered. */
    boolean vorspann;

    static HilfeSitzung aktiv()
    {
        return aktiv;
    }

    /** The guide's session, made on first use; a running one keeps playing. */
    static HilfeSitzung guide(Guides.Guide guide, int sekunde)
    {
        if (aktiv != null && aktiv.guide == guide)
        {
            return aktiv;
        }

        beenden();
        aktiv = new HilfeSitzung(guide, sekunde);

        return aktiv;
    }

    /** Nothing shows the video while the dashboard is closed: it must not play on unseen (and
     * unstoppable — measured 2026-10-14, it kept talking). It waits paused where it was. */
    static void anhalten()
    {
        if (aktiv != null && aktiv.spielt())
        {
            aktiv.kanal.pause();
            aktiv.speichern();
        }
    }

    static void beenden()
    {
        if (aktiv != null)
        {
            aktiv.speichern();
            aktiv.kanal.close();
            aktiv = null;
        }
    }

    private HilfeSitzung(Guides.Guide guide, int sekunde)
    {
        /* The browser plays its sound itself, past Minecraft's: where the game has no sound device
         * (ALSOFT_DRIVERS=null, every test instance) the guide stays silent too. 2026-10-14 a test
         * played McHorse's voice on the owner's speakers. */
        this.vorspann = !EditorZustaende.hilfeVorspannGesehen.get();
        this.kanal = new HilfeKanal(guide.video(), sekunde, "null".equals(System.getenv("ALSOFT_DRIVERS")), this.vorspann);
        this.guide = guide;
        this.gesehen = new double[guide.kapitel().size()];

        /* "bbs2:44,61,0,..." -- only for the guide it was written for */
        String stand = EditorZustaende.hilfeGesehen.get();
        String[] werte = stand.startsWith(guide.id() + ":") ? stand.substring(guide.id().length() + 1).split(",") : new String[0];

        for (int i = 0; i < werte.length && i < this.gesehen.length; i++)
        {
            try
            {
                this.gesehen[i] = Double.parseDouble(werte[i]);
            }
            catch (NumberFormatException e)
            {
                this.gesehen[i] = 0D;
            }
        }
    }

    void vorspannGesehen()
    {
        this.vorspann = false;
        EditorZustaende.hilfeVorspannGesehen.set(true);
        this.kanal.starten();
    }

    boolean spielt()
    {
        return this.kanal.zustand == 1 || this.kanal.zustand == 3;
    }

    /** Each frame something shows the video: seen seconds per chapter, the position every 2 s. */
    void tick()
    {
        if (this.kanal.zustand == 1 && this.kanal.video.equals(this.guide.video()))
        {
            int i = this.guide.kapitelBei(this.kanal.zeit);

            this.gesehen[i] = Math.max(this.gesehen[i], this.kanal.zeit - this.guide.kapitel().get(i).sekunde());
        }

        if (System.currentTimeMillis() - this.gespeichert > 2000L)
        {
            this.speichern();
        }
    }

    private void speichern()
    {
        this.gespeichert = System.currentTimeMillis();

        if (!this.kanal.video.equals(this.guide.video()))
        {
            return;
        }

        StringBuilder text = new StringBuilder(this.guide.id()).append(":");

        for (int i = 0; i < this.gesehen.length; i++)
        {
            text.append(i == 0 ? "" : ",").append(Math.round(this.gesehen[i]));
        }

        EditorZustaende.hilfeGesehen.set(text.toString());
        if (this.guide == Guides.alle().get(0))
        {
            EditorZustaende.hilfeStelle.set((int) this.kanal.zeit);
        }
    }

    /** The chapter about the editor the user is in (spec 2.3), or -1. */
    int kontextKapitel()
    {
        return kontextKapitel(this.guide);
    }

    static int kontextKapitel(Guides.Guide guide)
    {
        String thema = thema();

        for (int i = 0; thema != null && i < guide.kapitel().size(); i++)
        {
            if (thema.equals(guide.kapitel().get(i).thema()))
            {
                return i;
            }
        }

        return -1;
    }

    /** The editor in words: the film's timeline in camera clips or replays, blocks, morphing. */
    static String thema()
    {
        UIDashboard dashboard = BBSModClient.getDashboard();
        UIDashboardPanel panel = dashboard == null ? null : dashboard.getPanels().panel;

        if (panel instanceof UIFilmPanel film)
        {
            return film.cameraEditor != null && film.cameraEditor.isVisible() ? "camera" : "replays";
        }

        return panel instanceof UIModelBlockPanel ? "model_blocks" : panel instanceof UIMorphingPanel ? "forms" : null;
    }
}
