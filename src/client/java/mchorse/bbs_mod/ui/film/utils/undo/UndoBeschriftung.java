package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.List;

/**
 * Menschenlesbare Zeile fuer einen Undo-Eintrag: WAS ist passiert, nicht WO im Baum.
 *
 * <p>Nutzerbefund (Akte 05): "in der undo history ist man als normal user komplett
 * confused weil dort nur die pfade stehen". Der Uebersetzer deckt die haeufigen
 * Faelle; alles Unbekannte faellt auf die letzten Pfadglieder zurueck — nie
 * schlechter als der bisherige Rohpfad.</p>
 *
 * <p>Bewusst direkte englische Texte statt UIKeys: die Liste zeigte bisher rohe
 * Pfade (ebenfalls unlokalisiert), und neue statische UIKeys-Felder waeren nach
 * einem Hotswap null (Static-Falle, hotswap-Skill).</p>
 */
public final class UndoBeschriftung
{
    private UndoBeschriftung()
    {}

    public static String beschrifte(IUndo<ValueGroup> undo)
    {
        if (undo instanceof ValueChangeUndo wert)
        {
            return einzeln(wert);
        }

        if (undo instanceof CompoundUndo<ValueGroup> verbund)
        {
            List<IUndo<ValueGroup>> undos = verbund.getUndos();
            IUndo<ValueGroup> erster = undos.isEmpty() ? null : undos.get(0);
            String kern = erster instanceof ValueChangeUndo wert ? einzeln(wert) : "changes";

            return undos.size() <= 1 ? kern : kern + " (+" + (undos.size() - 1) + " more)";
        }

        return undo.getClass().getSimpleName();
    }

    private static String einzeln(ValueChangeUndo undo)
    {
        String[] teile = undo.name.toString().split("/");
        String aktion = wirkung(undo.oldValue, undo.newValue);

        for (int i = 0; i < teile.length; i++)
        {
            if (teile[i].equals("replays"))
            {
                return replay(teile, i, aktion, undo);
            }

            if (teile[i].equals("camera"))
            {
                String rest = i + 2 < teile.length ? teile[teile.length - 1] : "clip";

                return aktion + " camera clip " + nummer(teile, i + 1) + " " + huebsch(rest);
            }
        }

        /* Unbekanntes Muster: die letzten beiden Glieder, lesbar gemacht. */
        String ende = teile.length >= 2 ? huebsch(teile[teile.length - 2]) + " " + huebsch(teile[teile.length - 1]) : undo.name.toString();

        return aktion + " " + ende;
    }

    private static String replay(String[] teile, int i, String aktion, ValueChangeUndo undo)
    {
        /* Pfad endet AUF "replays": BBS speichert Hinzufuegen/Entfernen als Ersatz der
         * GANZEN Liste (alt N Elemente -> neu N+-1). Das Differenz-Element traegt den
         * Modellnamen — genau die Zeile, die der Nutzer wollte ("added player/steve"). */
        if (i + 1 == teile.length && BaseType.isList(undo.oldValue) && BaseType.isList(undo.newValue))
        {
            mchorse.bbs_mod.data.types.ListType alt = (mchorse.bbs_mod.data.types.ListType) undo.oldValue;
            mchorse.bbs_mod.data.types.ListType neu = (mchorse.bbs_mod.data.types.ListType) undo.newValue;

            if (alt.size() != neu.size())
            {
                boolean dazu = neu.size() > alt.size();
                mchorse.bbs_mod.data.types.ListType lang = dazu ? neu : alt;
                mchorse.bbs_mod.data.types.ListType kurz = dazu ? alt : neu;
                int k = 0;

                while (k < kurz.size() && BaseType.equals(lang.get(k), kurz.get(k)))
                {
                    k++;
                }

                String modell = modellName(lang.get(k));

                return (dazu ? "added " : "removed ") + (modell != null ? modell : "replay " + k);
            }

            return "changed replays";
        }

        String replay = "replay " + nummer(teile, i + 1);

        /* replays/<i> ohne Rest: ein ganzes Replay kam oder ging — Modellname steht im Wert. */
        if (i + 2 == teile.length)
        {
            String modell = modellName(aktion.equals("removed") ? undo.oldValue : undo.newValue);

            return aktion + " " + (modell != null ? modell : replay);
        }

        String letztes = teile[teile.length - 1];

        if (letztes.equals("enabled"))
        {
            boolean an = BaseType.isNumeric(undo.newValue) && undo.newValue.asNumeric().boolValue();

            return (an ? "enabled " : "disabled ") + replay;
        }

        for (int j = i; j < teile.length; j++)
        {
            if (teile[j].equals("pose"))
            {
                return aktion + " pose keyframe (" + replay + ")";
            }

            if (teile[j].equals("keyframes") && j + 1 < teile.length)
            {
                return aktion + " " + huebsch(teile[j + 1]) + " keyframe (" + replay + ")";
            }

            if (teile[j].equals("form"))
            {
                String modell = modellName(undo.newValue);

                return "changed form" + (modell != null ? " to " + modell : "") + " (" + replay + ")";
            }
        }

        return aktion + " " + huebsch(letztes) + " (" + replay + ")";
    }

    /** null -> etwas = added; etwas -> null = removed; sonst changed. */
    private static String wirkung(BaseType alt, BaseType neu)
    {
        boolean altLeer = alt == null;
        boolean neuLeer = neu == null;

        if (altLeer && !neuLeer) return "added";
        if (!altLeer && neuLeer) return "removed";

        return "changed";
    }

    /** Modellkennung aus einem Replay-/Form-Wert ziehen (form.model, sonst form.id). */
    private static String modellName(BaseType wert)
    {
        if (!BaseType.isMap(wert))
        {
            return null;
        }

        MapType map = wert.asMap();
        MapType form = BaseType.isMap(map.get("form")) ? map.getMap("form") : map;
        String modell = form.getString("model");

        return !modell.isEmpty() ? modell : (form.has("id") ? form.getString("id") : null);
    }

    private static String nummer(String[] teile, int i)
    {
        return i < teile.length ? teile[i] : "?";
    }

    private static String huebsch(String segment)
    {
        return segment.replace('_', ' ');
    }
}
