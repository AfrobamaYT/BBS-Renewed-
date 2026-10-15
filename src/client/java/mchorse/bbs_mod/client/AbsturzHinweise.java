package mchorse.bbs_mod.client;

import mchorse.bbs_mod.forms.forms.Form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render errors BBS caught and survived, for the "BBS Renewed" entry of every crash report.
 * A crash report alone then shows which form failed — without asking the player for the log
 * (2026-10-12: "Pose stack not empty" from the community, only the crash report at hand).
 */
public final class AbsturzHinweise
{
    private static final int GRENZE = 5;
    private static final int ZEILEN = 6;
    private static final Map<String, Eintrag> EINTRAEGE = new LinkedHashMap<>();

    private AbsturzHinweise()
    {}

    public static synchronized void merken(Form form, Throwable fehler)
    {
        String art = form == null ? "null" : form.getClass().getSimpleName();
        String schluessel = art + ":" + fehler.getClass().getName() + ":" + fehler.getMessage();
        Eintrag eintrag = EINTRAEGE.remove(schluessel);

        if (eintrag == null)
        {
            eintrag = new Eintrag(art, form == null ? "" : String.valueOf(form.getDisplayName()), fehler);
        }

        eintrag.anzahl += 1;
        EINTRAEGE.put(schluessel, eintrag);

        while (EINTRAEGE.size() > GRENZE)
        {
            EINTRAEGE.remove(EINTRAEGE.keySet().iterator().next());
        }
    }

    public static synchronized String text()
    {
        if (EINTRAEGE.isEmpty())
        {
            return "no caught render errors";
        }

        StringBuilder text = new StringBuilder(EINTRAEGE.size() + " caught render error(s), latest last");
        List<Eintrag> liste = new ArrayList<>(EINTRAEGE.values());

        for (Eintrag eintrag : liste)
        {
            text.append("\n\t\t").append(eintrag.art).append(" \"").append(eintrag.name).append("\" x")
                .append(eintrag.anzahl).append(": ").append(eintrag.fehler);

            StackTraceElement[] stapel = eintrag.fehler.getStackTrace();

            for (int i = 0; i < Math.min(ZEILEN, stapel.length); i++)
            {
                text.append("\n\t\t\tat ").append(stapel[i]);
            }
        }

        return text.toString();
    }

    private static final class Eintrag
    {
        private final String art;
        private final String name;
        private final Throwable fehler;
        private int anzahl;

        private Eintrag(String art, String name, Throwable fehler)
        {
            this.art = art;
            this.name = name;
            this.fehler = fehler;
        }
    }
}
