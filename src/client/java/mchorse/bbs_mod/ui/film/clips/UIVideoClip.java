package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.misc.VideoClientClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The video clip's panel: the audio clip's fields with a video picker, plus the image clip's picture fields (BBS FS 3efe13ebc) */
public class UIVideoClip extends UIAudioClip
{
    public UIToggle loop;
    public UIToggle fullscreen;
    public UIToggle smooth;
    public UIColor color;
    public UIPlacement placement;
    public UIPropTransform transform;

    public UIVideoClip(VideoClientClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    private VideoClientClip video()
    {
        return (VideoClientClip) this.clip;
    }

    /** The videos in assets/video */
    public static List<Link> getVideoLinks()
    {
        List<Link> links = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("video")))
        {
            String path = link.path.toLowerCase();

            if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") || path.endsWith(".mov") || path.endsWith(".avi")) links.add(link);
        }

        return links;
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickAudio = new UIButton(L10n.lang("bbs.ui.camera.panels.video.pick"), (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(L10n.lang("bbs.ui.camera.panels.video.pick"), getVideoLinks(), (l) -> this.editor.editMultiple(this.clip.audio, (value) -> value.set(l)));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.clip.audio.get()));
        });
        this.openFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            File folder = new File(BBSMod.getAssetsFolder(), "video");

            folder.mkdirs();
            UIUtils.openFolder(folder);
        });
        this.loop = new UIToggle(L10n.lang("bbs.ui.camera.panels.video.loop"), (b) -> this.editor.editMultiple(this.video().loop, (value) -> value.set(b.getValue())));
        this.fullscreen = new UIToggle(L10n.lang("bbs.ui.camera.panels.image.fullscreen"), (b) -> this.editor.editMultiple(this.video().fullscreen, (value) -> value.set(b.getValue())));
        this.smooth = new UIToggle(L10n.lang("bbs.ui.camera.panels.image.smooth"), (b) -> this.editor.editMultiple(this.video().smooth, (value) -> value.set(b.getValue())));
        this.color = new UIColor((c) -> this.editor.editMultiple(this.video().color, (value) -> value.set(c))).withAlpha();
        this.placement = new UIPlacement(new Placement(), (p) -> this.editor.editMultiple(this.video().placement, (value) -> value.set(p.copy())));
        this.transform = new UIPropTransform().callbacks(
            () -> this.editor.editMultiple(this.video().transform, IValueNotifier::preNotify),
            () -> this.editor.editMultiple(this.video().transform, (t) ->
            {
                t.set(this.transform.getTransform().copy());
                t.postNotify();
            })
        );
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.image.image"), this.loop, this.fullscreen, this.smooth, this.color));
        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.placement.title"), this.placement.fields()));
        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.image.transform"), this.transform));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.loop.setValue(this.video().loop.get());
        this.fullscreen.setValue(this.video().fullscreen.get());
        this.smooth.setValue(this.video().smooth.get());
        this.color.setColor(this.video().color.get());
        this.placement.setPlacement(this.video().placement.get());
        this.transform.setTransform(this.video().transform.get());
    }
}
