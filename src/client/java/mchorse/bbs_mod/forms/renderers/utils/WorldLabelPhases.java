package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Root world text phase policy; picking uses its own ID quad rather than a text collector. */
public final class WorldLabelPhases
{
    private WorldLabelPhases()
    {}

    public static boolean collecting(FormRenderingContext context)
    {
        return context.worldLabel && !context.isPicking() && FilmControllerContext.sammelDurchgang;
    }

    public static boolean text(FormRenderingContext context)
    {
        return !context.isPicking() && (!context.worldLabel || FilmControllerContext.sammelDurchgang);
    }

    public static boolean background(FormRenderingContext context)
    {
        return !context.isPicking() && !collecting(context);
    }

    public static void billboard(FormRenderingContext context)
    {
        if (!context.worldLabel)
        {
            MatrixStackUtils.billboard(context.stack);

            return;
        }

        /* BaseFilmController copies this camera-to-world orientation from this frame's
         * CameraRenderState. Never read next frame's live camera from a deferred callback. */
        Matrix4f pose = context.stack.last().pose();
        Matrix4f camera = context.camera.view;
        Vector3f scale = pose.getScale(new Vector3f());

        pose.m00(camera.m00()).m01(camera.m01()).m02(camera.m02());
        pose.m10(camera.m10()).m11(camera.m11()).m12(camera.m12());
        pose.m20(camera.m20()).m21(camera.m21()).m22(camera.m22());
        pose.scale(scale);
        context.stack.last().normal().identity();
    }
}
