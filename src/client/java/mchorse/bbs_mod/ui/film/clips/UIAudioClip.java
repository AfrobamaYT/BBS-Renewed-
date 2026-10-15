package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;

public class UIAudioClip extends UIClip<AudioClip>
{
    /* Die schluessel liegen hier statt in UIKeys, weil UIKeys von jedem parallel laufenden
     * feature-zweig angefasst wird und ein gleichzeitiger schreibzugriff die datei zerreisst.
     * L10n.lang faellt ohne uebersetzung auf den schluesselnamen zurueck, nichts crasht;
     * die en_us-eintraege stehen im uebergabe-bericht. Sie duerfen spaeter nach UIKeys wandern. */
    private static final IKey SPATIAL_TITLE = L10n.lang("bbs.ui.camera.panels.audio.spatial.title");
    private static final IKey SPATIAL_MODE_OFF = L10n.lang("bbs.ui.camera.panels.audio.spatial.off");
    private static final IKey SPATIAL_MODE_WORLD = L10n.lang("bbs.ui.camera.panels.audio.spatial.world");
    private static final IKey SPATIAL_MODE_ACTOR = L10n.lang("bbs.ui.camera.panels.audio.spatial.actor");
    private static final IKey SPATIAL_MODE_TOOLTIP = L10n.lang("bbs.ui.camera.panels.audio.spatial.mode_tooltip");
    private static final IKey SPATIAL_PICK_ACTOR = L10n.lang("bbs.ui.camera.panels.audio.spatial.pick_actor");
    private static final IKey SPATIAL_PICKED_ACTOR = L10n.lang("bbs.ui.camera.panels.audio.spatial.picked_actor");
    private static final IKey SPATIAL_POSITION = L10n.lang("bbs.ui.camera.panels.audio.spatial.position");

    public UIButton pickAudio;
    public UIIcon openFolder;
    public UIIcon extendDuration;
    public UITrackpad offset;
    public UISliderTrackpad volume;

    public UICirculate spatialMode;
    public UIButton spatialActor;
    public UIPointModule spatialPoint;

    public UIAudioClip(AudioClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickAudio = new UIButton(UIKeys.CAMERA_PANELS_AUDIO_PICK_AUDIO, (b) ->
        {
            UISoundOverlayPanel panel = new UISoundOverlayPanel((l) -> this.editor.editMultiple(this.clip.audio, (value) -> value.set(l)), this.getContext());

            UIOverlay.addOverlay(this.getContext(), panel.set(this.clip.audio.get()));
        });

        this.openFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            Link link = this.clip.audio.get();
            File file = BBSMod.getAudioFolder();

            if (link != null)
            {
                File audioFile = BBSMod.getProvider().getFile(link);

                if (audioFile.exists())
                {
                    file = audioFile.getParentFile();
                }
            }

            UIUtils.openFolder(file);
        });

        this.extendDuration = new UIIcon(Icons.RIGHTLOAD, (b) ->
        {
            Link link = this.clip.audio.get();

            if (link != null)
            {
                SoundBuffer buffer = BBSModClient.getSounds().get(link, true);

                if (buffer != null)
                {
                    this.clip.duration.set((int) ((buffer.getDuration() * 20) - this.clip.offset.get()));
                    this.fillData();
                }
            }
        });
        this.extendDuration.tooltip(UIKeys.CAMERA_PANELS_AUDIO_EXTEND_DURATION);

        this.offset = new UITrackpad((v) -> this.editor.editMultiple(this.clip.offset, (value) -> value.set(v.intValue())));
        this.offset.integer();

        /* ⚠ limit(this.clip.volume) unten ist PFLICHT und kein schmuck: klemmt das feld
         * frueher als der wert, kommt bei mehrfachauswahl keine neue zahl mehr an, das
         * delta ist 0 und die ganze gruppe steht still — ohne fehlermeldung. */
        this.volume = new UISliderTrackpad((v) -> this.editor.editMultiple(this.clip.volume, (value) -> value.set(v.floatValue())));
        this.volume.limit(this.clip.volume).values(0.05F, 0.01F, 0.2F).increment(0.1F).tooltip(UIKeys.CAMERA_PANELS_AUDIO_VOLUME);

        this.registerSpatialUI();
    }

    /**
     * Der raeumliche teil der maske. Position und actor-wahl bleiben in JEDEM modus
     * sichtbar: der spalten-layouter reserviert den platz auch fuer versteckte elemente,
     * ausblenden wuerde also nur ein loch in die spalte reissen.
     */
    private void registerSpatialUI()
    {
        this.spatialMode = new UICirculate((b) -> this.editor.editMultiple(this.clip.spatial, (value) -> value.set(b.getValue())));
        this.spatialMode.addLabel(SPATIAL_MODE_OFF);
        this.spatialMode.addLabel(SPATIAL_MODE_WORLD);
        this.spatialMode.addLabel(SPATIAL_MODE_ACTOR);
        this.spatialMode.tooltip(SPATIAL_MODE_TOOLTIP);

        this.spatialActor = new UIButton(SPATIAL_PICK_ACTOR, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.spatialSelector.get(), (i) ->
                {
                    this.clip.spatialSelector.set(i);
                    this.updateActorLabel();
                });
            }
        });

        /* contextMenu() gibt dem punkt "von der kamera uebernehmen" — der einzige
         * praktikable weg, eine weltkoordinate im editor zu treffen */
        this.spatialPoint = new UIPointModule(this.editor, SPATIAL_POSITION).contextMenu();
    }

    /** Ohne die nummer im knopf ist nicht erkennbar, an welchem actor der ton haengt. */
    private void updateActorLabel()
    {
        int selector = this.clip.spatialSelector.get();

        this.spatialActor.label = selector < 0 ? SPATIAL_PICK_ACTOR : SPATIAL_PICKED_ACTOR.format(selector);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:audio"),
            UI.row(this.pickAudio, this.extendDuration, this.openFolder),
            UI.labelRow(UIKeys.CAMERA_PANELS_AUDIO_OFFSET, this.offset).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.CAMERA_PANELS_AUDIO_VOLUME, this.volume)
        ));

        this.panels.add(this.section(SPATIAL_TITLE,
            this.spatialMode,
            this.spatialActor
        ));
        this.panels.add(this.spatialPoint);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.offset.setValue(this.clip.offset.get());
        this.volume.setValue(this.clip.volume.get());

        this.spatialMode.setValue(this.clip.spatial.get());
        this.spatialPoint.fill(this.clip.spatialPoint);
        this.updateActorLabel();
    }
}