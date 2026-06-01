package mchorse.bbs_mod.cubic.ik;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

final class FabrikSolver
{
    private static final float EPS = 1.0e-6f;

    /* Keep the goal a hair inside the reachable sphere so the chain never locks
     * dead straight: a fully extended chain has an undefined bend plane and rolls. */
    private static final float REACH_LIMIT = 0.999F;

    private FabrikSolver()
    {
    }

    public static List<Vector3f> solve(List<Vector3f> startPositions, Vector3f target, Vector3f pole, int maxIterations, float tolerance)
    {
        int n = startPositions.size();

        if (n < 2)
        {
            return startPositions;
        }

        List<Vector3f> p = startPositions;

        /* When no pole is given, remember which way each joint currently bends so
         * the solved pose keeps that side. This is what makes the bend deterministic
         * frame to frame and removes any need for flip hysteresis. */
        Vector3f[] bendRef = pole == null ? captureBendReferences(p) : null;

        float[] d = new float[n - 1];
        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            float len = p.get(i).distance(p.get(i + 1));
            d[i] = len;
            total += len;
        }

        Vector3f root = new Vector3f(p.get(0));
        Vector3f dir = new Vector3f();

        Vector3f goal = new Vector3f(target);
        float rootToTarget = root.distance(target);

        if (rootToTarget > total * REACH_LIMIT)
        {
            dir.set(target).sub(root);

            if (dir.lengthSquared() < 1.0e-10f)
            {
                return p;
            }

            dir.normalize();
            goal.set(root).fma(total * REACH_LIMIT, dir);
        }

        for (int iter = 0; iter < maxIterations; iter++)
        {
            if (p.get(n - 1).distanceSquared(goal) <= tolerance * tolerance)
            {
                break;
            }

            p.get(n - 1).set(goal);

            for (int i = n - 2; i >= 0; i--)
            {
                Vector3f pi = p.get(i);
                Vector3f pj = p.get(i + 1);

                dir.set(pi).sub(pj);
                float lenSq = dir.lengthSquared();

                if (lenSq < 1.0e-10f)
                {
                    continue;
                }

                dir.mul((float) (d[i] / Math.sqrt(lenSq)));
                pi.set(pj).add(dir);
            }

            p.get(0).set(root);

            for (int i = 0; i < n - 1; i++)
            {
                Vector3f pi = p.get(i);
                Vector3f pj = p.get(i + 1);

                dir.set(pj).sub(pi);
                float lenSq = dir.lengthSquared();

                if (lenSq < 1.0e-10f)
                {
                    continue;
                }

                dir.mul((float) (d[i] / Math.sqrt(lenSq)));
                pj.set(pi).add(dir);
            }
        }

        applyBend(p, pole, bendRef);

        return p;
    }

    /**
     * Captures, for every interior joint, the component of the incoming
     * limb direction that lies across the root-to-tip axis. That perpendicular
     * direction is the side the joint currently bends towards.
     */
    private static Vector3f[] captureBendReferences(List<Vector3f> p)
    {
        int n = p.size();
        Vector3f[] refs = new Vector3f[n];

        for (int i = 1; i < n - 1; i++)
        {
            Vector3f ref = perpendicular(p.get(i - 1), p.get(i), p.get(i + 1));

            if (ref != null)
            {
                refs[i] = ref;
            }
        }

        return refs;
    }

    /**
     * Rotates every interior joint around its own root-to-tip axis so the bend
     * lands on the requested side: towards the pole when one is given, otherwise
     * onto the captured rest side. The end positions stay put, so reach is kept.
     */
    private static void applyBend(List<Vector3f> p, Vector3f pole, Vector3f[] bendRef)
    {
        int n = p.size();

        if (n < 3)
        {
            return;
        }

        Vector3f axis = new Vector3f();
        Vector3f current = new Vector3f();
        Vector3f desired = new Vector3f();
        Vector3f cross = new Vector3f();
        Quaternionf q = new Quaternionf();
        Vector3f rel = new Vector3f();

        for (int i = 1; i < n - 1; i++)
        {
            Vector3f a = p.get(i - 1);
            Vector3f b = p.get(i);
            Vector3f c = p.get(i + 1);

            axis.set(c).sub(a);
            float axisLenSq = axis.lengthSquared();

            if (axisLenSq <= EPS * EPS)
            {
                continue;
            }

            axis.mul(1F / (float) Math.sqrt(axisLenSq));

            if (!project(current.set(b).sub(a), axis))
            {
                continue;
            }

            if (pole != null)
            {
                if (!project(desired.set(pole).sub(a), axis))
                {
                    continue;
                }
            }
            else
            {
                Vector3f ref = bendRef == null ? null : bendRef[i];

                if (ref == null || !project(desired.set(ref), axis))
                {
                    continue;
                }
            }

            cross.set(current).cross(desired);
            float sin = axis.dot(cross);
            float cos = current.dot(desired);
            float angle = (float) Math.atan2(sin, cos);

            if (Math.abs(angle) < EPS)
            {
                continue;
            }

            q.identity().fromAxisAngleRad(axis.x, axis.y, axis.z, angle);
            rel.set(b).sub(a);
            q.transform(rel);
            b.set(a).add(rel);
        }
    }

    /**
     * Returns the perpendicular component of (b - a) relative to the axis a-c,
     * normalized, or null when it is degenerate (joint sits on the axis).
     */
    private static Vector3f perpendicular(Vector3f a, Vector3f b, Vector3f c)
    {
        Vector3f axis = new Vector3f(c).sub(a);

        if (axis.lengthSquared() <= EPS * EPS)
        {
            return null;
        }

        axis.normalize();

        Vector3f out = new Vector3f(b).sub(a);

        return project(out, axis) ? out : null;
    }

    /**
     * Strips the axis-parallel part from {@code v} and normalizes the remainder
     * in place. Returns false when nothing perpendicular is left.
     */
    private static boolean project(Vector3f v, Vector3f axis)
    {
        float dot = v.dot(axis);
        v.x -= axis.x * dot;
        v.y -= axis.y * dot;
        v.z -= axis.z * dot;

        float lenSq = v.lengthSquared();

        if (lenSq <= EPS * EPS)
        {
            return false;
        }

        v.mul(1F / (float) Math.sqrt(lenSq));

        return true;
    }
}
