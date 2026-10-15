package mchorse.bbs_mod.compat;

import org.joml.Matrix4f;

/**
 * 26.2's CameraRenderState carries the frame's projection, view rotation and camera angles; the
 * state before 26.1 does not. LevelRendererAltMixin fills them at the start of renderLevel from
 * what GameRenderer passes in (view rotation, projection) and the camera. The shared code reads
 * them through these methods (stonecutter-regeln.gradle). Injected into CameraRenderState.
 */
public interface CameraRenderStateAlt
{
    default void bbs$setMatrizen(Matrix4f projektion, Matrix4f blick, float xRot, float yRot)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default Matrix4f bbsProjectionMatrix()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default Matrix4f bbsViewRotationMatrix()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default float bbsXRot()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default float bbsYRot()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }
}
