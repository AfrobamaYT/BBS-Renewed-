package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Videodatei, Zeit (Versatz, Tempo, Schleife) und die Billboard-Grundwerte einer Video-Form. */
public class UIVideoFormPanel extends UIFormPanel<VideoForm>
{
    public static final IKey TITLE = L10n.lang("bbs.ui.forms.editors.video.title");
    private static final IKey PICK = L10n.lang("bbs.ui.forms.editors.video.pick");
    private static final IKey PATH = L10n.lang("bbs.ui.forms.editors.video.path");
    private static final IKey OFFSET = L10n.lang("bbs.ui.forms.editors.video.offset");
    private static final IKey SPEED = L10n.lang("bbs.ui.forms.editors.video.speed");
    private static final IKey LOOP = L10n.lang("bbs.ui.forms.editors.video.loop");
    private static final IKey START = L10n.lang("bbs.ui.forms.editors.video.start");
    private static final IKey LENGTH = L10n.lang("bbs.ui.forms.editors.video.length");
    private static final List<String> EXTENSIONS = List.of(".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v");

    private final UIFormPropFactory props = new UIFormPropFactory();

    public UIVideoFormPanel(UIForm editor)
    {
        super(editor);

        UIButton pick = new UIButton(PICK, (b) -> UIOverlay.addOverlay(this.getContext(),
            new UIStringOverlayPanel(PICK, videosInAssets(), (path) ->
            {
                this.form.video.set(path);
                this.props.sync();
            }).set(this.form.video.get())));

        this.options.add(pick, UI.label(PATH), this.props.text(() -> this.form.video));
        this.options.add(UI.label(START).marginTop(UIConstants.SECTION_GAP), this.props.zahl(() -> this.form.start));
        this.options.add(UI.label(LENGTH), this.props.zahl(() -> this.form.length));
        this.options.add(UI.label(OFFSET), this.props.zahl(() -> this.form.offset));
        this.options.add(UI.label(SPEED), this.props.zahl(() -> this.form.speed), this.props.toggle(LOOP, () -> this.form.loop));
        this.options.add(this.props.color(() -> this.form.color).direction(Direction.LEFT).marginTop(UIConstants.SECTION_GAP),
            this.props.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard),
            this.props.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_SHADING, () -> this.form.shading));
    }

    @Override
    public void startEdit(VideoForm form)
    {
        super.startEdit(form);
        this.props.sync();
    }

    /** Alle Videodateien unter dem Assets-Ordner, als Pfade relativ dazu. */
    private static List<String> videosInAssets()
    {
        Path root = BBSMod.getAssetsFolder().toPath();

        try (Stream<Path> files = Files.walk(root, 6))
        {
            return files.filter(Files::isRegularFile)
                .filter((p) -> EXTENSIONS.stream().anyMatch(p.getFileName().toString().toLowerCase(Locale.ROOT)::endsWith))
                .map((p) -> root.relativize(p).toString().replace('\\', '/'))
                .sorted()
                .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }
}
