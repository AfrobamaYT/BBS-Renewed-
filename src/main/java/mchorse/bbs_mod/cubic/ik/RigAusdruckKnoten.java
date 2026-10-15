package mchorse.bbs_mod.cubic.ik;

/** Ein Knoten des geparsten Treiber-Ausdrucks: liefert seinen Wert fuer die Variablenbelegung. */
interface RigAusdruckKnoten
{
    double wert(double[] vars);
}
