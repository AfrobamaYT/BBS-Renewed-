package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BOBJBone
{
    /* Meta information */
    public int index;
    public String name;
    public String parent;
    public BOBJBone parentBone;

    /* Transformations */
    public final Transform transform = new Transform();

    /**
     * Computed bone matrix which is used for transformations. This 
     * matrix isn't multiplied by inverse bone matrix. 
     */
    public Matrix4f mat = new Matrix4f();

    public Matrix4f originMat = new Matrix4f();

    /**
     * Bone matrix 
     */
    public Matrix4f boneMat;

    /**
     * Inverse bone matrix 
     */
    public Matrix4f invBoneMat = new Matrix4f();

    /**
     * Relative-to-parent bone matrix
     */
    public Matrix4f relBoneMat = new Matrix4f();

    /**
     * Rest tail in armature space, straight from the {@code arm_bone} line. Parsed
     * since forever and dropped on the floor until the Blender IK port needed it:
     * Blender sizes every IK segment as {@code bone->length * |pose_mat Y|}, and
     * without the tail there is no bone length to size it with.
     */
    public Vector3f tail = new Vector3f();

    /** Rest length, i.e. head-to-tail distance — Blender's {@code bone->length}. */
    public float restLength;

    /**
     * Transient full local orientation for this bone, applied raw in place of the channel rotation —
     * the evaluated rotation of the pipeline {@code rest → channels → constraint stack → render}. Same
     * two-phase lifecycle as {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#orient}: layer composers
     * keep it in lockstep with the channels (null = channels compose trivially, may be reset on channel
     * re-author); constraint stages (IK → physics → limits) treat the channels as read-only FK truth,
     * read the evaluated-so-far rotation via {@link #evaluatedRotation()}, and write their blended
     * result here — never to the channels, and never null.
     */
    public Quaternionf orient;

    /**
     * Transient CUMULATIVE world translation the IK stretch gives this bone, applied to the SKINNING
     * matrix only, so the deformed mesh follows a chain that reached past its rest length — vertices
     * weighted across bones blend the neighbouring shifts into a smooth stretch instead of a seam.
     * Deliberately kept off {@link #mat}/{@link #originMat}: the skeleton frames that the solve and the
     * debug overlay read stay nominal, and the shift each bone carries is already its full cumulative
     * one. Null when the bone has no shift this frame.
     */
    public Vector3f offset;

    /**
     * Blenders {@code BONE_NO_LOCAL_LOCATION}: die Verschiebung wirkt im ELTERNRAUM
     * statt in der eigenen gedrehten Ruhebasis. Rigify setzt das auf seine IK-Griffe,
     * damit sie sich in geraden Achsen ziehen lassen. Kommt aus {@code rig.json}.
     *
     * ⛔ Polaritaet ist Blenders, nicht die lesbarere Umkehrung: ein per Hotswap neu
     * dazugekommenes Feld ist auf bestehenden Instanzen {@code false} und NICHT sein
     * Initialisierer. Nur so ist der Vorgabewert auch nach einem Swap der alte Weg.
     */
    public boolean elternraumVerschiebung;

    /** Blenders {@code inherit_scale_mode}; {@code null} ist FULL, der Normalfall. */
    public String skalenErbe;

    /**
     * Blenders {@code BONE_CONNECTED}: der Kopf klebt am Elternende. {@code BKE_pchan_to_mat4}
     * uebernimmt die {@code location} eines verbundenen Knochens NICHT — bis 2026-09-13 tat
     * BBS es und liess Rigifys verbundene FK-Controls (shin_fk, forearm_fk) vom Gelenk ziehen
     * (gemessen 0,10 m). Kommt aus {@code rig.json}.
     */
    public boolean verbunden;

    /** Blenders {@code BONE_HINGE} (use_inherit_rotation aus): die Pose-Drehung des Elters
     *  wird NICHT weitergegeben, nur seine Ruhedrehung. */
    public boolean keineElternDrehung;

    /**
     * Blenders {@code rotation_mode} fuer Euler-Knochen (XYZ, XZY, YXZ, YZX, ZXY, ZYX);
     * {@code null} heisst XYZ. Rigifys Tweaks stehen auf ZXY — BBS setzte bis 2026-09-13
     * jeden Euler-Kanal als XYZ zusammen, also in einer anderen Reihenfolge als Blender.
     */
    public String eulerOrdnung;

    /**
     * Blenders {@code rotation_mode} des Knochens, so wie er im Rig steht (QUATERNION,
     * AXIS_ANGLE oder eine Euler-Ordnung); {@code null} heisst XYZ. Anders als BBS' eigener
     * Kanalmodus: der Action-Constraint wertet seine Kurven danach aus.
     */
    public String drehModus;

    /** Bendy-Bone-Daten und -Cache; {@code null} bei gewoehnlichen Knochen. */
    public BOBJBBone bbone;

    /** Blenders {@code pchan->child}: das letzte VERBUNDENE Kind in Knochenreihenfolge —
     *  der AUTO-Griff am Ende eines B-Bones. */
    public BOBJBone verbundenesKind;

    /** Blenders Huelle: Kopfradius, Schwanzradius, Abstand ({@code rad_head, rad_tail, dist}). */
    public float[] huelle;

    /** {@code BONE_MULT_VG_ENV}: Huellgewicht gilt auch ohne den Schalter am Armature-Constraint. */
    public boolean huelleMult;

    public BOBJBone(int index, String name, String parent, Matrix4f boneMat)
    {
        this.index = index;
        this.name = name;
        this.parent = parent;
        this.boneMat = boneMat;

        this.invBoneMat.set(boneMat);
        this.invBoneMat.invert();

        this.relBoneMat.identity();
    }

    public Matrix4f compute()
    {
        Matrix4f mat = this.computeMatrix(new Matrix4f());

        this.mat.set(mat);
        mat.mul(this.invBoneMat);

        /* Stretch rides the skinning matrix alone — pre-multiplied, so the deformed vertices land
         * `offset` further along in world. mat/originMat stay nominal, so child bones and pivot frames
         * are unaffected; the offset is already this bone's full cumulative shift. */
        if (this.offset != null)
        {
            mat.translateLocal(this.offset);
        }

        return mat;
    }

    /** Die Hautmatrix aus dem AKTUELLEN {@link #mat} — noetig, wenn ein Constraint
     *  die Weltmatrix nach {@link #compute()} noch veraendert hat. */
    public Matrix4f hautMatrix()
    {
        Matrix4f haut = new Matrix4f(this.mat).mul(this.invBoneMat);

        if (this.offset != null)
        {
            haut.translateLocal(this.offset);
        }

        return haut;
    }

    public Matrix4f computeMatrix(Matrix4f m)
    {
        /* Weicht die Elternuebergabe von der Norm ab, rechnet Blenders eigener Weg —
         * fuer 654 von 706 Rigify-Knochen und fuer jedes Modell ohne Rig-Mechanik
         * bleibt es Zeile fuer Zeile der alte, allokationsfreie Pfad darunter. */
        if (this.elternraumVerschiebung || this.skalenErbe != null || this.keineElternDrehung || this.verbunden)
        {
            return BOBJElternTransform.rechne(this, m);
        }

        this.mat.set(this.relBoneMat);
        this.originMat.set(this.relBoneMat);
        this.applyTransformations();

        if (this.parentBone != null)
        {
            m.set(this.parentBone.mat).mul(this.originMat);
            this.originMat.set(m);
            m.identity().set(this.parentBone.mat);
        }

        m.mul(this.mat);

        return m;
    }

    public void applyTransformations()
    {
        /* Ein VERBUNDENER Knochen hat in Blender keine Verschiebung: BKE_pchan_to_mat4
         * laesst `loc` bei BONE_CONNECTED aus. */
        if (!this.verbunden)
        {
            this.mat.translate(this.transform.translate);
            this.originMat.translate(this.transform.translate);
        }

        this.kanalDrehung(this.mat);
    }

    /**
     * Dreh- und Skalenanteil der Kanaele auf {@code ziel} draufmultipliziert — Blenders
     * {@code chan_mat} ohne die Ortsspalte. DIE eine Stelle, an der entschieden wird,
     * welche Drehquelle gilt; beide Wege durch {@link #computeMatrix} teilen sie sich,
     * damit sie nicht auseinanderlaufen koennen.
     */
    public Matrix4f kanalDrehung(Matrix4f ziel)
    {
        if (this.orient != null)
        {
            /* orient is the full local rotation, so the euler channel is skipped. */
            ziel.rotate(this.orient);
        }
        else if (this.transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            ziel.rotate(this.transform.quat);
        }
        else
        {
            Vector3f rotate = this.transform.rotate;

            /* Rest bones (all angles zero) skip the trig entirely; BOBJ channels are radians. */
            if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
            {
                ziel.rotate(this.eulerQuaternion(rotate));
            }
        }

        return ziel.scale(this.transform.scale);
    }

    /**
     * The bone's evaluated local rotation as of this point in the pipeline — {@link #orient} when set,
     * otherwise the channel rotation (mode-aware; BOBJ channels are radians). THE read for every
     * constraint-stack stage; see {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#evaluatedRotation()}.
     * Returns a fresh instance safe to mutate.
     */
    public Quaternionf evaluatedRotation()
    {
        return this.orient != null ? new Quaternionf(this.orient) : this.kanalQuaternion();
    }

    /**
     * Die Euler-Kanaele als Quaternion — in der Reihenfolge, die Blender fuer DIESEN Knochen
     * benutzt ({@link #eulerOrdnung}); ohne Angabe XYZ, was JOMLs ZYX-Komposition ist.
     * DIE eine Stelle, an der ein Euler dieses Knochens zusammengesetzt wird.
     */
    public Quaternionf eulerQuaternion(Vector3f rotate)
    {
        if (this.eulerOrdnung == null || "XYZ".equals(this.eulerOrdnung))
        {
            return Matrices.toLocalRotationZYXRadians(rotate);
        }

        float[] m = mchorse.bbs_mod.cubic.ik.RigEuler.zuMatrix(new float[] {rotate.x, rotate.y, rotate.z}, this.eulerOrdnung);

        return new org.joml.Matrix3f(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8])
            .getNormalizedRotation(new Quaternionf());
    }

    /** Die Kanal-Drehung als Quaternion, modusabhaengig (Euler in Blenders Ordnung oder Quaternion). */
    public Quaternionf kanalQuaternion()
    {
        return this.transform.rotationMode == Transform.RotationMode.QUATERNION
            ? new Quaternionf(this.transform.quat) : this.eulerQuaternion(this.transform.rotate);
    }

    /** Ein Quaternion als Euler in Blenders Ordnung dieses Knochens, nahe an {@code bezug}. */
    public Vector3f eulerAus(Quaternionf q, Vector3f bezug, Vector3f dest)
    {
        if (this.eulerOrdnung == null || "XYZ".equals(this.eulerOrdnung))
        {
            return Matrices.toCompatibleEulerZYXRadians(q, bezug, dest);
        }

        float[] m = new float[9];

        new org.joml.Matrix3f().rotation(q).get(m);

        float[] e = mchorse.bbs_mod.cubic.ik.RigEuler.ausMatrixNahe(m,
            new float[] {bezug.x, bezug.y, bezug.z}, this.eulerOrdnung);

        return dest.set(e[0], e[1], e[2]);
    }

    /**
     * Composes one rotation layer into {@link #orient} (BOBJ rotations are radians). Mirrors
     * {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#composeOrient}: the first layer seeds from the euler
     * accumulated so far (rotate folded with rotate2) so a single layer is byte-identical; later layers
     * multiply their delta. Call AFTER the layer's additive euler readback to {@code transform.rotate}.
     */
    public void composeOrient(Quaternionf delta)
    {
        if (this.orient == null)
        {
            this.orient = this.kanalQuaternion();
        }
        else
        {
            this.orient.mul(delta);
        }
    }

    public void reset()
    {
        this.transform.identity();
        this.orient = null;
        this.offset = null;
    }
}