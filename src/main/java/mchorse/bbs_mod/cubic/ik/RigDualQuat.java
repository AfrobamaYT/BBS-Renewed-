package mchorse.bbs_mod.cubic.ik;

/**
 * Blenders {@code DualQuat} aus {@code math_rotation_c.cc}: Dreh- und Verschiebungsanteil als
 * duales Quaternion, dazu eine Skalenmatrix mit Gewicht. Gebraucht vom Armature-Constraint
 * mit "Preserve Volume" und vom Skinning mit Dual-Quaternion-Mischung.
 */
public final class RigDualQuat
{
    public final float[] quat = new float[4];
    public final float[] trans = new float[4];
    public final float[] scale = new float[16];
    public float scaleWeight;

    /** {@code mat4_to_dquat(dq, basemat, mat)}. */
    public static RigDualQuat aus(float[] basemat, float[] mat)
    {
        RigDualQuat dq = new RigDualQuat();
        float[] baseRS = RigMathe.mul(mat, basemat);
        float[] scale = RigMathe.groesse(baseRS);
        float[] dscale = {scale[0] - 1F, scale[1] - 1F, scale[2] - 1F};
        float[] r;

        if (!RigMatrix3.orthonormal(mat) || RigMathe.negativ(mat)
            || RigVektor.punktprodukt(dscale, dscale) > 1.0E-4F * 1.0E-4F)
        {
            float[] tmp = RigMathe.kopie(baseRS);

            RigMatrix3.orthogonalisiereUmY(tmp);

            float[] baseR = RigQuaternion.quaternionZuMatrix4(RigQuaternion.quaternion(tmp));

            RigMathe.setzeOrt(baseR, RigMathe.ort(baseRS));

            float[] baseinv = RigMathe.invertiert(basemat);

            r = RigMathe.mul(baseR, baseinv);

            float[] s = RigMathe.mul(RigMathe.invertiert(baseR), baseRS);
            float[] sk = RigMathe.mul(RigMathe.mul(basemat, s), baseinv);

            System.arraycopy(sk, 0, dq.scale, 0, 16);
            dq.scaleWeight = 1F;
        }
        else
        {
            r = RigMathe.kopie(mat);
            dq.scaleWeight = 0F;
        }

        float[] q = RigQuaternion.quaternion(r);

        System.arraycopy(q, 0, dq.quat, 0, 4);

        float[] t = RigMathe.ort(r);

        dq.trans[0] = -0.5F * (t[0] * q[1] + t[1] * q[2] + t[2] * q[3]);
        dq.trans[1] = 0.5F * (t[0] * q[0] + t[1] * q[3] - t[2] * q[2]);
        dq.trans[2] = 0.5F * (-t[0] * q[3] + t[1] * q[0] + t[2] * q[1]);
        dq.trans[3] = 0.5F * (t[0] * q[2] - t[1] * q[1] + t[2] * q[0]);

        return dq;
    }

    /** {@code add_weighted_dq_dq}. */
    public void addiere(RigDualQuat dq, float weight)
    {
        boolean flipped = false;

        if (RigQuaternion.punktprodukt4(dq.quat, this.quat) < 0F)
        {
            flipped = true;
            weight = -weight;
        }

        for (int i = 0; i < 4; i++)
        {
            this.quat[i] += weight * dq.quat[i];
            this.trans[i] += weight * dq.trans[i];
        }

        if (dq.scaleWeight != 0F)
        {
            if (flipped)
            {
                weight = -weight;
            }

            for (int i = 0; i < 16; i++)
            {
                this.scale[i] += dq.scale[i] * weight;
            }

            this.scaleWeight += weight;
        }
    }

    /** {@code add_weighted_dq_dq_pivot}. */
    public void addiereMitPivot(RigDualQuat dq, float[] pivot, float weight, boolean skalenMatrix)
    {
        if (dq.scaleWeight == 0F)
        {
            this.addiere(dq, weight);

            return;
        }

        RigDualQuat m = dq.kopie();
        float[] dst = RigVektor.differenz(RigMathe.punkt(m.scale, pivot), pivot);

        m.trans[0] -= 0.5F * (m.quat[1] * dst[0] + m.quat[2] * dst[1] + m.quat[3] * dst[2]);
        m.trans[1] += 0.5F * (m.quat[0] * dst[0] + m.quat[2] * dst[2] - m.quat[3] * dst[1]);
        m.trans[2] += 0.5F * (m.quat[0] * dst[1] + m.quat[3] * dst[0] - m.quat[1] * dst[2]);
        m.trans[3] += 0.5F * (m.quat[0] * dst[2] + m.quat[1] * dst[1] - m.quat[2] * dst[0]);

        if (skalenMatrix)
        {
            m.scale[12] -= dst[0];
            m.scale[13] -= dst[1];
            m.scale[14] -= dst[2];
        }
        else
        {
            m.scaleWeight = 0F;
        }

        this.addiere(m, weight);
    }

    /** {@code normalize_dq}. */
    public void normalisiere(float totweight)
    {
        float scale = 1F / totweight;

        for (int i = 0; i < 4; i++)
        {
            this.quat[i] *= scale;
            this.trans[i] *= scale;
        }

        if (this.scaleWeight != 0F)
        {
            float addweight = totweight - this.scaleWeight;

            if (addweight != 0F)
            {
                this.scale[0] += addweight;
                this.scale[5] += addweight;
                this.scale[10] += addweight;
                this.scale[15] += addweight;
            }

            for (int i = 0; i < 16; i++)
            {
                this.scale[i] *= scale;
            }

            this.scaleWeight = 1F;
        }
    }

    /** {@code dquat_to_mat4}. */
    public float[] zuMatrix()
    {
        float[] q0 = this.quat.clone();
        float len = (float) Math.sqrt(RigQuaternion.punktprodukt4(q0, q0));

        if (len != 0F)
        {
            len = 1F / len;
        }

        for (int i = 0; i < 4; i++)
        {
            q0[i] *= len;
        }

        float[] r = RigQuaternion.quaternionZuMatrix4(q0);
        float[] t = this.trans;

        r[12] = 2F * (-t[0] * q0[1] + t[1] * q0[0] - t[2] * q0[3] + t[3] * q0[2]) * len;
        r[13] = 2F * (-t[0] * q0[2] + t[1] * q0[3] + t[2] * q0[0] - t[3] * q0[1]) * len;
        r[14] = 2F * (-t[0] * q0[3] - t[1] * q0[2] + t[2] * q0[1] + t[3] * q0[0]) * len;

        if (this.scaleWeight != 0F)
        {
            r = RigMathe.mul(r, this.scale);
        }

        return r;
    }

    /** {@code mul_v3m3_dq} ohne die Korrekturmatrix — den Punkt transformieren. */
    public float[] punkt(float[] p)
    {
        float w = this.quat[0], x = this.quat[1], y = this.quat[2], z = this.quat[3];
        float t0 = this.trans[0], t1 = this.trans[1], t2 = this.trans[2], t3 = this.trans[3];
        float[] m = new float[9];

        m[0] = w * w + x * x - y * y - z * z;
        m[3] = 2F * (x * y - w * z);
        m[6] = 2F * (x * z + w * y);
        m[1] = 2F * (x * y + w * z);
        m[4] = w * w + y * y - x * x - z * z;
        m[7] = 2F * (y * z - w * x);
        m[2] = 2F * (x * z - w * y);
        m[5] = 2F * (y * z + w * x);
        m[8] = w * w + z * z - x * x - y * y;

        float len2 = RigQuaternion.punktprodukt4(this.quat, this.quat);

        if (len2 > 0F)
        {
            len2 = 1F / len2;
        }

        float[] t = {2F * (-t0 * x + w * t1 - t2 * z + y * t3), 2F * (-t0 * y + t1 * z - x * t3 + w * t2),
            2F * (-t0 * z + x * t2 + w * t3 - t1 * y)};
        float[] r = p.clone();

        if (this.scaleWeight != 0F)
        {
            r = RigMathe.punkt(this.scale, r);
        }

        r = RigMatrix3.mul3v(m, r);

        return new float[] {(r[0] + t[0]) * len2, (r[1] + t[1]) * len2, (r[2] + t[2]) * len2};
    }

    public RigDualQuat kopie()
    {
        RigDualQuat k = new RigDualQuat();

        System.arraycopy(this.quat, 0, k.quat, 0, 4);
        System.arraycopy(this.trans, 0, k.trans, 0, 4);
        System.arraycopy(this.scale, 0, k.scale, 0, 16);
        k.scaleWeight = this.scaleWeight;

        return k;
    }
}
