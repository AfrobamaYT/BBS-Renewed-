package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.cubic.ik.RigCtrl;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;

/**
 * Wer waehrend eines Zuges was zeichnet — Gizmo und Zughilfe sind zwei Dinge.
 *
 * <p>⛔ Am Control-Rig zeigt BBS absichtlich KEIN Gizmo ("das Rig IST der Griff", so haelt
 * es Blender im Pose-Mode auch). Damit fiel aber auch der Readout weg — die Fuehrungslinie
 * und die Zahlen, die Blender waehrend G/R/S sehr wohl zeichnet. Man zog blind
 * (gemeldet 2026-08-14).
 *
 * <p>⛔ Beim Rig-Zug laeuft NUR {@code renderReadout}, nicht {@code update} oder
 * {@code renderSphereHighlight}: deren Pick-Stencil legte sich frueher ueber die Controls
 * und frass jeden Klick.
 */
public final class ZugHilfe
{
    private ZugHilfe()
    {}

    public static void zeichne(GizmoInteraction gizmo, UIContext context, boolean vollesGizmo,
        boolean rigAktiv, UIKeyframeEditor editor)
    {
        if (vollesGizmo)
        {
            gizmo.update(context);
            gizmo.renderSphereHighlight(context);
        }

        if (vollesGizmo || (rigAktiv && zieht(editor)))
        {
            gizmo.renderReadout(context);
        }
    }

    private static boolean zieht(UIKeyframeEditor editor)
    {
        UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(editor);

        return transform != null && transform.isEditing();
    }
}
