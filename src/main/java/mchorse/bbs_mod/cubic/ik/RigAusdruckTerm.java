package mchorse.bbs_mod.cubic.ik;

import java.util.ArrayList;
import java.util.List;

import static mchorse.bbs_mod.cubic.ik.RigAusdruckLexer.*;

/**
 * Die arithmetischen Ebenen von Blenders einfachem Ausdruck: Summe, Produkt (mit Pythons
 * Modulo-Vorzeichen), Vorzeichen, Potenz, Atome (Klammer, Zahl, Variable, Konstante) und
 * Funktionsaufrufe ueber {@link RigAusdruckFunktion}.
 */
final class RigAusdruckTerm
{
    private final RigAusdruckParser parser;
    private final RigAusdruckLexer lex;

    RigAusdruckTerm(RigAusdruckParser parser)
    {
        this.parser = parser;
        this.lex = parser.lex;
    }

    RigAusdruckKnoten summe()
    {
        RigAusdruckKnoten a = this.produkt();

        while (this.lex.token == '+' || this.lex.token == '-')
        {
            boolean plus = this.lex.token == '+';

            this.lex.weiter();

            RigAusdruckKnoten links = a;
            RigAusdruckKnoten rechts = this.produkt();

            a = plus ? vars -> links.wert(vars) + rechts.wert(vars) : vars -> links.wert(vars) - rechts.wert(vars);
        }

        return a;
    }

    private RigAusdruckKnoten produkt()
    {
        RigAusdruckKnoten a = this.vorzeichen();

        while (this.lex.token == '*' || this.lex.token == '/' || this.lex.token == '%')
        {
            int op = this.lex.token;

            this.lex.weiter();

            RigAusdruckKnoten links = a;
            RigAusdruckKnoten rechts = this.vorzeichen();

            a = switch (op)
            {
                case '*' -> vars -> links.wert(vars) * rechts.wert(vars);
                case '/' -> vars ->
                {
                    double n = rechts.wert(vars);

                    if (n == 0D)
                    {
                        throw new ArithmeticException("Division durch Null");
                    }

                    return links.wert(vars) / n;
                };
                default -> vars ->
                {
                    double n = rechts.wert(vars);

                    if (n == 0D)
                    {
                        throw new ArithmeticException("Modulo Null");
                    }

                    /* Python: Vorzeichen des Teilers. */
                    double r = links.wert(vars) % n;

                    return r != 0D && (r < 0D) != (n < 0D) ? r + n : r;
                };
            };
        }

        return a;
    }

    private RigAusdruckKnoten vorzeichen()
    {
        if (this.lex.token == '+')
        {
            this.lex.weiter();

            return this.vorzeichen();
        }

        if (this.lex.token == '-')
        {
            this.lex.weiter();

            RigAusdruckKnoten a = this.vorzeichen();

            return vars -> -a.wert(vars);
        }

        return this.potenz();
    }

    private RigAusdruckKnoten potenz()
    {
        RigAusdruckKnoten a = this.atom();

        if (this.lex.token == POW)
        {
            this.lex.weiter();

            RigAusdruckKnoten b = this.vorzeichen();

            return vars -> Math.pow(a.wert(vars), b.wert(vars));
        }

        return a;
    }

    private RigAusdruckKnoten atom()
    {
        if (this.lex.token == '(')
        {
            this.lex.weiter();

            RigAusdruckKnoten a = this.parser.ausdruck();

            if (this.lex.token != ')')
            {
                throw new IllegalArgumentException("')' fehlt in: " + this.lex.text);
            }

            this.lex.weiter();

            return a;
        }

        if (this.lex.token == ZAHL)
        {
            double z = this.lex.zahl;

            this.lex.weiter();

            return vars -> z;
        }

        if (this.lex.token != NAME)
        {
            throw new IllegalArgumentException("Unerwartetes Zeichen in: " + this.lex.text);
        }

        String n = this.lex.name;

        this.lex.weiter();

        if (this.lex.token == '(')
        {
            return this.funktion(n);
        }

        int index = this.lex.namen.lastIndexOf(n);

        if (index >= 0)
        {
            return vars -> index < vars.length ? vars[index] : 0D;
        }

        return switch (n)
        {
            case "pi" -> vars -> Math.PI;
            case "True" -> vars -> 1D;
            case "False" -> vars -> 0D;
            default -> throw new IllegalArgumentException("Unbekannter Name '" + n + "' in: " + this.lex.text);
        };
    }

    private RigAusdruckKnoten funktion(String n)
    {
        List<RigAusdruckKnoten> args = new ArrayList<>();

        this.lex.weiter();

        if (this.lex.token != ')')
        {
            args.add(this.parser.ausdruck());

            while (this.lex.token == ',')
            {
                this.lex.weiter();
                args.add(this.parser.ausdruck());
            }
        }

        if (this.lex.token != ')')
        {
            throw new IllegalArgumentException("')' fehlt nach " + n + " in: " + this.lex.text);
        }

        this.lex.weiter();

        RigAusdruckKnoten f = RigAusdruckFunktion.funktion(n, args);

        if (f == null)
        {
            throw new IllegalArgumentException("Unbekannte Funktion " + n + "/" + args.size() + " in: " + this.lex.text);
        }

        return f;
    }
}
