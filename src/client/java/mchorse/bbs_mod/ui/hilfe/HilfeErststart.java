package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoBausteine;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;

/**
 * Whether the first-run hint is owed (spec section 3). State in {@code state_help_first_run}:
 * pending · later:&lt;n&gt; · known · watched. Later shows it again on the next start, three
 * times at most; until the Help menu is opened an orange dot sits on its label.
 */
public final class HilfeErststart
{
    private static final int MAL_LATER = 3;

    private static boolean geprueft;
    private static boolean punkt;
    private static UIHilfeHinweis offen;

    private HilfeErststart()
    {}

    /** The dashboard came up: the first time in this game, the hint may be owed. */
    public static void vielleichtZeigen(UIDashboard dashboard)
    {
        /* Tooltips of features with a short clip get the clip line */
        NanoBausteine.tooltipZusatz = Guides::clipZeile;
        NanoBausteine.tooltipAktion = Guides::clipOeffnen;
        UIHilfeLeer.anhaengen(dashboard);
        UIContextMenu.platz = UIHilfeMini::menuDaneben;

        if (geprueft)
        {
            return;
        }

        geprueft = true;

        String stand = EditorZustaende.hilfeErststart.get();

        punkt = stand.startsWith("later:");

        if (stand.equals("pending") || punkt && laters(stand) < MAL_LATER)
        {
            zeigen(dashboard);
        }
    }

    /** Help menu › Show welcome hint again. */
    public static void zeigen(UIDashboard dashboard)
    {
        if (offen != null)
        {
            offen.removeFromParent();
        }

        offen = new UIHilfeHinweis(dashboard);
        dashboard.overlay.add(offen);
    }

    /** Any opening of Help ends the hint as watched, and takes the dot away. */
    public static void hilfeGeoeffnet()
    {
        punktSetzen(false);

        if (offen != null)
        {
            offen.schliessen(false);
            offen = null;
        }

        if (!EditorZustaende.hilfeErststart.get().equals("known"))
        {
            EditorZustaende.hilfeErststart.set("watched");
        }
    }

    /** The orange dot on the Help label after Later. */
    public static boolean punkt()
    {
        return punkt;
    }

    /** The hint was answered: watched, known, or later (counted). */
    static void beantwortet(String antwort)
    {
        offen = null;

        String stand = EditorZustaende.hilfeErststart.get();

        if (antwort.equals("later"))
        {
            EditorZustaende.hilfeErststart.set("later:" + (stand.startsWith("later:") ? laters(stand) + 1 : 1));
        }
        else
        {
            EditorZustaende.hilfeErststart.set(antwort);
            punktSetzen(false);
        }
    }

    static void punktSetzen(boolean an)
    {
        punkt = an;

        if (BBSModClient.getDashboard() != null)
        {
            BBSModClient.getDashboard().getPanels().topbar.neuMalen();
        }
    }

    private static int laters(String stand)
    {
        try
        {
            return Integer.parseInt(stand.substring(6));
        }
        catch (NumberFormatException e)
        {
            return MAL_LATER;
        }
    }
}
