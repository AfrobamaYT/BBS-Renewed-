package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;

import java.util.List;

/**
 * G on a bone of an IK chain grabs the chain's target, so the limb follows: click the arm, G Y 1 Enter,
 * the hand control goes up and is keyed. The control sat inside the hand, under the gizmo rings, and
 * only the bone list reached it (speed test C2, 2026-10-16: 8.4 s; Blender 4.0 s via outliner, pose
 * mode and the control). Translating a chain bone itself did nothing useful -- the IK overrides it --
 * so G there loses nothing. R and S stay on the bone (rotating a chain bone swings the chain).
 */
public final class IKGriff
{
    private IKGriff()
    {}

    /** The translate key of a transform: redirected only for the film's bone transform */
    public static void greifen(UIPropTransform transform)
    {
        UIFilmPanel panel = BBSModClient.getDashboard() == null ? null : BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
        String ziel = panel == null ? null : ziel(panel, transform);

        if (ziel != null)
        {
            panel.replayEditor.pickForm(panel.replayEditor.getReplay().form.get(), ziel);

            UIPropTransform neu = UIReplayWurzelTransform.filmTransform(panel);

            if (neu != null)
            {
                neu.enableMode(TransformOp.TRANSLATE);

                return;
            }
        }

        transform.enableMode(TransformOp.TRANSLATE);
    }

    private static String ziel(UIFilmPanel panel, UIPropTransform transform)
    {
        if (panel.replayEditor == null || panel.replayEditor.getReplay() == null || panel.replayEditor.keyframeEditor == null
            || UIReplaysEditorUtils.getEditableTransform(panel.replayEditor.keyframeEditor) != transform
            || !(panel.replayEditor.keyframeEditor.editor instanceof UIPoseKeyframeFactory pose)
            || !(panel.replayEditor.getReplay().form.get() instanceof ModelForm form))
        {
            return null;
        }

        List<String> knochen = pose.poseEditor.groups.list.getCurrent();
        ModelInstance model = ModelFormRenderer.getModel(form);

        return knochen.size() != 1 || model == null ? null : ModelIKRuntime.zielFuer(model.model, form, knochen.get(0));
    }
}
