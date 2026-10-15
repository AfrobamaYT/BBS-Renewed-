package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.PoseStack;

/** PoseStack.Pose.set von 1.21.5+ vor 1.21.5: Matrix und Normalenmatrix uebernehmen. Injiziert. */
public interface PoseAlt
{
    default void set(PoseStack.Pose quelle)
    {
        PoseStack.Pose ziel = (PoseStack.Pose) this;

        ziel.pose().set(quelle.pose());
        ziel.normal().set(quelle.normal());
    }
}
