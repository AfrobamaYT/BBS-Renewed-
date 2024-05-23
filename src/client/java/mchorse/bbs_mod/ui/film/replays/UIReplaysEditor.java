package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UICameraDopeSheetEditor;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIProperty;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeSegment;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIReplaysEditor extends UIElement
{
    private static final Map<String, Integer> COLORS = new HashMap<>();

    public UIReplaysOverlayPanel replays;
    public UIIcon openReplays;
    public UIIcon record;
    public UIIcon recordOutside;
    public UIIcon toggleKeyframes;
    public UIIcon toggleProperties;
    public UIIcon toggleOrbitMode;
    public UIIcon changeOrbitPerspectice;
    public UIElement icons;

    /* Keyframes */
    public UIElement keyframes;
    public UICameraDopeSheetEditor keyframeEditor;
    public UIPropertyEditor propertyEditor;

    /* Clips */
    private UIFilmPanel filmPanel;
    private Film film;
    private Replay replay;

    static
    {
        COLORS.put("x", Colors.RED);
        COLORS.put("y", Colors.GREEN);
        COLORS.put("z", Colors.BLUE);
        COLORS.put("vX", Colors.RED);
        COLORS.put("vY", Colors.GREEN);
        COLORS.put("vZ", Colors.BLUE);
        COLORS.put("yaw", Colors.YELLOW);
        COLORS.put("pitch", Colors.CYAN);
        COLORS.put("bodyYaw", Colors.MAGENTA);

        COLORS.put("stick_lx", Colors.RED);
        COLORS.put("stick_ly", Colors.GREEN);
        COLORS.put("stick_rx", Colors.RED);
        COLORS.put("stick_ry", Colors.GREEN);
        COLORS.put("trigger_l", Colors.RED);
        COLORS.put("trigger_r", Colors.GREEN);

        COLORS.put("pose", Colors.RED);
        COLORS.put("transform", Colors.GREEN);
        COLORS.put("color", Colors.INACTIVE);
        COLORS.put("lighting", Colors.YELLOW);
        COLORS.put("actions", Colors.MAGENTA);
    }

    public UIReplaysEditor(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;

        this.replays = new UIReplaysOverlayPanel(filmPanel, this::setReplay);
        this.openReplays = new UIIcon(Icons.EDITOR, (b) ->
        {
            UIOverlay.addOverlayLeft(this.getContext(), this.replays, 200);
        });
        this.openReplays.tooltip(UIKeys.FILM_REPLAY_TITLE);
        this.record = new UIIcon(Icons.SPHERE, (b) -> this.filmPanel.getController().pickRecording());
        this.record.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordOutside = new UIIcon(Icons.UPLOAD, (b) ->
        {
            int index = this.replays.replays.getIndex();

            if (index >= 0)
            {
                this.filmPanel.dashboard.closeThisMenu();

                BBSModClient.getFilms().startRecording(this.filmPanel.getData(), index);
            }
        });
        this.recordOutside.tooltip(UIKeys.FILM_CONTROLLER_RECORD_OUTSIDE);
        this.toggleKeyframes = new UIIcon(Icons.GRAPH, (b) -> this.toggleProperties(false));
        this.toggleKeyframes.tooltip(UIKeys.FILM_REPLAY_ENTITY_KEYFRAMES);
        this.toggleProperties = new UIIcon(Icons.MORE, (b) -> this.toggleProperties(true));
        this.toggleProperties.tooltip(UIKeys.FILM_REPLAY_FORM_KEYFRAMES);
        this.toggleOrbitMode = new UIIcon(Icons.ORBIT, (b) -> this.filmPanel.getController().toggleOrbit());
        this.toggleOrbitMode.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORBIT);
        this.changeOrbitPerspectice = new UIIcon(Icons.VISIBLE, (b) -> this.filmPanel.getController().toggleOrbitMode());
        this.changeOrbitPerspectice.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORBIT_MODE);

        this.keyframes = new UIElement();
        this.keyframes.relative(this).y(20).w(1F).h(1F, -20);

        this.icons = UI.row(0, this.openReplays, this.record.marginLeft(5), this.recordOutside, this.toggleKeyframes.marginLeft(5), this.toggleProperties, this.toggleOrbitMode.marginLeft(10), this.changeOrbitPerspectice);
        this.icons.relative(this.keyframes).y(-20).w(60).h(20);

        this.add(this.openReplays, this.keyframes, this.icons);

        this.markContainer();

        this.keys().register(Keys.FILM_CONTROLLER_START_RECORDING_OUTSIDE, () -> this.recordOutside.clickItself());
    }

    private void toggleProperties(boolean properties)
    {
        if (this.propertyEditor != null)
        {
            this.keyframeEditor.setVisible(!properties);
            this.propertyEditor.setVisible(properties);
        }
    }

    public void handleUndo(ValueChangeUndo change, boolean redo)
    {
        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.keyframes.applySelection(change.getKeyframeSelection(redo));
        }

        if (this.propertyEditor != null)
        {
            this.propertyEditor.properties.applySelection(change.getPropertiesSelection(redo));
        }
    }

    public void setFilm(Film film)
    {
        this.film = film;

        if (film != null)
        {
            List<Replay> replays = film.replays.getList();

            this.replays.replays.setList(replays);
            this.setReplay(replays.isEmpty() ? null : replays.get(0));
        }
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public void setReplay(Replay replay)
    {
        this.replay = replay;

        this.keyframes.setVisible(replay != null);
        this.updateChannelsList();

        this.replays.replays.setCurrentScroll(replay);
        this.record.setEnabled(replay != null);
        this.recordOutside.setEnabled(replay != null);
    }

    public void moveReplay(double x, double y, double z)
    {
        if (this.replay != null)
        {
            int cursor = this.filmPanel.getCursor();

            this.replay.keyframes.x.insert(cursor, x);
            this.replay.keyframes.y.insert(cursor, y);
            this.replay.keyframes.z.insert(cursor, z);
        }
    }

    public void updateChannelsList()
    {
        if (this.keyframeEditor != null) this.keyframeEditor.removeFromParent();
        if (this.propertyEditor != null) this.propertyEditor.removeFromParent();

        if (this.replay == null)
        {
            return;
        }

        int duration = this.film.camera.calculateDuration();

        /* Replay keyframes */
        List<KeyframeChannel> keyframes = new ArrayList<>();
        List<Integer> tempKeyframesColors = new ArrayList<>();

        for (String key : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue value = this.replay.keyframes.get(key);

            keyframes.add((KeyframeChannel) value);
            tempKeyframesColors.add(COLORS.getOrDefault(key, Colors.ACTIVE));
        }

        this.keyframeEditor = new UICameraDopeSheetEditor(this.filmPanel.cameraClips);
        this.keyframeEditor.setChannels(keyframes, tempKeyframesColors);
        this.keyframeEditor.relative(this.keyframes).full();

        this.keyframeEditor.keyframes.setBackgroundRender(this::renderBackground);
        this.keyframeEditor.keyframes.absolute();
        this.keyframeEditor.keyframes.duration = duration;

        this.keyframes.add(this.keyframeEditor);

        /* Form properties */
        List<GenericKeyframeChannel> properties = new ArrayList<>();
        List<Integer> propertiesColors = new ArrayList<>();
        List<IFormProperty> formProperties = new ArrayList<>();

        for (String key : FormUtils.collectPropertyPaths(this.replay.form.get()))
        {
            GenericKeyframeChannel property = this.replay.properties.getOrCreate(this.replay.form.get(), key);

            if (property != null)
            {
                IFormProperty formProperty = FormUtils.getProperty(this.replay.form.get(), key);

                properties.add(property);
                propertiesColors.add(COLORS.getOrDefault(StringUtils.fileName(key), Colors.ACTIVE));
                formProperties.add(formProperty);
            }
        }

        if (!properties.isEmpty())
        {
            this.propertyEditor = new UIPropertyEditor(this.filmPanel.cameraClips);
            this.propertyEditor.setChannels(properties, formProperties, propertiesColors);
            this.propertyEditor.relative(this.keyframes).full();
            this.propertyEditor.setVisible(false);

            this.propertyEditor.properties.setBackgroundRender(this::renderBackground);
            this.propertyEditor.properties.duration = duration;

            this.keyframes.add(this.propertyEditor);
        }

        this.toggleProperties.setEnabled(this.propertyEditor != null);
        this.keyframes.resize();

        if (this.keyframeEditor != null) this.keyframeEditor.resetView();
        if (this.propertyEditor != null) this.propertyEditor.resetView();
    }

    public void updateDuration()
    {
        int duration = this.film.camera.calculateDuration();

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.keyframes.duration = duration;
        }

        if (this.propertyEditor != null)
        {
            this.propertyEditor.properties.duration = duration;
        }
    }

    public void pickForm(Form form, String bone)
    {
        String path = FormUtils.getPath(form);

        if (!bone.isEmpty())
        {
            if (this.propertyEditor == null)
            {
                return;
            }

            this.toggleProperties(true);
            this.pickProperty(bone, StringUtils.combinePaths(path, "pose"), false);
        }
    }

    public void pickFormProperty(Form form, String bone)
    {
        String path = FormUtils.getPath(form);
        boolean shift = Window.isShiftPressed();
        ContextMenuManager manager = new ContextMenuManager();

        for (IFormProperty formProperty : form.getProperties().values())
        {
            if (!formProperty.canCreateChannel())
            {
                continue;
            }

            manager.action(Icons.POINTER, IKey.raw(formProperty.getKey()), () ->
            {
                this.toggleProperties(true);
                this.pickProperty(bone, StringUtils.combinePaths(path, formProperty.getKey()), shift);
            });
        }

        this.getContext().replaceContextMenu(manager.create());
    }

    private void pickProperty(String bone, String key, boolean insert)
    {
        List<UIProperty> properties = this.propertyEditor.properties.getProperties();

        for (UIProperty property : properties)
        {
            if (FormUtils.getPropertyPath(property.property).equals(key))
            {
                this.pickProperty(bone, property, insert);

                break;
            }
        }
    }

    private void pickProperty(String bone, UIProperty property, boolean insert)
    {
        int tick = this.filmPanel.getRunner().ticks;

        if (insert)
        {
            this.propertyEditor.properties.addCurrent(property, tick);
            this.propertyEditor.fillData(this.propertyEditor.properties.getCurrent());

            return;
        }

        GenericKeyframeSegment segment = property.channel.find(tick);

        if (segment != null)
        {
            GenericKeyframe closest = segment.getClosest();

            this.propertyEditor.pickKeyframe(closest);

            if (this.propertyEditor.editor instanceof UIPoseKeyframeFactory)
            {
                ((UIPoseKeyframeFactory) this.propertyEditor.editor).poseEditor.selectBone(bone);
            }

            this.filmPanel.setCursor((int) closest.getTick());
        }
    }

    public boolean clickViewport(UIContext context, Area area)
    {
        if (!this.filmPanel.isFlightDisabled())
        {
            return false;
        }

        StencilFormFramebuffer stencil = this.filmPanel.getController().getStencil();

        if (stencil.hasPicked())
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null && context.mouseButton < 2)
            {
                if (!this.isVisible())
                {
                    this.filmPanel.showPanel(this);
                }

                if (context.mouseButton == 0)
                {
                    this.pickForm(pair.a, pair.b);

                    return true;
                }
                else if (context.mouseButton == 1)
                {
                    this.pickFormProperty(pair.a, pair.b);

                    return true;
                }
            }
        }
        else if (context.mouseButton == 1 && this.isVisible())
        {
            World world = MinecraftClient.getInstance().world;
            Camera camera = this.filmPanel.getCamera();

            BlockHitResult blockHitResult = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(camera.position),
                RayTracing.fromVector3f(CameraUtils.getMouseDirection(camera.projection, camera.view, context.mouseX, context.mouseY, area.x, area.y, area.w, area.h)),
                64F
            );

            if (blockHitResult.getType() != HitResult.Type.MISS)
            {
                Vector3d vec = new Vector3d(blockHitResult.getPos().x, blockHitResult.getPos().y, blockHitResult.getPos().z);

                context.replaceContextMenu((menu) ->
                {
                    float pitch = 0F;
                    float yaw = MathUtils.toDeg(camera.rotation.y);

                    menu.action(Icons.ADD, UIKeys.FILM_REPLAY_CONTEXT_ADD, () -> this.replays.replays.addReplay(vec, pitch, yaw));
                    menu.action(Icons.POINTER, UIKeys.FILM_REPLAY_CONTEXT_MOVE_HERE, () -> this.moveReplay(vec.x, vec.y, vec.z));
                });

                return true;
            }
        }

        return false;
    }

    private void renderBackground(UIContext context)
    {
        if (!BBSSettings.audioWaveformVisible.get())
        {
            return;
        }

        UIPropertyEditor propertyEditor = this.propertyEditor;

        Scale scale = this.keyframeEditor.keyframes.getScaleX();

        if (propertyEditor != null && propertyEditor.isVisible())
        {
            scale = propertyEditor.properties.getScaleX();
        }

        for (Clip clip : this.film.camera.get())
        {
            if (clip instanceof AudioClip audioClip)
            {
                Link link = audioClip.audio.get();

                if (link == null)
                {
                    continue;
                }

                SoundBuffer buffer = BBSModClient.getSounds().get(link, true);

                if (buffer == null || buffer.getWaveform() == null)
                {
                    continue;
                }

                Waveform wave = buffer.getWaveform();

                if (wave != null)
                {
                    float offset = audioClip.tick.get();
                    float duration = wave.getDuration();

                    int x1 = (int) scale.to(offset);
                    int x2 = (int) scale.to(offset + duration * 20);

                    wave.render(context.batcher, Colors.WHITE, x1, this.keyframeEditor.area.y + 15, x2 - x1, 20, 0F, duration);
                }
            }
        }
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.icons.area.x, this.icons.area.y, this.keyframes.area.ex(), this.icons.area.ey(), Colors.CONTROL_BAR);

        if (this.keyframeEditor != null && this.keyframeEditor.isVisible())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.toggleKeyframes.area);
        }
        else if (this.propertyEditor != null && this.propertyEditor.isVisible())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.toggleProperties.area);
        }

        if (this.filmPanel.getController().orbit.enabled)
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.toggleOrbitMode.area);
        }

        super.render(context);
    }
}