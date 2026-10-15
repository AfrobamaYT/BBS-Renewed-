package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;

/**
 * Der Rahmenwechsel zwischen BBS und Blender. Der BOBJ-Exporter schreibt jede Ruhelage als
 * {@code G · W · matrix_local} (G = Achsenwandlung Z-oben nach Y-oben, W = Weltmatrix des
 * Armature-Objekts); Posen folgen derselben Regel. Blenders Constraints rechnen in seinem
 * WELTRAUM ({@code object_to_world · pose_mat}), also {@code W · inv(G·W) · M_bbs = inv(G) · M_bbs}.
 *
 * <p>⛔ Ohne diesen Wechsel stimmt jede achsbezogene Rechnung nur zufaellig: "Copy Location X"
 * meint Blenders X (in BBS -X), die Track-To-Hochachse ist Blenders Welt-Z (in BBS +Y), der
 * Floor-Constraint klemmt eine Blender-Weltachse. Seit 2026-09-13 rechnet die Laufzeit deshalb
 * in Blenders Rahmen und wandelt an der Grenze — G und W stehen in {@code rig.json}.
 */
public final class RigRahmen
{
    /** {@code object_to_world} des Armature-Objekts (Blender). */
    public final float[] welt;
    private final float[] gw;
    private final float[] gwInv;
    private final float[] gInv;
    private final float[] g;

    public RigRahmen(float[] g, float[] w)
    {
        this.g = g;
        this.welt = w;
        this.gw = RigMathe.mul(g, w);
        this.gwInv = RigMathe.invertiert(this.gw);
        this.gInv = RigMathe.invertiert(g);
    }

    public static RigRahmen einheit()
    {
        return new RigRahmen(RigMathe.einheit(), RigMathe.einheit());
    }

    public static RigRahmen lesen(ListType g, ListType w)
    {
        return new RigRahmen(werte(g), werte(w));
    }

    private static float[] werte(ListType l)
    {
        float[] m = RigMathe.einheit();

        if (l == null || l.size() != 16)
        {
            return m;
        }

        /* Blenders Zeilenschreibweise (row-major) in Spaltenlayout drehen. */
        for (int r = 0; r < 4; r++)
        {
            for (int c = 0; c < 4; c++)
            {
                m[c * 4 + r] = l.getFloat(r * 4 + c);
            }
        }

        return m;
    }

    /** {@code inv(G·W)} als Kopie — fuers Skinning ohne Rahmen-Objekt. */
    public float[] poseWandler()
    {
        return RigMathe.kopie(this.gwInv);
    }

    /** BBS-Armature -> Blender-Welt. */
    public float[] zuWelt(float[] bbs)
    {
        return RigMathe.mul(this.gInv, bbs);
    }

    /** Blender-Welt -> BBS-Armature. */
    public float[] ausWelt(float[] welt)
    {
        return RigMathe.mul(this.g, welt);
    }

    /** BBS-Armature -> Blender-Pose (Armature-Raum des Objekts). */
    public float[] zuPose(float[] bbs)
    {
        return RigMathe.mul(this.gwInv, bbs);
    }

    /** Blender-Pose -> BBS-Armature. */
    public float[] ausPose(float[] pose)
    {
        return RigMathe.mul(this.gw, pose);
    }

    /** Blender-Pose -> Blender-Welt. */
    public float[] poseZuWelt(float[] pose)
    {
        return RigMathe.mul(this.welt, pose);
    }

    /** Blender-Welt -> Blender-Pose. */
    public float[] weltZuPose(float[] welt)
    {
        return RigMathe.mul(RigMathe.invertiert(this.welt), welt);
    }

    /** {@code pchan->pose_mat} in Blenders Pose-Raum. */
    public float[] poseMat(BOBJBone b)
    {
        return this.zuPose(b.mat.get(new float[16]));
    }

    /** {@code bone->arm_mat} in Blenders Pose-Raum. */
    public float[] armMat(BOBJBone b)
    {
        return this.zuPose(b.boneMat.get(new float[16]));
    }

    /** Blenders Welt-Matrix des Knochens. */
    public float[] weltMat(BOBJBone b)
    {
        return this.zuWelt(b.mat.get(new float[16]));
    }

    /** Schreibt eine Blender-Welt-Matrix als BBS-Matrix in den Knochen. */
    public void setzeWeltMat(BOBJBone b, float[] welt)
    {
        b.mat.set(this.ausWelt(welt));
    }
}
