package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scrub-Streifen eines Films fuer die Kachel-Liste: {@link #FELDER} gleichmaessig ueber die
 * Filmdauer verteilte Bilder in EINEM PNG — die Form von Storyboard/Sprite-Sheet bei YouTube,
 * Mux oder Jellyfin-Trickplay: der x-Anteil der Maus waehlt das Feld.
 *
 * <p>Nutzerwunsch 2026-10-13, uebernommen aus Claude-Design-Durchgang 1: „wenn man ins
 * Dashboard reingeht und die Filme sieht, konnte man den Inhalt sehen, wenn man mit der Maus
 * scrubbt".</p>
 *
 * <p>⛔ QUELLE: der Film selbst, wenn er durch die Filmkamera laeuft — Abspielen im Editor
 * ohne Flug, oder Video-Export. Ein nie abgespielter Film hat keinen Streifen, seine Kachel
 * zeigt weiter das Standbild. Offscreen zu beliebigen Ticks rendern (Unreal Sequencer) hiesse,
 * den ganzen Filmzustand je Tick auszuwerten und zurueckzusetzen.</p>
 *
 * <p>Leere Felder fuellt das Schreiben mit dem naechsten echten Bild — die Liste braucht so
 * keine Maske. Welche Felder echt sind und fuer welche Dauer, steht in {@code .strip.properties};
 * aendert sich die Dauer, beginnt der Streifen neu. Die Datei-Seite ist {@link FilmStreifenBild}.</p>
 */
public final class FilmStreifen
{
    public static final int FELDER = 20;
    public static final int B = 192;
    public static final int H = 108;

    private static final Logger LOG = LogManager.getLogger("bbs/streifen");

    /** Ein Schreiber: verkleinern und PNG schreiben gehoeren nicht in den Render-Thread. */
    private static final ExecutorService SCHREIBER = Executors.newSingleThreadExecutor((r) ->
    {
        Thread t = new Thread(r, "bbs-filmstreifen");

        t.setDaemon(true);

        return t;
    });

    /* Render-Thread. */
    private static int letztesFeld = -1;
    private static volatile boolean unterwegs;

    private FilmStreifen()
    {}

    public static String pfad(String film)
    {
        return "film_thumbnails/" + film + ".strip.png";
    }

    /** Link auf den Streifen, oder {@code null}, wenn der Film noch keinen hat. */
    public static Link link(String film)
    {
        return datei(film, ".strip.png").isFile() ? Link.assets(pfad(film)) : null;
    }

    static File datei(String film, String endung)
    {
        return new File(BBSMod.getAssetsFolder(), "film_thumbnails/" + film + endung);
    }

    /**
     * Je Bild aus der Filmvorschau: betritt der Cursor beim Abspielen ein neues Feld, wird das
     * gerade gezeigte Bild dorthin gelegt. Asynchron zurueckgelesen — kein Halt im Bild.
     */
    public static void erfassen(UIFilmPanel panel, Texture texture)
    {
        Film data = panel.getData();
        boolean durchKamera = panel.recorder.isRecording() || (panel.isRunning() && !panel.isFlying());

        if (data == null || data.getId() == null || data.getId().isEmpty() || texture == null || !durchKamera)
        {
            letztesFeld = -1;

            return;
        }

        int laenge = data.camera.calculateDuration();

        if (laenge <= 0 || unterwegs)
        {
            return;
        }

        int feld = Math.min(FELDER - 1, Math.max(0, panel.getCursor() * FELDER / laenge));

        if (feld == letztesFeld)
        {
            return;
        }

        letztesFeld = feld;
        unterwegs = true;

        String id = data.getId();
        int w = texture.gpu().getWidth(0);
        int h = texture.gpu().getHeight(0);

        GpuReadback.requestRaw(texture.gpu(), new GpuReadback.Region(0, 0, w, h)).whenReady(
            (raw) -> SCHREIBER.execute(() ->
            {
                try
                {
                    FilmStreifenBild.schreiben(id, laenge, feld, raw);
                }
                finally
                {
                    unterwegs = false;
                }
            }),
            (fehler) ->
            {
                unterwegs = false;
                LOG.warn("Streifenbild konnte nicht zurueckgelesen werden", fehler);
            });
    }
}
