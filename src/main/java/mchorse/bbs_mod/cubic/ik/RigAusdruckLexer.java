package mchorse.bbs_mod.cubic.ik;

import java.util.List;

/**
 * Der Zeichenleser von Blenders einfachem Treiber-Ausdruck ({@code expr_pylike_eval.cc}):
 * Zahlen (auch mit Exponent), Namen, die Woerter and/or/not/if/else, die Zweizeichen-Operatoren
 * {@code == != <= >= **}; alles andere ist sein eigenes Zeichen als Token.
 */
final class RigAusdruckLexer
{
    static final int ENDE = 0, ZAHL = 1, NAME = 2, EQ = 3, NE = 4, LE = 5, GE = 6, AND = 7, OR = 8,
        NOT = 9, IF = 10, ELSE = 11, POW = 12;

    final String text;
    final List<String> namen;
    int token;
    double zahl;
    String name;
    private int pos;

    RigAusdruckLexer(String text, List<String> namen)
    {
        this.text = text;
        this.namen = namen;
        this.weiter();
    }

    void weiter()
    {
        while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos)))
        {
            this.pos++;
        }

        if (this.pos >= this.text.length())
        {
            this.token = ENDE;

            return;
        }

        char c = this.text.charAt(this.pos);

        if (Character.isDigit(c) || (c == '.' && this.pos + 1 < this.text.length() && Character.isDigit(this.text.charAt(this.pos + 1))))
        {
            this.zahlLesen();

            return;
        }

        if (Character.isLetter(c) || c == '_')
        {
            int start = this.pos;

            while (this.pos < this.text.length() && (Character.isLetterOrDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '_'))
            {
                this.pos++;
            }

            this.name = this.text.substring(start, this.pos);
            this.token = switch (this.name)
            {
                case "and" -> AND;
                case "or" -> OR;
                case "not" -> NOT;
                case "if" -> IF;
                case "else" -> ELSE;
                default -> NAME;
            };

            return;
        }

        String zwei = this.pos + 1 < this.text.length() ? this.text.substring(this.pos, this.pos + 2) : "";

        switch (zwei)
        {
            case "==" -> { this.token = EQ; this.pos += 2; return; }
            case "!=" -> { this.token = NE; this.pos += 2; return; }
            case "<=" -> { this.token = LE; this.pos += 2; return; }
            case ">=" -> { this.token = GE; this.pos += 2; return; }
            case "**" -> { this.token = POW; this.pos += 2; return; }
            default ->
            {}
        }

        this.token = c;
        this.pos++;
    }

    private void zahlLesen()
    {
        int start = this.pos;

        while (this.pos < this.text.length() && (Character.isDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '.'))
        {
            this.pos++;
        }

        if (this.pos < this.text.length() && (this.text.charAt(this.pos) == 'e' || this.text.charAt(this.pos) == 'E'))
        {
            int p = this.pos + 1;

            if (p < this.text.length() && (this.text.charAt(p) == '+' || this.text.charAt(p) == '-'))
            {
                p++;
            }

            if (p < this.text.length() && Character.isDigit(this.text.charAt(p)))
            {
                this.pos = p;

                while (this.pos < this.text.length() && Character.isDigit(this.text.charAt(this.pos)))
                {
                    this.pos++;
                }
            }
        }

        this.zahl = Double.parseDouble(this.text.substring(start, this.pos));
        this.token = ZAHL;
    }
}
