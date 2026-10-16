package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

/**
 * One bone's CEM variables (tx..sz, visible) and their mapping to and from the bone, as Blockbench's
 * CEM editor maps them: rotation {@code (-rx, -ry, rz)} in radians, scale as is, and a position that
 * depends on the bone's kind. With {@code Op = parent.initial.translate}:
 * <ul>
 *     <li><b>Part:</b> {@code translate = (-tx, 24 - ty, tz)} (the entity origin height).</li>
 *     <li><b>Direct submodel of a part:</b> {@code translate = (-tx, -ty, tz)}.</li>
 *     <li><b>Deeper submodel, or a reparented part:</b> {@code translate = (Op.x - tx, Op.y - ty, Op.z + tz)}.</li>
 * </ul>
 * The seeds are the inverse of the writeback, so a bone no statement mentions writes back exactly the
 * pose it came in with. From BBS FS b44e56c87, 26ca19c49, d3f28b58f, 355b61b40, 6d7aa0fd1 (Wemppy4/bbs-fs, MIT).
 */
final class CemBinding
{
    static final int TOP = 0;
    static final int SUB1 = 1;
    static final int SUBN = 2;

    /** The vertical offset CEM applies to parts (entity model origin height) */
    private static final float Y_OFFSET = 24F;

    private final ModelGroup group;
    private final int kind;

    private final Variable tx, ty, tz;
    private final Variable rx, ry, rz;
    private final Variable sx, sy, sz;
    private final Variable visible, visibleBoxes;

    CemBinding(CemParser p, ModelGroup group, int kind)
    {
        this.group = group;
        this.kind = kind;

        String id = group.id;

        this.tx = p.getOrCreateVariable(id + ".tx");
        this.ty = p.getOrCreateVariable(id + ".ty");
        this.tz = p.getOrCreateVariable(id + ".tz");
        this.rx = p.getOrCreateVariable(id + ".rx");
        this.ry = p.getOrCreateVariable(id + ".ry");
        this.rz = p.getOrCreateVariable(id + ".rz");
        this.sx = p.getOrCreateVariable(id + ".sx");
        this.sy = p.getOrCreateVariable(id + ".sy");
        this.sz = p.getOrCreateVariable(id + ".sz");
        this.visible = p.getOrCreateVariable(id + ".visible");
        this.visibleBoxes = p.getOrCreateVariable(id + ".visible_boxes");
    }

    boolean shown()
    {
        return this.visible.doubleValue() != 0;
    }

    boolean boxesShown()
    {
        return this.visibleBoxes.doubleValue() != 0;
    }

    /**
     * Seed the variables: from the vanilla frame where it has the part, else from the pose standing in
     * {@code current}. OptiFine evaluates a pack over the frame vanilla just posed, so {@code head.ry}
     * arrives holding the vanilla head yaw; Fresh Moves writes {@code head.ry = wraprad(head.ry)}, which
     * against a rest-pose seed froze the player's head (BBS FS d3f28b58f, 6d7aa0fd1).
     */
    void reset(CemVanillaSeed seed)
    {
        CemVanillaSeed.Part part = seed == null ? null : seed.get(this.group.id);

        if (part != null)
        {
            this.seed(part);

            return;
        }

        Transform current = this.group.current;
        Vector3f translate = current.translate;
        Vector3f pivot = this.group.initial.translate;

        /* The writeback lays X down mirrored about the pivot; undo that first */
        float x = 2F * pivot.x - translate.x;

        switch (this.kind)
        {
            case SUB1 ->
            {
                this.tx.set(-x);
                this.ty.set(-translate.y);
                this.tz.set(translate.z);
            }
            case SUBN ->
            {
                Vector3f parent = this.group.parent.initial.translate;

                this.tx.set(parent.x - x);
                this.ty.set(parent.y - translate.y);
                this.tz.set(translate.z - parent.z);
            }
            default ->
            {
                this.tx.set(-x);
                this.ty.set(Y_OFFSET - translate.y);
                this.tz.set(translate.z);
            }
        }

        this.rx.set(-Math.toRadians(current.rotate.x));
        this.ry.set(-Math.toRadians(current.rotate.y));
        this.rz.set(Math.toRadians(current.rotate.z));

        this.sx.set(current.scale.x);
        this.sy.set(current.scale.y);
        this.sz.set(current.scale.z);

        /* Nothing resets what the last frame wrote, so every frame starts from shown */
        this.visible.set(1);
        this.visibleBoxes.set(1);
    }

    /**
     * The vanilla frame's values for the part: its position, angle, scale and flag outright. A part of
     * the file hangs inside vanilla's part of the same name, so its position is vanilla's whatever the
     * file says; a reparented or deeper part is placed against its vanilla parent, a top-level one
     * against the model. Fresh Animations' fox is the proof: vanilla holds its body at (0, 8, -6) and
     * pitches it ninety degrees, and the pack's head and tail are placed for that frame.
     */
    private void seed(CemVanillaSeed.Part part)
    {
        boolean local = this.kind == SUBN;

        this.tx.set(local ? part.tx : part.ax);
        this.ty.set(local ? part.ty : part.ay);
        this.tz.set(local ? part.tz : part.az);
        this.rx.set(part.rx);
        this.ry.set(part.ry);
        this.rz.set(part.rz);
        this.sx.set(part.sx);
        this.sy.set(part.sy);
        this.sz.set(part.sz);
        this.visible.set(part.visible ? 1 : 0);
        this.visibleBoxes.set(1);
    }

    void writeback()
    {
        Transform current = this.group.current;
        Vector3f pivot = this.group.initial.translate;
        float x, y, z;

        switch (this.kind)
        {
            case SUB1 ->
            {
                x = safe(-this.tx.doubleValue());
                y = safe(-this.ty.doubleValue());
                z = safe(this.tz.doubleValue());
            }
            case SUBN ->
            {
                Vector3f parent = this.group.parent.initial.translate;

                x = safe(parent.x - this.tx.doubleValue());
                y = safe(parent.y - this.ty.doubleValue());
                z = safe(parent.z + this.tz.doubleValue());
            }
            default ->
            {
                x = safe(-this.tx.doubleValue());
                y = safe(Y_OFFSET - this.ty.doubleValue());
                z = safe(this.tz.doubleValue());
            }
        }

        /* BBS lays a bone's X offset down mirrored (ICubicRenderer.translateGroup negates translate.x -
         * pivot.x), so the bone lands at X only when written as X's reflection about the pivot */
        current.translate.set(2F * pivot.x - x, y, z);

        current.rotate.set(
            safe(-Math.toDegrees(this.rx.doubleValue())),
            safe(-Math.toDegrees(this.ry.doubleValue())),
            safe(Math.toDegrees(this.rz.doubleValue()))
        );

        current.scale.set(safeScale(this.sx.doubleValue()), safeScale(this.sy.doubleValue()), safeScale(this.sz.doubleValue()));

        /* Visibility is the tree's, not the bone's — see CemAnimation.show */
    }

    private static float safe(double value)
    {
        return Double.isFinite(value) ? (float) value : 0F;
    }

    private static float safeScale(double value)
    {
        return Double.isFinite(value) ? (float) value : 1F;
    }
}
