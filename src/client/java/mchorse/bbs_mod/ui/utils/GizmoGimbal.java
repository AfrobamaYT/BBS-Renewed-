package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The rings of {@link TransformSpace#GIMBAL} lie on the euler channels' own axes, so the ring
 * a bone is grabbed by is the axis its single channel really turns it about. Maya's gimbal
 * mode: Z stays fixed, Y turns with Z, X with Y and Z (2026-10-15).
 *
 * <p>Each viewport supplies the parent and full rotation frames before placement, for both
 * the visual and pick passes. Cache entries cover bones, forms and animation states;
 * model blocks and film anchors supply their composed matrices directly.</p>
 */
public final class GizmoGimbal
{
    /** Ring axes in the placed (origin) frame, as columns X, Y, Z */
    private static final Matrix3f ACHSEN = new Matrix3f();
    private static boolean gesetzt;
    private static boolean gueltig;

    private GizmoGimbal()
    {}

    /** The film's bone placement: the flavour it always picked, plus the channel axes remembered */
    public static Matrix4f merken(boolean local, MatrixCacheEntry entry)
    {
        return entry == null ? merken(local, null, null) : merken(local, entry.origin(), entry.matrix());
    }

    /** Placement without a bone cache, for model-block transforms and film anchor offsets. */
    public static Matrix4f merken(boolean local, Matrix4f origin, Matrix4f matrix)
    {
        gesetzt = matrix != null && origin != null && achsen(origin, matrix);

        return local ? matrix : origin;
    }

    /** Unattached anchors may already have mutated the default frame; factor their own offset out. */
    public static Matrix4f anker(TransformSpace space, Matrix4f parent, Matrix4f full, Transform offset)
    {
        Matrix4f origin = space == TransformSpace.GIMBAL
            ? new Matrix4f(full).mul(offset.createMatrix().invert()) : parent;

        return merken(false, MatrixStackUtils.stripScale(origin), full);
    }

    /** Drawing is deferred; another viewport must not replace an already submitted ring frame. */
    public record Lage(TransformSpace raum, Matrix3f achsen)
    {}

    public static Lage sichern()
    {
        return new Lage(GizmoAnsicht.raum, gueltig ? new Matrix3f(ACHSEN) : null);
    }

    public static void anwenden(Lage lage)
    {
        GizmoAnsicht.raum = lage.raum();
        gueltig = lage.achsen() != null;

        if (gueltig)
        {
            ACHSEN.set(lage.achsen());
        }
    }

    /** From {@link Gizmo#reorientForSpace}, once per placement, so nothing carries over to a host that remembers nothing */
    static void platzieren(TransformSpace space)
    {
        GizmoAnsicht.raum = space;
        gueltig = gesetzt && space == TransformSpace.GIMBAL;
        gesetzt = false;
    }

    /** Before a ring is drawn or picked: turns the stack onto the ring's channel axis; true when it pushed */
    static boolean ausrichten(PoseStack stack, Axis axis)
    {
        if (!gueltig)
        {
            return false;
        }

        Vector3f von = new Vector3f(axis == Axis.X ? 1F : 0F, axis == Axis.Y ? 1F : 0F, axis == Axis.Z ? 1F : 0F);

        stack.pushPose();
        stack.rotate(new Quaternionf().rotationTo(von, ACHSEN.getColumn(axis.ordinal(), new Vector3f())));

        return true;
    }

    /**
     * Channel axes of a ZYX euler bone in its origin frame: X is the bone's own X (Rz·Ry·X, the
     * innermost Rx leaves it), Z the origin's, Y their common perpendicular (Rz·Y). This also
     * holds on cubic models: there origin⁻¹·matrix is the Ry(180) conjugate of the channels'
     * turn, itself a ZYX turn whose axes are the same lines (measured 2026-10-15 on the head at
     * 20/30/45 deg, against the gizmo drag's measured response axes). False at the euler pole.
     */
    private static boolean achsen(Matrix4f origin, Matrix4f matrix)
    {
        Matrix3f o = new Matrix4f(origin).normalize3x3().get3x3(new Matrix3f());
        Vector3f x = o.invert().mul(new Matrix4f(matrix).normalize3x3().get3x3(new Matrix3f())).getColumn(0, new Vector3f());
        Vector3f z = new Vector3f(0F, 0F, 1F);
        Vector3f y = new Vector3f(z).cross(x);

        if (y.lengthSquared() < 1.0E-6F || x.lengthSquared() < 1.0E-6F)
        {
            return false;
        }

        ACHSEN.setColumn(0, x.normalize()).setColumn(1, y.normalize()).setColumn(2, z);

        return true;
    }
}
