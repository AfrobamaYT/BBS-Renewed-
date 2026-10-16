package mchorse.bbs_mod.film;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import org.joml.Matrix4f;

/**
 * Keeps an anchored form where it is when what it hangs off changes: the new anchor's transform is
 * rebased so that T_new = P_new⁻¹ · W_old, where both matrices are measured through the film
 * controller's own composition rather than derived again, as the parent frame depends on the
 * target's pose, its own anchor chain and the inherit flags. From BBS FS a220e30c5
 * (Wemppy4/bbs-fs, MIT).
 */
public final class AnchorRebase
{
    private AnchorRebase()
    {}

    /**
     * Rebase {@code to}'s transform so the replay's form sits exactly where {@code from} had it at
     * this moment; returns whether it could (no new target, or a target squashed to zero scale, has
     * no frame to invert). Both anchors are measured settled, not mid-blend.
     */
    public static boolean keepWorldTransform(IntObjectMap<IEntity> entities, IEntity entity, int index, float transition, Anchor from, Anchor to)
    {
        if (entity == null || to.replay == Anchor.NO_ATTACHMENT)
        {
            return false;
        }

        Matrix4f own = BaseFilmController.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
        Anchor parent = to.copy();

        parent.transform.identity();

        Matrix4f world = frame(entities, settled(from), own, transition, index);
        Matrix4f target = frame(entities, parent, own, transition, index);

        if (target == null || Math.abs(target.determinant()) < 1E-9F)
        {
            return false;
        }

        to.transform.fromMatrix(new Matrix4f(target).invert().mul(world));

        return true;
    }

    /** Where the form sits under an anchor: the anchor's frame, or the replay's own without a target */
    private static Matrix4f frame(IntObjectMap<IEntity> entities, Anchor anchor, Matrix4f own, float transition, int index)
    {
        Matrix4f matrix = BaseFilmController.getTotalMatrix(entities, anchor, new Matrix4f(own), 0D, 0D, 0D, transition, index).a;

        return matrix == null ? new Matrix4f(own) : matrix;
    }

    private static Anchor settled(Anchor anchor)
    {
        Anchor copy = anchor.copy();

        copy.previous = null;
        copy.x = 1F;

        return copy;
    }
}
