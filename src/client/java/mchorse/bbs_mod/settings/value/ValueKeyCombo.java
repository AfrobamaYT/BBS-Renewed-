package mchorse.bbs_mod.settings.value;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Zweitbelegungen;

public class ValueKeyCombo extends BaseValueBasic<KeyCombo>
{
    public ValueKeyCombo(String id, KeyCombo combo)
    {
        super(id, combo);
    }

    @Override
    public void set(KeyCombo value, int flag)
    {
        this.preNotify(flag);
        this.value.copy(value);
        this.postNotify(flag);
    }

    /**
     * ⛔ OHNE Zweitbelegung bleibt das Format BYTE-GLEICH zum alten: eine blanke Tastenliste.
     * Das ist der Normalfall (die meisten der 169 Aktionen bekommen nie eine zweite Belegung)
     * und der Grund, warum eine von diesem Build geschriebene Datei in einem aelteren Build
     * weiter laedt.
     *
     * <p>Mit Zweitbelegung wird daraus ein Objekt {@code {"keys": [...], "zweit": [...]}} —
     * dasselbe Muster wie IntelliJs Keymap-XML, wo unter EINER Aktion mehrere
     * {@code <keyboard-shortcut>} stehen. Bewusst NICHT eine Liste von Listen: {@code getInt}
     * auf einem Listen-Element liefert still 0, und 0 ist hier der vergiftete Tastencode, der
     * jeden Linksklick trifft.</p>
     */
    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (int key : this.value.keys)
        {
            list.addInt(key);
        }

        /* UNGESPERRT gelesen (hinterlegt, nicht zweit): sonst wuerde ein einmal ausgeschalteter
         * Schalter beim naechsten Speichern alle Zweitbelegungen des Nutzers loeschen. */
        KeyCombo zweit = Zweitbelegungen.hinterlegt(this.value);

        if (zweit == null || zweit.keys.isEmpty())
        {
            return list;
        }

        ListType zweitListe = new ListType();

        for (int key : zweit.keys)
        {
            zweitListe.addInt(key);
        }

        MapType map = new MapType();

        map.put("keys", list);
        map.put("zweit", zweitListe);

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isList())
        {
            /* ALTES FORMAT und Normalfall: blanke Tastenliste, keine Zweitbelegung. */
            this.tastenLesen(data.asList());
            Zweitbelegungen.entfernen(this.value);

            return;
        }

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();
        BaseType keys = map.get("keys");

        if (keys != null && keys.isList())
        {
            this.tastenLesen(keys.asList());
        }

        BaseType zweit = map.get("zweit");

        if (zweit != null && zweit.isList())
        {
            ListType liste = zweit.asList();
            java.util.List<Integer> tasten = new java.util.ArrayList<>(liste.size());

            for (int i = 0; i < liste.size(); i++)
            {
                tasten.add(liste.getInt(i));
            }

            Zweitbelegungen.setzen(this.value, tasten);
        }
        else
        {
            Zweitbelegungen.entfernen(this.value);
        }
    }

    private void tastenLesen(ListType list)
    {
        this.value.keys.clear();

        for (int i = 0; i < list.size(); i++)
        {
            this.value.keys.add(list.getInt(i));
        }
    }
}