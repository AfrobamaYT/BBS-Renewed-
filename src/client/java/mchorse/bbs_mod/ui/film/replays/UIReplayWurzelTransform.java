package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The film gizmo on a replay's own placement (where the actor stands and faces), from BBS FS's
 * UIReplayPropTransform (Wemppy4/bbs-fs, MIT); before, the actor could only be scrubbed in the side
 * panel (speed test T1, 2026-10-16). A scratch transform for the gizmo, every change written back as
 * x/y/z and yaw/pitch keys at the playhead; target when no bone or track claims the gizmo.
 * translate = world blocks; rotate.y = -yaw (the actor turns with rotateY(-bodyYaw)), rotate.x = pitch;
 * no roll, no size; a turn moves yaw, headYaw and bodyYaw together. One instance.
 */
public class UIReplayWurzelTransform extends UIPropTransform
{
    private static UIReplayWurzelTransform instanz;

    private final Transform scratch = new Transform();
    /** Channels that had no key at the tick before this gesture: Escape takes the new keys out again */
    private final Set<KeyframeChannel<Double>> neu = new LinkedHashSet<>();
    private Replay replay;
    private IEntity entity;
    private float tick;
    private double kopfVersatz;
    private double koerperVersatz;

    private UIReplayWurzelTransform()
    {
        /* One undo entry per gesture: writes merge on the keyframes group, the gesture's end seals it */
        this.callbacks(() -> this.melden(true, 0), () -> this.melden(false, 0), () -> this.melden(true, IValueListener.FLAG_UNMERGEABLE));
        this.enableHotkeys(); /* R/G/S; fired by UIFilmPreview while this is the film's transform */
        this.setVisible(false);
    }

    private void melden(boolean vorher, int flag)
    {
        if (this.replay != null && vorher) this.replay.keyframes.preNotify(flag);
        else if (this.replay != null) this.replay.keyframes.postNotify(flag);
    }

    /** UIFilmUndoHandler: while a gesture runs it holds the entry back, one entry at release */
    public static boolean ziehtGerade()
    {
        return instanz != null && instanz.isEditing();
    }

    /** The transform the film's gizmo and R/G/S edit: a bone or track's, else the replay placement */
    public static UIPropTransform filmTransform(UIFilmPanel panel)
    {
        UIPropTransform eigen = panel == null || panel.replayEditor == null ? null : UIReplaysEditorUtils.getEditableTransform(panel.replayEditor.keyframeEditor);

        return eigen != null ? eigen : wurzel(panel);
    }

    /** Render eligibility only: refreshing numeric controls can upload a cold glyph atlas. */
    public static boolean istWurzelZiel(UIFilmPanel panel)
    {
        if (panel == null || panel.replayEditor == null) return false;
        UIPropTransform eigen = UIReplaysEditorUtils.getEditableTransform(panel.replayEditor.keyframeEditor);
        return eigen instanceof UIReplayWurzelTransform || eigen == null
            && panel.replayEditor.getReplay() != null && panel.getController().getCurrentEntity() != null;
    }

    /** The placement target synced to the playhead, or null (no replay, no actor) */
    public static UIReplayWurzelTransform wurzel(UIFilmPanel panel)
    {
        Replay replay = panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();
        IEntity entity = replay == null ? null : panel.getController().getCurrentEntity();

        if (entity == null)
        {
            return null;
        }

        if (instanz == null)
        {
            instanz = new UIReplayWurzelTransform();
        }

        /* Hidden in the tree, as BBS FS adds it to the replays editor: a gesture reads the mouse from its context */
        if (instanz.getParent() != panel.replayEditor)
        {
            instanz.removeFromParent();
            panel.replayEditor.add(instanz);
        }

        instanz.laden(replay, entity, panel.getCursor());

        return instanz;
    }

    /** Read from the live entity: that is where the actor is drawn, looping and empty channels included */
    private void laden(Replay replay, IEntity entity, float tick)
    {
        if (this.isEditing())
        {
            return; /* a running gesture rebuilds its values from the pose it grabbed */
        }

        double yaw = entity.getYaw();

        this.replay = replay;
        this.entity = entity;
        this.tick = tick;
        this.kopfVersatz = entity.getHeadYaw() - yaw;
        this.koerperVersatz = entity.getBodyYaw() - yaw;
        this.scratch.rotationMode = Transform.RotationMode.EULER;
        this.scratch.translate.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
        this.scratch.rotate.set(MathUtils.toRad(entity.getPitch()), MathUtils.toRad((float) -yaw), 0F);
        this.scratch.scale.set(1F, 1F, 1F);
        this.neu.clear();
        this.setTransform(this.scratch);
    }

    /** Looping replays read their channels through Replay#getTick: write where playback looks */
    private float schreibTick()
    {
        return this.replay.getTick((int) this.tick) + (this.tick - (int) this.tick);
    }

    private void schreiben(KeyframeChannel<Double> kanal, double wert)
    {
        float t = this.schreibTick();
        boolean da = false;

        for (Keyframe<Double> k : kanal.getKeyframes())
        {
            da |= Math.abs(k.getTick() - t) < 1.0E-3F;
        }

        if (!da && this.isEditing()) this.neu.add(kanal);

        kanal.insert(t, wert);
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        /* Unchanged: an R gesture passes the position along too, and wrote x/y/z keys (C5, 2026-10-16) */
        if (this.replay == null || this.scratch.translate.equals((float) x, (float) y, (float) z))
        {
            return;
        }

        this.preCallback();
        this.scratch.translate.set((float) x, (float) y, (float) z);
        /* All three, even for one axis: a half-keyed position would read the others from the neighbours */
        this.schreiben(this.replay.keyframes.x, x);
        this.schreiben(this.replay.keyframes.y, y);
        this.schreiben(this.replay.keyframes.z, z);
        this.entity.setPosition(x, y, z); /* the entity re-reads its channels only on the 20 Hz tick */
        this.postCallback();
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        if (this.replay == null || new org.joml.Vector3f(MathUtils.toRad((float) x), MathUtils.toRad((float) y), 0F).equals(this.scratch.rotate, 1.0E-5F))
        {
            return;
        }

        double yaw = -y;

        this.preCallback();
        this.scratch.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), 0F);
        this.schreiben(this.replay.keyframes.pitch, x);
        this.schreiben(this.replay.keyframes.yaw, yaw);
        this.schreiben(this.replay.keyframes.headYaw, yaw + this.kopfVersatz);
        this.schreiben(this.replay.keyframes.bodyYaw, yaw + this.koerperVersatz);
        this.entity.setPitch((float) x);
        this.entity.setYaw((float) yaw);
        this.entity.setHeadYaw((float) (yaw + this.kopfVersatz));
        this.entity.setBodyYaw((float) (yaw + this.koerperVersatz));
        this.postCallback();
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {}

    @Override
    public void setRQuat(Quaternionf quat)
    {} /* no size, no quaternion: the placement has neither */

    @Override
    public void acceptChanges()
    {
        super.acceptChanges();
        this.neu.clear();
    }

    @Override
    public void rejectChanges()
    {
        super.rejectChanges();

        if (this.replay != null)
        {
            float t = this.schreibTick();

            this.melden(true, 0);

            for (KeyframeChannel<Double> kanal : this.neu)
            {
                for (int i = kanal.getKeyframes().size() - 1; i >= 0; i--)
                {
                    if (Math.abs(kanal.get(i).getTick() - t) < 1.0E-3F) kanal.remove(i);
                }
            }

            this.melden(false, 0);
        }

        this.neu.clear();
    }

    /**
     * From BaseFilmController.renderEntity beside the anchor gizmo: the placement gizmo at the actor's
     * feet while it is the target. Not in the Camera View's pass (its camera would place it).
     */
    public static void gizmo(IEntity entity, double cx, double cy, double cz, float transition, TransformSpace space, Matrix4f gizmoView, StencilMap map, PoseStack stack)
    {
        UIReplayWurzelTransform w = instanz;
        UIFilmPanel panel = mchorse.bbs_mod.BBSModClient.getDashboard() == null ? null : mchorse.bbs_mod.BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);

        if (w == null || w.entity != entity || panel == null || com.afrobama.blendertransform.ZweitKamera.aktiv() || mchorse.bbs_mod.ui.film.controller.ErstKlick.wartet() || !istWurzelZiel(panel))
        {
            return;
        }

        stack.pushPose();
        stack.translate(entity.getX() - cx, entity.getY() - cy, entity.getZ() - cz);
        Gizmo.INSTANCE.reorientForSpace(stack, space, gizmoView, BaseFilmController.getReplayWorldAxes(entity, transition));

        if (map == null) Gizmo.INSTANCE.captureVisual(stack);
        else Gizmo.INSTANCE.renderStencil(stack, map);

        stack.popPose();
    }
}
