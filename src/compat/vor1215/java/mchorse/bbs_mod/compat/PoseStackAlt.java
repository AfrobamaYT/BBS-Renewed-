package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * PoseStack.isEmpty von 1.21.5+: vor 1.21.5 hiess die Abfrage clear() (wahr, wenn nur die
 * Grundpose uebrig ist). Injiziert in PoseStack.
 */
public interface PoseStackAlt
{
    default boolean isEmpty()
    {
        return ((PoseStack) this).clear();
    }
}
