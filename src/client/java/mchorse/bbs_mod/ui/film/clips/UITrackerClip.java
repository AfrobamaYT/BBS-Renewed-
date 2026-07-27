package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
public class UITrackerClip extends UIClip<TrackerClip>
{
    public UIButton selector;
    public UIButton group;

    public UIPointModule point;
    public UIPointModule angle;
    public UITrackpad fov;
    public UIToggle lookAt;
    public UIToggle relative;
    public UIBitToggle active;

    /* afrobama 2026-07-27 — show hand/HUD in den camera properties.
     *
     * Die felder gab es auf TrackerClip laengst (show_hand/show_hud, serialisiert), und
     * der gesamte render-apparat dahinter ist vollstaendig: TrackerClientClip.renderHand,
     * shouldRenderHand(), die verbraucher an mc.options.hudHidden. Nur die zeile zum
     * SETZEN fehlte — ohne sie blieben beide werte ewig auf ihrem default false, und der
     * schreiber in TrackerClientClip konnte nie feuern. Der nutzer sah deshalb kein
     * "show hud"/"show hand" in den properties.
     *
     * ⚠ First-person-hand ist laut ARCHITEKTUR.md das wichtigste feature des projekts. */
    public UIToggle showHand;
    public UIToggle showHud;

    public UITrackerClip(TrackerClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) ->
        {
            UIAnchorKeyframeFactory.displayAttachments(this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(), (attachment) -> this.clip.group.set(attachment));
        });

        this.point = new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();
        this.angle = new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu();
        this.fov = new UITrackpad((v) -> this.clip.fov.set(v.floatValue()));
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, b -> this.clip.lookAt.set(b.getValue()));
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, b -> this.clip.relative.set(b.getValue()));
        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).all();

        /* QUELLE: cml-fresh/src/client/java/mchorse/bbs_mod/ui/film/clips/UITrackerClip.java:61-62.
         *
         * ABWEICHUNG: dort IKey.raw(...), hier IKey.constant(...) plus tooltips. Die
         * uebersetzungs-keys liegen in fs-fremdcode (UIKeys.java + strings/*.json in 22
         * sprachen) — konstanten halten den upstream mergebar. */
        this.showHand = new UIToggle(mchorse.bbs_mod.l10n.keys.IKey.constant("Show hand"),
            (b) -> this.clip.showHand.set(b.getValue()));
        this.showHand.tooltip(mchorse.bbs_mod.l10n.keys.IKey.constant(
            "Zeigt die first-person-hand waehrend dieses clips."));

        this.showHud = new UIToggle(mchorse.bbs_mod.l10n.keys.IKey.constant("Show HUD"),
            (b) -> this.clip.showHud.set(b.getValue()));
        this.showHud.tooltip(mchorse.bbs_mod.l10n.keys.IKey.constant(
            "Zeigt das HUD (hotbar, herzen, chat) waehrend dieses clips."));
    }

    private UIFilmPanel getPanel()
    {
        return this.getContext().menu.getRoot().getChildren(UIFilmPanel.class).get(0);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.group));

        this.panels.add(this.point);
        this.panels.add(this.angle);
        this.panels.add(this.fov);
        this.panels.add(this.lookAt);
        this.panels.add(this.relative);
        this.panels.add(this.showHand);
        this.panels.add(this.showHud);
        this.panels.add(this.active);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.point.fill(this.clip.offset);
        this.angle.fill(this.clip.angle);
        this.fov.setValue(this.clip.fov.get());
        this.lookAt.setValue(this.clip.lookAt.get());
        this.relative.setValue(this.clip.relative.get());
        this.showHand.setValue(this.clip.showHand.get());
        this.showHud.setValue(this.clip.showHud.get());
        this.active.setValue(this.clip.active.get());
    }
}
