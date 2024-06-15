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
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
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
    public UIIcon changeOrbitPerspectice;

    /* Keyframes */
    public UIKeyframeEditor keyframeEditor;

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

        COLORS.put("visible", Colors.WHITE & 0xFFFFFF);
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
        this.changeOrbitPerspectice = new UIIcon(Icons.VISIBLE, (b) -> this.filmPanel.getController().toggleOrbitMode());
        this.changeOrbitPerspectice.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORBIT_MODE);

        this.markContainer();

        this.keys().register(Keys.FILM_CONTROLLER_START_RECORDING_OUTSIDE, () -> this.recordOutside.clickItself(this.getContext()));
    }

    public void handleUndo(ValueChangeUndo change, boolean redo)
    {
        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.view.applyState(change.getPropertiesSelection(redo));
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

        this.updateChannelsList();

        this.replays.replays.setCurrentScroll(replay);
        this.record.setEnabled(replay != null);
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
        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.removeFromParent();
        }

        if (this.replay == null)
        {
            return;
        }

        /* Replay keyframes */
        List<KeyframeChannel> properties = new ArrayList<>();
        List<Integer> propertiesColors = new ArrayList<>();
        List<IFormProperty> formProperties = new ArrayList<>();

        for (String key : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue value = this.replay.keyframes.get(key);

            properties.add((KeyframeChannel) value);
            propertiesColors.add(COLORS.getOrDefault(key, Colors.ACTIVE));
            formProperties.add(null);
        }

        /* Form properties */
        for (String key : FormUtils.collectPropertyPaths(this.replay.form.get()))
        {
            KeyframeChannel property = this.replay.properties.getOrCreate(this.replay.form.get(), key);

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
            this.keyframeEditor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.filmPanel.cameraClips, consumer).absolute()).target(this.filmPanel.editArea);
            this.keyframeEditor.relative(this).full();

            this.keyframeEditor.view.backgroundRenderer(this::renderBackground);
            this.keyframeEditor.view.duration(() -> this.film.camera.calculateDuration());

            for (int i = 0; i < properties.size(); i++)
            {
                KeyframeChannel channel = properties.get(i);
                UIKeyframeSheet sheet = new UIKeyframeSheet(channel.getId(), IKey.raw(channel.getId()), propertiesColors.get(i), channel, formProperties.get(i));

                this.keyframeEditor.view.addSheet(sheet);
            }

            this.add(this.keyframeEditor);
        }

        this.resize();

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.view.resetView();
        }
    }

    public void pickForm(Form form, String bone)
    {
        String path = FormUtils.getPath(form);

        if (this.keyframeEditor == null || bone.isEmpty())
        {
            return;
        }

        this.pickProperty(bone, StringUtils.combinePaths(path, "pose"), false);
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
                this.pickProperty(bone, StringUtils.combinePaths(path, formProperty.getKey()), shift);
            });
        }

        this.getContext().replaceContextMenu(manager.create());
    }

    private void pickProperty(String bone, String key, boolean insert)
    {
        for (UIKeyframeSheet sheet : this.keyframeEditor.view.getSheets())
        {
            IFormProperty property = sheet.property;

            if (property != null && FormUtils.getPropertyPath(property).equals(key))
            {
                this.pickProperty(bone, sheet, insert);

                break;
            }
        }
    }

    private void pickProperty(String bone, UIKeyframeSheet sheet, boolean insert)
    {
        int tick = this.filmPanel.getRunner().ticks;

        if (insert)
        {
            this.keyframeEditor.view.addKeyframe(sheet, tick);
            this.keyframeEditor.view.selectKeyframe(this.keyframeEditor.view.getSelected());

            return;
        }

        KeyframeSegment segment = sheet.channel.find(tick);

        if (segment != null)
        {
            Keyframe closest = segment.getClosest();

            this.keyframeEditor.view.selectKeyframe(closest);

            if (this.keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
            {
                poseFactory.poseEditor.selectBone(bone);
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
        UIKeyframes keyframes = this.keyframeEditor.view;

        if (!BBSSettings.audioWaveformVisible.get() || keyframes == null)
        {
            return;
        }

        Scale scale = keyframes.getXAxis();

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

                    wave.render(context.batcher, Colors.WHITE, x1, keyframes.area.y + 15, x2 - x1, 20, 0F, duration);
                }
            }
        }
    }
}