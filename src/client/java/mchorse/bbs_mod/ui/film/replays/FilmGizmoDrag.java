package mchorse.bbs_mod.ui.film.replays;

import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Pair;

/**
 * How a film edit builds the drag its gizmo and its R/G/S keys run on: the world origin and
 * axes of the edited bone (or the form's anchor), the numeric Jacobian a translation needs,
 * and the rotation axes the renderer actually applies.
 *
 * <p>Split out of {@link UIReplaysEditorUtils}, which had grown past the point where the drag
 * chain could be read in one sitting.
 */
public class FilmGizmoDrag
{
    /**
     * Whether a keyboard-started transform (R/G/S) is currently being dragged on
     * the film panel's editable transform.
     *
     * <p>Character control normally takes the cursor away from the UI
     * ({@code UIFilmPanel#render} pins {@code context.mouseX/Y} to -1 so nothing
     * hovers while the mouse steers the actor). A modal drag cannot survive that:
     * every strategy begins and updates from those two numbers
     * ({@link UIPropTransform}), so -1/-1 means a delta of zero for as long as
     * the edit lasts — which is exactly why R/G/S did nothing in first person.
     * Both the cursor pin and the mouse-look step ask this before they run.
     */
    public static boolean isModalTransformActive(UIFilmPanel panel)
    {
        if (panel == null || panel.replayEditor == null)
        {
            return false;
        }

        UIPropTransform transform = UIReplayWurzelTransform.filmTransform(panel);

        return transform != null && transform.isEditing();
    }

    /**
     * Whether the drawn gizmo can serve as the reference point a ray gesture needs.
     *
     * <p>Every axis-constrained R/G/S gesture builds its screen geometry from the gizmo's
     * projected origin. Sit the camera on the edited bone — first person, or a camera clip
     * parked on the actor's head — and that origin leaves the frustum or ends up BEHIND the
     * near plane (measured in a prepared scene: w = -0.297, ndc 0.028/3.372, bone 0.809 blocks
     * from the camera). The projection then yields a degenerate point, the swept angle comes
     * out as exactly zero, and X/Y/Z look dead while the unconstrained gestures — which never
     * touch that geometry — keep working. That asymmetry is the symptom this checks for.
     *
     * <p>Handing back no drag drops the ray-only steps from the R/G/S walk (view ring, sphere,
     * screen plane) and leaves the axis steps on the screen-space drag, which needs no gizmo
     * and works from the pure cursor delta.
     */
    public static boolean isGizmoUsable(UIFilmPanel panel)
    {
        Matrix4f projection = panel.getController().getGizmoProjection();
        Area area = panel.getController().getGizmoArea();

        if (projection == null || area == null)
        {
            return false;
        }

        Vector2f center = new Vector2f();

        /* Same projection the visual and the pick stencil run on; it already answers "behind
         * the camera" with false, which is the case a parked first-person camera produces. */
        if (!Gizmo.INSTANCE.computeScreenCenter(projection, area.x, area.y, area.w, area.h, center))
        {
            return false;
        }

        return area.isInside((int) center.x, (int) center.y);
    }

    public static boolean startFilmGizmo(UIFilmPanel panel, UIContext context, int stencilIndex, float gizmoTransition)
    {
        if (panel.isFlying())
        {
            return false;
        }

        UIPropTransform transform = UIReplayWurzelTransform.filmTransform(panel);
        GizmoDrag drag = buildFilmGizmoDrag(
            panel,
            panel.getCamera(),
            panel.preview.getViewport(),
            transform,
            gizmoTransition
        );

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, drag);
    }

    public static void configureFilmHotkeyDrag(UIFilmPanel panel, UIContext context)
    {
        UIPropTransform transform = UIReplayWurzelTransform.filmTransform(panel);

        if (transform == null)
        {
            return;
        }

        transform.hotkeyDrag(() ->
        {
            if (BBSSettings.transformHotkeysWhileControlling.get() && !isGizmoUsable(panel))
            {
                return null;
            }

            return buildFilmGizmoDrag(
                panel,
                panel.getCamera(),
                panel.preview.getViewport(),
                transform,
                panel.replayEditor.getContext() == null ? 0F : panel.replayEditor.getContext().getTransition()
            );
        });

        /* Reference frame for the middle-click axis pick (UIPropTransform#axisPickDrag).
         *
         * ⛔ Deliberately NOT routed through the guard above, and deliberately NOT
         * buildFilmGizmoDrag:
         *   - the guard withholds the drag exactly where the pick is most wanted (camera on the
         *     edited bone), and the pick survives an unprojectable origin by design;
         *   - the numeric Jacobian and rotate axes cost four keyframe applications per call and
         *     the pick reads neither — only camera, view, viewport and the frame axes. */
        transform.axisPickDrag(() ->
        {
            GizmoDrag pick = GizmoDrag.fromRenderedGizmo(panel.getCamera(), panel.preview.getViewport());

            if (pick != null)
            {
                float pickTransition = panel.replayEditor.getContext() == null ? 0F : panel.replayEditor.getContext().getTransition();

                /* Same GLOBAL frame the edit itself gets, or GLOBAL would mean two different
                 * things in the same gesture (see buildFilmGizmoDrag). */
                pick.setGlobalAxes(BaseFilmController.getReplayWorldAxes(panel.getController().getCurrentEntity(), pickTransition));
            }

            return pick;
        });

        /* World-space copy/paste only makes sense for an actor's bone in the scene, so the world
         * matrix provider is wired solely for the pose editor's transform (other tracks leave it off
         * and the world context actions stay hidden there). */
        boolean pose = panel.replayEditor.keyframeEditor.editor instanceof UIPoseKeyframeFactory;

        transform.worldTransform(pose ? new FilmBoneWorldProvider(panel) : null);
        transform.rotationConstrained(pose ? () -> isFilmBoneRotationConstrained(panel) : null);
    }

    /**
     * Whether the film pose editor's current bone rotation is owned by an
     * enabled IK chain of its (possibly nested) model form — the gizmo then
     * refuses rotation gestures and dims its rings (see
     * {@link ModelIKRuntime#isRotationConstrained}).
     */
    private static boolean isFilmBoneRotationConstrained(UIFilmPanel panel)
    {
        UIKeyframeEditor keyframeEditor = panel.replayEditor.keyframeEditor;

        if (keyframeEditor == null || !(keyframeEditor.editor instanceof UIPoseKeyframeFactory))
        {
            return false;
        }

        IEntity entity = panel.getController().getCurrentEntity();
        Pair<String, Boolean> bone = keyframeEditor.getBone();

        if (entity == null || bone == null || bone.a == null)
        {
            return false;
        }

        UIKeyframeSheet sheet = keyframeEditor.getSheet(keyframeEditor.editor.getKeyframe());
        BaseValueBasic property = sheet == null ? null : FormUtils.getProperty(entity.getForm(), sheet.id);
        Form owner = property == null ? null : FormUtils.getForm(property);

        if (!(owner instanceof ModelForm modelForm))
        {
            return false;
        }

        ModelInstance instance = ModelFormRenderer.getModel(modelForm);

        return instance != null && ModelIKRuntime.isRotationConstrained(instance.model, modelForm, StringUtils.fileName(bone.a));
    }

    /**
     * Ray gizmo context for the film / replay viewport: same camera-origin and
     * axes as {@link GizmoDrag#fromRenderedGizmo}, plus numeric
     * {@link GizmoDrag#computeRotateAxes} / {@link GizmoDrag#computeTranslateJacobian}
     * driven by the composite bone matrix {@code target.mul(bone)} so replay
     * {@code bodyYaw}, anchor parents, and other film-only transforms match
     * {@link BaseFilmController#renderEntity}. Also the one place the film's
     * GLOBAL frame is set on the drag &mdash; the replay's own facing
     * ({@link BaseFilmController#getReplayWorldAxes}).
     */
    public static GizmoDrag buildFilmGizmoDrag(
        UIFilmPanel panel,
        Camera camera,
        Area viewport,
        UIPropTransform transform,
        float transition
    )
    {
        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(camera, viewport);

        if (drag == null || panel == null)
        {
            return drag;
        }

        IEntity entity = panel.getController().getCurrentEntity();

        /* The GLOBAL frame of a film edit is the edited replay's own facing, not
         * the map's axes — set before any early return, since it is the gizmo's
         * frame for every track (it doesn't depend on the bone or the sampled
         * matrices below). The drawn handles get the same axes in
         * BaseFilmController#renderAxes; the two must not drift apart. */
        drag.setGlobalAxes(BaseFilmController.getReplayWorldAxes(entity, transition));

        if (transform == null || transform.getTransform() == null)
        {
            return drag;
        }

        UIKeyframeEditor keyframeEditor = panel.replayEditor.keyframeEditor;

        if (keyframeEditor == null)
        {
            return drag;
        }

        Pair<String, Boolean> bone = keyframeEditor.getBone();
        Replay replay = panel.replayEditor.getReplay();

        if (bone == null || bone.a == null || replay == null || entity == null)
        {
            /* The anchor track has no model bone: its transform parents the whole
             * form, so sample the form's resolved anchor matrix instead. */
            if (keyframeEditor.isFormAnchorTrack() && replay != null && entity != null)
            {
                buildAnchorGizmoDrag(panel, camera, drag, transform, replay, entity, transition);
            }

            return drag;
        }

        Supplier<Matrix4f> matrixSampler = () ->
        {
            Form form = entity.getForm();
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);

            if (form != null)
            {
                /* Force-apply the perturbed keyframe state to the entity's form 
                 * so that FormUtilsClient's matrix cache updates for this sample. */
                replay.properties.applyProperties(form, tick);
            }

            Matrix4f m = BaseFilmController.getGizmoBoneCompositeMatrix(
                panel.getController().getEntities(),
                entity,
                replay,
                camera.position.x,
                camera.position.y,
                camera.position.z,
                transition,
                bone.a,
                true
            );

            return m == null ? new Matrix4f() : m;
        };

        drag.setRotateAxes(GizmoDrag.computeRotateAxes(transform.getTransform(), matrixSampler));
        drag.setJacobian(GizmoDrag.computeTranslateJacobian(
            transform.getTransform(),
            () -> matrixSampler.get().getTranslation(new Vector3f())
        ));
        /* Restore the form to its unperturbed state */
        Form form = entity.getForm();
        if (form != null)
        {
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);
            replay.properties.applyProperties(form, tick);
        }

        /* After the restore, so the evaluated channels the base reads reflect
         * the unperturbed pose (the helper re-collects the capture itself). */
        drag.setAdditiveRotationBase(filmPoseRotationBase(keyframeEditor, entity, transition, bone.a));

        return drag;
    }

    /**
     * The additive euler base under the edited pose/overlay track's channels for
     * the current bone ({@link FormUtils#additivePoseRotationBase}): the pose
     * stack merges per-channel, so an overlay's drag deltas must compose at the
     * bone's EFFECTIVE angles, not the overlay's own near-zero channels. The
     * total comes from the bone's EVALUATED channels in the render capture
     * ({@link BaseFilmController#getGizmoBoneEvaluatedRotation}) — folding the
     * animator's actions and the model's rest rotation in — with the edited
     * track resolved through the sheet's property path on the LIVE form and its
     * own contribution subtracted. {@code null} (zero base) when the track isn't
     * a pose one or the merge for this bone isn't purely additive.
     */
    private static Vector3f filmPoseRotationBase(UIKeyframeEditor keyframeEditor, IEntity entity, float transition, String bonePath)
    {
        if (!(keyframeEditor.editor instanceof UIPoseKeyframeFactory))
        {
            return null;
        }

        UIKeyframeSheet sheet = keyframeEditor.getSheet(keyframeEditor.editor.getKeyframe());

        if (sheet == null)
        {
            return null;
        }

        BaseValueBasic property = FormUtils.getProperty(entity.getForm(), sheet.id);

        if (!(property instanceof ValuePose valuePose))
        {
            return null;
        }

        Vector3f evaluated = BaseFilmController.getGizmoBoneEvaluatedRotation(entity, transition, bonePath);

        return FormUtils.additivePoseRotationBase(valuePose, StringUtils.fileName(bonePath), evaluated);
    }

    /**
     * Numeric Jacobian / rotate-axes for the anchor gizmo: the sampler returns
     * the form's resolved anchor matrix ({@link BaseFilmController#getGizmoAnchorCompositeMatrix},
     * the same {@code target} the form renders with), so perturbing the keyframe's
     * {@code anchor.transform} reveals how it moves the form in world space —
     * exactly mirroring the bone path in {@link #buildFilmGizmoDrag}.
     */
    private static void buildAnchorGizmoDrag(
        UIFilmPanel panel,
        Camera camera,
        GizmoDrag drag,
        UIPropTransform transform,
        Replay replay,
        IEntity entity,
        float transition
    )
    {
        Supplier<Matrix4f> matrixSampler = () ->
        {
            Form form = entity.getForm();
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);

            if (form != null)
            {
                /* Push the perturbed keyframe state onto the form so the resolved
                 * anchor matrix reflects this sample. */
                replay.properties.applyProperties(form, tick);
            }

            Matrix4f m = BaseFilmController.getGizmoAnchorCompositeMatrix(
                panel.getController().getEntities(),
                entity,
                replay,
                camera.position.x,
                camera.position.y,
                camera.position.z,
                transition
            );

            return m == null ? new Matrix4f() : m;
        };

        drag.setRotateAxes(GizmoDrag.computeRotateAxes(transform.getTransform(), matrixSampler));
        drag.setJacobian(GizmoDrag.computeTranslateJacobian(
            transform.getTransform(),
            () -> matrixSampler.get().getTranslation(new Vector3f())
        ));
        /* Restore the form to its unperturbed state */
        Form form = entity.getForm();
        if (form != null)
        {
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);
            replay.properties.applyProperties(form, tick);
        }
    }
}
