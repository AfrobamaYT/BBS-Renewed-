package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

import java.util.HashSet;
import java.util.Set;

public class ValueStringKeys extends BaseValueBasic<Set<String>>
{
    public ValueStringKeys(String id)
    {
        super(id, new HashSet<>());
    }

    /**
     * ⛔ ERZWINGT EINE MENGE, auch wenn der Aufrufer etwas anderes hereingibt.
     *
     * <h2>Warum das kein Misstrauen gegen den Compiler ist</h2>
     *
     * <p>Der Typ {@code Set<String>} steht nur im Quelltext. Nach der Typloeschung ist die
     * Basis-Methode {@code set(Object)}, und jeder Weg an der Uebersetzung vorbei —
     * Reflexion, JShell, ein Debugger, eine Fremdsprache — kann hier eine {@code ArrayList}
     * ablegen. Sie faellt dabei NICHT auf: das Feld nimmt sie an, {@code toData()} und
     * {@code fromData()} funktionieren weiter, und auf der Platte sieht beides gleich aus
     * (beides ist eine JSON-Liste).</p>
     *
     * <p>Auffallen tut es erst weit weg: {@code UIFormList.setupForms} ruft
     * {@code get().contains(...)}, und dort setzt der Compiler die Umwandlung nach
     * {@code Set} ein. Ergebnis:</p>
     *
     * <pre>ClassCastException: class java.util.ArrayList cannot be cast to class java.util.Set</pre>
     *
     * <p>KOSTEN, am 2026-08-01 gemessen: das Morph-Panel oeffnet gar nicht mehr, und mit ihm
     * sterben "add a replay", "Pick" und "Edit form" — alle drei laufen ueber dieselbe
     * Formliste. Drei Symptome, die nach drei kaputten Bedienelementen aussehen, aus EINEM
     * falschen Sammlungstyp. Der Nutzer hat sie als drei getrennte Fehler gemeldet, und die
     * Suche ging in drei Richtungen los.</p>
     *
     * <p>Eine Einstellung, deren Vertrag "Menge" lautet, haelt diesen Vertrag besser selbst,
     * als dass eine Zeile in einem anderen Paket daran zerbricht.</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public void set(Set<String> value)
    {
        /* Die Pruefung haengt am ROHEN Objekt, nicht am deklarierten Typ -- genau die
         * Typloeschung ist ja das Loch. Jede Mengen-Implementierung geht durch
         * (LinkedHashSet, TreeSet, ...), nur was keine Menge IST, wird umgepackt. */
        Object roh = value;

        if (roh == null || roh instanceof Set)
        {
            super.set(value);

            return;
        }

        super.set(roh instanceof java.util.Collection
            ? new HashSet<>((java.util.Collection<String>) roh)
            : new HashSet<>());
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (String s : this.value)
        {
            list.addString(s);
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.clear();

        if (!data.isList())
        {
            return;
        }

        for (BaseType type : data.asList())
        {
            if (type.isString()) this.value.add(type.asString());
        }
    }

    @Override
    protected Set<String> copyValue(Set<String> value)
    {
        return value == null ? null : new HashSet<>(value);
    }
}