package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.cubic.ik.RigCtrl;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.utils.Pair;

/**
 * Blenders Pose-Mode-Griff (Live-Keymap gemessen: LMB CLICK_DRAG -> transform.translate):
 * liegt der Mausdruck auf einem Control des {@link RigCtrl}-Rigs, greift der Drag ab
 * ~3px das Control selbst — Screen-Ebenen-Translate wie G, auf location-gelockten
 * FK-Ringen View-Rotate wie R. Das Rig ist der Griff, kein Gizmo.
 */
public class CtrlGriff
{
    private boolean bereit;
    private int x;
    private int y;

    public void merken(UIContext context)
    {
        this.bereit = true;
        this.x = context.mouseX;
        this.y = context.mouseY;
    }

    public void loslassen()
    {
        this.bereit = false;
    }

    /** Je Bild rufen; startet den modalen Grab, sobald die gehaltene Maus die Schwelle reisst. */
    public void foerdere(UIReplaysEditor editor, UIContext context)
    {
        if (!this.bereit || editor.pendingPick == null)
        {
            return;
        }

        int dx = context.mouseX - this.x;
        int dy = context.mouseY - this.y;

        if (dx * dx + dy * dy < 9)
        {
            return;
        }

        Pair<Form, String> pick = editor.pendingPick;

        this.bereit = false;
        editor.pendingPick = null;

        /* Ohne sichtbares Panel rendert die UIPropTransform nicht — ihr modaler Grab
         * tickt NUR im render() (editing=true, Wert stand still; gemessen 2026-08-14). */
        if (!editor.isVisible())
        {
            editor.filmPanel.showPanel(editor);
        }

        editor.pickForm(pick.a, pick.b);

        UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(editor.keyframeEditor);

        if (transform == null)
        {
            return;
        }

        /* Der Hotkey-Achsen-Walk faellt ohne Gizmo auf Achse X (gemessen: die Fussbox
         * wanderte nur seitlich) — deshalb direkt Blenders Startmodi. */
        if (RigCtrl.dragRotiert(pick.b))
        {
            transform.enableViewRotate(null, true);
        }
        else
        {
            transform.enableScreenTranslate(null, true);
        }
    }
}
