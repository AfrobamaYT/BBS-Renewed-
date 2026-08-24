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

    /**
     * Release-Haelfte: ein noch bereiter Griff (Down auf Control, Schwelle nie gerissen)
     * ist ein KLICK — orbit.wasDragged klebt hier vom LETZTEN Kamera-Drag und darf nicht
     * mitreden. Leerer Pick = Ctrl_Master -> Form-Spur (Master bewegt ALLES).
     * true = vollstaendig behandelt.
     */
    public boolean release(UIReplaysEditor editor, UIContext context, Pair<Form, String> pending)
    {
        boolean widgetKlick = this.bereit;

        this.bereit = false;

        if (!widgetKlick || pending == null || context.mouseButton != 0)
        {
            return false;
        }

        /* ⛔ KEIN PANELWECHSEL BEIM REINEN KLICK (2026-08-23, vom Nutzer gemeldet: „an einem
         * Control-Rig-Ring geklickt und das hat die alte Timeline geoeffnet").
         *
         * Hier stand `showPanel(editor)`. Fuer den KLICK ist das unnoetig: gemessen am
         * laufenden Editor sind `UIReplaysListPanel` und `UIReplayPropertiesPanel`
         * (UIPropertiesNano) im Kameramodus BEREITS sichtbar — die Auswahl hat also ihr
         * Ziel, ohne dass die Zeitleiste unter dem Nutzer weggetauscht wird. Blender macht
         * es genauso: eine Bone-Auswahl im Pose-Mode wechselt keinen Editor.
         *
         * Der Wechsel bleibt in {@link #foerdere} stehen, wo er einen belegten Grund hat:
         * die modale UIPropTransform tickt NUR im render() eines sichtbaren Panels. */

        if (pending.b == null || pending.b.isEmpty())
        {
            UIReplaysEditorUtils.pickProperty(editor.keyframeEditor, editor.filmPanel, "", "transform", true);

            return true;
        }

        /* ⛔ AUSWAEHLEN, CURSOR ZURUECKSTELLEN, DANN SETZEN (Wunsch des Nutzers 2026-08-24:
         * "wenn man den Ring klickt, dass dann ein Pose-Keyframe gesetzt wird, wenn da
         * keiner ist"). Die Reihenfolge ist zweimal gemessen worden:
         *   1. Einfuegen VOR der Auswahl scheitert im Kamera-Blick still — die Kategorie
         *      steht noch auf PLAYER (Sonde: category=PLAYER), der Pose-Kanal existiert
         *      erst, nachdem pickForm auf POSE umgeschaltet hat.
         *   2. Einfuegen NACH der Auswahl ohne Zuruecksetzen scheitert auch: die Auswahl
         *      SPRINGT den Abspielkopf auf den naechsten vorhandenen Pose-Keyframe
         *      (cursor.setCursor(closest)), das Einfuegen landet also immer auf einem
         *      bestehenden. Gemessen: Cursor 377 -> Kopf auf 0, nichts gesetzt.
         * Also: auswaehlen (baut Kategorie und Kanal), Cursor auf den Klick-Zeitpunkt
         * zurueck, dann einfuegen — insertIntoPoseSheet setzt nur, wenn dort keiner ist. */
        int tick = editor.filmPanel.getCursor();

        editor.pickForm(pending.a, pending.b);
        editor.filmPanel.setCursor(tick);
        UIReplaysEditorUtils.pickForm(editor.keyframeEditor, editor.filmPanel,
            pending.a, pending.b, true);

        return true;
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

        /* Leerer Pick = Ctrl_Master: greift die Form-Spur (Master bewegt ALLES). */
        if (pick.b == null || pick.b.isEmpty())
        {
            UIReplaysEditorUtils.pickProperty(editor.keyframeEditor, editor.filmPanel, "", "transform", true);
        }
        else
        {
            editor.pickForm(pick.a, pick.b);
        }

        UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(editor.keyframeEditor);

        if (transform == null)
        {
            return;
        }

        /* ⛔ In Blender gesperrte Controls bleiben ANKLICKBAR (man will ihre Regler
         * sehen), lassen sich aber nicht ziehen. Die Auswahl oben ist also richtig —
         * nur der Transform-Modus darf hier nicht starten. */
        if (RigCtrl.gesperrt(RigCtrl.fuerEntity(editor.filmPanel.getController().getCurrentEntity()), pick.b))
        {
            return;
        }

        /* Der Hotkey-Achsen-Walk faellt ohne Gizmo auf Achse X (gemessen: die Fussbox
         * wanderte nur seitlich) — deshalb direkt Blenders Startmodi. */
        if (RigCtrl.dragRotiert(RigCtrl.fuerEntity(editor.filmPanel.getController().getCurrentEntity()), pick.b))
        {
            transform.enableViewRotate(null, true);
        }
        else
        {
            transform.enableScreenTranslate(null, true);
        }
    }
}
