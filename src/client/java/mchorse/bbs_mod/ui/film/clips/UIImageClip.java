package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;

/** The image clip's panel: the picture, its placement in the frame and its fade transform (BBS FS e898ba310, 7f19dfbd5) */
public class UIImageClip extends UIClip<ImageClip>
{
    public UIButton pickTexture;
    public UIToggle fullscreen;
    public UIToggle smooth;
    public UIColor color;
    public UIPlacement placement;
    public UIPropTransform transform;

    public UIImageClip(ImageClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickTexture = new UIButton(L10n.lang("bbs.ui.camera.panels.image.pick"), (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.texture.get(), (l) -> this.editor.editMultiple(this.clip.texture, (value) -> value.set(l)));
        });
        this.fullscreen = new UIToggle(L10n.lang("bbs.ui.camera.panels.image.fullscreen"), (b) -> this.editor.editMultiple(this.clip.fullscreen, (value) -> value.set(b.getValue())));
        this.smooth = new UIToggle(L10n.lang("bbs.ui.camera.panels.image.smooth"), (b) -> this.editor.editMultiple(this.clip.smooth, (value) -> value.set(b.getValue())));
        this.color = new UIColor((c) -> this.editor.editMultiple(this.clip.color, (value) -> value.set(c))).withAlpha();
        this.placement = new UIPlacement(new Placement(), (p) -> this.editor.editMultiple(this.clip.placement, (value) -> value.set(p.copy())));
        this.transform = new UIPropTransform().callbacks(
            () -> this.editor.editMultiple(this.clip.transform, IValueNotifier::preNotify),
            () -> this.editor.editMultiple(this.clip.transform, (t) ->
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

        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.image.image"), this.pickTexture, this.fullscreen, this.smooth, this.color));
        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.placement.title"), this.placement.fields()));
        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.image.transform"), this.transform));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.fullscreen.setValue(this.clip.fullscreen.get());
        this.smooth.setValue(this.clip.smooth.get());
        this.color.setColor(this.clip.color.get());
        this.placement.setPlacement(this.clip.placement.get());
        this.transform.setTransform(this.clip.transform.get());
    }
}
