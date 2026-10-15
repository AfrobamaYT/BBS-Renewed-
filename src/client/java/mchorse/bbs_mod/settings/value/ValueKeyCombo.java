package mchorse.bbs_mod.settings.value;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.LegacyKeyboardKeys;
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
     * 26.3 stores marked SDL scancodes. Historical bare lists and unmarked primary/secondary
     * maps retain their GLFW interpretation during import, so numeric codes are never guessed.
     * Unsupported historical bindings retain their original identity and source metadata.
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
            MapType map = new MapType();

            map.putString("key_format", "sdl3-scancode");
            map.put("keys", list);
            this.unmappedPreserve(map);

            return map;
        }

        ListType zweitListe = new ListType();

        for (int key : zweit.keys)
        {
            zweitListe.addInt(key);
        }

        MapType map = new MapType();

        map.putString("key_format", "sdl3-scancode");
        map.put("keys", list);
        map.put("zweit", zweitListe);
        this.unmappedPreserve(map);

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isList())
        {
            /* ALTES FORMAT und Normalfall: blanke Tastenliste, keine Zweitbelegung. */
            this.tastenLesen(data.asList(), true);
            Zweitbelegungen.entfernen(this.value);

            return;
        }

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();
        boolean legacy = !"sdl3-scancode".equals(map.getString("key_format"));
        BaseType keys = map.get("keys");

        if (keys != null && keys.isList())
        {
            this.tastenLesen(keys.asList(), legacy);
        }

        BaseType zweit = map.get("zweit");

        if (zweit != null && zweit.isList())
        {
            ListType liste = zweit.asList();
            java.util.List<Integer> tasten = new java.util.ArrayList<>(liste.size());

            for (int i = 0; i < liste.size(); i++)
            {
                int key = liste.getInt(i);

                tasten.add(legacy && key > 0 ? LegacyKeyboardKeys.fromGlfw(key) : key);
            }

            Zweitbelegungen.setzen(this.value, tasten);
        }
        else
        {
            Zweitbelegungen.entfernen(this.value);
        }
    }

    private void tastenLesen(ListType list, boolean legacy)
    {
        this.value.keys.clear();

        for (int i = 0; i < list.size(); i++)
        {
            int key = list.getInt(i);

            this.value.keys.add(legacy && key > 0 ? LegacyKeyboardKeys.fromGlfw(key) : key);
        }
    }

    private void unmappedPreserve(MapType map)
    {
        ListType primary = new ListType();
        ListType secondary = new ListType();
        KeyCombo zweit = Zweitbelegungen.hinterlegt(this.value);

        for (int i = 0; i < this.value.keys.size(); i++)
        {
            if (LegacyKeyboardKeys.isUnmapped(this.value.keys.get(i)))
            {
                MapType entry = new MapType();

                entry.putInt("index", i);
                entry.putInt("glfw_code", 314);
                primary.add(entry);
            }
        }

        if (zweit != null)
        {
            for (int i = 0; i < zweit.keys.size(); i++)
            {
                if (LegacyKeyboardKeys.isUnmapped(zweit.keys.get(i)))
                {
                    MapType entry = new MapType();

                    entry.putInt("index", i);
                    entry.putInt("glfw_code", 314);
                    secondary.add(entry);
                }
            }
        }

        if (!primary.isEmpty()) map.put("unmapped_legacy_keys", primary);
        if (!secondary.isEmpty()) map.put("unmapped_legacy_zweit", secondary);
    }
}
