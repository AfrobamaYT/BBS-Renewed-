package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.list.UIKachelListe;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UISelectionScreen;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;

import java.io.File;
import java.util.List;

public class UIFilmSelectionPanel extends UISelectionScreen<Film>
{
    private UIIcon duplicateCurrentFilm;

    public UIFilmSelectionPanel(UIFilmPanel panel)
    {
        super(panel);

        /* Ein Film hat kein Bild auf der Platte — seine Kennung ist die Figur, die darin
         * spielt. Also die Form des ersten Replays, und die steckt in der Filmdatei:
         * asynchron nachladen, wie Unity es mit GetAssetPreview macht. */
        if (this.namesList instanceof UIKachelListe kachel)
        {
            /* Ein Ausschnitt aus dem Film sagt mehr als die Figur darin. Liegt ein Standbild
             * vor, gewinnt es; sonst greift die Form des ersten Replays darunter. */
            kachel.bild((pfad) -> standbildLink(pfad.toString()));
            kachel.streifen(FilmStreifen.FELDER, (pfad) -> FilmStreifen.link(pfad.toString()));

            kachel.nachlader((pfad, fertig) -> panel.getType().getRepository().load(pfad.toString(), (daten) ->
            {
                /* getType() ist roh, der Rueckgabetyp also ein Wildcard — pruefen statt casten. */
                List<Replay> replays = daten instanceof Film film ? film.replays.getList() : null;

                fertig.accept(replays == null || replays.isEmpty() ? null : replays.get(0).form.get());
            }));
        }
    }

    /** Wo ein Film-Standbild liegt — unter der Assets-Wurzel, damit der Link-Weg greift. */
    public static String standbildPfad(String film)
    {
        return "film_thumbnails/" + film + ".png";
    }

    /** Link auf das Standbild, oder {@code null}, wenn es keines gibt. */
    private static Link standbildLink(String film)
    {
        File datei = new File(BBSMod.getAssetsFolder(), standbildPfad(film));

        return datei.isFile() ? Link.assets(standbildPfad(film)) : null;
    }

    @Override
    protected IKey getHeaderTitle()
    {
        return UIKeys.FILM_TITLE;
    }

    @Override
    protected Icon getFileIcon()
    {
        return Icons.FILM;
    }

    @Override
    protected void appendHeaderIcons(List<UIIcon> icons)
    {
        this.duplicateCurrentFilm = new UIIcon(Icons.SCENE, (b) -> this.openDuplicateCurrentFilmPrompt());
        this.duplicateCurrentFilm.wh(20, 20);

        icons.add(this.duplicateCurrentFilm);
    }

    @Override
    protected void updateCustomActionButtons(List<DataPath> selected)
    {
        if (this.duplicateCurrentFilm != null)
        {
            UIFilmPanel filmPanel = (UIFilmPanel) this.panel;
            boolean hasSelectedFilm = false;

            for (DataPath dataPath : selected)
            {
                if (this.isFilmFile(dataPath))
                {
                    hasSelectedFilm = true;

                    break;
                }
            }

            this.duplicateCurrentFilm.setEnabled(filmPanel.getData() != null || hasSelectedFilm);
        }
    }

    private void openDuplicateCurrentFilmPrompt()
    {
        UIFilmPanel filmPanel = (UIFilmPanel) this.panel;
        DataPath current = this.namesList.getCurrentFirst();
        String sourceId = this.getSourceFilmId(filmPanel, current);

        if (sourceId == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_DUPE,
            UIKeys.PANELS_MODALS_DUPE,
            (str) -> filmPanel.dupeFilmTo(sourceId, this.namesList.getPath(str).toString())
        );

        if (this.isFilmFile(current))
        {
            panel.text.setText(current.getLast());
        }
        else if (filmPanel.getData().getId() != null)
        {
            panel.text.setText(new DataPath(filmPanel.getData().getId()).getLast());
        }

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private boolean isFilmFile(DataPath path)
    {
        return path != null && !path.folder && !"..".equals(path.getLast());
    }

    private String getSourceFilmId(UIFilmPanel filmPanel, DataPath selected)
    {
        if (this.isFilmFile(selected))
        {
            return selected.toString();
        }

        Film data = filmPanel.getData();

        return data == null ? null : data.getId();
    }

    @Override
    protected void onDuplicateData(Film data)
    {
        if (data != null)
        {
            data.stampCreationTimeNow();
        }
    }
}
