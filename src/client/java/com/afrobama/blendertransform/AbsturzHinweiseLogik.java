package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.AbsturzHinweise;

/** {@code SystemReport.<init>} @RETURN — text of the "BBS Renewed" crash report entry. */
public final class AbsturzHinweiseLogik
{
    private AbsturzHinweiseLogik()
    {}

    public static String text()
    {
        return AbsturzHinweise.text();
    }
}
