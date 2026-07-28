package mchorse.bbs_mod.ui.framework.elements.input.list;

/**
 * Wie eine Suche in einer Liste den getippten Text mit einem Dateinamen vergleicht.
 *
 * <p>⛔ WOFÜR — gemessen 2026-07-29 an echten Dateien des Nutzers: seine Audio-Clips heißen
 * {@code Verity-2026-07-23-02-48-[excited]-Aight-bet!-You're-on.wav}, also mit BINDESTRICHEN
 * statt Leerzeichen, weil sie aus Transkripten erzeugt werden. Er sucht die gesprochenen
 * Worte — „aight bet" — und findet nichts, obwohl die Datei vor ihm in der Liste steht.
 * Der Grund war ein stures {@code contains}: „aight" trifft, „aight bet" trifft nie, weil im
 * Namen ein Bindestrich zwischen den Worten steht. Der Windows-Explorer findet die Datei,
 * weil er in Worte zerlegt — und genau das erwartet man von einer Suche.
 *
 * <p>Zweiter Fall aus derselben Meldung: {@code that´s} (Akut, die Taste liegt auf der
 * deutschen Tastatur direkt daneben) traf {@code That's} nicht. Auch das ist für den
 * Suchenden kein Unterschied.
 *
 * <p>Die Regel bleibt trotzdem eng: ALLE getippten Worte müssen vorkommen, nur eben
 * unabhängig davon, was zwischen ihnen steht. Wer weiter eine zusammenhängende Zeichenkette
 * sucht, bekommt sie über den unveränderten {@code contains}-Weg, der zuerst geprüft wird.
 */
public final class ListFilterText
{
    private ListFilterText()
    {
    }

    /**
     * Trennzeichen zu Leerzeichen, Apostroph-Varianten vereinheitlichen.
     *
     * <p>Erwartet bereits kleingeschriebenen Text — die Suche macht das vorher, und es hier
     * zu wiederholen würde bei jedem Element noch einmal Arbeit kosten.
     */
    public static String entschaerfe(String text)
    {
        StringBuilder sb = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (c == '\'' || c == '´' || c == '`' || c == '‘' || c == '’')
            {
                /* Apostroph faellt WEG, nicht auf ein leerzeichen: sonst wird aus „It's" ein
                 * „it s", und wer „its a wrap" tippt, findet seine eigene datei nicht.
                 * Beide seiten verlieren ihn, damit passt jede schreibweise auf jede. */
                continue;
            }

            sb.append(Character.isLetterOrDigit(c) ? c : ' ');
        }

        return sb.toString();
    }

    /** Die getippten Worte, entschärft und ohne Leereinträge. */
    public static String[] worte(String filter)
    {
        return entschaerfe(filter).trim().split("\\s+");
    }

    /** Kommt JEDES wort im ziel vor? Ohne worte: nein — sonst passte ein leerer filter auf alles. */
    public static boolean enthaeltAlle(String ziel, String[] worte)
    {
        if (worte.length == 0)
        {
            return false;
        }

        for (String wort : worte)
        {
            if (wort.isEmpty() || !ziel.contains(wort))
            {
                return false;
            }
        }

        return true;
    }
}
