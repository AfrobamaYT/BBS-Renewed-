package mchorse.bbs_mod.ui.film.mehrfach;

import java.util.List;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Die eine zeile ueber der eigenschaften-maske, die sagt, dass eine aenderung hier gerade
 * MEHRERE clips trifft.
 *
 * <p>WARUM ES DAS BRAUCHT: das panel zeigt die werte EINES clips (des zuletzt gewaehlten).
 * Ohne hinweis zieht ein regler still vier weitere clips mit, und der nutzer sieht nur,
 * dass sich „irgendwas" geaendert hat. DaVinci loest dasselbe problem mit dem titel
 * „Multiple Clips" ueber dem inspektor.</p>
 *
 * <p>Bewusst KEIN doppelstrich („--") in den feldern wie bei DaVinci: BBS hat, anders als
 * DaVinci, einen AKTIVEN clip — die maske gehoert einem bestimmten clip. Ein doppelstrich
 * wuerde dessen wert unlesbar machen und man koennte ihn nicht mehr abtippen. Blender
 * macht es aus demselben grund genauso: das feld zeigt den wert des AKTIVEN objekts.</p>
 *
 * <p>Haengt in {@code UIClip} und damit in JEDER clip-maske; die einzelnen panels wissen
 * nichts davon.</p>
 */
public class UIMehrfachHinweis extends UILabel
{
    /* Der schluessel steht hier statt in UIKeys, weil UIKeys von jedem parallel laufenden
     * feature-zweig angefasst wird und ein gleichzeitiger schreibzugriff die datei zerreisst.
     * L10n.lang faellt ohne uebersetzung auf den schluesselnamen zurueck, nichts crasht. */
    private static final IKey DELTA = L10n.lang("bbs.ui.camera.panels.multi.delta");
    private static final IKey ABSOLUT = L10n.lang("bbs.ui.camera.panels.multi.absolute");

    private final IUIClipsDelegate editor;

    public UIMehrfachHinweis(IUIClipsDelegate editor)
    {
        super(IKey.EMPTY);

        this.editor = editor;

        this.color(Colors.LIGHTER_GRAY, true);
        this.h(12);
    }

    /**
     * Aus {@code UIClip.fillData()} aufgerufen — also bei jedem clip-wechsel und jeder
     * auswahl-aenderung, die das panel neu befuellt.
     *
     * <p>⚠ Wechselt die auswahl OHNE dass das panel neu befuellt wird, steht hier eine
     * frame alte zahl. Das ist kosmetisch: die verteilung selbst fragt die auswahl
     * jedesmal frisch ab.</p>
     */
    public void aktualisiere()
    {
        List<Clip> ziele = this.editor.getMehrfachZiele();
        int anzahl = ziele == null ? 0 : ziele.size();

        this.setVisible(anzahl > 1);

        if (anzahl > 1)
        {
            this.label = (BBSSettings.editorMultiClipDelta.get() ? DELTA : ABSOLUT).format(anzahl);
        }
    }
}
