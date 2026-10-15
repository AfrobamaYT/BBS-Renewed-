package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoFlaeche;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.TopbarReiter;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIReiterKnopf;
import mchorse.bbs_mod.ui.film.UISeitenleiste;
import mchorse.bbs_mod.ui.film.UIZeitleisteNano;
import mchorse.bbs_mod.ui.film.ZeitleisteZweig;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;

/**
 * The places Show me points at ("show" in guides.json): what the label says, what has to be on
 * screen first, and where the place is right now — found in the live UI, never a stored position.
 */
final class HilfeZiel
{
    private HilfeZiel()
    {}

    /** "Here: …", or null when this version has no such place. */
    static String etikett(String ziel)
    {
        return switch (ziel)
        {
            case "settings" -> "Here: Settings";
            case "record" -> "Here: Record replay";
            case "properties" -> "Here: Properties";
            case "rig" -> "Here: the Rig tab";
            case "guide_tab" -> "Here: the Guide tab";
            case "resolve_tab" -> "Here: the Resolve tab";
            case "textures" -> "Here: the Textures workspace";
            case "help_menu" -> "Here: the Help menu";
            case "mic" -> "Here: Voice-over";
            case "blocks" -> "Here: the Blocks workspace";
            case "morphing" -> "Here: the Morphing workspace";
            case "camera_menu" -> "Here: the timeline menu, set to Camera clips";
            default -> null;
        };
    }

    /** The film editor, the side bar tab, the timeline mode — whatever the place needs. */
    static void vorbereiten(UIDashboard d, String ziel)
    {
        if (!ziel.equals("settings") && !ziel.equals("blocks") && !ziel.equals("morphing") && !ziel.equals("resolve_tab")
            && !ziel.equals("textures") && !ziel.equals("help_menu"))
        {
            UIFilmPanel film = d.getPanels().getPanel(UIFilmPanel.class);

            if (d.getPanels().panel != film)
            {
                d.setPanel(film);
            }

            switch (ziel)
            {
                case "rig" -> seitenleiste(d, "Rig");
                case "guide_tab" -> seitenleiste(d, "Guide");
                case "camera_menu" ->
                {
                    /* What picking Camera clips in that menu does */
                    if (film.zeitleisteNano instanceof UIZeitleisteNano z && z.modus != 2)
                    {
                        z.modus = 2;
                        z.auffrischen();
                        ZeitleisteZweig.anwenden(film, 2);
                    }
                }
                default -> {}
            }
        }
    }

    private static void seitenleiste(UIDashboard d, String reiter)
    {
        for (UISeitenleiste leiste : d.getRoot().getChildren(UISeitenleiste.class))
        {
            if (leiste.canBeSeen())
            {
                leiste.zeige(reiter);
            }
        }
    }

    /** Where the target is now, in GUI px (x, y, w, h), or null. */
    static float[] ort(String ziel)
    {
        UIDashboard d = BBSModClient.getDashboard();
        UIElement wurzel = d.getRoot();

        return switch (ziel)
        {
            case "settings", "record", "mic", "camera_menu", "help_menu" -> zone(wurzel, zonenId(ziel));
            case "textures" -> zone(wurzel, "reiter" + d.getPanels().panels.indexOf(d.getPanels().getPanel(UITextureManagerPanel.class)));
            case "blocks" -> zone(wurzel, "reiter" + d.getPanels().panels.indexOf(d.getPanels().getPanel(UIModelBlockPanel.class)));
            case "morphing" -> zone(wurzel, "reiter" + d.getPanels().panels.indexOf(d.getPanels().getPanel(UIMorphingPanel.class)));
            case "properties" -> d.getPanels().panel instanceof UIFilmPanel film ? flaeche(film.replayEditor.replayProperties.propsNano) : null;
            case "rig" -> reiter(wurzel, "Rig");
            case "guide_tab" -> reiter(wurzel, "Guide");
            case "resolve_tab" -> zusatz(wurzel, "Resolve");
            default -> null;
        };
    }

    /** The click zone a place is, where it is one, else null. */
    static String zonenId(String ziel)
    {
        return switch (ziel)
        {
            case "settings" -> "tbsetup";
            case "record" -> "tr-rec";
            case "mic" -> "tr-voiceover";
            case "camera_menu" -> "titelklappe";
            case "help_menu" -> "tbmHelp";
            default -> null;
        };
    }

    /** Said instead of a ring when the place is not on screen — never a ring around nothing. */
    static String fehlt(String ziel)
    {
        return ziel.equals("resolve_tab")
            ? "The Resolve tab shows in the top bar while DaVinci Resolve is connected through BBS Link."
            : "That place isn't on screen right now.";
    }

    /** Shift+F1: the short clip about what the mouse is over — its Show me place, or a panel that names its clip. */
    static String clipUnterMaus(UIContext context)
    {
        int mx = context.mouseX(), my = context.mouseY();

        for (UIElement e : BBSModClient.getDashboard().getRoot().getChildren(UIElement.class))
        {
            if (e instanceof Guides.ClipQuelle quelle && e.canBeSeen() && e.area.isInside(mx, my))
            {
                return quelle.hilfeClip();
            }
        }

        for (Guides.Kurz kurz : Guides.kurzvideos())
        {
            float[] r = kurz.zeige() == null ? null : ort(kurz.zeige());

            if (r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3])
            {
                return kurz.id();
            }
        }

        return null;
    }

    /** An add-on tab of the top bar (TopbarReiter): its zone counts only the visible ones. */
    private static float[] zusatz(UIElement wurzel, String name)
    {
        int nr = 0;

        for (TopbarReiter reiter : TopbarReiter.ALL)
        {
            if (reiter.visible().getAsBoolean())
            {
                if (name.equals(reiter.name().get()))
                {
                    return zone(wurzel, "zusatz" + nr);
                }

                nr++;
            }
        }

        return null;
    }

    private static float[] flaeche(UIElement e)
    {
        return e == null || !e.canBeSeen() ? null : new float[] {e.area.x, e.area.y, e.area.w, e.area.h};
    }

    /** A click zone of any NanoVG surface on screen, by its id — the conversion of /ui/zonen. */
    private static float[] zone(UIElement wurzel, String id)
    {
        for (NanoFlaeche fl : wurzel.getChildren(NanoFlaeche.class))
        {
            int i = fl.zonenNamen().indexOf(id);

            if (i >= 0 && i < fl.zonenMasse().size() && fl.canBeSeen())
            {
                float[] m = fl.zonenMasse().get(i);
                float k = Math.max(1F, fl.skala());

                return new float[] {fl.area.x + m[0] / k, fl.area.y + m[1] / k, m[2] / k, m[3] / k};
            }
        }

        return null;
    }

    private static float[] reiter(UIElement wurzel, String name)
    {
        for (UIReiterKnopf knopf : wurzel.getChildren(UIReiterKnopf.class))
        {
            if (name.equals(knopf.beschriftung()) && knopf.canBeSeen())
            {
                return flaeche(knopf);
            }
        }

        return null;
    }
}
