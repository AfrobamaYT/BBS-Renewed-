// Coded by Afrobama/Claude — Undo-Historie, die Neustart und Crash ueberlebt
package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Liest und schreibt die Undo-Historie eines Films auf Platte.
 *
 * <p>Aufbau nach Vims {@code undofile} (https://vimhelp.org/undo.txt.html), das dasselbe Problem
 * seit Jahren loest — drei Entscheidungen sind von dort uebernommen:</p>
 *
 * <ul>
 *   <li><b>Identitaetspruefung per Hash.</b> Im Kopf steht ein Hash des Film-Datenstands. Passt er
 *       beim Laden nicht, wird die Historie VERWORFEN statt auf einen fremden Stand angewendet.
 *       Ohne das wuerde ein ausserhalb geaenderter Film beim ersten Undo still zerlegt.</li>
 *   <li><b>Magic und Version im Kopf</b>, damit fremde oder aeltere Dateien erkannt und abgelehnt
 *       werden, statt als Muell interpretiert zu werden.</li>
 *   <li><b>Geschrieben wird beim Speichern des Films</b>, nicht bei jedem Edit. Das ist Sache des
 *       Aufrufers; diese Klasse macht nur das Dateiformat.</li>
 * </ul>
 *
 * <p><b>Crash-Toleranz</b> (aus der Journal-Literatur, https://arxiv.org/pdf/1804.00701): jeder
 * Eintrag traegt ein Sentinel, seine Laenge und eine CRC32 ueber die Nutzdaten. Beim Lesen wird das
 * Ende der Historie AUS DEN DATEN ABGELEITET, statt einem gespeicherten Zaehler zu vertrauen — beim
 * ersten Eintrag, der nicht vollstaendig und pruefsummen-rein ist, endet die Historie. Ein Absturz
 * mitten im Schreiben kostet damit genau den letzten Eintrag, nicht die Datei.</p>
 *
 * <p>Zusaetzlich wird ueber eine Nachbardatei geschrieben und erst danach umbenannt, damit ein
 * Absturz waehrend des Schreibens nicht die bereits gueltige alte Historie zerstoert.</p>
 *
 * <p>Serialisiert werden {@link ValueChangeUndo} und {@link CompoundUndo} daraus — zusammen ist das
 * die VOLLSTAENDIGE Undo-Historie der Film-Bearbeitung: {@code UIFormUndoHandler.submitUndo} legt
 * genau diese beiden Arten in den Manager (Zeilen 157 und 161), und kein anderer Aufrufer schreibt
 * in den Undo-Manager des Films. Beide tragen nur BBS-eigene Datentypen ({@link DataPath},
 * {@link BaseType}, {@link MapType}), also genau das, was BBS ohnehin in die Film-{@code .dat}
 * schreibt. Die Undo-Arten des Pixel-Editors und der Textfelder haengen an eigenen, getrennten
 * Managern und kommen hier nie an.</p>
 */
public final class UndoJournal
{
    private UndoJournal() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/undo-journal");

    /** "BBSU" — steht am Dateianfang, damit fremde Dateien sofort auffallen. */
    private static final int MAGIC = 0x42425355;

    /**
     * Beim Format-Bruch hochzaehlen; aeltere Dateien werden dann verworfen statt fehlgedeutet.
     *
     * <p>2 = Kopf traegt zusaetzlich die Cursor-Position, Eintraege koennen verschachtelt sein
     * ({@link CompoundUndo}).</p>
     */
    private static final int VERSION = 2;

    /** Steht vor jedem Eintrag. Fehlt es, ist die Historie an dieser Stelle zu Ende. */
    private static final int ENTRY_SENTINEL = 0x424E5452;

    /** Obergrenze, damit eine kaputte Laengenangabe keinen riesigen Puffer anfordert. */
    private static final int MAX_ENTRY_BYTES = 32 * 1024 * 1024;

    /* Schluessel im serialisierten Eintrag. Kurz gehalten, das steht pro Eintrag auf der Platte. */
    private static final String KEY_TYPE = "t";
    private static final String KEY_PATH = "p";
    private static final String KEY_FOLDER = "pf";
    private static final String KEY_OLD = "o";
    private static final String KEY_NEW = "n";
    private static final String KEY_UI_BEFORE = "ub";
    private static final String KEY_UI_AFTER = "ua";
    private static final String KEY_CHILDREN = "c";
    private static final String TYPE_VALUE_CHANGE = "vc";
    private static final String TYPE_COMPOUND = "cu";

    /**
     * Gelesene Historie samt Cursor-Position.
     *
     * <p>Die Position ist der Index des zuletzt ANGEWENDETEN Eintrags, {@code -1} heisst "ganz
     * zurueckgedreht" — dieselbe Zaehlweise wie {@code UndoManager.getCurrentUndoIndex()}.</p>
     */
    public static final class Historie
    {
        public final List<IUndo<ValueGroup>> undos;
        public final int position;

        public Historie(List<IUndo<ValueGroup>> undos, int position)
        {
            this.undos = undos;
            this.position = position;
        }

        public boolean istLeer()
        {
            return this.undos.isEmpty();
        }
    }

    /**
     * Hash ueber den Datenstand eines Films. Genau dieser Wert landet im Kopf der Undo-Datei und
     * entscheidet beim Laden, ob die Historie noch zu diesem Film gehoert.
     */
    public static byte[] hash(byte[] filmData)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(filmData == null ? new byte[0] : filmData);
        }
        catch (Exception e)
        {
            /* SHA-256 gibt es auf jeder JVM; faellt es doch aus, lieber ein leerer Hash als eine
             * Ausnahme im Speicherpfad. Ein leerer Hash passt auf nichts und verwirft die Historie. */
            LOG.warn("SHA-256 nicht verfuegbar, Undo-Historie wird nicht wiederverwendet", e);

            return new byte[0];
        }
    }

    /**
     * Schreibt die Historie. Erst in eine Nachbardatei, dann umbenennen — ein Absturz mittendrin
     * laesst die vorherige gueltige Datei unangetastet.
     *
     * @param position Cursor-Position wie {@code UndoManager.getCurrentUndoIndex()}.
     * @param limit    Hoechstzahl gespeicherter Eintraege. Laeuft die Historie ueber, faellt der
     *                 AELTESTE Eintrag heraus — genau wie im {@code UndoManager} selbst. Die
     *                 Cursor-Position wandert mit, damit sie auf denselben Eintrag zeigt wie vorher.
     */
    public static void write(File file, byte[] filmHash, List<IUndo<ValueGroup>> undos,
        int position, int limit)
    {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");

        try
        {
            if (file.getParentFile() != null)
            {
                file.getParentFile().mkdirs();
            }

            List<IUndo<ValueGroup>> zuSchreiben = undos;
            int cursor = position;

            if (limit > 0 && undos.size() > limit)
            {
                int ueberzaehlig = undos.size() - limit;

                zuSchreiben = undos.subList(ueberzaehlig, undos.size());

                /* Der Cursor zaehlt Indizes in DIESER Liste. Faellt sein Eintrag mit heraus, ist
                 * "ganz zurueckgedreht" (-1) die einzige ehrliche Antwort: die Schritte davor gibt
                 * es nicht mehr, also darf auch nicht so getan werden, als stuende man mittendrin. */
                cursor = Math.max(-1, cursor - ueberzaehlig);
            }

            /* Erst serialisieren, dann schreiben: der Cursor steht im KOPF, muss also schon
             * feststehen, bevor das erste Byte rausgeht. Faellt unterwegs ein Eintrag aus, ruecken
             * alle dahinter auf — dann muss der Cursor mit, sonst zeigt er nach dem Laden auf einen
             * anderen Schritt als vorher. */
            List<byte[]> nutzdaten = new ArrayList<>();
            int index = -1;

            for (IUndo<ValueGroup> undo : zuSchreiben)
            {
                index += 1;

                MapType data = toData(undo);

                if (data == null)
                {
                    /* Sollte nicht vorkommen: die Film-Historie kennt nur ValueChangeUndo und
                     * CompoundUndo daraus (UIFormUndoHandler.submitUndo, Zeilen 157/161). Wenn hier
                     * doch etwas anderes liegt, WUERDE stilles Auslassen die Historie um einen
                     * Schritt verkuerzen — deshalb laut melden statt schlucken. */
                    LOG.warn("Undo-Eintrag vom Typ {} ist nicht speicherbar und faellt aus der Historie",
                        undo == null ? "null" : undo.getClass().getName());

                    if (index <= cursor)
                    {
                        cursor -= 1;
                    }

                    continue;
                }

                nutzdaten.add(DataStorageUtils.writeToBytes(data));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            writeInt(out, MAGIC);
            writeInt(out, VERSION);
            writeInt(out, filmHash.length);
            out.write(filmHash);
            writeInt(out, cursor);

            int geschrieben = nutzdaten.size();

            for (byte[] payload : nutzdaten)
            {
                CRC32 crc = new CRC32();

                crc.update(payload);

                writeInt(out, ENTRY_SENTINEL);
                writeInt(out, payload.length);
                writeInt(out, (int) crc.getValue());
                out.write(payload);
            }

            Files.write(temp.toPath(), out.toByteArray());
            Files.move(temp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            LOG.debug("Undo-Historie geschrieben: {} eintraege -> {}", geschrieben, file.getName());
        }
        catch (Throwable t)
        {
            LOG.warn("Undo-Historie konnte nicht geschrieben werden: {}", file, t);

            temp.delete();
        }
    }

    /**
     * Liest die Historie zurueck. Leere Liste heisst immer "keine brauchbare Historie" — falscher
     * Film, fremdes Format, oder abgerissen. Nie eine Ausnahme nach oben: eine unlesbare
     * Undo-Datei darf das Oeffnen eines Films nicht verhindern.
     */
    public static Historie read(File file, byte[] filmHash)
    {
        List<IUndo<ValueGroup>> undos = new ArrayList<>();
        int position = -1;

        if (file == null || !file.isFile())
        {
            return new Historie(undos, -1);
        }

        try
        {
            ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(file.toPath()));

            if (buffer.remaining() < 12 || buffer.getInt() != MAGIC)
            {
                LOG.debug("{} ist keine Undo-Datei, wird ignoriert", file.getName());

                return new Historie(undos, -1);
            }

            int version = buffer.getInt();

            if (version != VERSION)
            {
                LOG.debug("Undo-Datei hat Version {}, erwartet {} — wird ignoriert", version, VERSION);

                return new Historie(undos, -1);
            }

            int hashLength = buffer.getInt();

            if (hashLength < 0 || hashLength > buffer.remaining())
            {
                return new Historie(undos, -1);
            }

            byte[] storedHash = new byte[hashLength];

            buffer.get(storedHash);

            if (!Arrays.equals(storedHash, filmHash))
            {
                /* Der Film ist seit dem Schreiben ein anderer geworden. Die Historie jetzt
                 * anzuwenden wuerde Werte auf einen fremden Stand zurueckdrehen — das ist genau
                 * der Fall, den Vims Hash-Pruefung verhindert. */
                LOG.info("Film hat sich seit der letzten Sitzung geaendert, Undo-Historie wird verworfen ({})",
                    file.getName());

                return new Historie(undos, -1);
            }

            if (buffer.remaining() < 4)
            {
                return new Historie(undos, -1);
            }

            position = buffer.getInt();

            /* Ende der Historie aus den Daten ableiten: beim ersten unvollstaendigen oder
             * pruefsummen-fehlerhaften Eintrag ist Schluss. Ein abgerissener letzter Eintrag
             * kostet damit nur sich selbst. */
            while (buffer.remaining() >= 12)
            {
                int start = buffer.position();

                if (buffer.getInt() != ENTRY_SENTINEL)
                {
                    buffer.position(start);
                    break;
                }

                int length = buffer.getInt();
                int expectedCrc = buffer.getInt();

                if (length < 0 || length > MAX_ENTRY_BYTES || length > buffer.remaining())
                {
                    break;
                }

                byte[] payload = new byte[length];

                buffer.get(payload);

                CRC32 crc = new CRC32();

                crc.update(payload);

                if ((int) crc.getValue() != expectedCrc)
                {
                    LOG.info("Undo-Eintrag {} ist beschaedigt, Historie endet hier ({})",
                        undos.size(), file.getName());
                    break;
                }

                IUndo<ValueGroup> undo = fromData(DataStorageUtils.readFromBytes(payload));

                if (undo != null)
                {
                    undos.add(undo);
                }
            }
        }
        catch (Throwable t)
        {
            LOG.warn("Undo-Historie konnte nicht gelesen werden: {}", file, t);
        }

        /* Die Position gegen die TATSAECHLICH gelesene Eintragszahl klemmen. Riss die Datei mitten
         * im Schreiben ab, steht im Kopf noch der alte Cursor — ein Wert jenseits des letzten
         * gelesenen Eintrags wuerde den UndoManager in einen Zustand setzen, den seine eigene
         * Zaehlung nie erzeugen kann. */
        return new Historie(undos, Math.max(-1, Math.min(position, undos.size() - 1)));
    }

    /** {@code null} heisst: diese Undo-Art wird nicht persistiert. */
    private static MapType toData(IUndo<ValueGroup> undo)
    {
        if (undo instanceof CompoundUndo)
        {
            /* Ein Bearbeitungsschritt, der mehrere Werte auf einmal geaendert hat — beim Ziehen
             * eines Keyframes der Normalfall (UIFormUndoHandler.submitUndo, Zeile 161). Ohne diesen
             * Zweig fiele so ein Schritt aus der gespeicherten Historie heraus, und das Zurueckdrehen
             * wuerde ihn stillschweigend ueberspringen. */
            List<IUndo<ValueGroup>> children = ((CompoundUndo<ValueGroup>) undo).getUndos();
            ListType list = new ListType();

            for (IUndo<ValueGroup> child : children)
            {
                MapType childData = toData(child);

                if (childData == null)
                {
                    /* Ein unvollstaendiger Sammel-Eintrag waere schlimmer als gar keiner: er wuerde
                     * einen Teil der Werte zurueckdrehen und den Rest stehen lassen. */
                    return null;
                }

                list.add(childData);
            }

            if (list.size() == 0)
            {
                return null;
            }

            MapType data = new MapType();

            data.putString(KEY_TYPE, TYPE_COMPOUND);
            data.put(KEY_CHILDREN, list);

            return data;
        }

        if (!(undo instanceof ValueChangeUndo))
        {
            return null;
        }

        ValueChangeUndo change = (ValueChangeUndo) undo;

        if (change.name == null || change.oldValue == null || change.newValue == null)
        {
            return null;
        }

        MapType data = new MapType();

        data.putString(KEY_TYPE, TYPE_VALUE_CHANGE);
        data.putString(KEY_PATH, change.name.toString());
        data.putBool(KEY_FOLDER, change.name.folder);
        data.put(KEY_OLD, change.oldValue);
        data.put(KEY_NEW, change.newValue);

        if (change.uiBefore != null)
        {
            data.put(KEY_UI_BEFORE, change.uiBefore);
        }

        if (change.uiAfter != null)
        {
            data.put(KEY_UI_AFTER, change.uiAfter);
        }

        return data;
    }

    private static IUndo<ValueGroup> fromData(BaseType type)
    {
        if (!(type instanceof MapType))
        {
            return null;
        }

        MapType data = (MapType) type;

        if (TYPE_COMPOUND.equals(data.getString(KEY_TYPE)))
        {
            BaseType children = data.get(KEY_CHILDREN);

            if (!(children instanceof ListType))
            {
                return null;
            }

            ListType list = (ListType) children;
            List<IUndo<ValueGroup>> undos = new ArrayList<>();

            for (int i = 0; i < list.size(); i++)
            {
                IUndo<ValueGroup> child = fromData(list.get(i));

                if (child == null)
                {
                    return null;
                }

                undos.add(child);
            }

            return undos.isEmpty() ? null : new CompoundUndo<>(undos);
        }

        if (!TYPE_VALUE_CHANGE.equals(data.getString(KEY_TYPE)))
        {
            return null;
        }

        DataPath path = new DataPath(data.getString(KEY_PATH));

        path.folder = data.getBool(KEY_FOLDER, false);

        ValueChangeUndo undo = new ValueChangeUndo(path, data.get(KEY_OLD), data.get(KEY_NEW));

        if (data.has(KEY_UI_BEFORE) && data.get(KEY_UI_BEFORE) instanceof MapType)
        {
            undo.uiBefore = (MapType) data.get(KEY_UI_BEFORE);
        }

        if (data.has(KEY_UI_AFTER) && data.get(KEY_UI_AFTER) instanceof MapType)
        {
            undo.uiAfter = (MapType) data.get(KEY_UI_AFTER);
        }

        return undo;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException
    {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
