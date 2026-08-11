package mchorse.bbs_mod.ui.film.mehrfach;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.numeric.ValueLong;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;

/**
 * Der EINE ort, an dem entschieden wird, ob eine aenderung im eigenschaften-panel als
 * DELTA oder ABSOLUT auf die uebrigen gewaehlten clips wirkt.
 *
 * <p>Die aufgabe verlangt ausdruecklich, dass diese unterscheidung <em>nicht</em> in jedes
 * panel kopiert wird. Darum steht sie hier und nirgends sonst; die panels rufen nur
 * {@code editor.editMultiple(...)} auf und wissen von delta nichts.</p>
 *
 * <h2>Die regel, und woher sie kommt</h2>
 * Aus DaVinci Resolve (Handbuch, „Adjusting Multiple Clips at the Same Time"): am
 * <b>schieberegler ZIEHEN</b> = relative aenderung, die abstaende zwischen den clips
 * bleiben; eine <b>zahl TIPPEN</b> = alle bekommen denselben absoluten wert. Blender macht
 * dieselbe zweiteilung mit Alt-Klick (absolut) gegen Alt-Enter (relativ). Beide trennen
 * also nicht nach eigenschaft, sondern nach GESTE — und genau das kann hier zentral
 * abgefragt werden, weil {@link UINumericInput} die geste des laufenden callbacks meldet.
 *
 * <h2>⚠ FALLE: „zahl" heisst nicht „messwert"</h2>
 * Ein {@link ValueInt} traegt in BBS auch dinge, bei denen addieren unsinn ist:
 * <ul>
 *   <li>farben ({@code subtitle.color}, {@code subtitle.background}) — ein delta blutet
 *       zwischen den kanaelen und ergibt eine falsche farbe,</li>
 *   <li>modus-listen ({@code audio.spatial}) und auswahl-indizes ({@code clip.selector}),</li>
 *   <li>bitmasken ({@code component.active}, ueber UIBitToggle).</li>
 * </ul>
 * Zwei tore halten die raus: der subtyp (farbe/modus) und die geste (ein UICirculate,
 * UIToggle, UIButton oder UIBitToggle ist kein zahlenfeld, meldet also
 * {@link UINumericInput.Eingabeart#KEINE} und faellt damit auf absolut).
 */
public final class MehrfachWert
{
    private MehrfachWert()
    {}

    /**
     * Ob eine aenderung an diesem wert als delta auf die uebrigen clips gehen darf.
     *
     * <p>Wird MITTEN im callback des bedienelements gefragt — {@link UINumericInput}
     * setzt seine eingabeart nur fuer die dauer des callbacks, ausserhalb steht dort
     * {@link UINumericInput.Eingabeart#KEINE}. Ein aufruf ausserhalb liefert deshalb
     * immer {@code false}, und das ist die sichere seite: absolut setzen.</p>
     */
    public static boolean nimmtDelta(BaseValue wert)
    {
        if (!gesteIstZug())
        {
            return false;
        }

        /* Farbe/modus in einem int: addieren ergaebe muell, nicht bloss einen anderen wert. */
        if (wert instanceof ValueInt ganz && ganz.getSubtype() != ValueInt.Subtype.INTEGER)
        {
            return false;
        }

        return wert instanceof BaseValueNumber;
    }

    /**
     * Nur die halbe frage: schalter an UND die laufende eingabe ist ein zug an einem
     * zahlenfeld. Fuer werte, die selbst keine zahl sind, deren einzelne komponenten aber
     * schon — siehe {@link MehrfachKomponente}.
     */
    public static boolean gesteIstZug()
    {
        /* Der schalter entscheidet DELTA gegen ABSOLUT, nicht ob ueberhaupt verteilt wird:
         * aus = das verhalten von vorher (alle gewaehlten clips bekommen denselben wert). */
        if (!BBSSettings.editorMultiClipDelta.get())
        {
            return false;
        }

        return UINumericInput.getEingabeart() == UINumericInput.Eingabeart.ZUG;
    }

    /** Der zahlenwert, oder 0 wenn es keine zahl ist. Nur zusammen mit {@link #nimmtDelta} sinnvoll. */
    public static double lies(BaseValue wert)
    {
        return wert instanceof BaseValueNumber<?> zahl ? zahl.get().doubleValue() : 0D;
    }

    /**
     * Addiert das delta auf den wert und schreibt ihn zurueck.
     *
     * <p>GEKLEMMT WIRD PRO WERT, nicht fuer die gruppe: {@code BaseValueNumber.set}
     * klemmt selbst an die grenzen des jeweiligen wertes. Ein clip, der an seiner grenze
     * ankommt, bleibt dort stehen, die anderen ziehen weiter — so hat der nutzer es
     * vorgegeben („klemmen, aber die anderen nicht mitziehen"). Der preis ist bekannt und
     * gewollt: der abstand zum geklemmten clip geht dabei verloren.</p>
     *
     * @return ob der typ ueberhaupt geschrieben werden konnte
     */
    public static boolean addiere(BaseValue wert, double delta)
    {
        if (wert instanceof ValueInt ganz)
        {
            ganz.set((int) Math.round(ganz.get() + delta));

            return true;
        }

        if (wert instanceof ValueFloat komma)
        {
            komma.set((float) (komma.get() + delta));

            return true;
        }

        if (wert instanceof ValueDouble doppelt)
        {
            doppelt.set(doppelt.get() + delta);

            return true;
        }

        if (wert instanceof ValueLong lang)
        {
            lang.set(Math.round(lang.get() + delta));

            return true;
        }

        return false;
    }
}
