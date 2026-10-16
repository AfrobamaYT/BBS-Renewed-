package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * In {@link TransformSpace#VIEW} the handles face the eye instead of only lying parallel to the
 * screen (BBS FS 861257a74, Wemppy4). Off centre, perspective showed them from the side: at the
 * film viewport's right edge the Z bar drew 60 px against 48.7 for X and the view ring went oval.
 * Only the DRAWING frame changes, in the visual and pick pass alike; {@link Gizmo#lastRenderMatrix}
 * is taken before, so drags and pick projections keep the orthonormal camera basis.
 */
final class GizmoAnsicht
{
    /** The space the handles were last placed in ({@link Gizmo#reorientForSpace}), {@code null} when left untouched */
    static TransformSpace raum;

    private GizmoAnsicht()
    {}

    /** Lays the third column on the unit ray to the eye: X/Y stay exact, Z collapses to a point, and at the centre nothing changes. */
    static void scheren(PoseStack stack)
    {
        Matrix4f matrix = stack.last().pose();
        Vector3f toCamera = matrix.getTranslation(new Vector3f()).negate();

        if (raum != TransformSpace.VIEW || toCamera.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        toCamera.normalize();
        matrix.m20(toCamera.x);
        matrix.m21(toCamera.y);
        matrix.m22(toCamera.z);
    }
}
