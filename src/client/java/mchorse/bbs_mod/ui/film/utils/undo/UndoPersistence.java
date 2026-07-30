// Coded by Afrobama/Claude — verbindet den Undo-Manager des Films mit dem UndoJournal
package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Speichert die Undo-Historie eines Films beim Speichern und stellt sie beim Oeffnen wieder her.
 *
 * <p>Das Dateiformat macht {@link UndoJournal}; diese Klasse beantwortet nur die drei Fragen
 * drumherum: <b>wohin</b>, <b>ob ueberhaupt</b>, und <b>wie kommen die Eintraege in den
 * {@link UndoManager} zurueck, ohne dass er sie fuer neue Bearbeitungen haelt</b>.</p>
 *
 * <p><b>Ablage:</b> {@code <welt>/bbs/undo/<filmId>.undo}, neben dem Film-Ordner. Damit ist "pro
 * Welt" automatisch erfuellt, und ein geloeschter Welt-Ordner nimmt seine Undo-Dateien mit.</p>
 *
 * <p><b>Der Hash kommt aus der Film-DATEI, nicht aus dem Objekt im Speicher.</b> Beim Speichern
 * werden die gerade geschriebenen Bytes gelesen, beim Laden dieselbe Datei — damit stimmt der Hash
 * per Konstruktion ueberein. Waere er stattdessen aus {@code film.toData()} gebildet, haetten schon
 * eine andere Schluesselreihenfolge in der Map oder ein geaenderter Standardwert gereicht, damit er
 * NIE mehr passt: die Historie wuerde dann bei jedem Oeffnen stumm verworfen, und das Feature waere
 * kaputt, ohne dass irgendwo ein Fehler auftaucht.</p>
 */
public final class UndoPersistence
{
    private UndoPersistence() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/undo-persistence");

    private static final String EXTENSION = ".undo";

    /**
     * Nur bei eigener Welt. Auf einem Server liegen die Filme dort (siehe
     * {@code ContentType.getFilmsRepository}), es gibt also keine lokale Film-Datei zum Hashen und
     * der Welt-Ordner ist nicht unserer.
     */
    private static boolean aktiv()
    {
        if (BBSSettings.editorPersistentUndo == null || !BBSSettings.editorPersistentUndo.get())
        {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        return mc != null && mc.hasSingleplayerServer();
    }

    /** {@code null} heisst: fuer diesen Film gibt es keinen Ablageort. */
    public static File datei(String filmId)
    {
        if (filmId == null || filmId.isEmpty())
        {
            return null;
        }

        File filme = BBSMod.getFilms() == null ? null : BBSMod.getFilms().getFolder();

        if (filme == null || filme.getParentFile() == null)
        {
            return null;
        }

        return new File(new File(filme.getParentFile(), "undo"), filmId + EXTENSION);
    }

    /** Hash ueber die Film-Datei auf Platte. {@code null} heisst: kein verlaesslicher Bezugspunkt. */
    private static byte[] filmHash(String filmId)
    {
        try
        {
            File film = BBSMod.getFilms() == null ? null : BBSMod.getFilms().getFile(filmId);

            if (film == null || !film.isFile())
            {
                return null;
            }

            return UndoJournal.hash(Files.readAllBytes(film.toPath()));
        }
        catch (Throwable t)
        {
            LOG.warn("Film-Datei fuer den Undo-Hash nicht lesbar: {}", filmId, t);

            return null;
        }
    }

    /**
     * Schreibt die Historie. Aufzurufen NACH dem Speichern des Films — vorher stuende in der Datei
     * noch der alte Stand, und der Hash wuerde beim naechsten Oeffnen nicht passen.
     */
    public static void speichern(String filmId, UndoManager<ValueGroup> manager)
    {
        if (!aktiv() || manager == null)
        {
            return;
        }

        File datei = datei(filmId);

        if (datei == null)
        {
            return;
        }

        if (manager.getTotalUndos() == 0)
        {
            /* Nichts zu sichern. Eine noch herumliegende alte Datei jetzt stehen zu lassen waere
             * schlechter als sie zu loeschen: sie gehoert zu einem Stand, den es nicht mehr gibt. */
            datei.delete();

            return;
        }

        byte[] hash = filmHash(filmId);

        if (hash == null)
        {
            return;
        }

        UndoJournal.write(datei, hash, manager.getUndos(), manager.getCurrentUndoIndex(), grenze());
    }

    /**
     * Stellt die Historie wieder her. Aufzurufen NACH dem Fuellen des Panels — der
     * {@link UndoManager} wird beim Fuellen neu angelegt, ein frueherer Aufruf schriebe in den alten.
     */
    public static void laden(String filmId, UndoManager<ValueGroup> manager)
    {
        if (!aktiv() || manager == null)
        {
            return;
        }

        if (manager.getTotalUndos() != 0)
        {
            /* Der Manager ist beim Fuellen frisch. Ist er es nicht, wurde schon geladen — ein
             * zweiter Durchlauf wuerde die Historie verdoppeln. */
            return;
        }

        File datei = datei(filmId);
        byte[] hash = filmHash(filmId);

        if (datei == null || hash == null)
        {
            return;
        }

        UndoJournal.Historie historie = UndoJournal.read(datei, hash);

        if (historie.istLeer())
        {
            return;
        }

        einsetzen(manager, historie);

        LOG.info("Undo-Historie wiederhergestellt: {} eintraege, cursor {} ({})",
            historie.undos.size(), historie.position, filmId);
    }

    /**
     * Setzt Eintraege UND Cursor-Position wieder ein.
     *
     * <p>Der Trick steckt in der Zweiteilung. {@code pushUndo} erhoeht die Position bei jedem
     * Aufruf, waere also nur fuer eine Historie richtig, bei der man ganz am Ende steht. Hatte der
     * Nutzer vor dem Beenden zurueckgedreht, muessen die Eintraege HINTER dem Cursor zwar da sein
     * (sonst gibt es kein Redo), duerfen die Position aber nicht mitschieben. Deshalb:</p>
     *
     * <ul>
     *   <li>alles bis zum Cursor per {@code pushUndo} — das setzt die Position mit;</li>
     *   <li>alles dahinter direkt in die Liste aus {@code getUndos()} — sie ist die LEBENDE Liste
     *       des Managers (der Getter reicht {@code this.undos} durch), das Anhaengen laesst die
     *       Position also unberuehrt. {@code redo()} findet die Eintraege danach ueber
     *       {@code position + 1 < undos.size()}.</li>
     * </ul>
     *
     * <p>Das ist bewusst so gebaut, statt dem {@code UndoManager} einen Setter zu spendieren: eine
     * neue Methode an einer bereits geladenen Klasse kostet einen Neustart, dieser Weg nicht.</p>
     */
    private static void einsetzen(UndoManager<ValueGroup> manager, UndoJournal.Historie historie)
    {
        List<IUndo<ValueGroup>> liste = manager.getUndos();

        for (int i = 0; i < historie.undos.size(); i++)
        {
            IUndo<ValueGroup> undo = historie.undos.get(i);

            /* Ohne das wuerde pushUndo zwei aufeinanderfolgende Aenderungen AM SELBEN PFAD zu einem
             * Eintrag verschmelzen (ValueChangeUndo.isMergeable). Waehrend der Bearbeitung ist das
             * gewollt, beim Wiedereinsetzen nicht: die Schritte sind fertig und einzeln. */
            undo.noMerging();

            if (i <= historie.position)
            {
                manager.pushUndo(undo);
            }
            else
            {
                liste.add(undo);
            }
        }
    }

    /** Wie viele Schritte hoechstens auf die Platte gehen. */
    private static int grenze()
    {
        return BBSSettings.editorPersistentUndoLimit == null
            ? 100
            : BBSSettings.editorPersistentUndoLimit.get();
    }
}
