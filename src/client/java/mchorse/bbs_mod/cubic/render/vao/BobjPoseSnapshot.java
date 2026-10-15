package mchorse.bbs_mod.cubic.render.vao;

import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

/** Exact evaluated inputs: a partial digest must never cancel a changed mesh update. */
public final class BobjPoseSnapshot
{
    private final int[] cells;
    private final Map<String, Integer> shapes = new HashMap<>();

    /** The same native mode decision that updateMesh makes, before cancelling its work. */
    public static boolean ready(boolean valid, Object current, Object expected, boolean currentGpu,
        boolean requestedGpu, boolean supportChecked, boolean supported)
    {
        if (requestedGpu && !supportChecked) return false;
        return valid && expected != null && current == expected && currentGpu == (requestedGpu && supported);
    }

    public BobjPoseSnapshot(Matrix4f[] matrices, Map<String, Float> shapes)
    {
        this.cells = new int[Math.multiplyExact(matrices.length, 17)];
        for (int bone = 0; bone < matrices.length; bone++)
        {
            Matrix4f matrix = matrices[bone];
            if (matrix == null) continue;
            this.cells[bone * 17] = 1;
            for (int cell = 0; cell < 16; cell++)
                this.cells[bone * 17 + cell + 1] = Float.floatToRawIntBits(matrix.get(cell / 4, cell % 4));
        }
        shapes.forEach((name, value) -> this.shapes.put(name, value == null ? null : Float.floatToRawIntBits(value)));
    }

    public boolean matches(Matrix4f[] matrices, Map<String, Float> shapes)
    {
        if (matrices == null || (long) matrices.length * 17 != this.cells.length || shapes.size() != this.shapes.size()) return false;
        for (int bone = 0; bone < matrices.length; bone++)
        {
            Matrix4f matrix = matrices[bone];
            if ((matrix == null ? 0 : 1) != this.cells[bone * 17]) return false;
            if (matrix == null) continue;
            for (int cell = 0; cell < 16; cell++)
                if (Float.floatToRawIntBits(matrix.get(cell / 4, cell % 4)) != this.cells[bone * 17 + cell + 1]) return false;
        }
        for (Map.Entry<String, Float> entry : shapes.entrySet())
        {
            if (!this.shapes.containsKey(entry.getKey())) return false;
            Integer value = entry.getValue() == null ? null : Float.floatToRawIntBits(entry.getValue());
            if (!java.util.Objects.equals(this.shapes.get(entry.getKey()), value)) return false;
        }
        return true;
    }
}
