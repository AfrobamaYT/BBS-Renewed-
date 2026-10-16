package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blender's pose copy/paste: Ctrl+C copies the selected bones (all bones when none is selected) as
 * they stand at the film cursor, Ctrl+V writes them into the key at the cursor, Ctrl+Shift+V writes
 * them mirrored onto the opposite side. Works over the viewport and the pose editor; over the dope
 * sheet the keys stay with the keyframe copy/paste.
 *
 * <p>Same clipboard slot as the pose editor's copy/paste menu ("_CopyPose"). The extra "bones" list
 * carries bones at rest, which {@link Pose#toData} drops, so pasting them resets those bones.</p>
 */
public final class FilmPoseClipboard
{
    public static final KeyCombo PASTE_FLIPPED = new KeyCombo("pose_paste_flipped", IKey.raw("Paste pose flipped (left/right)"),
        InputConstants.KEY_V, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("transformations");

    private static final String SLOT = "_CopyPose";

    private FilmPoseClipboard()
    {}

    /** The keys on a pose key editor; inactive while the mouse is over the dope sheet. */
    public static void anmelden(UIPoseKeyframeFactory.UIPoseFactoryEditor element, UIKeyframes editor, Keyframe<Pose> keyframe)
    {
        java.util.function.Supplier<Boolean> frei = () -> element.getContext() != null && !editor.area.isInside(element.getContext());

        element.keys().register(Keys.COPY, () -> kopieren(element, editor, keyframe)).strict().active(frei).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        element.keys().register(Keys.PASTE, () -> einfuegen(element, editor, keyframe, false)).strict().active(frei).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        element.keys().register(PASTE_FLIPPED, () -> einfuegen(element, editor, keyframe, true)).strict().active(frei).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
    }

    /** The film cursor when this editor is the film's, else the edited key's own tick. */
    private static float tick(UIKeyframes editor, Keyframe<Pose> keyframe)
    {
        UIFilmPanel panel = film(editor);

        return panel == null ? keyframe.getTick() : panel.getCursor();
    }

    private static UIFilmPanel film(UIKeyframes editor)
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        boolean eigen = panel != null && panel.replayEditor != null && panel.replayEditor.keyframeEditor != null
            && panel.replayEditor.keyframeEditor.view == editor;

        return eigen ? panel : null;
    }

    public static void kopieren(UIPoseEditor element, UIKeyframes editor, Keyframe<Pose> keyframe)
    {
        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (sheet == null)
        {
            return;
        }

        float t = sheet.channel.getSourceTick(tick(editor, keyframe));
        Pose stand = (Pose) sheet.channel.interpolate(t, null);
        Set<String> knochen = new LinkedHashSet<>(element.groups.list.getCurrent());

        if (stand == null)
        {
            stand = new Pose();
        }

        if (knochen.isEmpty())
        {
            knochen.addAll(element.groups.list.getList());
            knochen.addAll(stand.transforms.keySet());
        }

        Pose kopie = new Pose();
        ListType liste = new ListType();

        for (String bone : knochen)
        {
            PoseTransform pt = stand.transforms.get(bone);

            kopie.get(bone).copy(pt == null ? new PoseTransform() : pt);
            liste.addString(bone);
        }

        MapType data = kopie.toData();

        data.put("bones", liste);
        Window.setClipboard(data, SLOT);
        element.getContext().notifyInfo(IKey.raw("Copied pose: " + knochen.size() + (knochen.size() == 1 ? " bone" : " bones")));
        UIUtils.playClick();
    }

    public static void einfuegen(UIPoseEditor element, UIKeyframes editor, Keyframe<Pose> keyframe, boolean gespiegelt)
    {
        MapType data = Window.getClipboardMap(SLOT);
        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (data == null || sheet == null)
        {
            element.getContext().notifyInfo(IKey.raw("No pose copied (Ctrl+C over the viewport or pose editor)"));

            return;
        }

        Pose quelle = new Pose();
        List<String> knochen = new ArrayList<>();

        quelle.fromData(data);

        for (int i = 0, n = data.getList("bones").size(); i < n; i++)
        {
            knochen.add(data.getList("bones").getString(i));
        }

        if (knochen.isEmpty())
        {
            knochen.addAll(quelle.transforms.keySet());
        }

        if (gespiegelt)
        {
            knochen = spiegeln(quelle, knochen, element.getFlippedParts());
        }

        List<String> auswahl = new ArrayList<>(element.groups.list.getCurrent());
        Keyframe<Pose> ziel = schreiben(editor, sheet, tick(editor, keyframe), quelle, knochen);

        if (ziel == null)
        {
            return;
        }

        UIFilmPanel panel = film(editor);

        editor.getGraph().clearSelection();
        sheet.selection.add(sheet.channel.getKeyframes().indexOf(ziel));
        editor.getGraph().pickKeyframe(ziel);

        /* pickKeyframe builds a new pose editor; give it back the bone selection */
        if (panel != null && panel.replayEditor.keyframeEditor.editor instanceof UIPoseKeyframeFactory neu)
        {
            for (int i = 0; i < auswahl.size(); i++)
            {
                neu.poseEditor.selectBone(auswahl.get(i), i > 0);
            }
        }

        UIUtils.playClick();
    }

    /** Mirrors the pose in place; returns the bones that now hold the copied ones (their mirror names). */
    private static List<String> spiegeln(Pose pose, List<String> knochen, Map<String, String> paare)
    {
        List<String> ziel = new ArrayList<>();

        for (String bone : knochen)
        {
            pose.get(bone); /* rest bones need an entry, or flip leaves their partner untouched */
        }

        pose.flip(paare);

        for (String bone : knochen)
        {
            ziel.add(partner(bone, paare));
        }

        return ziel;
    }

    /** The bone's left/right partner: the model's pairs (either direction), else the name pattern (or the bone itself) */
    public static String partner(String bone, Map<String, String> paare)
    {
        String partner = paare == null ? null : paare.get(bone);

        if (partner == null && paare != null)
        {
            for (Map.Entry<String, String> e : paare.entrySet())
            {
                if (e.getValue().equals(bone)) partner = e.getKey();
            }
        }

        return partner != null ? partner : Pose.getMirrorName(bone);
    }

    /** One undo step, also when the key at the cursor has to be created (as {@link FilmPoseKey}). */
    private static Keyframe<Pose> schreiben(UIKeyframes editor, UIKeyframeSheet sheet, float tick, Pose quelle, List<String> knochen)
    {
        UIFilmPanel panel = film(editor);
        Keyframe<Pose>[] ziel = new Keyframe[1];

        if (panel != null)
        {
            panel.getUndoHandler().submitUndo();
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }

        BaseValue.edit(sheet.channel, channel ->
        {
            ziel[0] = UIReplaysEditorUtils.ensureKeyframe(sheet, tick);

            if (ziel[0] == null)
            {
                return;
            }

            Pose pose = ziel[0].getValue();

            for (String bone : knochen)
            {
                PoseTransform pt = quelle.transforms.get(bone);

                pose.get(bone).copy(pt == null ? new PoseTransform() : pt);
            }
        });

        if (panel != null)
        {
            panel.getUndoHandler().submitUndo();
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }

        return ziel[0];
    }
}
