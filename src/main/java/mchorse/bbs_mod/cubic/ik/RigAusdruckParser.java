package mchorse.bbs_mod.cubic.ik;

import java.util.ArrayList;
import java.util.List;

import static mchorse.bbs_mod.cubic.ik.RigAusdruckLexer.*;

/**
 * Die logischen Ebenen von Blenders einfachem Ausdruck: {@code a if c else b}, {@code or},
 * {@code and}, {@code not} und Python-Vergleichsketten. Die Arithmetik darunter liegt in
 * {@link RigAusdruckTerm}; beide teilen sich den {@link RigAusdruckLexer}.
 */
final class RigAusdruckParser
{
    final RigAusdruckLexer lex;
    private final RigAusdruckTerm term;

    RigAusdruckParser(String text, List<String> namen)
    {
        this.lex = new RigAusdruckLexer(text, namen);
        this.term = new RigAusdruckTerm(this);
    }

    RigAusdruckKnoten ausdruck()
    {
        RigAusdruckKnoten a = this.oder();

        if (this.lex.token == IF)
        {
            this.lex.weiter();

            RigAusdruckKnoten bedingung = this.oder();

            if (this.lex.token != ELSE)
            {
                throw new IllegalArgumentException("'else' fehlt in: " + this.lex.text);
            }

            this.lex.weiter();

            RigAusdruckKnoten b = this.ausdruck();

            return vars -> bedingung.wert(vars) != 0D ? a.wert(vars) : b.wert(vars);
        }

        return a;
    }

    private RigAusdruckKnoten oder()
    {
        RigAusdruckKnoten a = this.und();

        if (this.lex.token == OR)
        {
            this.lex.weiter();

            RigAusdruckKnoten b = this.oder();

            /* Python: `a or b` liefert a, wenn a wahr ist, sonst b. */
            return vars ->
            {
                double x = a.wert(vars);

                return x != 0D ? x : b.wert(vars);
            };
        }

        return a;
    }

    private RigAusdruckKnoten und()
    {
        RigAusdruckKnoten a = this.nicht();

        if (this.lex.token == AND)
        {
            this.lex.weiter();

            RigAusdruckKnoten b = this.und();

            return vars ->
            {
                double x = a.wert(vars);

                return x == 0D ? x : b.wert(vars);
            };
        }

        return a;
    }

    private RigAusdruckKnoten nicht()
    {
        if (this.lex.token == NOT)
        {
            this.lex.weiter();

            RigAusdruckKnoten a = this.nicht();

            return vars -> a.wert(vars) == 0D ? 1D : 0D;
        }

        return this.vergleich();
    }

    private static int vergleicher(int token)
    {
        return token == EQ || token == NE || token == LE || token == GE || token == '<' || token == '>' ? token : -1;
    }

    private static boolean pruefe(int op, double a, double b)
    {
        return switch (op)
        {
            case EQ -> a == b;
            case NE -> a != b;
            case LE -> a <= b;
            case GE -> a >= b;
            case '<' -> a < b;
            case '>' -> a > b;
            default -> false;
        };
    }

    private RigAusdruckKnoten vergleich()
    {
        RigAusdruckKnoten erst = this.term.summe();
        List<Integer> ops = new ArrayList<>();
        List<RigAusdruckKnoten> glieder = new ArrayList<>();

        glieder.add(erst);

        while (vergleicher(this.lex.token) >= 0)
        {
            ops.add(this.lex.token);
            this.lex.weiter();
            glieder.add(this.term.summe());
        }

        if (ops.isEmpty())
        {
            return erst;
        }

        /* Python-Vergleichskette: a < b <= c heisst (a < b) and (b <= c). */
        return vars ->
        {
            double links = glieder.get(0).wert(vars);

            for (int i = 0; i < ops.size(); i++)
            {
                double rechts = glieder.get(i + 1).wert(vars);

                if (!pruefe(ops.get(i), links, rechts))
                {
                    return 0D;
                }

                links = rechts;
            }

            return 1D;
        };
    }
}
