package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Die Eigenschaften eines Kamera-Clips im Dock des Replay-Editors zeigen — dort, wo sonst die
 * Knochen-Eigenschaften eines Pose-Keyframes stehen.
 *
 * <p>USER-WUNSCH 2026-07-29: „mann soll auch wenn man die clips selected die properties sehen
 * vom clip (da im dock wo die bone properties vom pose keyframe sind)."</p>
 *
 * <p>Gemessen 2026-07-29: die Oberfläche für den Clip existiert bereits, sobald er gewählt ist
 * ({@code UIClipsPanel#pickClip} baut sie), und beide Editoren zeigen auf DASSELBE Dock-Element.
 * Sie ist nur unsichtbar und hängt unter der Kamera-Leiste, die im Replay-Editor nicht
 * gezeichnet wird. Deshalb wird hier keine zweite Oberfläche erfunden, sondern eine eigene
 * Instanz derselben Klasse in den Replay-Editor gehängt — sie bearbeitet denselben Clip.</p>
 *
 * <p>⛔ Warum nicht die vorhandene umhängen: die Kamera-Leiste verwaltet ihre Instanz selbst
 * (sie baut sie beim Clip-Wechsel neu und wirft sie beim Abwählen weg). Wer sie ihr wegnimmt,
 * bekommt sie beim Zurückwechseln nicht zurück, weil {@code pickClip} bei gleichem Clip früh
 * aussteigt.</p>
 */
public final class KameraClipDock
{
    private KameraClipDock()
    {
    }

    /**
     * Den gewählten Clip ins Dock stellen. {@code null} räumt es wieder frei.
     *
     * <p>Das Dock hat nur Platz für einen: ein Keyframe-Panel dort wird verdrängt, und
     * umgekehrt verdrängt {@link UIKeyframeEditor#pickKeyframe} dieses hier wieder.</p>
     */
    public static void zeige(UIElement editor, UIKeyframeEditor keyframeEditor, UIFilmPanel panel, Clip clip)
    {
        if (keyframeEditor == null || panel == null)
        {
            return;
        }

        weg(keyframeEditor);

        if (clip == null || !KameraSpuren.an())
        {
            return;
        }

        UIClip eigenschaften = UIClip.createPanel(clip, panel.cameraEditor);

        eigenschaften.setUndoId("clip_panel");

        UIElement ziel = keyframeEditor.getTarget();
        int oben = panel.getEditPanelTopOffsetPx();

        if (ziel == null)
        {
            eigenschaften.relative(editor).x(1F, -160).w(160).h(1F);
        }
        else
        {
            eigenschaften.relative(ziel).x(0).y(0, oben).w(1F).h(1F, -oben);
        }

        editor.add(eigenschaften);

        keyframeEditor.fremdesPanel = eigenschaften;

        editor.resize();
        eigenschaften.fillData();
        eigenschaften.setVisible(keyframeEditor.isPropertiesVisible());
    }

    /** Das Dock freimachen, ohne zu wissen, ob überhaupt etwas drinsteht. */
    public static void weg(UIKeyframeEditor keyframeEditor)
    {
        if (keyframeEditor == null || keyframeEditor.fremdesPanel == null)
        {
            return;
        }

        keyframeEditor.fremdesPanel.removeFromParent();
        keyframeEditor.fremdesPanel = null;
    }
}
