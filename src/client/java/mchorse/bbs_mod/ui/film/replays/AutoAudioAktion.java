package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.autoaudio.AutoAudio;
import mchorse.bbs_mod.film.autoaudio.AutoAudioZuendstellen;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;

import java.util.List;

/**
 * Der Kontextmenue-Befehl "Auto Audio" (#68) — aus {@code UIReplayList} herausgeloest, die
 * Datei steht ueber der 800-Zeilen-Grenze und darf nur noch schrumpfen.
 *
 * <p>⛔ Ausdruecklicher befehl, nie automatisch. Die erzeugten clips sind echte
 * {@code AudioClip}s und landen damit im fertigen video — das darf nie nebenbei passieren.
 * Begruendung vollstaendig in {@link AutoAudio}.</p>
 *
 * <p>Meldet auch die faelle, in denen NICHTS passiert ist, und warum: kein partikel-effekt
 * im replay, kein bekannter ton zum partikel, keine abspielbare datei, oder der effekt
 * zuendet nie (dauerhaft pausiert). Ein stilles "nichts passiert" waere hier der teuerste
 * ausgang, weil der nutzer es nicht von "kein ton noetig" unterscheiden kann.</p>
 *
 * <p>Die sprachschluessel liegen ABSICHTLICH hier statt in {@code UIKeys} — genau wie bei
 * {@code UIAudioClip}: UIKeys wird von jedem parallel laufenden feature-zweig angefasst,
 * ein gleichzeitiger schreibzugriff zerreisst die datei. {@code L10n.lang} faellt ohne
 * uebersetzung auf den schluesselnamen zurueck.</p>
 */
public final class AutoAudioAktion
{
    public static final IKey AUTO_AUDIO = L10n.lang("bbs.ui.film.replays.context.auto_audio");
    private static final IKey DONE = L10n.lang("bbs.ui.film.replays.context.auto_audio_done");
    private static final IKey NO_EMITTER = L10n.lang("bbs.ui.film.replays.context.auto_audio_no_emitter");
    private static final IKey NEVER_FIRES = L10n.lang("bbs.ui.film.replays.context.auto_audio_never_fires");
    private static final IKey UNKNOWN = L10n.lang("bbs.ui.film.replays.context.auto_audio_unknown");
    private static final IKey NO_FILE = L10n.lang("bbs.ui.film.replays.context.auto_audio_no_file");
    private static final IKey CAPPED = L10n.lang("bbs.ui.film.replays.context.auto_audio_capped");

    private AutoAudioAktion()
    {}

    public static void ausfuehren(UIFilmPanel panel, List<Replay> selected, UIContext context)
    {
        Film film = panel == null ? null : panel.getData();

        if (film == null || context == null || selected.isEmpty())
        {
            return;
        }

        AutoAudio.Bericht bericht = AutoAudio.anwenden(film, selected);

        /* Reihenfolge wie bei jedem anderen programmatischen film-schreiber: erst den
         * undo-eintrag abgeben, den die wert-benachrichtigungen gesammelt haben, dann
         * speichern. Die kamera-zeitleiste
         * liest film.camera pro frame neu, sie braucht keinen anstoss. */
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
        }

        panel.save();

        if (bericht.ohneEmitter())
        {
            context.notifyError(NO_EMITTER);

            return;
        }

        if (!bericht.leer())
        {
            context.notifySuccess(DONE.format(bericht.angelegt(), bericht.entfernt()));
        }
        else if (bericht.ohneTon().isEmpty() && bericht.ohneDatei().isEmpty())
        {
            context.notifyInfo(NEVER_FIRES);
        }

        if (!bericht.ohneTon().isEmpty())
        {
            context.notifyError(UNKNOWN.format(String.join(", ", bericht.ohneTon())));
        }

        if (!bericht.ohneDatei().isEmpty())
        {
            context.notifyError(NO_FILE.format(String.join(", ", bericht.ohneDatei())));
        }

        if (bericht.abgeschnitten())
        {
            context.notifyError(CAPPED.format(AutoAudioZuendstellen.MAX_ZUENDSTELLEN));
        }
    }
}
