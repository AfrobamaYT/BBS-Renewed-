package mchorse.bbs_mod.ui.hilfe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.Blaze3D;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.texturstudio.TexturStudio;
import mchorse.bbs_mod.ui.utils.Tutorials;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The long video guides, chapter by chapter, and the short feature clips (owner 2026-10-14:
 * McHorse's guide right in the game, since nobody reads the mod page). They ship in
 * assets/bbs/tutorials/guides.json -- a new guide, chapter text or clip is an entry there.
 */
public final class Guides
{
    /**
     * {@code hier}: where the chapter's feature lives in this version ("In this version");
     * {@code zeige}: what Show me points at; {@code thema}: the editor it is about. Each may be null.
     */
    public record Kapitel(int sekunde, String titel, String thema, String hier, String zeige)
    {}

    public record Guide(String id, String titel, String autor, String video, int laenge, List<Kapitel> kapitel)
    {
        /** The chapter playing at this second. */
        public int kapitelBei(double sekunde)
        {
            int i = 0;

            while (i + 1 < this.kapitel.size() && this.kapitel.get(i + 1).sekunde() <= sekunde)
            {
                i++;
            }

            return i;
        }

        public int dauer(int i)
        {
            return (i + 1 < this.kapitel.size() ? this.kapitel.get(i + 1).sekunde() : this.laenge) - this.kapitel.get(i).sekunde();
        }
    }

    /** A short feature clip: an id in {@code Tutorials}' links, its length and where it lives. */
    /** A panel that has a short clip about itself: Shift+F1 over it plays that clip. */
    public interface ClipQuelle
    {
        String hilfeClip();
    }

    public record Kurz(String id, String titel, int laenge, String wo, String text, String zeige)
    {}

    private static List<Guide> guides;
    private static List<Kurz> kurz;
    private static String hinweis = "";
    private static String community = "";

    private Guides()
    {}

    public static List<Guide> alle()
    {
        laden();

        return guides;
    }

    public static List<Kurz> kurzvideos()
    {
        laden();

        return kurz;
    }

    /** That the guides show the original BBS, not this fork (owner 2026-10-14). */
    public static String hinweis()
    {
        laden();

        return hinweis;
    }

    public static String community()
    {
        laden();

        return community;
    }

    /** The guide at a moment: in the game with Rinku, else in the browser at the same moment. */
    public static void oeffnen(UIContext context, Guide guide, int sekunde)
    {
        if (TexturStudio.verfuegbar() && UIHilfeFenster.oeffnen(context, guide, sekunde))
        {
            return;
        }

        imBrowser(guide.video(), sekunde);
    }

    /** Shift+F1: the short clip about what the mouse is over, else the chapter about the editor you
     * are in, else the guide where you left it. Clips open on Discord until they are on YouTube. */
    public static void taste(UIContext context)
    {
        String clip = HilfeZiel.clipUnterMaus(context);

        HilfeErststart.hilfeGeoeffnet();

        if (clip != null && Tutorials.link(clip) != null)
        {
            Tutorials.open(clip);

            return;
        }

        Guide guide = alle().get(0);
        int kontext = kontextKapitel(guide);

        oeffnen(context, guide, kontext >= 0 ? guide.kapitel().get(kontext).sekunde() : mchorse.bbs_mod.settings.EditorZustaende.hilfeStelle.get());
    }

    /** The tooltip action line of a click zone that is a feature with a short clip, or null. */
    public static String[] clipZeile(String zone)
    {
        for (Kurz kurz : kurzvideos())
        {
            if (kurz.zeige() != null && zone.equals(HilfeZiel.zonenId(kurz.zeige())) && Tutorials.link(kurz.id()) != null)
            {
                return new String[] {"Watch the " + kurz.laenge() + "-second clip", "Shift+F1"};
            }
        }

        return null;
    }

    /** Help menu › Video tutorial: where you left it, read at the click — an open session just
     * shows itself (the place the menu was built with lagged behind and jumped back). */
    public static void weiter(UIContext context, Guide guide)
    {
        HilfeSitzung offen = HilfeSitzung.aktiv();
        int stelle = mchorse.bbs_mod.settings.EditorZustaende.hilfeStelle.get();

        oeffnen(context, guide, offen != null && offen.guide == guide ? (int) offen.kanal.zeit : stelle > 30 ? stelle : 0);
    }

    /** End of every frame: the mini-player's video once more on top (YouTube: nothing over the player). */
    public static void obenauf(UIContext context)
    {
        UIHilfeMini.obenauf(context);
    }

    /** The dashboard closed: a playing guide pauses. */
    public static void dashboardZu()
    {
        HilfeSitzung.anhalten();
    }

    /** A click on that action line: the clip of the zone's feature. */
    public static void clipOeffnen(String zone)
    {
        for (Kurz kurz : kurzvideos())
        {
            if (kurz.zeige() != null && zone.equals(HilfeZiel.zonenId(kurz.zeige())))
            {
                Tutorials.open(kurz.id());

                return;
            }
        }
    }

    public static int kontextKapitel(Guide guide)
    {
        return HilfeSitzung.kontextKapitel(guide);
    }

    public static String zeit(double sekunden)
    {
        return HilfeNano.zeit(sekunden);
    }

    public static void imBrowser(String video, int sekunde)
    {
        net.minecraft.util.Util.getPlatform().openUri(URI.create("https://www.youtube.com/watch?v=" + video + "&t=" + sekunde + "s"));
    }

    private static String text(JsonObject o, String name)
    {
        return o.has(name) ? o.get(name).getAsString() : null;
    }

    private static void laden()
    {
        if (guides != null)
        {
            return;
        }

        guides = new ArrayList<>();
        kurz = new ArrayList<>();

        try (InputStream in = Guides.class.getResourceAsStream("/assets/bbs/tutorials/guides.json"))
        {
            JsonObject daten = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            hinweis = daten.get("note").getAsString();
            community = daten.get("community").getAsString();

            for (JsonElement g : daten.getAsJsonArray("guides"))
            {
                JsonObject o = g.getAsJsonObject();
                List<Kapitel> kapitel = new ArrayList<>();

                for (JsonElement k : o.getAsJsonArray("chapters"))
                {
                    JsonObject c = k.getAsJsonObject();

                    kapitel.add(new Kapitel(c.get("t").getAsInt(), c.get("title").getAsString(), text(c, "topic"), text(c, "here"), text(c, "show")));
                }

                guides.add(new Guide(o.get("id").getAsString(), o.get("title").getAsString(), o.get("author").getAsString(),
                    o.get("video").getAsString(), o.get("length").getAsInt(), kapitel));
            }

            for (JsonElement k : daten.getAsJsonArray("shorts"))
            {
                JsonObject o = k.getAsJsonObject();

                kurz.add(new Kurz(o.get("id").getAsString(), o.get("title").getAsString(), o.get("length").getAsInt(),
                    text(o, "where"), text(o, "text"), text(o, "show")));
            }
        }
        catch (Exception e)
        {
            /* Loud: a help menu that is silently empty looks like there is no help */
            throw new IllegalStateException("assets/bbs/tutorials/guides.json is missing or broken", e);
        }
    }
}
