package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One captured bone/part frame: the full matrix, the origin flavour (the frame
 * BEFORE the bone's own rotation), and — for model bones — the bone's EVALUATED
 * channel rotation (ZYX euler radians) after the channels phase
 * (rest &rarr; actions &rarr; pose). The rotation is the effective additive
 * total the renderer composes, which the gizmo needs to edit one additive layer
 * (a pose overlay) of it correctly; {@code null} for non-bone entries and for
 * bones whose rotation left the euler channels (quaternion mode).
 */
public final class MatrixCacheEntry
{
    private final Matrix4f matrix;
    private final Matrix4f origin;
    private final Vector3f evaluatedRotation = new Vector3f();
    private boolean hasEvaluatedRotation;

    public MatrixCacheEntry(Matrix4f matrix, Matrix4f origin)
    {
        this.matrix = matrix;
        this.origin = origin;
    }

    public MatrixCacheEntry(Matrix4f matrix, Matrix4f origin, Vector3f evaluatedRotation)
    {
        this(matrix, origin);
        this.setEvaluatedRotation(evaluatedRotation);
    }

    public Matrix4f matrix()
    {
        return this.matrix;
    }

    public Matrix4f origin()
    {
        return this.origin;
    }

    public Vector3f evaluatedRotation()
    {
        return this.hasEvaluatedRotation ? this.evaluatedRotation : null;
    }

    /** Writable storage owned by this entry; call {@link #clearEvaluatedRotation()} when invalid. */
    public Vector3f evaluatedRotationForWrite()
    {
        this.hasEvaluatedRotation = true;

        return this.evaluatedRotation;
    }

    public void clearEvaluatedRotation()
    {
        this.hasEvaluatedRotation = false;
    }

    public void setEvaluatedRotation(Vector3f rotation)
    {
        this.hasEvaluatedRotation = rotation != null;

        if (rotation != null)
        {
            this.evaluatedRotation.set(rotation);
        }
    }

    /** Capture an evaluated channel rotation into this entry's reusable vector. */
    public void captureEvaluatedRotation(Transform current, Quaternionf orient, boolean degrees)
    {
        if (current.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.clearEvaluatedRotation();

            return;
        }

        Vector3f radians = this.evaluatedRotationForWrite();

        if (degrees)
        {
            radians.set(
                MathUtils.toRad(current.rotate.x),
                MathUtils.toRad(current.rotate.y),
                MathUtils.toRad(current.rotate.z)
            );
        }
        else
        {
            radians.set(current.rotate);
        }

        if (orient != null)
        {
            Quaternionf channels = Matrices.toQuaternionZYXRadians(radians.x, radians.y, radians.z);

            /* |dot| = cos(theta/2) between the two rotations (double cover); anything
             * under about 1.6 degrees apart means a genuinely multiplicative stack. */
            if (Math.abs(channels.dot(orient)) < 0.9999F)
            {
                this.clearEvaluatedRotation();
            }
        }
    }
}
