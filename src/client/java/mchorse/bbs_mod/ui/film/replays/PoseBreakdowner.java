package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blender's pose breakdowner (Shift+E) and Maya's tween machine: a pose between the key before
 * and the key after the cursor, set by moving the mouse sideways (left = the pose before, right =
 * the pose after). It writes the selected bones -- all bones when none is selected -- into the
 * key at the cursor, creating it when there is none. LMB or Enter keeps it, RMB or Escape leaves
 * everything as it was. One Undo takes back the blend together with a key it had to create
 * (laeuft() holds the undo handler for the whole gesture; measured 2026-10-15: 2 steps -> 1).
 *
 * <p>Ctrl+E and Alt+E are Blender's Push and Relax (Katalog D4): the breakdown is the linear blend of
 * the neighbouring keys at the cursor; Relax moves the current pose toward it, Push exaggerates the
 * pose away from it (mouse right: 0 to 100 %). Both touch translate, rotate and scale only.</p>
 *
 * <p>Modal like the R/G/S gestures: an element over the whole dashboard takes the clicks and the
 * two keys while it runs, so Escape cancels instead of closing the editor.</p>
 */
public class PoseBreakdowner extends UIElement
{
    public static final KeyCombo TASTE = new KeyCombo("pose_breakdown", IKey.raw("Breakdown: blend the pose between the keys before and after"),
        InputConstants.KEY_E, InputConstants.KEY_LSHIFT).categoryKey("transformations");
    public static final KeyCombo PUSH = new KeyCombo("pose_push", IKey.raw("Push: exaggerate the pose away from the breakdown"),
        InputConstants.KEY_E, InputConstants.KEY_LCONTROL).categoryKey("transformations");
    public static final KeyCombo RELAX = new KeyCombo("pose_relax", IKey.raw("Relax: ease the pose toward the breakdown"),
        InputConstants.KEY_E, InputConstants.KEY_LALT).categoryKey("transformations");

    private static final int BREAKDOWN = 0;
    private static final int PUSH_ART = 1;
    private static final int RELAX_ART = 2;

    /** Mouse travel (GUI pixels) for the whole way from the pose before to the pose after */
    private static final float WEG = 240F;

    private final UIKeyframeSheet sheet;
    private final Keyframe<Pose> davor;
    private final Keyframe<Pose> danach;
    private final Keyframe<Pose> ziel;
    private final boolean neu;
    private final List<String> knochen;
    private final Map<String, PoseTransform> vorher = new HashMap<>();
    private final float startFaktor;
    private final int art;
    /* Push/Relax: the linear blend of the neighbours at the cursor */
    private final float zeitAnteil;
    private int startX = Integer.MIN_VALUE;
    private float faktor;
    private boolean angewendet;

    /** While it runs the undo handler holds every change (key creation included) for one entry */
    public static boolean laeuft()
    {
        return !BBSModClient.getDashboard().getRoot().getChildren(PoseBreakdowner.class).isEmpty();
    }

    /** The key on a pose key editor (its bone list says which bones) */
    public static void anmelden(UIPoseEditor element, UIKeyframes editor, Keyframe<Pose> keyframe)
    {
        element.keys().register(TASTE, () -> starten(element.getContext(), editor, keyframe, element.groups.list.getCurrent(), BREAKDOWN))
            .category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        element.keys().register(PUSH, () -> starten(element.getContext(), editor, keyframe, element.groups.list.getCurrent(), PUSH_ART))
            .category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        element.keys().register(RELAX, () -> starten(element.getContext(), editor, keyframe, element.groups.list.getCurrent(), RELAX_ART))
            .category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
    }

    /** Starts it for the pose key editor's channel at the film cursor; false (and a message) without a key on both sides. */
    public static boolean starten(UIContext context, UIKeyframes editor, Keyframe<Pose> keyframe, List<String> gewaehlt, int art)
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        UIKeyframeSheet sheet = keyframe == null ? null : editor.getGraph().getSheet(keyframe);

        if (panel == null || sheet == null)
        {
            return false;
        }

        float tick = panel.getCursor();
        Keyframe<Pose> davor = null;
        Keyframe<Pose> danach = null;
        boolean vorhanden = false;

        for (Object o : sheet.channel.getKeyframes())
        {
            Keyframe<Pose> k = (Keyframe<Pose>) o;

            if (k.getTick() < tick) davor = k;
            else if (k.getTick() > tick && danach == null) danach = k;
            else if (k.getTick() == tick) vorhanden = true;
        }

        if (davor == null || danach == null)
        {
            context.notifyInfo(IKey.raw((art == PUSH_ART ? "Push" : art == RELAX_ART ? "Relax" : "Breakdown") + " needs a pose key before and after the cursor"));

            return false;
        }

        Set<String> knochen = new LinkedHashSet<>(gewaehlt);

        if (knochen.isEmpty())
        {
            knochen.addAll(davor.getValue().transforms.keySet());
            knochen.addAll(danach.getValue().transforms.keySet());
        }

        Keyframe<Pose> ziel = UIReplaysEditorUtils.ensureKeyframe(sheet, tick);

        if (ziel == null)
        {
            return false;
        }

        PoseBreakdowner modal = new PoseBreakdowner(sheet, davor, danach, ziel, !vorhanden, new ArrayList<>(knochen), (tick - davor.getTick()) / (danach.getTick() - davor.getTick()), art);
        UIElement root = BBSModClient.getDashboard().getRoot();

        modal.relative(root).wh(1F, 1F);
        root.add(modal);
        modal.resize();

        return true;
    }

    private PoseBreakdowner(UIKeyframeSheet sheet, Keyframe<Pose> davor, Keyframe<Pose> danach, Keyframe<Pose> ziel, boolean neu, List<String> knochen, float zeitAnteil, int art)
    {
        this.sheet = sheet;
        this.davor = davor;
        this.danach = danach;
        this.ziel = ziel;
        this.neu = neu;
        this.knochen = knochen;
        this.art = art;
        this.zeitAnteil = zeitAnteil;
        this.startFaktor = this.faktor = art == BREAKDOWN ? zeitAnteil : 0F;

        for (String bone : knochen)
        {
            PoseTransform alt = ziel.getValue().transforms.get(bone);

            this.vorher.put(bone, alt == null ? null : (PoseTransform) alt.copy());
        }
    }

    private void anwenden(float f)
    {
        Pose pose = this.ziel.getValue();

        this.ziel.preNotify();

        for (String bone : this.knochen)
        {
            PoseTransform a = this.davor.getValue().transforms.get(bone);
            PoseTransform b = this.danach.getValue().transforms.get(bone);
            PoseTransform mix = (PoseTransform) (a == null ? new PoseTransform() : a).copy();

            if (this.art == BREAKDOWN)
            {
                mix.lerp(b == null ? new PoseTransform() : b, f);
                pose.get(bone).copy(mix);

                continue;
            }

            /* Plain Transforms: Push extrapolates, and colours or rig values must not run past their range */
            Transform zwischen = new Transform();
            Transform jetzt = new Transform();
            PoseTransform alt = this.vorher.get(bone);

            mix.lerp(b == null ? new PoseTransform() : b, this.zeitAnteil);
            zwischen.copy(mix);
            jetzt.copy(alt == null ? new PoseTransform() : alt);

            if (this.art == PUSH_ART)
            {
                zwischen.lerp(jetzt, 1F + f);
                jetzt = zwischen;
            }
            else
            {
                jetzt.lerp(zwischen, f);
            }

            PoseTransform wert = pose.get(bone);

            wert.translate.set(jetzt.translate);
            wert.scale.set(jetzt.scale);
            wert.rotate.set(jetzt.rotate);
            wert.quat.set(jetzt.quat);
            wert.rotationMode = jetzt.rotationMode;
        }

        this.ziel.postNotify();
    }

    private void beenden(boolean behalten)
    {
        if (!behalten)
        {
            Pose pose = this.ziel.getValue();

            this.ziel.preNotify();

            for (Map.Entry<String, PoseTransform> e : this.vorher.entrySet())
            {
                if (e.getValue() == null) pose.transforms.remove(e.getKey());
                else pose.get(e.getKey()).copy(e.getValue());
            }

            this.ziel.postNotify();

            if (this.neu)
            {
                this.sheet.channel.remove(this.sheet.channel.getKeyframes().indexOf(this.ziel));
            }
        }

        /* Seal the undo block, as the transform gestures do (UIPoseTransforms.endGesture) */
        this.ziel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.removeFromParent();
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.beenden(context.mouseButton == 0);

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(InputConstants.KEY_RETURN) || context.isPressed(InputConstants.KEY_ESCAPE))
        {
            this.beenden(context.isPressed(InputConstants.KEY_RETURN));

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.startX == Integer.MIN_VALUE)
        {
            this.startX = context.mouseX;
        }

        float f = MathUtils.clamp(this.startFaktor + (context.mouseX - this.startX) / WEG, 0F, 1F);
        String name = this.art == PUSH_ART ? "Push" : this.art == RELAX_ART ? "Relax" : "Breakdown";

        if (f != this.faktor || !this.angewendet)
        {
            this.faktor = f;
            this.angewendet = true;
            this.anwenden(f);
        }

        context.batcher.textCard(name + " " + Math.round(f * 100) + " %   LMB keep · RMB/Esc cancel", context.mouseX + 12, context.mouseY + 8);

        super.render(context);
    }
}
