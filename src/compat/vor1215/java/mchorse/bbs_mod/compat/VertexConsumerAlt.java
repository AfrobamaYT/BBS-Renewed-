package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/**
 * vor1215: Ecken-Aufrufe von 1.21.6+ an 1.21.4s VertexConsumer. Die GUI-Pose ist dort 2D
 * (Matrix3x2f, z = 0); die Linienbreite ist in 1.21.4 kein Eckenattribut, sondern ein Uniform.
 */
public interface VertexConsumerAlt
{
    default VertexConsumer addVertexWith2DPose(Matrix3x2fc pose, float x, float y)
    {
        Vector2f lage = pose.transformPosition(x, y, new Vector2f());

        return ((VertexConsumer) this).addVertex(lage.x, lage.y, 0F);
    }

    /** 1.21.4 nimmt nur Matrix4f. */
    default VertexConsumer addVertex(org.joml.Matrix4fc matrix, float x, float y, float z)
    {
        return ((VertexConsumer) this).addVertex(matrix instanceof org.joml.Matrix4f m ? m : new org.joml.Matrix4f(matrix), x, y, z);
    }

    default VertexConsumer setLineWidth(float breite)
    {
        return (VertexConsumer) this;
    }
}
