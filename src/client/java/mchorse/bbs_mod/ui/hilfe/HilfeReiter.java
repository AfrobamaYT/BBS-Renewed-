package mchorse.bbs_mod.ui.hilfe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcstudio.nano.NanoBilder;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.ui.utils.Tutorials;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Help window's "Short clips" and "What's new" tabs (spec 5.3). The short clips are ours,
 * recorded in this version; until they are on YouTube they open on Discord in the browser. What's
 * new comes from assets/bbs/tutorials/news.json, kept with every feature.
 */
final class HilfeReiter
{
    record Neu(String titel, String text, String clip)
    {}

    private static String version;
    private static List<Neu> neu;

    private HilfeReiter()
    {}

    static String version()
    {
        laden();

        return version;
    }

    static void kurzclips(long vg, NanoFlaeche f, float k, float x, float y, float w, float h, Consumer<String> zeigen)
    {
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, x * k, (y + 8F) * k, NanoDesign.TXT_DIM, "SHORT CLIPS");

        String rechts = "Recorded in this version";

        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, (x + w) * k - NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, rechts),
            (y + 8F) * k, NanoDesign.TXT_DIM, rechts);

        /* As many cards as fit; the wheel scrolls the rest */
        List<Guides.Kurz> clips = Guides.kurzvideos();
        int sichtbar = Math.max(1, (int) ((h - 22F - 22F) / 64F));

        f.rollGrenze(Math.max(0, clips.size() - sichtbar));

        int ab = (int) f.rollVersatz();
        float zy = y + 22F;

        for (Guides.Kurz kurz : clips.subList(ab, Math.min(clips.size(), ab + sichtbar)))
        {
            String id = "clip-" + kurz.id();
            boolean da = Tutorials.link(kurz.id()) != null;

            NanoStifte.flaeche(vg, x * k, zy * k, w * k, 58F * k, 6F * k, f.istHover(id) ? NanoDesign.WIDGET : NanoDesign.PANEL);
            vorschau(vg, kurz, (x + 8F) * k, (zy + 8F) * k, k);

            float tx = (x + 92F) * k, tw = (w - 92F - 140F) * k;

            NanoStifte.textInBox(vg, NanoDesign.BOLD, 12F * k, tx, (zy + 14F) * k, NanoDesign.TXT_MAX, kurz.titel());
            NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, tx, (zy + 30F) * k, NanoDesign.TXT_DIM,
                HilfeNano.kuerzen(vg, NanoDesign.SANS, 10.5F * k, kurz.text(), tw));
            NanoStifte.kreis(vg, tx + 3F * k, (zy + 46F) * k, 2.5F * k, NanoDesign.ORANGE);
            NanoStifte.textInBox(vg, NanoDesign.SANS, 10F * k, tx + 10F * k, (zy + 46F) * k, NanoDesign.TXT_DIM, kurz.wo());

            /* Both right-aligned, 12 from the card's edge */
            float rand = (x + w - 12F) * k;

            if (da)
            {
                HilfeNano.knopf(vg, f, id, "Watch on Discord", rand - knopfBreite(vg, "Watch on Discord", k), (zy + 6F) * k, false, k, () -> Tutorials.open(kurz.id()));
            }

            if (kurz.zeige() != null)
            {
                HilfeNano.knopf(vg, f, id + "-zeige", "Show me", rand - knopfBreite(vg, "Show me", k), (zy + 31F) * k, false, k, () -> zeigen.accept(kurz.zeige()));
            }

            zy += 64F;
        }

        int rest = clips.size() - ab - sichtbar;

        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, x * k, (zy + 10F) * k, NanoDesign.TXT_OFF,
            rest > 0 ? rest + " more below — scroll" : "New clips arrive with updates.");
    }

    /** A still from the clip with its length on it; a plain field while the picture is missing. */
    private static void vorschau(long vg, Guides.Kurz kurz, float x, float y, float k)
    {
        float w = 72F * k, h = 42F * k;

        if (!NanoBilder.bild(vg, "/assets/bbs/tutorials/thumbs/" + kurz.id() + ".jpg", x, y, w, h, 4F * k))
        {
            NanoStifte.flaeche(vg, x, y, w, h, 4F * k, NanoDesign.EDITOR);
        }

        NanoStifte.flaecheArgb(vg, x + w / 2F - 9F * k, y + h / 2F - 9F * k, 18F * k, 18F * k, 9F * k, 0x99000000);
        HilfeNano.spielSymbol(vg, x + w / 2F + 1F * k, y + h / 2F, 8F * k, false, NanoDesign.TXT_MAX);

        String laenge = HilfeNano.zeit(kurz.laenge());
        float lw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 9F * k, laenge) + 6F * k;

        NanoStifte.flaecheArgb(vg, x + w - lw - 3F * k, y + h - 14F * k, lw, 11F * k, 2F * k, 0xB0000000);
        NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 9F * k, x + w - 3F * k - lw / 2F, y + h - 8.5F * k, NanoDesign.TXT_MAX, laenge);
    }

    static void neuigkeiten(long vg, NanoFlaeche f, float k, float x, float y, float w, float h)
    {
        laden();

        NanoStifte.textInBox(vg, NanoDesign.BOLD, 15F * k, x * k, (y + 10F) * k, NanoDesign.TXT_MAX, "BBS Renewed " + version);

        float iw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 15F * k, "BBS Renewed " + version) + 12F * k;

        NanoStifte.flaeche(vg, x * k + iw, (y + 3F) * k, 64F * k, 14F * k, 3F * k, 0x2A3A26);
        NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 9F * k, x * k + iw + 32F * k, (y + 10F) * k, NanoDesign.GRUEN, "INSTALLED");

        float zy = y + 30F;
        float spalte = (w - 16F) / 2F;

        /* Two per row; a row is as high as its longer text */
        for (int i = 0; i < neu.size(); i += 2)
        {
            float zeile = 54F;

            for (int j = i; j < Math.min(i + 2, neu.size()); j++)
            {
                zeile = Math.max(zeile, 34F + HilfeNano.textHoehe(vg, 10.5F * k, textBreite(neu.get(j), spalte) * k, neu.get(j).text()) / k);
            }

            for (int j = i; j < Math.min(i + 2, neu.size()); j++)
            {
                Neu n = neu.get(j);
                float sx = x + (j % 2) * (spalte + 16F);
                final int nr = j;

                NanoStifte.flaeche(vg, sx * k, zy * k, spalte * k, zeile * k, 6F * k, NanoDesign.PANEL);
                NanoStifte.textInBox(vg, NanoDesign.BOLD, 12F * k, (sx + 12F) * k, (zy + 14F) * k, NanoDesign.TXT_MAX, n.titel());
                HilfeNano.text(vg, NanoDesign.SANS, 10.5F * k, (sx + 12F) * k, (zy + 24F) * k, textBreite(n, spalte) * k, NanoDesign.TXT_DIM, n.text());

                if (n.clip() != null && Tutorials.link(n.clip()) != null)
                {
                    HilfeNano.knopf(vg, f, "neu-" + nr, "Watch clip", (sx + spalte - 88F) * k, (zy + 16F) * k, false, k, () -> Tutorials.open(n.clip()));
                }
            }

            zy += zeile + 8F;
        }
    }

    private static float knopfBreite(long vg, String text, float k)
    {
        return NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, text) + 20F * k;
    }

    private static float textBreite(Neu n, float spalte)
    {
        return spalte - (n.clip() != null ? 108F : 24F);
    }

    private static void laden()
    {
        if (neu != null)
        {
            return;
        }

        neu = new ArrayList<>();

        try (InputStream in = HilfeReiter.class.getResourceAsStream("/assets/bbs/tutorials/news.json"))
        {
            JsonObject daten = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            version = daten.get("version").getAsString();

            for (JsonElement e : daten.getAsJsonArray("items"))
            {
                JsonObject o = e.getAsJsonObject();

                neu.add(new Neu(o.get("title").getAsString(), o.get("text").getAsString(), o.has("clip") ? o.get("clip").getAsString() : null));
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("assets/bbs/tutorials/news.json is missing or broken", e);
        }
    }
}
