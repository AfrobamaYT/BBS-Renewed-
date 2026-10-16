package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * "Render last": a form with the flag skips its turn in a pass and is drawn at the end of the same
 * pass, by the same code, after its neighbours — Blockbench's "render last", for transparency, as a
 * switch on one form instead of reordering the replay list. The phase of the frame stays the same,
 * so only that form is affected. Picking and the Iris shadow pass draw everything where it stands.
 * From BBS FS a0fe6de30 (Wemppy4/bbs-fs, MIT); the scope is the films' pass.
 */
public final class FormRenderLast
{
    private static final List<Postponed> POSTPONED = new ArrayList<>();
    private static boolean active;

    private FormRenderLast()
    {}

    /** Opens a scope for a pass about to draw forms; returns whether this call opened it, for {@link #close} */
    public static boolean open()
    {
        if (active) return false;

        active = true;

        return true;
    }

    /** Closes the scope this {@link #open()} opened and draws what it postponed, in list order */
    public static void close(boolean opened)
    {
        if (!opened) return;

        active = false;

        List<Postponed> forms = new ArrayList<>(POSTPONED);

        POSTPONED.clear();

        for (Postponed form : forms) form.render();
    }

    /** Skips the form's turn if it renders last: keeps its frame for later, and the caller draws nothing now */
    public static boolean postpone(Form form, FormRenderingContext context)
    {
        if (!active || !form.renderLast.get() || context.isPicking() || BBSRendering.isIrisShadowPass())
        {
            return false;
        }

        POSTPONED.add(new Postponed(form, context));

        return true;
    }

    /** A form's frame at the moment it was skipped — enough to run its renderer later */
    private static final class Postponed
    {
        private final Form form;
        private final FormRenderType type;
        private final IEntity entity;
        private final Matrix4f position;
        private final Matrix3f normal;
        private final Matrix4f worldPosition;
        private final Matrix3f worldNormal;
        private final int light;
        private final int overlay;
        private final int color;
        private final float transition;
        private final Camera camera = new Camera();

        private Postponed(Form form, FormRenderingContext context)
        {
            PoseStack.Pose entry = context.stack.last();
            PoseStack.Pose world = context.world == null ? null : context.world.last();

            this.form = form;
            this.type = context.type;
            this.entity = context.entity;
            this.position = new Matrix4f(entry.pose());
            this.normal = new Matrix3f(entry.normal());
            this.worldPosition = world == null ? null : new Matrix4f(world.pose());
            this.worldNormal = world == null ? null : new Matrix3f(world.normal());
            this.light = context.light;
            this.overlay = context.overlay;
            this.color = context.color;
            this.transition = context.transition;
            this.camera.copy(context.camera);
        }

        private void render()
        {
            PoseStack stack = new PoseStack();

            stack.last().pose().set(this.position);
            stack.last().normal().set(this.normal);

            FormRenderingContext context = new FormRenderingContext()
                .set(this.type, this.entity, stack, this.light, this.overlay, this.transition)
                .camera(this.camera)
                .color(this.color);

            /* set() rebuilt the world stack from the entity alone; the capture holds what the pass had */
            if (this.worldPosition != null && context.world != null)
            {
                context.world.last().pose().set(this.worldPosition);
                context.world.last().normal().set(this.worldNormal);
            }

            FormUtilsClient.render(this.form, context);
        }
    }
}
