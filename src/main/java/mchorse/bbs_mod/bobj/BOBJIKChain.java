package mchorse.bbs_mod.bobj;

/**
 * Eine IK-Kette aus einer {@code arm_ik}-Zeile — geschrieben vom eigenen
 * io_export_bobj-Fork, 1:1 Blenders IK-Constraint: die Kette endet am {@code tip},
 * greift {@code chainLength} Knochen aufwaerts (0 = bis zur Wurzel) und zielt auf
 * {@code target}. {@code -} steht fuer "kein Pol-Ziel"; der Pol-Winkel reist in Grad,
 * wie ihn {@code ModelIKConfig} erwartet.
 */
public record BOBJIKChain(String tip, String target, int chainLength, String poleTarget, float poleAngle, float weight, boolean tipRotation, boolean stretch)
{
    /** Liest {@code arm_ik <tip> <target> <chainLen> <poleTarget|-> <poleAngleGrad> <weight> <tipRot> <stretch>}. */
    public static BOBJIKChain parse(String[] tokens)
    {
        if (tokens.length < 9)
        {
            throw new IllegalArgumentException("arm_ik erwartet 8 werte, bekam " + (tokens.length - 1));
        }

        return new BOBJIKChain(
            tokens[1],
            tokens[2],
            Integer.parseInt(tokens[3]),
            tokens[4].equals("-") ? "" : tokens[4],
            Float.parseFloat(tokens[5]),
            Float.parseFloat(tokens[6]),
            !tokens[7].equals("0"),
            !tokens[8].equals("0"));
    }
}
