package mchorse.bbs_mod.film;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.camera.data.Point;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Was am Schauspieler EINGEREICHT statt gezeichnet wird: sein Schatten und sein Namensschild.
 *
 * <p>⛔ WARUM DAS EINE EIGENE DATEI IST: beide gehen in 26.2 über den SubmitNodeCollector
 * ({@code submitShadow} / {@code submitText}) und werden damit erst später gezeichnet — im
 * Gegensatz zum Modell, das sofort in den Puffer geht ({@code ModelVAORenderer}). Genau
 * dieser Unterschied war der Fehler: BBS reichte beides aus AFTER_SOLID_FEATURES ein, also
 * NACH dem Punkt, an dem die Sammlung geleert wird, und das Eingereichte wurde erst im
 * nächsten Bild gezeichnet — mit einer Matrix aus dem Bild davor. Die Figur stand still,
 * ihr Schatten zog hinterher.
 *
 * <p>Seit dem Fix laufen sie in einer eigenen Phase (siehe
 * {@link FilmControllerContext#sammelDurchgang}). Damit sind sie kein beliebiger Teil des
 * Zeichen-Durchgangs mehr, sondern eine eigene Sache mit eigener Regel — und stehen deshalb
 * hier statt mitten in {@code BaseFilmController}.
 */
public class SchattenUndSchild
{
    /**
     * Reicht Schatten und Namensschild ein. Ruft NUR im Sammel-Durchgang etwas aus; der
     * Aufrufer muss das nicht selbst prüfen.
     */
    public static void einreichen(FilmControllerContext context, Form form, IEntity entity,
        Vector3d position, Matrix4f target, PoseStack stack, float transition, float opacity,
        double cx, double cy, double cz, int light, boolean relative)
    {
        if (!FilmControllerContext.sammelDurchgang || relative || context.gizmoOnly
            || context.map != null || !form.visible.get())
        {
            return;
        }

        schatten(context, form, entity, position, target, stack, transition, opacity, cx, cy, cz);
        namensschild(context, entity, position, stack, cx, cy, cz, light);
    }

    private static void schatten(FilmControllerContext context, Form form, IEntity entity,
        Vector3d position, Matrix4f target, PoseStack stack, float transition, float opacity,
        double cx, double cy, double cz)
    {
        if (opacity <= 0F || context.shadowRadius <= 0F)
        {
            return;
        }

        /* Skip the shadow when the form is hidden (form.visible, animatable via keyframes): the form
         * itself renders nothing then - see FormRenderer.render - so its shadow must vanish too.
         * The animated value is live here, applied to form.visible in startRenderFrame this frame.
         *
         * Place the shadow under the replay's perceived position: shift the actual shadow position
         * by how far the model (form transform + anchor-bone root motion) has moved from rest,
         * mapped from form-local into world axes via the render target. Moving the position itself
         * (not just translating the quad) makes the shadow's ground projection and shading match. */
        double shadowX = position.x;
        double shadowY = position.y;
        double shadowZ = position.z;

        FormRenderer renderer = FormUtilsClient.getRenderer(FormUtils.getRoot(form));

        if (renderer != null && !BBSRendering.isIrisShadowPass() && context.replay != null && context.replay.shadowFollow.get())
        {
            Vector3f displacement = renderer.getShadowDisplacement(entity, transition);

            if (displacement != null)
            {
                target.transformDirection(displacement);

                shadowX += displacement.x;
                shadowY += displacement.y;
                shadowZ += displacement.z;
            }

            /* Extra world-space nudge to seat the shadow on the model's real floor (added after the
             * form-local displacement is mapped to world, so it stays vertical regardless of facing). */
            Point offset = context.replay.shadowOffset.get();

            shadowX += offset.x;
            shadowY += offset.y;
            shadowZ += offset.z;
        }

        stack.pushPose();
        stack.translate(shadowX - cx, shadowY - cy, shadowZ - cz);

        ModelBlockEntityRenderer.renderShadow(context.consumers, stack, transition, shadowX, shadowY, shadowZ, 0F, 0F, 0F, context.shadowRadius, opacity);

        stack.popPose();
    }

    private static void namensschild(FilmControllerContext context, IEntity entity,
        Vector3d position, PoseStack stack, double cx, double cy, double cz, int light)
    {
        if (context.nameTag.isEmpty())
        {
            return;
        }

        /* Hide the name tag along with the form (form.visible, animatable via keyframes): when the
         * form renders nothing, its name tag must vanish too - same reasoning as the shadow above. */
        stack.pushPose();
        stack.translate(position.x - cx, position.y - cy, position.z - cz);

        BaseFilmController.renderNameTag(entity, Component.literal(StringUtils.processColoredText(context.nameTag)),
            stack, context.consumers, light, context.camera.orientation);

        stack.popPose();
    }
}
