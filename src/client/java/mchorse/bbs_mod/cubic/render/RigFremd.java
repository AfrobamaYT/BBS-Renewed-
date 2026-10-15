package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.cubic.ik.RigCtrl;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.CollectionUtils;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import io.netty.util.collection.IntObjectMap;

import java.util.List;
import java.util.Map;

/**
 * The control rigs of the replays that are not selected: drawn dimmed and clickable, so a
 * click on another actor's control switches to that replay and selects the control — what
 * Blender does with several armatures in pose mode (owner, 2026-10-14: "die Drivers immer
 * sehen ... draufklicken und direkt switchen").
 *
 * <p>The film's pick stencil only holds the selected replay (outside Alt), so the hit test here
 * works in screen space on the projected widget lines, with the gizmo's hover tolerance.
 */
public final class RigFremd
{
    private RigFremd()
    {}

    /** Whether any replay other than {@code aktuell} brings a control rig. */
    public static boolean gibt(IntObjectMap<IEntity> entities, IEntity aktuell)
    {
        for (IEntity e : entities.values())
        {
            if (e != aktuell && rigVon(e) != null)
            {
                return true;
            }
        }

        return false;
    }

    public static void weltPass(BufferBuilder wire, Matrix4f sicht, Vec3 kamera, IntObjectMap<IEntity> entities,
        List<Replay> replays, IEntity aktuell, float a, float transition)
    {
        for (Map.Entry<Integer, IEntity> eintrag : entities.entrySet())
        {
            IEntity e = eintrag.getValue();
            List<RigCtrl.Eintrag> rig = e == aktuell ? null : rigVon(e);
            Replay replay = CollectionUtils.getSafe(replays, eintrag.getKey());

            if (rig == null || replay == null)
            {
                continue;
            }

            /* Dimmed like Blender's armatures that are not in pose mode. */
            RigCtrlZeichner.weltPass(wire, sicht, kamera, entities, e, replay, rig, "", a * 0.5F, transition);
            RigDeko.weltPass(wire, sicht, kamera, entities, e, replay, ((ModelForm) e.getForm()).model.get(), a, transition);
        }
    }

    /**
     * The other replay's control under the mouse, or null: its index and the control's pick bone.
     */
    public static Object[] treffer(UIFilmPanel panel, int mausX, int mausY, Area feld)
    {
        IntObjectMap<IEntity> entities = panel.getController().getEntities();
        IEntity aktuell = panel.getController().getCurrentEntity();
        List<Replay> replays = panel.getData() == null ? null : panel.getData().replays.getList();
        Camera camera = panel.getCamera();

        if (replays == null || camera == null || !feld.isInside(mausX, mausY))
        {
            return null;
        }

        Matrix4f pv = new Matrix4f(camera.projection).mul(camera.view);
        float toleranz = Math.max(6F, mchorse.bbs_mod.BBSSettings.gizmoHoverTolerance.get());
        float bester = toleranz * toleranz;
        Object[] treffer = null;

        for (Map.Entry<Integer, IEntity> eintrag : entities.entrySet())
        {
            IEntity e = eintrag.getValue();
            List<RigCtrl.Eintrag> rig = e == aktuell ? null : rigVon(e);
            Replay replay = CollectionUtils.getSafe(replays, eintrag.getKey());

            if (rig == null || replay == null)
            {
                continue;
            }

            BOBJArmature armature = RigCtrl.armatureVon(e);

            for (RigCtrl.Eintrag c : rig)
            {
                if (!RigCtrl.sichtbar(c, armature))
                {
                    continue;
                }

                Matrix4f welt = c.anker().isEmpty()
                    ? BaseFilmController.getGizmoAnchorCompositeMatrix(entities, e, replay, 0D, 0D, 0D, 0F)
                    : BaseFilmController.getBoneCompositeMatrix(entities, e, replay, 0D, 0D, 0D, 0F, c.anker(), true);

                if (welt == null)
                {
                    continue;
                }

                for (float[] linie : c.linien())
                {
                    float[] vorher = null;

                    for (int i = 0; i + 2 < linie.length; i += 3)
                    {
                        float[] jetzt = schirm(pv, welt, camera, linie[i], linie[i + 1], linie[i + 2], feld);

                        if (jetzt != null && vorher != null)
                        {
                            float d = abstandQuadrat(mausX, mausY, vorher, jetzt);

                            if (d < bester)
                            {
                                bester = d;
                                treffer = new Object[] {eintrag.getKey(), c.pick()};
                            }
                        }

                        vorher = jetzt;
                    }
                }
            }
        }

        return treffer;
    }

    /**
     * Left click in the film viewport on another replay's control: switch to that replay and
     * select the control. Only when the selected replay's own pick found nothing there.
     */
    public static boolean klick(UIFilmPanel panel, mchorse.bbs_mod.ui.framework.UIContext context, Area feld,
        boolean eigenerTreffer)
    {
        if (context.mouseButton != 0 || eigenerTreffer)
        {
            return false;
        }

        Object[] t = treffer(panel, context.mouseX, context.mouseY, feld);
        IEntity e = t == null ? null : panel.getController().getEntities().get((Integer) t[0]);
        Replay replay = t == null ? null : CollectionUtils.getSafe(panel.getData().replays.getList(), (Integer) t[0]);

        if (e == null || replay == null)
        {
            return false;
        }

        panel.replayEditor.setReplay(replay, true, mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.OrbitReaction.KEEP);
        panel.replayEditor.pickForm(e.getForm(), (String) t[1]);

        return true;
    }

    private static List<RigCtrl.Eintrag> rigVon(IEntity e)
    {
        if (e == null || !(e.getForm() instanceof ModelForm form)
            || !(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer)
            || renderer.getModel() == null || !(renderer.getModel().model instanceof BOBJModel bobj))
        {
            return null;
        }

        return RigCtrl.fuer(bobj.getArmature(), form.model.get());
    }

    private static float[] schirm(Matrix4f pv, Matrix4f welt, Camera camera, float x, float y, float z, Area feld)
    {
        Vector3f p = welt.transformPosition(x, y, z, new Vector3f());
        Vector4f clip = pv.transform(new Vector4f((float) (p.x - camera.position.x),
            (float) (p.y - camera.position.y), (float) (p.z - camera.position.z), 1F));

        if (clip.w <= 1E-4F)
        {
            return null;
        }

        return new float[] {feld.x + (clip.x / clip.w + 1F) * 0.5F * feld.w,
            feld.y + (1F - clip.y / clip.w) * 0.5F * feld.h};
    }

    private static float abstandQuadrat(float px, float py, float[] a, float[] b)
    {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float l = dx * dx + dy * dy;
        float t = l <= 0F ? 0F : Math.max(0F, Math.min(1F, ((px - a[0]) * dx + (py - a[1]) * dy) / l));
        float qx = a[0] + t * dx - px, qy = a[1] + t * dy - py;

        return qx * qx + qy * qy;
    }
}
