package mchorse.bbs_mod.cubic.ik;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blenders "einfacher Ausdruck" fuer Treiber ({@code blenlib/intern/expr_pylike_eval.cc}):
 * die Python-Teilmenge, die Blender ohne Python auswertet — Zahlen, Variablen, {@code pi},
 * {@code True/False}, {@code + - * / == != < <= > >= and or not}, das Bedingungs-Idiom
 * {@code a if c else b}, Vergleichsketten und die Funktionen aus {@link RigAusdruckFunktion}.
 * Dazu {@code **} und {@code %}, die Blender ueber Python rechnet, hier mit derselben Bedeutung.
 *
 * <p>Warum kein Molang-Parser (BBS' MathBuilder): dessen Grammatik ist eine andere
 * ({@code ^}, {@code ? :}, {@code &&}), ein Blender-Ausdruck wie {@code (1-sy)*pi if x else 0}
 * kaeme dort nie an. Ein Baum je Ausdruck, einmal geparst, dann nur noch ausgewertet — ein
 * Fehler beim Parsen ist LAUT (Ausnahme), nicht still 0.
 */
public final class RigAusdruck
{
    private final RigAusdruckKnoten wurzel;
    private final List<String> namen;

    private RigAusdruck(RigAusdruckKnoten wurzel, List<String> namen)
    {
        this.wurzel = wurzel;
        this.namen = namen;
    }

    /** Parst {@code text} mit den Variablennamen {@code namen} (Index = Position). */
    public static RigAusdruck parse(String text, List<String> namen)
    {
        RigAusdruckParser p = new RigAusdruckParser(text, namen);
        RigAusdruckKnoten k = p.ausdruck();

        if (p.lex.token != RigAusdruckLexer.ENDE)
        {
            throw new IllegalArgumentException("Rest nach Ausdruck: " + text);
        }

        return new RigAusdruck(k, namen);
    }

    /** Wertet aus; NaN, wenn ein Rechenfehler auftrat (Blender: Division by Zero / Math Domain). */
    public double wert(double[] vars)
    {
        try
        {
            double v = this.wurzel.wert(vars);

            return Double.isFinite(v) ? v : Double.NaN;
        }
        catch (ArithmeticException e)
        {
            return Double.NaN;
        }
    }

    public List<String> namen()
    {
        return this.namen;
    }

    /** Rein informativ: Blenders Namensregel — was sich hier nicht parsen laesst, ginge in
     *  Blender ueber Python, dessen Ergebnis wir nicht nachbilden. */
    public static boolean einfach(String text, Map<String, ?> namen)
    {
        try
        {
            parse(text, new ArrayList<>(namen.keySet()));

            return true;
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }
}
