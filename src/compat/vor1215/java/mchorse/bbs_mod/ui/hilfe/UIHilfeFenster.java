package mchorse.bbs_mod.ui.hilfe;

import mchorse.bbs_mod.ui.framework.UIContext;

/** vor1215: kein Hilfe-Fenster im Spiel (braucht Rinku, nur 26.3); Guides nimmt den Systembrowser. */
final class UIHilfeFenster
{
    private UIHilfeFenster()
    {}

    static boolean oeffnen(UIContext context, Guides.Guide guide, int sekunde)
    {
        return false;
    }

    static void zeigen(UIContext context, HilfeSitzung sitzung)
    {}
}
