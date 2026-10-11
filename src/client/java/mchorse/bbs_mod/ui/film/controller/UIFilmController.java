package mchorse.bbs_mod.ui.film.controller;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import mchorse.bbs_mod.graphics.InverseView;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
/* 26.2: com.mojang.blaze3d.shaders.Uniform ist ersatzlos entfallen -- es gibt keine
 * Einzel-Uniforms mehr, nur noch Uniform-BLOECKE, die ein Zeichendurchgang bindet. Diese
 * Datei benutzte den Typ schon nicht mehr; nur der Import stand noch. */
import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIRecordOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class UIFilmController extends UIElement implements GizmoViewport
{
    public static final int CAMERA_MODE_CAMERA = 0;
    public static final int CAMERA_MODE_FREE = 1;
    public static final int CAMERA_MODE_ORBIT = 2;
    public static final int CAMERA_MODE_FIRST_PERSON = 3;
    public static final int CAMERA_MODE_THIRD_PERSON_BACK = 4;
    public static final int CAMERA_MODE_THIRD_PERSON_FRONT = 5;
    private static final int REPLAY_STENCIL_OFFSET = Gizmo.STENCIL_MAX + 1;

    public final UIFilmPanel panel;

    public FilmEditorController editorController;
    private Map<String, Integer> actors;

    /* Character control */
    private IEntity controlled;
    private final Vector2i lastMouse = new Vector2i();
    private int mouseMode;
    final Vector2f mouseStick = new Vector2f();

    /* Recording state */
    private IEntity previousEntity;
    private Form playerForm;
    private int recordingTick;
    private boolean recording;
    private int recordingCountdown;
    private List<String> recordingGroups;
    private BaseType recordingOld;
    private boolean instantKeyframes;

    /* Replay and group picking */
    private int hoveredReplayIndex = -1;
    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();
    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    public final OrbitFilmCameraController orbit = new OrbitFilmCameraController(this);
    public final OrbitViewGizmo orbitGizmo = new OrbitViewGizmo(this);
    private int pov;
    private boolean paused;

    private LevelRenderContext worldRenderContext;

    public UIFilmController(UIFilmPanel panel)
    {
        this.panel = panel;
        this.setPov(BBSSettings.editorCameraMode.get());

        UIFilmControllerKeys.register(this);

        this.noCulling();
    }

    /* Paket-sichtbar: UIFilmControllerKeys haengt die tasten daran. */
    void switchReplay(int direction)
    {
        List<Replay> list = this.panel.getData().replays.getList();

        int index = list.indexOf(this.getReplay());
        int newIndex = MathUtils.cycler(index + direction, list);
        Replay replay = list.get(newIndex);

        this.panel.replayEditor.setReplay(replay);
        UIUtils.playClick();
    }

    public boolean isInstantKeyframes()
    {
        return this.instantKeyframes;
    }

    public void toggleInstantKeyframes()
    {
        this.instantKeyframes = !this.instantKeyframes;
    }

    public boolean isPaused()
    {
        return this.paused;
    }

    public void setPaused(boolean paused)
    {
        this.paused = paused;
    }

    private void toggleMousePointer(boolean disable)
    {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();

        if (disable)
        {
            GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
        else
        {
            GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    public ValueOnionSkin getOnionSkin()
    {
        return BBSSettings.editorOnionSkin;
    }

    public ValueMotionPath getMotionPath()
    {
        return BBSSettings.editorMotionPath;
    }

    /**
     * A motion path target pinned so it keeps showing regardless of what's
     * selected. When nothing is pinned the path follows the selection (the
     * selected replay's bone, or its root coordinates). The pinned replay is
     * held live (not by id), so it self-clears once the replay is gone.
     */
    private Replay pinnedReplay;
    private Pair<String, Boolean> pinnedBone;

    public boolean isMotionPathPinned()
    {
        if (this.pinnedReplay != null && this.panel.getData() != null && !this.panel.getData().replays.getList().contains(this.pinnedReplay))
        {
            this.unpinMotionPath();
        }

        return this.pinnedReplay != null;
    }

    /** Pin the currently selected replay and bone so its motion path stays shown. */
    public void pinMotionPath()
    {
        Replay replay = this.getReplay();

        this.pinnedReplay = replay;
        this.pinnedBone = replay == null ? null : this.getBone();
    }

    public void unpinMotionPath()
    {
        this.pinnedReplay = null;
        this.pinnedBone = null;
    }

    public void toggleMotionPathPin()
    {
        if (this.isMotionPathPinned())
        {
            this.unpinMotionPath();
        }
        else
        {
            this.pinMotionPath();
        }
    }

    private int getTick()
    {
        return this.panel.getCursor();
    }

    private Replay getReplay()
    {
        return this.panel.replayEditor.getReplay();
    }

    private int getCurrentReplayIndex()
    {
        if (this.panel.getData() == null)
        {
            return -1;
        }

        Replay replay = this.getReplay();

        return replay == null ? -1 : this.panel.getData().replays.getList().indexOf(replay);
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public IEntity getCurrentEntity()
    {
        int idx = this.getCurrentReplayIndex();

        return idx < 0 ? null : this.getEntities().get(idx);
    }

    public int getPovMode()
    {
        return this.pov % 6;
    }

    public void setPov(int pov)
    {
        this.pov = pov;
        this.orbit.enabled = this.getPovMode() > 1;

        BBSSettings.editorCameraMode.set(this.getPovMode());

        /* Der Modus entscheidet, ob die Vorschau die Flaeche fuellt (freie Ansicht) oder im
         * Exportformat bleibt (Kamera-Ansicht) — also muss die Groesse hier neu gerechnet
         * werden, nicht erst beim naechsten Layout. */
        this.panel.vorschauGroesseNachziehen();
    }

    private int getMouseMode()
    {
        return this.mouseMode % 6;
    }

    private void setMouseMode(int mode)
    {
        if (!ClientNetwork.isIsBBSModOnServer() && mode == 0)
        {
            mode = 1;

            this.getContext().notifyError(UIKeys.FILM_CONTROLLER_SERVER_WARNING);
        }

        this.mouseMode = mode;

        if (this.controlled != null)
        {
            /* Restore value of the mouse stick */
            int index = this.getMouseMode() - 1;

            if (index >= 0)
            {
                float[] variables = this.controlled.getExtraVariables();

                this.mouseStick.set(variables[index * 2 + 1], variables[index * 2]);
            }
        }
    }

    boolean isMouseLookMode()
    {
        return this.getMouseMode() == 0;
    }

    public void createEntities()
    {
        this.stopRecording();

        if (this.controlled != null)
        {
            this.toggleControl();
        }

        this.editorController = new FilmEditorController(this.panel.getData(), this);
        this.editorController.createEntities();

        IntObjectMap<IEntity> entities = this.panel.getRunner().getContext().entities;

        entities.clear();
        entities.putAll(this.editorController.getEntities());
    }

    public IntObjectMap<IEntity> getEntities()
    {
        return this.editorController == null ? new IntObjectHashMap<>() : this.editorController.getEntities();
    }

    public Map<String, Integer> getActors()
    {
        return this.actors;
    }

    public void updateActors(Map<String, Integer> actors)
    {
        this.actors = actors;
    }

    /* Character control state */

    public IEntity getControlled()
    {
        return this.controlled;
    }

    public boolean isControlling()
    {
        return this.controlled != null;
    }

    public void toggleControl()
    {
        this.getContext().unfocus();

        if (this.panel.replayEditor.isVisible())
        {
            this.panel.replayEditor.pickPlayerCategory();
        }

        boolean replacePlayer = ClientNetwork.isIsBBSModOnServer();
        IntObjectMap<IEntity> entities = this.getEntities();

        if (this.controlled != null)
        {
            if (replacePlayer && this.previousEntity != null)
            {
                this.controlled.setForm(this.playerForm);

                entities.put(CollectionUtils.getKey(entities, this.controlled), this.previousEntity);
                this.previousEntity = null;
            }

            this.controlled = null;
        }
        else if (this.panel.replayEditor.replaysList.replays.isSelected())
        {
            this.controlled = this.getCurrentEntity();

            if (replacePlayer && this.controlled != null)
            {
                MCEntity player = Morph.getMorph(Minecraft.getInstance().player).entity;

                this.playerForm = player.getForm();
                this.previousEntity = this.controlled;

                player.copy(this.controlled);
                PlayerUtils.teleport(this.controlled.getX(), this.controlled.getY(), this.controlled.getZ(), this.controlled.getHeadYaw(), this.controlled.getBodyYaw(), this.controlled.getPitch());
                entities.put(CollectionUtils.getKey(entities, this.controlled), player);

                this.controlled = player;
            }
        }

        this.setMouseMode(this.mouseMode);
        this.toggleMousePointer(this.controlled != null);

        if (this.controlled == null && this.recording)
        {
            this.stopRecording();
        }
    }

    boolean canControl()
    {
        UIContext context = this.getContext();

        return this.controlled != null && context != null && !UIOverlay.has(context);
    }

    /* Recording */

    public boolean isPlaying()
    {
        boolean playing = !UIOverlay.has(this.getContext()) && this.panel.isRunning();

        if (this.isPaused())
        {
            playing = true;
        }

        return playing;
    }

    public boolean isRecording()
    {
        return this.recording;
    }

    public int getRecordingCountdown()
    {
        return this.recordingCountdown;
    }

    public List<String> getRecordingGroups()
    {
        return this.recordingGroups;
    }

    private boolean hasTransformRecordingGroup()
    {
        return this.recordingGroups != null && this.recordingGroups.contains(ReplayKeyframes.GROUP_TRANSFORM);
    }

    public boolean isTransformRecording()
    {
        return this.recording
            && this.recordingCountdown <= 0
            && this.hasTransformRecordingGroup();
    }

    public void startRecording(List<String> groups)
    {
        if (groups != null && groups.contains("outside"))
        {
            Minecraft.getInstance().gui.setScreen(null);

            Replay replay = this.panel.replayEditor.getReplay();
            int index = this.panel.getData().replays.getList().indexOf(replay);

            if (index >= 0)
            {
                BBSModClient.getFilms().startRecording(this.panel.getData(), index, this.panel.getCursor());
            }

            return;
        }

        this.recordingTick = this.getTick();
        this.recording = true;
        this.recordingCountdown = Math.max(0, TimeUtils.toTick(BBSSettings.recordingCountdown.get()));
        this.recordingGroups = groups;
        boolean transformRecording = groups != null && groups.contains(ReplayKeyframes.GROUP_TRANSFORM);

        this.recordingOld = transformRecording ? this.getReplay().properties.toData() : this.getReplay().keyframes.toData();

        if (transformRecording)
        {
            if (this.controlled != null)
            {
                this.toggleControl();
            }

            this.setMouseMode(0);
        }
        else if (groups != null)
        {
            if (groups.contains(ReplayKeyframes.GROUP_LEFT_STICK))
            {
                this.setMouseMode(1);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_RIGHT_STICK))
            {
                this.setMouseMode(2);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_TRIGGERS))
            {
                this.setMouseMode(3);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_EXTRA1))
            {
                this.setMouseMode(4);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_EXTRA2))
            {
                this.setMouseMode(5);
            }
            else
            {
                this.setMouseMode(0);
            }
        }

        if (!transformRecording && this.controlled == null)
        {
            this.toggleControl();
        }

        this.toggleMousePointer(!transformRecording && this.controlled != null);
    }

    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        boolean transformRecording = this.hasTransformRecordingGroup();

        this.recording = false;
        this.recordingGroups = null;

        if (!transformRecording && this.controlled != null)
        {
            this.toggleControl();
        }

        this.panel.setCursor(this.recordingTick);

        if (this.panel.getRunner().isRunning())
        {
            this.panel.togglePlayback();
        }

        if (this.recordingCountdown > 0)
        {
            return;
        }

        Replay replay = this.getReplay();

        if (replay != null && this.recordingOld != null)
        {
            if (transformRecording)
            {
                for (KeyframeChannel<?> channel : replay.properties.properties.values())
                {
                    if (PerLimbService.isPoseBoneChannel(channel.getId()))
                    {
                        channel.simplify();
                    }
                }

                BaseType newData = replay.properties.toData();

                replay.properties.fromData(this.recordingOld);
                replay.properties.preNotify();
                replay.properties.fromData(newData);
                replay.properties.postNotify();

                if (this.panel.replayEditor.getReplay() == replay)
                {
                    this.panel.replayEditor.setReplay(replay, false, UIReplaysEditor.OrbitReaction.SWITCH);
                }
            }
            else
            {
                for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                {
                    channel.simplify();
                }

                BaseType newData = replay.keyframes.toData();

                replay.keyframes.fromData(this.recordingOld);
                replay.keyframes.preNotify();
                replay.keyframes.fromData(newData);
                replay.keyframes.postNotify();
            }

            this.recordingOld = null;
        }

        this.setMouseMode(ClientNetwork.isIsBBSModOnServer() ? 0 : 1);
    }

    /* Input handling */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.canControl())
        {
            return true;
        }

        boolean gizmoShown = this.canShowGizmo();

        /* Gizmo-Handles schlagen alles (oben gezeichnet); die Trackball-Kugel kommt
         * zuletzt, sonst schluckt ihre Scheibe fremde Picks. Beides nur bei sichtbarem
         * Gizmo — sonst faengt die Kugel Klicks ohne selektierten Bone. */
        if (gizmoShown && this.gizmo.mouseClickedHandle(context))
        {
            return true;
        }

        if (context.mouseButton == 0 && this.hoveredReplayIndex >= 0)
        {
            this.pickReplay(this.hoveredReplayIndex);

            return true;
        }

        if (gizmoShown && this.gizmo.mouseClickedSphere(context))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.panel.lastProjection;
    }

    @Override
    public Area getGizmoArea()
    {
        return this.panel.preview.getViewport();
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        float gizmoTransition = this.isPlaying() ? context.getTransition() : 0F;

        return mchorse.bbs_mod.ui.film.replays.FilmGizmoDrag.startFilmGizmo(this.panel, context, stencilIndex, gizmoTransition);
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        this.panel.replayEditor.pickFormWithOffers(context, form, bone);
    }

    private void pickReplay(int index)
    {
        /* ⛔ Nur AUSWAEHLEN. Hier stand showPanel(replayEditor) — Alt+Klick auf ein
         * Replay riss den Nutzer in die alte Timeline (Akte 34). */
        this.panel.replayEditor.setReplay(this.panel.getData().replays.getList().get(index));
    }

    public void stopGizmoInteraction()
    {
        this.gizmo.stop();
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.canControl())
        {
            return true;
        }

        boolean consumed = this.gizmo.mouseReleased(context);

        consumed = this.orbitGizmo.mouseReleased(context) || consumed;

        this.stopGizmoInteraction();

        this.panel.replayEditor.releaseViewport(context, this.orbit.wasDragged());
        this.orbit.stop();

        if (this.panel.isFlying() && context.mouseButton == 2)
        {
            this.panel.dashboard.orbit.release();
        }

        return consumed || super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.canControl())
        {
            if (this.isControlling() && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.toggleControl();
                UIUtils.playClick();

                return true;
            }
            else if (context.getKeyAction() == KeyAction.PRESSED && context.getKeyCode() >= GLFW.GLFW_KEY_1 && context.getKeyCode() <= GLFW.GLFW_KEY_6)
            {
                /* Switch mouse input mode */
                this.setMouseMode(context.getKeyCode() - GLFW.GLFW_KEY_1);

                return true;
            }

            /* 26.2: getKey(int, int) ist entfallen; uebrig sind getKey(KeyEvent) und
             * getKey(String), und ein KeyEvent haben wir hier nicht. Die zwei Zeilen sind
             * genau das, was die alte Ueberladung tat -- im Bytecode von getKey(KeyEvent)
             * nachgelesen: bei unbekanntem Tastencode (-1) zaehlt der Scancode, sonst der
             * Tastencode. Wer nur KEYSYM nimmt, verliert die Tasten ohne Symbol. */
            InputConstants.Key utilKey = context.getKeyCode() == GLFW.GLFW_KEY_UNKNOWN
                ? InputConstants.Type.SCANCODE.getOrCreate(context.getScanCode())
                : InputConstants.Type.KEYSYM.getOrCreate(context.getKeyCode());

            if (this.canControlWithKeyboard(utilKey))
            {
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    private boolean canControlWithKeyboard(InputConstants.Key utilKey)
    {
        if (!ClientNetwork.isIsBBSModOnServer())
        {
            return false;
        }

        Options options = Minecraft.getInstance().options;

        return options.keyUp.getDefaultKey() == utilKey
            || options.keyDown.getDefaultKey() == utilKey
            || options.keyLeft.getDefaultKey() == utilKey
            || options.keyRight.getDefaultKey() == utilKey
            || options.keyShift.getDefaultKey() == utilKey
            || options.keySprint.getDefaultKey() == utilKey
            || options.keyJump.getDefaultKey() == utilKey;
    }

    public void pickRecording()
    {
        if (this.panel.replayEditor.getReplay() == null)
        {
            return;
        }

        if (this.recording)
        {
            this.stopRecording();

            return;
        }

        this.toggleMousePointer(false);

        UIRecordOverlayPanel panel = new UIRecordOverlayPanel(
            UIKeys.FILM_CONTROLLER_RECORD_TITLE,
            UIKeys.FILM_CONTROLLER_RECORD_DESCRIPTION,
            this::startRecording,
            this.panel.replayEditor.getCategory() == UIReplaysEditor.ReplayCategory.POSE
        );
        UIIcon icon = new UIIcon(Icons.UPLOAD, (b) -> panel.submit(Arrays.asList("outside")));

        icon.tooltip(UIKeys.FILM_GROUPS_OUTSIDE);
        panel.bar.add(icon);
        panel.keys().register(Keys.RECORDING_GROUP_OUTSIDE, icon::clickItself);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    public Icon getOrbitModeIcon()
    {
        return this.getOrbitModeIcon(this.getPovMode());
    }

    public Icon getOrbitModeIcon(int povMode)
    {
        if (povMode == UIFilmController.CAMERA_MODE_FREE) return Icons.REFRESH;
        else if (povMode == UIFilmController.CAMERA_MODE_ORBIT) return Icons.ORBIT;
        else if (povMode == UIFilmController.CAMERA_MODE_FIRST_PERSON) return Icons.VISIBLE;
        else if (povMode == UIFilmController.CAMERA_MODE_THIRD_PERSON_BACK) return Icons.ARROW_UP;
        else if (povMode == UIFilmController.CAMERA_MODE_THIRD_PERSON_FRONT) return Icons.ARROW_DOWN;

        return Icons.CAMERA;
    }

    public void teleportOrbitPivotToReplay()
    {
        this.orbit.teleportPivotToReplay();
    }

    public void toggleOrbitAttachment()
    {
        this.orbit.toggleAttachment();
    }

    public boolean zoomOrbit(double mouseWheel)
    {
        return this.orbit.zoom(mouseWheel);
    }

    public void toggleOrbitMode()
    {
        if (this.controlled != null)
        {
            this.setPov(this.pov + (Window.isShiftPressed() ? -1 : 1));

            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.autoKeys();

            menu.action(this.getOrbitModeIcon(0), UIKeys.FILM_REPLAY_ORBIT_CAMERA, this.pov == CAMERA_MODE_CAMERA, () -> this.setPov(0));
            menu.action(this.getOrbitModeIcon(1), UIKeys.FILM_REPLAY_ORBIT_FREE, this.pov == CAMERA_MODE_FREE, () -> this.setPov(1));
            menu.action(this.getOrbitModeIcon(2), UIKeys.FILM_REPLAY_ORBIT_ORBIT, this.pov == CAMERA_MODE_ORBIT, () -> this.setPov(2));
            menu.action(this.getOrbitModeIcon(3), UIKeys.FILM_REPLAY_ORBIT_FIRST_PERSON, this.pov == CAMERA_MODE_FIRST_PERSON, () -> this.setPov(3));
            menu.action(this.getOrbitModeIcon(4), UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_BACK, this.pov == CAMERA_MODE_THIRD_PERSON_BACK, () -> this.setPov(4));
            menu.action(this.getOrbitModeIcon(5), UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_FRONT, this.pov == CAMERA_MODE_THIRD_PERSON_FRONT, () -> this.setPov(5));
        });
    }

    public void toggleReplayMenu()
    {
        if (this.controlled != null)
        {
            return;
        }

        UISimpleContextMenu menu = new UISimpleContextMenu();

        menu.actions.scroll.scrollItemSize = 30;

        this.getContext().replaceContextMenu((manager) ->
        {
            manager.custom(menu);
            manager.autoKeys();

            for (Replay replay : this.panel.getData().replays.getList())
            {
                int color = this.getReplay() == replay ? BBSSettings.primaryColor(0) : 0;

                manager.action(new ReplayContextAction(replay, IKey.raw(replay.getName()), () ->
                {
                    this.panel.replayEditor.setReplay(replay, false, UIReplaysEditor.OrbitReaction.SWITCH);

                    UIReplayList list = this.panel.replayEditor.replaysList.replays;

                    list.scrollToReplay(replay);

                    UIUtils.playClick();
                }, color));
            }
        });
    }

    public void handleCamera(Camera camera, float transition)
    {
        if (this.orbit.enabled)
        {
            int mode = this.getPovMode();

            if (mode == CAMERA_MODE_ORBIT)
            {
                this.orbit.setup(camera, transition);
            }
            else if (mode != CAMERA_MODE_FREE)
            {
                this.handleFirstThirdPerson(camera, transition, mode);
            }

            /* While flying, the FOV is driven live by the flight camera, so don't overwrite it */
            if (!this.panel.isFlying())
            {
                camera.fov = BBSSettings.getFov();
            }
        }
    }

    private void handleFirstThirdPerson(Camera camera, float transition, int mode)
    {
        IEntity controller = this.getCurrentEntity();

        if (controller == null)
        {
            return;
        }

        Vector3d position = new Vector3d();
        Vector3f rotation = new Vector3f();
        float distance = 5F;

        position.set(controller.getPrevX(), controller.getPrevY(), controller.getPrevZ());
        position.lerp(new Vector3d(controller.getX(), controller.getY(), controller.getZ()), transition);
        position.y += controller.getEyeHeight();

        rotation.set(controller.getPrevPitch(), controller.getPrevHeadYaw(), 0);
        rotation.lerp(new Vector3f(controller.getPitch(), controller.getHeadYaw(), 0), transition);

        rotation.x = MathUtils.toRad(rotation.x);
        rotation.y = MathUtils.toRad(rotation.y);

        if (mode == CAMERA_MODE_FIRST_PERSON)
        {
            camera.position.set(position);
            camera.rotation.set(rotation.x, rotation.y + MathUtils.PI, 0F);

            return;
        }

        boolean back = mode == CAMERA_MODE_THIRD_PERSON_BACK;
        Vector3f rotate = Matrices.rotation(rotation.x * (back ? 1 : -1), (back ? 0F : MathUtils.PI) - rotation.y);
        Level world = Minecraft.getInstance().level;

        HitResult result = RayTracing.rayTraceEntity(
            world,
            RayTracing.fromVector3d(position),
            RayTracing.fromVector3f(rotate),
            distance
        );

        if (result.getType() == HitResult.Type.BLOCK)
        {
            distance = (float) position.distance(result.getLocation().x, result.getLocation().y, result.getLocation().z) - 0.1F;
        }

        rotate.mul(distance);
        position.add(rotate);

        camera.position.set(position);
        camera.rotation.set(rotation.x * (back ? -1 : 1), rotation.y + (back ? 0 : MathUtils.PI), 0);
    }

    public void insertFrame()
    {
        Replay replay = this.getReplay();

        if (replay == null)
        {
            return;
        }

        UIReplaysEditor.ReplayCategory category = this.panel.replayEditor.getCategory();

        if (this.panel.replayEditor.keyframeEditor.insertSelectedPose(this.getTick(), replay.properties)
            || category == UIReplaysEditor.ReplayCategory.MODEL)
        {
            return;
        }

        if (category == UIReplaysEditor.ReplayCategory.POSE)
        {
            UIReplaysEditorUtils.insertPoseKeyframesAtTick(replay, this.getTick());
            return;
        }

        if (Window.isCtrlPressed())
        {
            this.toggleMousePointer(false);

            UIRecordOverlayPanel panel = new UIRecordOverlayPanel(
                UIKeys.FILM_CONTROLLER_INSERT_FRAME_TITLE,
                UIKeys.FILM_CONTROLLER_INSERT_FRAME_DESCRIPTION,
                (groups) ->
                {
                    BaseValue.edit(replay.keyframes, (keyframes) ->
                    {
                        keyframes.record(this.getTick(), this.getCurrentEntity(), groups);
                    });
                }
            );

            panel.onClose((event) -> this.toggleMousePointer(this.controlled != null));

            UIOverlay.addOverlay(this.getContext(), panel);
        }
        else
        {
            List<String> chosenGroups = Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION);

            if (this.mouseMode == 1) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_LEFT_STICK);
            else if (this.mouseMode == 2) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_RIGHT_STICK);
            else if (this.mouseMode == 3) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_TRIGGERS);
            else if (this.mouseMode == 4) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA1);
            else if (this.mouseMode == 5) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA2);

            final List<String> groups = chosenGroups;

            BaseValue.edit(replay.keyframes, (keyframes) ->
            {
                keyframes.record(this.getTick(), this.getCurrentEntity(), groups);
            });
        }
    }

    /**
     * Insert position and rotation keyframes at the current tick from the live
     * Minecraft player's world transform - a quick way to "teleport" the replay
     * to where the player is standing (and facing). Only the transform channels
     * are touched (unlike full player recording), so no stance/velocity noise is
     * added to the replay.
     */
    public void insertPlayerFrame()
    {
        Replay replay = this.getReplay();

        if (replay == null || Minecraft.getInstance().player == null)
        {
            return;
        }

        Morph morph = Morph.getMorph(Minecraft.getInstance().player);

        if (morph == null)
        {
            return;
        }

        IEntity player = morph.entity;
        int tick = this.getTick();

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.x.insert(tick, player.getX());
            keyframes.y.insert(tick, player.getY());
            keyframes.z.insert(tick, player.getZ());

            keyframes.yaw.insert(tick, (double) player.getYaw());
            keyframes.pitch.insert(tick, (double) player.getPitch());
            keyframes.headYaw.insert(tick, (double) player.getHeadYaw());
            keyframes.bodyYaw.insert(tick, (double) player.getBodyYaw());
        });

        UIUtils.playClick();
    }

    /* Update */

    public void update()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        RunnerCameraController runner = this.panel.getRunner();

        this.handleRecording(runner);

        if (this.editorController != null)
        {
            this.editorController.update();
        }

        if (this.canControl())
        {
            this.updateControls();
        }
    }

    private void handleRecording(RunnerCameraController runner)
    {
        if (this.recording)
        {
            if (this.recordingCountdown > 0)
            {
                this.recordingCountdown -= 1;

                if (this.recordingCountdown <= 0)
                {
                    this.panel.togglePlayback();
                }
            }

            if (this.recordingCountdown <= 0)
            {
                boolean stopped = !runner.isRunning();

                if (BBSSettings.editorLoop.get())
                {
                    Vector2i loop = this.panel.getLoopingRange();
                    int min = loop.x;
                    int max = loop.y;
                    int ticks = this.panel.getCursor();

                    if (min >= 0 && max >= 0 && min < max && (ticks >= max - 1 || ticks < min) || stopped)
                    {
                        this.stopRecording();
                    }
                }
                else if (stopped)
                {
                    this.stopRecording();
                }
            }
        }
    }

    private void updateControls()
    {
        IEntity controller = this.controlled;

        if (!this.isMouseLookMode())
        {
            int index = this.getMouseMode() - 1;
            float[] extraVariables = controller.getExtraVariables();

            extraVariables[index * 2] = this.mouseStick.y;
            extraVariables[index * 2 + 1] = this.mouseStick.x;
        }

        if (this.instantKeyframes)
        {
            this.insertFrame();
        }
    }

    /* Render */

    public void renderHUD(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        int mode = this.getMouseMode();

        if (this.controlled != null)
        {
            /* Render helpful guides for sticks and triggers controls */
            if (mode > 0)
            {
                String label = UIKeys.FILM_GROUPS_LEFT_STICK.get();

                if (mode == 2)
                {
                    label = UIKeys.FILM_GROUPS_RIGHT_STICK.get();
                }
                else if (mode == 3)
                {
                    label = UIKeys.FILM_GROUPS_TRIGGERS.get();
                }
                else if (mode == 4)
                {
                    label = UIKeys.FILM_GROUPS_EXTRA_1.get();
                }
                else if (mode == 5)
                {
                    label = UIKeys.FILM_GROUPS_EXTRA_2.get();
                }

                context.batcher.textCard(label, area.x + 5, area.ey() - 5 - font.getHeight(), Colors.WHITE, BBSSettings.primaryColor(Colors.A100));

                int ww = (int) (Math.min(area.w, area.h) * 0.75F);
                int hh = ww;
                int x = area.x + (area.w - ww) / 2;
                int y = area.y + (area.h - hh) / 2;
                int color = Colors.setA(Colors.WHITE, 0.5F);

                context.batcher.outline(x, y, x + ww, y + hh, color);

                int bx = area.x + area.w / 2 + (int) ((this.mouseStick.y) * ww / 2);
                int by = area.y + area.h / 2 + (int) ((this.mouseStick.x) * hh / 2);

                context.batcher.box(bx - 4, by - 4, bx + 4, by + 4, color);
            }
        }

        /* Render recording overlay */
        if (this.recording)
        {
            int x = area.x + 5 + 16;
            int y = area.y + 5;

            context.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y, 1F, 0F);

            if (this.recordingCountdown <= 0)
            {
                context.batcher.textCard(UIKeys.FILM_CONTROLLER_TICKS.format(this.getTick()).get(), x + 3, y + 4, Colors.WHITE, Colors.A50);
            }
            else
            {
                context.batcher.textCard(String.valueOf(this.recordingCountdown / 20F), x + 3, y + 4, Colors.WHITE, Colors.A50);
            }
        }

        int x = area.ex() - 4;
        int y = area.y + 5;

        if (BBSSettings.editorLoop.get())
        {
            context.batcher.icon(Icons.REFRESH, Colors.WHITE | Colors.A100, x, y, 1F, 0F);

            y += 16 + 5;
        }

        if (this.panel.isFlying())
        {
            String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.panel.dashboard.orbit.speed.getValue()).get();
            int w = font.getWidth(label);

            context.batcher.textCard(label, x - w, y, Colors.WHITE, Colors.A50);

            y += font.getHeight() + 7;
        }

        Replay replay = this.panel.replayEditor.getReplay();

        /* ⛔ Der Name steht NICHT mehr in der Ecke: die Kontextzeile links oben traegt ihn
         * bereits („(142) rigtest · rigtest_human"), und der Entwurf haelt die rechte obere
         * Ecke fuer das Achsen-Gizmo frei. Zwei Beschriftungen derselben Sache, die sich
         * gegenseitig ueberlappen, waren das Ergebnis. Die Formvorschau bleibt. */
        if (replay != null)
        {
            int w = 0;

            Form form = replay.form.get();

            if (form != null)
            {
                /* ⛔ Das 40er-Feld muss GANZ in der Flaeche liegen: `y -= 5` und `y -= 10`
                 * zogen es 15 Einheiten hoch, ein Viertel lag ueber dem Bild und wurde
                 * abgeschnitten (Sonde in renderUI: „839,14 bis 879,54", Flaeche ab y=24). */
                x -= w + 35;
                context.batcher.clip(x, y, 40, 40, context);
                FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);
                context.batcher.unclip(context);
            }
        }

        /* ⛔ GRENZE VOR DEM GIZMO (2026-08-11). Die Replay-Vorschauen oben werden nur
         * EINGEREIHT und fahren am Ende des Durchlaufs ueber das ganze GUI; Gizmo,
         * Pick-Vorschau und Orbit-Gizmo zeichnen danach in denselben Viewport und
         * kaemen darunter. In 1.21.1 liegen sie oben. Siehe MiniaturGrenze. */
        mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.abgeben(
            context, context.menu.width, context.menu.height);

        /* The visual gizmo draws here, before the picking preview, so the bone /
         * sphere hover highlights composite on top of it. It moved out of the
         * world pass into the UI pipeline so its translucent parts blend
         * correctly (see Gizmo#renderInterface). */
        if (this.canShowGizmo())
        {
            this.gizmo.renderGizmo(context);
        }

        this.renderPickingPreview(context, area);

        this.orbitGizmo.render(context, area);

        this.orbit.handleOrbiting(context);
    }

    private void renderPickingPreview(UIContext context, Area area)
    {
        if (this.panel.isFlying() || this.worldRenderContext == null)
        {
            return;
        }

        boolean altPressed = Window.isAltPressed();

        /* 26.2: depthFunc ist entfallen -- der Tiefenvergleich ist kein globaler Schalter
         * mehr, er steht in der Pipeline jedes Zuges. Der Auswahl-Durchgang darunter zeichnet
         * ueber ModelVAOPipeline.PICKER_* bzw. die Stencil-Wege, und die bringen ihren
         * Vergleich selbst mit. ⚠️ GL_LESS heisst dort NICHT LESS_THAN: 26.2 rechnet die Tiefe
         * umgekehrt (naeher = groesserer Wert), das Gegenstueck waere GREATER_THAN. */

        /* Cache the global stuff */
        MatrixStackUtils.cacheMatrices();

        /* ⛔ PERSPECTIVE, NICHT ORTHOGRAPHIC — die ART muss zur (perspektivischen)
         * lastProjection passen, sonst landet die Figur ~5x zu breit im Auswahlpuffer
         * (gemessen 2026-08-02). Und die Zeile MUSS stehen: beim Sammeln ist in 26.2
         * keine brauchbare Projektion aktiv (w=0-Falle, siehe Bildfilter.lauf). */
        mchorse.bbs_mod.graphics.Projektion.setzen(this.panel.lastProjection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);

        /* ⛔ DIE ANSICHT AUS DEM CONTEXT, NICHT AUS BBSRendering.camera. Beide tragen
         * dieselbe Drehung, aber aus verschiedenen Augenblicken -- gemischt ergibt das den
         * Umriss-Versatz bei Kamerabewegung (Messwerte und ganze Begruendung: Pufferlage
         * #sichtvergleich). Der Kontext gewinnt: mit IHM ist das sichtbare Weltbild
         * entstanden, und er geht unten an renderStencilEntity weiter. */
        org.joml.Matrix4f ansicht = new org.joml.Matrix4f(
            this.worldRenderContext.levelState().cameraRenderState.viewRotationMatrix);

        InverseView.set(new Matrix3f(ansicht).invert());

        /* ⚗️ ALLE eingaenge dieser stelle auf einmal — gegenstueck haengt in
         * UIFilmPanel#renderInWorld. Vergleich: python tools/lagen.py stencil weltbild */
        mchorse.bbs_mod.graphics.Zeichenlage.melden("stencil");

        /* ⚗️ Die zwei Kameraquellen nebeneinander — Begruendung und Messwerte in Pufferlage. */
        mchorse.bbs_mod.graphics.Pufferlage.sichtvergleich(
            this.worldRenderContext.levelState().cameraRenderState.viewRotationMatrix);

        /* Render the stencil */
        PoseStack worldStack = this.worldRenderContext.poseStack();

        worldStack.pushPose();
        worldStack.setIdentity();
        /* Dieselbe Quelle wie InverseView oben — siehe die Begruendung dort. */
        MatrixStackUtils.multiply(worldStack, ansicht);

        /* ⚗️ Die ENDGUELTIGE Zeichenlage, mit der der Puffer gefuellt wird — Begruendung und
         * bisheriger Messstand stehen in Pufferlage. */
        mchorse.bbs_mod.graphics.Pufferlage.melden(
            worldStack, this.getCurrentEntity(), this.isPlaying());

        this.renderStencil(this.worldRenderContext, this.getContext(), altPressed);
        worldStack.popPose();

        /* Return back to orthographic projection */
        MatrixStackUtils.restoreMatrices();

        /* Hier stand GL_ALWAYS, damit alles Folgende ueber dem Weltbild liegt. Alle diese
         * Ueberlagerungen — Kugel-Hervorhebung, Ablesekarte, Umriss, Vorschaukachel — gehen
         * ueber den Batcher in den GUI-Durchgang (Batcher2D#blit reicht einen BlitRenderState
         * an den GuiRenderer). Der Tiefenzustand kommt dort aus der Pipeline des Elements
         * (RenderPipelines.GUI_TEXTURED bzw. die davon geerbten BBS-Pipelines); einen
         * globalen Tiefenvergleich, den man vorher umstellen koennte, gibt es nicht mehr. */

        this.hoveredReplayIndex = -1;

        /* Der Control-Griff lebt gerade dann, wenn KEIN Gizmo gezeigt wird. */
        this.panel.replayEditor.foerdereCtrlDrag(context);

        ZugHilfe.zeichne(this.gizmo, context, this.canShowGizmo(),
            mchorse.bbs_mod.cubic.ik.RigCtrl.aktivFuer(this.getCurrentEntity()),
            this.panel.replayEditor.keyframeEditor);

        /* Der Umriss haengt NICHT am Ueberfahren — er steht, solange ein Replay gewaehlt ist. */
        /* Bei aktivem Control-Rig keine Objekt-Silhouette — sie uebermalt die Controls
         * (User-Abnahme 2026-08-14); Blender zeigt sie im Pose-Mode auch nicht. */
        if (!mchorse.bbs_mod.cubic.ik.RigCtrl.aktivFuer(this.getCurrentEntity()))
        {
            StencilAnzeige.umriss(context, area, this.panel, this.stencil, altPressed, REPLAY_STENCIL_OFFSET, this.stencilMap.widgetBasis);
        }

        if (!this.stencil.hasPickedOhneWarten())
        {
            return;
        }

        int index = this.stencil.getIndexOhneWarten();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPickedOhneWarten();
        int w = texture.width;
        int h = texture.height;

        RenderPipeline previewProgram = BBSShaders.getPickerPreviewProgram();
        Supplier<RenderPipeline> getPickerPreviewProgram = BBSShaders::getPickerPreviewProgram;
        /* ⛔ EIGENES ZWISCHENBILD, NICHT das des Umrisses (2026-08-05, #21): 26.2 sammelt
         * Blits und zeichnet spaeter — teilen sich beide den Puffer, sampeln beide den
         * Endinhalt und der Umriss verschwindet. Vorrechnen statt GUI-Binden: Bildfilter. */
        mchorse.bbs_mod.graphics.Bildfilter.kennungHervorheben(texture,
            this.stencil.hervorhebungsbild(), index,
            BBSSettings.stencilHighlightColor.get());

        /* Das Mischen kommt aus der Pipeline der Kachel: der Batcher zeichnet sie ueber
         * BBSPipelines (von RenderPipelines.GUI_TEXTURED geerbt), und deren ColorTargetState
         * traegt BlendFunction.TRANSLUCENT. Ein enableBlend davor gibt es in 26.2 nicht mehr. */
        if (!Boolean.getBoolean("bbs.probe.keinStencilBlit")) context.batcher.texturedBox(() -> net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.stencil.hervorhebungsbild(), Colors.WHITE, area.x, area.y, area.w, area.h, 0, h, w, 0, w, h);

        this.hoveredReplayIndex = StencilAnzeige.beschriftung(context, this.panel, altPressed,
            index, REPLAY_STENCIL_OFFSET, this.getCurrentReplayIndex(), pair);
    }

    public void startRenderFrame(float tickDelta)
    {
        if (this.editorController != null)
        {
            this.editorController.startRenderFrame(tickDelta);
        }
    }

    public void renderFrame(LevelRenderContext context)
    {
        this.worldRenderContext = context;

        /* 26.2: kein enableDepthTest mehr noetig — jede Pipeline bringt ihren Tiefentest mit. */

        Pair<String, Boolean> picked = this.getBone();

        ModelIKDebug.setSelection(picked == null ? null : picked.a);

        if (!this.isRecording())
        {
            ModelIKDebug.renderEditor(this, context);
        }

        if (this.editorController != null)
        {
            this.editorController.render(context);

            int povMode = this.panel.getController().getPovMode();

            if (povMode != UIFilmController.CAMERA_MODE_CAMERA && BBSSettings.recordingCameraPreview.get())
            {
                Recorder.renderCameraPreview(this.panel.getRunner().getPosition(), context.levelState().cameraRenderState, context.poseStack(), context.submitNodeCollector());
            }
        }

        this.renderOrbitCenterMarker(context);

        ValueMotionPath motionPath = this.getMotionPath();

        if (motionPath.enabled.get() && !this.isRecording())
        {
            boolean pinned = this.isMotionPathPinned();
            Replay replay = pinned ? this.pinnedReplay : this.getReplay();
            Pair<String, Boolean> bone = pinned ? this.pinnedBone : this.getBone();

            MotionPath.render(context, motionPath, this, replay, bone, replay == null ? 0F : replay.getTick(this.getTick()));
        }

        MouseHandler mouse = Minecraft.getInstance().mouseHandler;
        int x = (int) mouse.xpos();
        int y = (int) mouse.ypos();

        ControlMouse.steer(this, x, y, this.lastMouse.x, this.lastMouse.y);

        this.lastMouse.set(x, y);
    }

    private void renderOrbitCenterMarker(LevelRenderContext context)
    {
        if (this.getPovMode() != CAMERA_MODE_ORBIT || !BBSSettings.editorOrbitCenterMarker.get())
        {
            return;
        }

        Vector3d center = this.orbit.getOrbitCenter(this.getCurrentTransition());

        if (center == null)
        {
            return;
        }

        CameraRenderState camera = context.levelState().cameraRenderState;
        double x = center.x - camera.pos.x;
        double y = center.y - camera.pos.y;
        double z = center.z - camera.pos.z;
        float distanceScale = BBSSettings.getAxesDistanceScale((float) Math.sqrt(x * x + y * y + z * z));
        PoseStack stack = context.poseStack();

        stack.pushPose();
        stack.translate(x, y, z);
        stack.scale(distanceScale, distanceScale, distanceScale);
        Draw.coolerAxes(stack, 0.12F, 0.007F);
        stack.popPose();

        /* Stellte den Tiefentest wieder her, den Draw.coolerAxes global abschaltete, um die
         * Achsen durch die Geometrie zu zeigen. In 26.2 leckt dieser Zustand nicht mehr aus
         * dem Helfer heraus — er sitzt in dessen Pipeline —, also gibt es nichts
         * wiederherzustellen. */
    }

    private float getCurrentTransition()
    {
        UIContext context = this.getContext();

        return context == null ? 0F : context.getTransition();
    }

    public Pair<String, Boolean> getBone()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null ? keyframeEditor.getBone() : null;
    }

    /** The space the bone gizmo should be drawn in (active transform's space). */
    public TransformSpace getBoneSpace()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null ? keyframeEditor.getBoneSpace() : TransformSpace.LOCAL;
    }

    /** The film camera's world&rarr;camera rotation, for reorienting the gizmo into a space. */
    public Matrix4f getGizmoView()
    {
        return this.panel.getCamera().view;
    }

    /** Whether the selected keyframe is the form's anchor track, so its transform gets a gizmo. */
    public boolean isAnchorGizmo()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null && keyframeEditor.isFormAnchorTrack();
    }

    public boolean getAnchorLocal()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null && keyframeEditor.getAnchorLocal();
    }

    /**
     * Whether the preview gizmo is actually drawn right now — the same gate the
     * renderer uses ({@link BaseFilmController#render}): axes enabled, not
     * recording, and a bone selected. The gizmo interaction must honour it, or
     * its trackball sphere keeps grabbing clicks (and blocking actor markers)
     * after a keyframe is deselected and nothing is rendered.
     */
    private boolean canShowGizmo()
    {
        /* Control-Rig: das Rig IST der Griff — Blender zeigt im Pose-Mode kein Gizmo. */
        if (this.getBone() != null && mchorse.bbs_mod.cubic.ik.RigCtrl.aktivFuer(this.getCurrentEntity()))
        {
            return false;
        }

        return UIBaseMenu.shouldRenderAxes() && !this.isRecording() && (this.getBone() != null || this.isAnchorGizmo());
    }

    /** Liegt die Maus auf einem Control-WIDGET (Kennung ab widgetBasis), nicht auf dem Koerper? */
    public boolean istCtrlGriff()
    {
        return this.stencil.hasPicked() && this.stencilMap.widgetBasis > 0
            && this.stencil.getIndex() >= this.stencilMap.widgetBasis
            && mchorse.bbs_mod.cubic.ik.RigCtrl.aktivFuer(this.getCurrentEntity());
    }

    /**
     * Der Bone, an dem das GIZMO haengt — bei aktivem Control-Rig null: sonst stuende
     * sein UNSICHTBARES Pick-Stencil ueber dem Control und fraesse jeden Klick
     * (gemessen: pick="screen" auf der Fussbox).
     */
    private Pair<String, Boolean> gizmoBone()
    {
        return mchorse.bbs_mod.cubic.ik.RigCtrl.aktivFuer(this.getCurrentEntity()) ? null : this.getBone();
    }

    private void renderStencil(LevelRenderContext renderContext, UIContext context, boolean altPressed)
    {
        Area viewport = this.panel.preview.getViewport();
        boolean drin = viewport.isInside(context);

        /* ⚗️ Zaehler an JEDER Abzweigung. Der Kennungs-Durchgang lief nur 4x auf 122 Bilder,
         * und ohne diese Trennung ist nicht zu sagen, an welchem der drei Ausgaenge er
         * haengenbleibt. Standardmaessig aus, siehe Messpunkt. */
        if (this.controlled != null || (!drin && !StencilAnzeige.ohneMaus(this.stencil)))
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.tor-zu",
                "controlled=" + (this.controlled != null) + " drin=" + drin);
            this.stencil.clearPicking();

            return;
        }

        IEntity entity = this.getCurrentEntity();

        if (entity == null && !altPressed)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.kein-entity");

            return;
        }

        mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.gerendert",
            "altPressed=" + altPressed);

        this.ensureStencilFramebuffer();

        /* Match the visual gizmo's on-screen size compensation (see
         * Gizmo#setViewportScale) so the pick handles line up with what is drawn. */
        Gizmo.INSTANCE.setViewportScale(context.menu.height / (float) viewport.h);

        boolean isPlaying = this.isPlaying();
        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();

        this.stencilMap.setup();
        this.stencil.apply();

        if (altPressed)
        {
            List<Replay> replays = this.panel.getData().replays.getList();
            int selectedReplayIndex = this.getCurrentReplayIndex();
            Pair<String, Boolean> bone = this.gizmoBone();

            for (Map.Entry<Integer, IEntity> entry : this.getEntities().entrySet())
            {
                Replay replay = CollectionUtils.getSafe(replays, entry.getKey());

                if (replay == null)
                {
                    continue;
                }

                FilmControllerContext filmContext = FilmControllerContext.instance
                    .setup(this.getEntities(), entry.getValue(), replay, renderContext)
                    /* 26.2: tickCounter() ist am Weltkontext entfallen — der DeltaTracker
                     * haengt am Client, derselbe Zaehler. */
                    .transition(isPlaying ? Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0)
                    .stencil(this.stencilMap)
                    .relative(replay.relative.get());

                if (entry.getKey() == selectedReplayIndex)
                {
                    this.stencilMap.objectIndex = replays.size() + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(true);

                    filmContext
                        .bone(bone == null ? null : bone.a, bone != null && bone.b)
                        .gizmoSpace(this.getBoneSpace(), this.getGizmoView())
                        .anchorGizmo(this.isAnchorGizmo(), this.getAnchorLocal());
                }
                else
                {
                    this.stencilMap.objectIndex = entry.getKey() + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(false);
                }

                this.editorController.renderStencilEntity(filmContext, entry.getValue());
            }
        }
        else
        {
            Replay replay = this.panel.replayEditor.getReplay();
            Pair<String, Boolean> bone = this.gizmoBone();

            this.stencilMap.setIncrement(true);

            this.editorController.renderStencilEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, renderContext)
                .transition(isPlaying ? Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0)
                .stencil(this.stencilMap)
                .relative(replay.relative.get())
                .bone(bone == null ? null : bone.a, bone != null && bone.b)
                .gizmoSpace(this.getBoneSpace(), this.getGizmoView())
                .anchorGizmo(this.isAnchorGizmo(), this.getAnchorLocal()), entity);
        }

        /* Ohne Maus im Fenster gibt es nichts zu picken — der Puffer wurde nur fuer den Umriss
         * gefuellt. Gelesen wird trotzdem nicht: die Mauskoordinaten zeigen dann irgendwohin. */
        if (drin)
        {
            int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
            int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);
            int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) viewport.w);

            this.stencil.pickAsync(x, y, radius, Gizmo.STENCIL_MAX);
        }

        this.stencil.unbind(this.stencilMap);

        /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
         * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
         * zeichnen will, gibt das Ziel dort an. */
    }

    private void ensureStencilFramebuffer()
    {
        StencilPuffergroesse.sicherstellen(this.stencil);
    }
}
