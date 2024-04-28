package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VoiceLines;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ValueGroup;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.screenplay.UIScreenplayEditor;
import mchorse.bbs_mod.ui.film.utils.undo.FilmEditorUndo;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeSegment;
import mchorse.bbs_mod.utils.math.MathUtils;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class UIFilmPanel extends UIDataDashboardPanel<Film> implements IFlightSupported
{
    private static VoiceLines voiceLines = new VoiceLines(null);

    private RunnerCameraController runner;
    private boolean lastRunning;
    private final Position position = new Position(0, 0, 0, 0, 0);
    private final Position lastPosition = new Position(0, 0, 0, 0, 0);

    public UIElement main;
    public UIFilmRecorder recorder;

    public UIClipsPanel cameraClips;
    public UIReplaysEditor replayEditor;

    public UIScreenplayEditor screenplay;

    public UIIcon plause;
    public UIIcon teleport;
    public UIIcon record;
    public UIIcon screenshot;
    public UIIcon openVideos;
    public UIIcon openCamera;
    public UIIcon openReplays;
    public UIIcon openScreenplay;

    public UIIcon duplicateFilm;

    public UIRenderable renderableOverlay;
    public UIDraggable draggable;

    private Camera camera = new Camera();
    private Timer undoTimer = new Timer(1000);
    private boolean entered;

    /* Entity control */
    private UIFilmController controller = new UIFilmController(this);

    /* Undo and caches */
    private UndoManager<ValueGroup> undoManager;
    private List<Integer> cachedCameraSelection = new ArrayList<>();
    private List<Integer> cachedVoicelineSelection = new ArrayList<>();
    private FilmEditorUndo.KeyframeSelection cachedKeyframeSelection;
    private FilmEditorUndo.KeyframeSelection cachedPropertiesSelection;
    private Map<BaseValue, BaseType> cachedUndo = new HashMap<>();

    public final Matrix4f lastView = new Matrix4f();
    public final Matrix4f lastProjection = new Matrix4f();

    public static VoiceLines getVoiceLines()
    {
        return voiceLines;
    }

    /**
     * Initialize the camera editor with a camera profile.
     */
    public UIFilmPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.runner = new RunnerCameraController(this);

        this.main = new UIElement();
        this.main.relative(this.editor).w(0.75F).h(1F);

        this.cameraClips = new UIClipsPanel(this, BBSMod.getFactoryCameraClips());
        this.cameraClips.relative(this.main).full();

        this.recorder = new UIFilmRecorder(this);

        this.replayEditor = new UIReplaysEditor(this);
        this.replayEditor.relative(this.main).full();
        this.replayEditor.setVisible(false);

        this.screenplay = new UIScreenplayEditor(this);
        this.screenplay.relative(this.main).full();
        this.screenplay.setVisible(false);

        /* Setup elements */
        this.plause = new UIIcon(Icons.PLAY, (b) -> this.togglePlayback());
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE, Direction.BOTTOM);
        this.teleport = new UIIcon(Icons.MOVE_TO, (b) ->
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Vector3d cameraPos = this.camera.position;
            String name = player.getGameProfile().getName();
            double posX = Math.floor(cameraPos.x);
            double posY = Math.floor(cameraPos.y);
            double posZ = Math.floor(cameraPos.z);

            player.networkHandler.sendCommand("tp " + name + " " + posX + " " + posY + " " + posZ);
        });
        this.teleport.tooltip(UIKeys.FILM_TELEPORT_TITLE, Direction.LEFT);
        this.record = new UIIcon(Icons.SPHERE, (b) ->
        {
            if (!FFMpegUtils.checkFFMpeg())
            {
                UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
                UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

                guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
                panel.icons.add(guide);

                UIOverlay.addOverlay(this.getContext(), panel);

                return;
            }

            this.recorder.startRecording(this.data.camera.calculateDuration(), BBSRendering.getTexture());
        });
        this.record.tooltip(UIKeys.CAMERA_TOOLTIPS_RECORD, Direction.LEFT);
        this.screenshot = new UIIcon(Icons.CAMERA, (b) ->
        {
            ScreenshotRecorder recorder = BBSModClient.getScreenshotRecorder();
            Texture texture = BBSRendering.getTexture();

            recorder.takeScreenshot(Window.isAltPressed() ? null : recorder.getScreenshotFile(), texture.id, texture.width, texture.height);

            UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                UIKeys.FILM_SCREENSHOT_TITLE,
                UIKeys.FILM_SCREENSHOT_DESCRIPTION,
                recorder.getScreenshots()
            );

            UIOverlay.addOverlay(this.getContext(), overlayPanel);
        });
        this.screenshot.tooltip(UIKeys.FILM_SCREENSHOT, Direction.LEFT);
        this.openVideos = new UIIcon(Icons.FILM, (b) -> this.recorder.openMovies());
        this.openVideos.tooltip(UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, Direction.LEFT);
        this.openCamera = new UIIcon(Icons.FRUSTUM, (b) -> this.showPanel(this.cameraClips));
        this.openCamera.tooltip(UIKeys.FILM_OPEN_CAMERA_EDITOR, Direction.LEFT);
        this.openReplays = new UIIcon(Icons.SCENE, (b) -> this.showPanel(this.replayEditor));
        this.openReplays.tooltip(UIKeys.FILM_OPEN_REPLAY_EDITOR, Direction.LEFT);
        this.openScreenplay = new UIIcon(Icons.FILE, (b) -> this.showPanel(this.screenplay));
        this.openScreenplay.tooltip(UIKeys.FILM_OPEN_VOICE_LINE_EDITOR, Direction.LEFT);

        this.draggable = new UIDraggable((context) ->
        {
            float value = (context.mouseX - this.main.area.x) / (float) this.editor.area.w;

            this.main.w(MathUtils.clamp(value, 0.05F, 0.95F), 0);
            this.resize();
        });
        this.draggable.hoverOnly().relative(this.main).x(1F, -3).y(0.5F, -40).wh(6, 80);

        this.iconBar.add(this.plause.marginTop(9), this.teleport, this.screenshot, this.record, this.openVideos, this.openCamera.marginTop(9), this.openReplays, this.openScreenplay);

        /* Adding everything */

        this.editor.add(this.main, new UIRenderable(this::renderIcons));
        this.main.add(this.cameraClips, this.replayEditor, this.screenplay, this.draggable);
        this.add(this.controller, new UIRenderable(this::renderDividers));
        this.overlay.namesList.setFileIcon(Icons.FILM);

        /* Register keybinds */
        IKey modes = UIKeys.CAMERA_EDITOR_KEYS_MODES_TITLE;
        IKey editor = UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE;
        IKey looping = UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TITLE;
        Supplier<Boolean> active = this::isFlightDisabled;

        this.keys().register(Keys.PLAUSE, () -> this.plause.clickItself()).active(active).category(editor);
        this.keys().register(Keys.NEXT_CLIP, () -> this.setCursor(this.data.camera.findNextTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.PREV_CLIP, () -> this.setCursor(this.data.camera.findPreviousTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.NEXT, () -> this.setCursor(this.getCursor() + 1)).active(active).category(editor);
        this.keys().register(Keys.PREV, () -> this.setCursor(this.getCursor() - 1)).active(active).category(editor);
        this.keys().register(Keys.UNDO, this::undo).category(editor);
        this.keys().register(Keys.REDO, this::redo).category(editor);
        this.keys().register(Keys.FLIGHT, () -> this.setFlight(this.isFlightDisabled())).active(() -> this.data != null).category(modes);
        this.keys().register(Keys.LOOPING, () -> BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get())).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MIN, () -> this.cameraClips.clips.setLoopMin()).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MAX, () -> this.cameraClips.clips.setLoopMax()).active(active).category(looping);
        this.keys().register(Keys.JUMP_FORWARD, () -> this.setCursor(this.getCursor() + BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.JUMP_BACKWARD, () -> this.setCursor(this.getCursor() - BBSSettings.editorJump.get())).active(active).category(editor);

        this.fill(null);
    }

    public void showPanel(UIElement element)
    {
        this.cameraClips.setVisible(false);
        this.replayEditor.setVisible(false);
        this.screenplay.setVisible(false);

        element.setVisible(true);

        if (this.replayEditor == element)
        {
            this.replayEditor.updateDuration();
        }
    }

    public UIFilmController getController()
    {
        return this.controller;
    }

    public RunnerCameraController getRunner()
    {
        return this.runner;
    }

    public Framebuffer getFramebuffer()
    {
        return MinecraftClient.getInstance().getFramebuffer();
    }

    public Area getViewportArea()
    {
        return new Area(this.cameraClips.area.ex(), this.area.y, this.iconBar.area.x - this.cameraClips.area.ex(), this.area.h);
    }

    public Area getFramebufferViewport()
    {
        return this.getFramebufferArea(this.getViewportArea());
    }

    public Area getFramebufferArea(Area viewport)
    {
        int width = BBSSettings.videoWidth.get(); // this.texture.width;
        int height = BBSSettings.videoHeight.get(); // this.texture.height;

        Camera camera = new Camera();

        camera.copy(this.getWorldCamera());
        camera.updatePerspectiveProjection(width, height);

        Vector2i size = Vectors.resize(width / (float) height, viewport.w, viewport.h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(viewport.mx() - area.w / 2, viewport.my() - area.h / 2);

        return area;
    }

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        UICRUDOverlayPanel crudPanel = super.createOverlayPanel();

        this.duplicateFilm = new UIIcon(Icons.SCENE, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> this.dupeData(crudPanel.namesList.getPath(str).toString())
            );

            panel.text.setText(crudPanel.namesList.getCurrentFirst().getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        crudPanel.icons.add(this.duplicateFilm);

        return crudPanel;
    }

    private void dupeData(String name)
    {
        if (this.getData() != null && !this.overlay.namesList.getList().contains(name))
        {
            this.save();
            this.overlay.namesList.addFile(name);

            Film data = new Film();
            Position position = new Position();
            IdleClip idle = new IdleClip();
            int tick = this.runner.ticks;

            position.set(this.getCamera());
            idle.duration.set(BBSSettings.getDefaultDuration());
            idle.position.set(position);
            data.camera.addClip(idle);
            data.setId(name);

            for (Replay replay : this.data.replays.getList())
            {
                Replay copy = new Replay(replay.getId());

                copy.form.set(FormUtils.copy(replay.form.get()));

                for (BaseValue value : replay.keyframes.getAll())
                {
                    if (!(value instanceof KeyframeChannel))
                    {
                        continue;
                    }

                    KeyframeChannel channel = (KeyframeChannel) value;

                    if (!channel.isEmpty())
                    {
                        KeyframeChannel newChannel = (KeyframeChannel) copy.keyframes.get(channel.getId());

                        newChannel.insert(0, channel.interpolate(tick));
                    }
                }

                for (Map.Entry<String, GenericKeyframeChannel> entry : replay.properties.properties.entrySet())
                {
                    GenericKeyframeChannel channel = entry.getValue();

                    if (channel.isEmpty())
                    {
                        continue;
                    }

                    GenericKeyframeChannel newChannel = new GenericKeyframeChannel(channel.getId(), channel.getFactory());
                    GenericKeyframeSegment segment = channel.find(tick);

                    if (segment != null)
                    {
                        newChannel.insert(0, segment.createInterpolated());
                    }

                    if (!newChannel.isEmpty())
                    {
                        copy.properties.properties.put(newChannel.getId(), newChannel);
                        copy.properties.add(newChannel);
                    }
                }

                data.replays.add(copy);
            }

            this.fill(data);
            this.save();
        }
    }

    @Override
    public void open()
    {
        super.open();

        this.cameraClips.open();
    }

    @Override
    public void appear()
    {
        super.appear();

        BBSRendering.setCustomSize(true);
        MorphRenderer.hidePlayer = true;

        CameraController cameraController = this.getCameraController();

        this.fillData();
        this.setFlight(false);
        cameraController.add(this.runner);
        this.dashboard.getRoot().prepend(this.renderableOverlay);
    }

    @Override
    public void close()
    {
        super.close();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        CameraController cameraController = this.getCameraController();

        this.cameraClips.embedView(null);
        this.setFlight(false);
        cameraController.remove(this.runner);

        this.disableContext();
    }

    @Override
    public void disappear()
    {
        super.disappear();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        this.setFlight(false);
        this.getCameraController().remove(this.runner);
        this.dashboard.getRoot().remove(this.renderableOverlay);

        this.disableContext();
    }

    private void disableContext()
    {
        this.runner.getContext().shutdown();
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }

    @Override
    public boolean canToggleVisibility()
    {
        return false;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public boolean canRefresh()
    {
        return false;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.FILMS;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.FILM_TITLE;
    }

    @Override
    public void fill(Film data)
    {
        if (this.data != null)
        {
            this.disableContext();
        }

        if (data != null)
        {
            voiceLines.delete();
            voiceLines = new VoiceLines(BBSMod.getAssetsPath("audio/elevenlabs/" + data.getId()));

            data.preCallback(this::handlePreValues);
            data.postCallback(this::handlePostValues);

            this.undoManager = new UndoManager<>(50);
            this.undoManager.setCallback(this::handleUndos);
        }
        else
        {
            this.undoManager = null;
        }

        super.fill(data);

        this.plause.setEnabled(data != null);
        this.record.setEnabled(data != null);
        this.screenshot.setEnabled(data != null);
        this.openCamera.setEnabled(data != null);
        this.openReplays.setEnabled(data != null);
        this.openScreenplay.setEnabled(data != null);
        this.duplicateFilm.setEnabled(data != null);

        this.runner.setWork(data == null ? null : data.camera);
        this.cameraClips.clips.setClips(data == null ? null : data.camera);
        this.replayEditor.setFilm(data);
        this.cameraClips.pickClip(null);

        this.fillData();
        this.controller.createEntities();

        this.entered = data != null;
    }

    private void handlePreValues(BaseValue baseValue)
    {
        if (this.cachedCameraSelection.isEmpty())
        {
            this.cachedCameraSelection.addAll(this.cameraClips.clips.getSelection());
        }

        if (this.cachedVoicelineSelection.isEmpty())
        {
            this.cachedVoicelineSelection.addAll(this.screenplay.editor.clips.getSelection());
        }

        if (this.cachedKeyframeSelection == null)
        {
            this.cachedKeyframeSelection = this.replayEditor.keyframeEditor == null
                ? new FilmEditorUndo.KeyframeSelection()
                : this.replayEditor.keyframeEditor.keyframes.createSelection();
        }

        if (this.cachedPropertiesSelection == null)
        {
            this.cachedPropertiesSelection = this.replayEditor.propertyEditor == null
                ? new FilmEditorUndo.KeyframeSelection()
                : this.replayEditor.propertyEditor.properties.createSelection();
        }

        if (!this.cachedUndo.containsKey(baseValue))
        {
            this.cachedUndo.put(baseValue, baseValue.toData());
        }
    }

    private void handlePostValues(BaseValue baseValue)
    {}

    private void submitUndo()
    {
        if (this.undoTimer.checkReset())
        {
            this.markLastUndoNoMerging();
        }

        if (this.cachedUndo.isEmpty())
        {
            return;
        }

        Iterator<BaseValue> it = this.cachedUndo.keySet().iterator();

        while (it.hasNext())
        {
            BaseValue value = it.next().getParent();
            boolean remove = false;

            while (value != null)
            {
                if (this.cachedUndo.containsKey(value))
                {
                    remove = true;

                    break;
                }

                value = value.getParent();
            }

            if (remove)
            {
                it.remove();
            }
        }

        List<ValueChangeUndo> changeUndos = new ArrayList<>();

        for (Map.Entry<BaseValue, BaseType> entry : this.cachedUndo.entrySet())
        {
            BaseValue value = entry.getKey();
            ValueChangeUndo undo = new ValueChangeUndo(value.getPath(), entry.getValue(), value.toData());

            undo.editor(this);
            undo.selectedBefore(this.cachedCameraSelection, this.cachedVoicelineSelection, this.cachedKeyframeSelection, this.cachedPropertiesSelection);
            changeUndos.add(undo);
        }

        if (changeUndos.size() == 1)
        {
            this.undoManager.pushUndo(changeUndos.get(0));
        }
        else
        {
            this.undoManager.pushUndo(new CompoundUndo<>(changeUndos.toArray(new IUndo[0])));
        }

        this.cachedUndo.clear();
        this.cachedKeyframeSelection = this.cachedPropertiesSelection = null;

        this.undoTimer.mark();
    }

    private void handleUndos(IUndo<ValueGroup> undo, boolean redo)
    {
        IUndo<ValueGroup> anotherUndo = undo;

        if (anotherUndo instanceof CompoundUndo)
        {
            anotherUndo = ((CompoundUndo<ValueGroup>) anotherUndo).getFirst(ValueChangeUndo.class);
        }

        if (anotherUndo instanceof ValueChangeUndo)
        {
            ValueChangeUndo change = (ValueChangeUndo) anotherUndo;

            this.showPanel(change.panel == 1 ? this.replayEditor : (change.panel == 2 ? this.screenplay : this.cameraClips));

            List<Integer> cameraSelection = change.cameraClips.getSelection(redo);
            List<Integer> voiceLineSelection = change.voiceLinesClips.getSelection(redo);

            if (cameraSelection.isEmpty())
            {
                this.cameraClips.pickClip(null);
            }
            else
            {
                this.cameraClips.clips.setSelection(cameraSelection);

                Clip last = this.data.camera.get(cameraSelection.get(cameraSelection.size() - 1));

                this.cameraClips.pickClip(last);
            }

            if (voiceLineSelection.isEmpty())
            {
                this.screenplay.editor.pickClip(null);
            }
            else
            {
                this.screenplay.editor.clips.setSelection(voiceLineSelection);

                Clip last = this.data.voiceLines.get(voiceLineSelection.get(voiceLineSelection.size() - 1));

                this.screenplay.editor.pickClip(last);
            }

            change.cameraClips.apply(this.cameraClips.clips);
            change.voiceLinesClips.apply(this.screenplay.editor.clips);

            this.setCursor(change.tick);
            this.controller.createEntities();
            this.replayEditor.handleUndo(change, redo);
        }

        this.cameraClips.handleUndo(undo, redo);
        this.screenplay.editor.handleUndo(undo, redo);

        this.fillData();
    }

    public void undo()
    {
        if (this.data != null && this.undoManager.undo(this.data)) UIUtils.playClick();
    }

    public void redo()
    {
        if (this.data != null && this.undoManager.redo(this.data)) UIUtils.playClick();
    }

    public boolean isFlightDisabled()
    {
        return !this.dashboard.orbitUI.canControl();
    }

    /**
     * Set flight mode
     */
    public void setFlight(boolean flight)
    {
        this.runner.setManual(flight ? this.position : null);

        if (!this.isRunning() || !flight)
        {
            this.dashboard.orbitUI.setControl(flight);

            /* Marking the latest undo as unmergeable */
            if (!flight)
            {
                this.markLastUndoNoMerging();
            }
            else
            {
                this.lastPosition.set(Position.ZERO);
            }
        }
    }

    /**
     * Update display icon of the plause button
     */
    private void updatePlauseButton()
    {
        this.plause.both(this.isRunning() ? Icons.PAUSE : Icons.PLAY);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getFramebufferViewport();

        if (area.isInside(context))
        {
            return this.replayEditor.clickViewport(context, area);
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void update()
    {
        this.controller.update();

        super.update();
    }

    /* Rendering code */

    /**
     * Draw everything on the screen
     */
    @Override
    public void render(UIContext context)
    {
        this.submitUndo();
        this.updateLogic(context);
        this.renderOverlays(context);

        super.render(context);

        if (this.entered)
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Vec3d pos = player.getPos();
            Vector3d cameraPos = this.camera.position;
            double distance = cameraPos.distance(pos.x, pos.y, pos.z);
            int value = MinecraftClient.getInstance().options.getViewDistance().getValue();

            if (distance > value * 12)
            {
                this.getContext().notify(UIKeys.FILM_TELEPORT_DESCRIPTION, Colors.RED | Colors.A100);
            }

            this.entered = false;
        }
    }

    /**
     * Update logic for such components as repeat fixture, minema recording,
     * sync mode, flight mode, etc.
     */
    private void updateLogic(UIContext context)
    {
        Clip clip = this.cameraClips.getClip();

        /* Loop fixture */
        if (BBSSettings.editorLoop.get() && this.isRunning())
        {
            long min = -1;
            long max = -1;

            if (clip != null)
            {
                min = clip.tick.get();
                max = min + clip.duration.get();
            }

            UIClips clips = this.cameraClips.clips;

            if (clips.loopMin != clips.loopMax && clips.loopMin >= 0 && clips.loopMin < clips.loopMax)
            {
                min = clips.loopMin;
                max = clips.loopMax;
            }

            max = Math.min(max, this.data.camera.calculateDuration());

            if (min >= 0 && max >= 0 && min < max && (this.runner.ticks >= max - 1 || this.runner.ticks < min))
            {
                this.setCursor((int) min);
            }
        }

        /* Animate flight mode */
        if (this.dashboard.orbitUI.canControl())
        {
            this.dashboard.orbit.apply(this.position);

            Position current = new Position(this.getCamera());

            if (this.cameraClips.getClip() != null && this.cameraClips.isVisible())
            {
                if (!this.lastPosition.equals(current))
                {
                    this.cameraClips.editClip(current);
                }
            }

            this.lastPosition.set(current);
        }
        else
        {
            this.dashboard.orbit.setup(this.getCamera());
        }

        /* Rewind playback back to 0 */
        if (this.lastRunning && !this.isRunning())
        {
            this.lastRunning = this.runner.isRunning();
            this.setCursor(0);

            this.updatePlauseButton();
        }
    }

    /**
     * Draw icons for indicating different active states (like syncing
     * or flight mode)
     */
    private void renderIcons(UIContext context)
    {
        int x = this.iconBar.area.ex() - 18;
        int y = this.iconBar.area.ey() - 18;

        if (this.dashboard.orbitUI.canControl())
        {
            context.batcher.icon(Icons.ORBIT, x, y);
            y -= 20;
        }

        if (BBSSettings.editorLoop.get())
        {
            context.batcher.icon(Icons.REFRESH, x, y);
        }
    }

    private void renderDividers(UIContext context)
    {
        Area a1 = this.saveIcon.area;
        Area a2 = this.openVideos.area;

        context.batcher.box(a1.x + 3, a1.ey() + 4, a1.ex() - 3, a1.ey() + 5, 0x22ffffff);
        context.batcher.box(a2.x + 3, a2.ey() + 4, a2.ex() - 3, a2.ey() + 5, 0x22ffffff);
    }

    /**
     * Draw different camera type overlays (custom texture overlay, letterbox,
     * rule of thirds and crosshair)
     */
    private void renderOverlays(UIContext context)
    {
        int color = BBSSettings.primaryColor.get();

        this.area.render(context.batcher, Colors.mulRGB(color | Colors.A100, 0.2F));

        if (this.data == null)
        {
            return;
        }

        this.camera.copy(this.getWorldCamera());
        this.camera.view.set(this.lastView);
        this.camera.projection.set(this.lastProjection);
        context.batcher.flush();

        Texture texture = BBSRendering.getTexture();
        Area viewport = this.getViewportArea();
        Area area = this.getFramebufferArea(viewport);

        viewport.render(context.batcher, Colors.A75);

        if (texture != null)
        {
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        /* Render rule of thirds */
        if (BBSSettings.editorRuleOfThirds.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();

            context.batcher.box(area.x + area.w / 3 - 1, area.y, area.x + area.w / 3, area.y + area.h, guidesColor);
            context.batcher.box(area.x + area.w - area.w / 3, area.y, area.x + area.w - area.w / 3 + 1, area.y + area.h, guidesColor);

            context.batcher.box(area.x, area.y + area.h / 3 - 1, area.x + area.w, area.y + area.h / 3, guidesColor);
            context.batcher.box(area.x, area.y + area.h - area.h / 3, area.x + area.w, area.y + area.h - area.h / 3 + 1, guidesColor);
        }

        if (BBSSettings.editorCenterLines.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();
            int x = area.mx();
            int y = area.my();

            context.batcher.box(area.x, y, area.ex(), y + 1, guidesColor);
            context.batcher.box(x, area.y, x + 1, area.ey(), guidesColor);
        }

        if (BBSSettings.editorCrosshair.get())
        {
            int x = area.mx() + 1;
            int y = area.my() + 1;

            context.batcher.box(x - 4, y - 1, x + 3, y, Colors.setA(Colors.WHITE, 0.5F));
            context.batcher.box(x - 1, y - 4, x, y + 3, Colors.setA(Colors.WHITE, 0.5F));
        }

        this.controller.renderHUD(context, area);

        if (this.replayEditor.isVisible())
        {
            this.renderAudio(context, area);
        }
    }

    private void renderAudio(UIContext context, Area area)
    {
        int w = (int) (area.w * BBSSettings.audioWaveformWidth.get());
        int x = area.x(0.5F, w);

        AudioRenderer.renderAll(context.batcher, x, this.area.y + 10, w, BBSSettings.audioWaveformHeight.get(), this.dashboard.width, this.dashboard.height);
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        if (!BBSRendering.isIrisShadowPass())
        {
            this.lastProjection.set(RenderSystem.getProjectionMatrix());
            this.lastView.set(context.matrixStack().peek().getPositionMatrix());
        }

        this.controller.renderFrame(context);
    }

    /* IUICameraWorkDelegate implementation */

    public Film getFilm()
    {
        return this.data;
    }

    public Camera getCamera()
    {
        return this.camera;
    }

    public Camera getWorldCamera()
    {
        return BBSModClient.getCameraController().camera;
    }

    public CameraController getCameraController()
    {
        return BBSModClient.getCameraController();
    }

    public int getCursor()
    {
        return this.runner.ticks;
    }

    public void setCursor(int value)
    {
        this.runner.ticks = Math.max(0, value);

        this.screenplay.setCursor(this.runner.ticks);
    }

    public boolean isRunning()
    {
        return this.runner.isRunning();
    }

    public void togglePlayback()
    {
        this.setFlight(false);

        this.runner.toggle(this.getCursor());
        this.lastRunning = this.runner.isRunning();
        this.updatePlauseButton();
    }

    public boolean canUseKeybinds()
    {
        return this.isFlightDisabled();
    }

    public void fillData()
    {
        this.cameraClips.fillData();

        if (this.data != null)
        {
            this.screenplay.setFilm(this.data);
        }
    }

    public void markLastUndoNoMerging()
    {
        if (this.data == null)
        {
            return;
        }

        IUndo<ValueGroup> undo = this.undoManager.getCurrentUndo();

        if (undo != null)
        {
            undo.noMerging();
        }
    }
}