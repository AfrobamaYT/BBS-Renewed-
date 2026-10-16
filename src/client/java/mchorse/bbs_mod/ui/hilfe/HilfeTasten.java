package mchorse.bbs_mod.ui.hilfe;

import com.afrobama.bbstools.client.keybinds.UIKeybindEditor;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Help window's "Shortcuts" tab (spec 5.3): every key BBS knows, read from the keybind registry
 * (never a hand-written list), grouped by category in three columns, filtered live by name or
 * key. The wheel scrolls; Open Keybinds leads to where they are changed.
 */
final class HilfeTasten
{
    private static final float ZEILE = 25F;

    private String suche = "";

    void tippen(NanoFlaeche f, String fokus, char c)
    {
        if (!"tasten-suche".equals(fokus))
        {
            return;
        }

        this.suche = c == '\b' ? this.suche.substring(0, Math.max(0, this.suche.length() - 1)) : c == '\n' ? this.suche : this.suche + c;
        f.neuMalen();
    }

    void zeichnen(long vg, NanoFlaeche f, float k, UIContext context, float x, float y, float w, float h)
    {
        /* Search field */
        boolean fokus = "tasten-suche".equals(f.fokus());

        NanoStifte.flaeche(vg, x * k, y * k, 260F * k, 22F * k, 4F * k, NanoDesign.FELD);
        NanoStifte.rahmen(vg, x * k, y * k, 260F * k, 22F * k, 4F * k, Math.max(1F, k), fokus ? NanoDesign.BLAU : 0x000000);
        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, (x + 9F) * k, (y + 11F) * k, this.suche.isEmpty() ? NanoDesign.TXT_DIM : NanoDesign.TXT,
            this.suche.isEmpty() && !fokus ? "Search shortcuts by name or key" : this.suche);

        if (fokus)
        {
            float cx = (x + 9F) * k + NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, this.suche) + 1F * k;

            NanoStifte.flaeche(vg, cx, (y + 5F) * k, 1.5F * k, 12F * k, 0F, NanoDesign.BLAU);
        }

        f.zone("tasten-suche", x * k, y * k, 260F * k, 22F * k, () -> f.setzeFokus("tasten-suche"));

        /* Rows: a header per category, then its keys */
        String q = this.suche.toLowerCase(Locale.ROOT);
        Map<String, List<KeyCombo>> gruppen = new LinkedHashMap<>();
        int alle = 0;

        for (KeyCombo c : KeyCombo.getRegistrierte())
        {
            String name = c.label.get(), taste = c.getKeyCombo();

            alle++;

            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || taste.toLowerCase(Locale.ROOT).contains(q))
            {
                /* The same groups as Settings › Keybinds (UIKeybindEditor): the category key */
                String kategorie = c.categoryKey == null || c.categoryKey.equals("all") ? "General" : c.categoryKey.replace('_', ' ');

                gruppen.computeIfAbsent(kategorie, (s) -> new ArrayList<>()).add(c);
            }
        }

        List<Object> zeilen = new ArrayList<>();

        gruppen.forEach((kategorie, liste) ->
        {
            zeilen.add(kategorie);
            zeilen.addAll(liste);
        });

        float ly = y + 34F, lh = h - 34F - 30F;
        int sichtbar = Math.max(1, (int) (lh / ZEILE));
        int jeSpalte = Math.max(1, (zeilen.size() + 2) / 3);
        float spalte = (w - 2F * 22F) / 3F;

        f.rollGrenze(Math.max(0, jeSpalte - sichtbar));

        int ab = (int) f.rollVersatz();

        for (int i = 0; i < zeilen.size(); i++)
        {
            int s = i / jeSpalte, z = i % jeSpalte - ab;

            if (z < 0 || z >= sichtbar)
            {
                continue;
            }

            float zx = (x + s * (spalte + 22F)) * k, zy = (ly + z * ZEILE) * k;

            if (zeilen.get(i) instanceof String kategorie)
            {
                NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, zx, zy + 13F * k, NanoDesign.TXT_DIM, kategorie.toUpperCase(Locale.ROOT));
            }
            else if (zeilen.get(i) instanceof KeyCombo c)
            {
                String taste = c.getKeyCombo();
                float tw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 10F * k, taste) + 12F * k;

                NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, zx, zy + 12F * k, NanoDesign.TXT,
                    HilfeNano.kuerzen(vg, NanoDesign.SANS, 11F * k, c.label.get(), spalte * k - tw - 10F * k));
                NanoStifte.flaeche(vg, zx + spalte * k - tw, zy + 4F * k, tw, 17F * k, 3F * k, 0x2C2C2C);
                NanoStifte.rahmen(vg, zx + spalte * k - tw, zy + 4F * k, tw, 17F * k, 3F * k, Math.max(1F, k), 0x3A3A3A);
                NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 10F * k, zx + spalte * k - tw / 2F, zy + 12.5F * k, NanoDesign.TXT, taste);
            }
        }

        /* Foot: where to change them */
        float fy = (y + h - 22F) * k;
        String text = "See all " + alle + " and change any of them in Settings › Keybinds.";

        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, x * k, fy + 11F * k, NanoDesign.TXT_DIM, text);
        HilfeNano.knopf(vg, f, "tasten-editor", "Open Keybinds", x * k + NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, text) + 12F * k, fy,
            false, k, () ->
            {
                UIKeybindEditor editor = new UIKeybindEditor();

                editor.setInitialOffset(40, 40);
                UIOverlay.addOverlay(context, editor);
            });
    }
}
