package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.properties.AnchorProperty;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.mixin.client.MinecraftClientInvoker;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIRecordOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.math.Interpolations;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class UIFilmController extends UIElement
{
    public final UIFilmPanel panel;

    public final List<IEntity> entities = new ArrayList<>();

    /* Character control */
    private IEntity controlled;
    private final Vector2i lastMouse = new Vector2i();
    private int mouseMode;
    private final Vector2f mouseStick = new Vector2f();

    /* Recording state */
    private IEntity previousEntity;
    private Form playerForm;
    private int recordingTick;
    private boolean recording;
    private int recordingCountdown;
    private List<String> recordingGroups;
    private BaseType recordingOld;

    /* Replay and group picking */
    private IEntity hoveredEntity;
    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();

    public final OrbitFilmCameraController orbit = new OrbitFilmCameraController(this);
    private int pov;

    public UIFilmController(UIFilmPanel panel)
    {
        this.panel = panel;

        IKey category = UIKeys.FILM_CONTROLLER_KEYS_CATEGORY;

        Supplier<Boolean> hasActor = () -> this.getCurrentEntity() != null;

        this.keys().register(Keys.FILM_CONTROLLER_START_RECORDING, this::pickRecording).active(hasActor).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_INSERT_FRAME, this::insertFrame).active(hasActor).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ORBIT, this::toggleOrbit).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_CONTROL, this::toggleControl).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ORBIT_MODE, () -> this.setPov(this.pov + 1)).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_MOVE_REPLAY_TO_CURSOR, () ->
        {
            Area area = this.panel.getFramebufferViewport();
            UIContext context = this.getContext();
            World world = MinecraftClient.getInstance().world;
            Camera camera = this.panel.getCamera();

            HitResult result = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(camera.position),
                RayTracing.fromVector3f(camera.getMouseDirection(context.mouseX, context.mouseY, area.x, area.y, area.w, area.h)),
                64F
            );

            if (result.getType() == HitResult.Type.BLOCK)
            {
                this.panel.replays.moveReplay(result.getPos().x, result.getPos().y, result.getPos().z);
            }
        }).active(hasActor).category(category);

        this.noCulling();
    }

    private void toggleMousePointer(boolean disable)
    {
        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();

        if (disable)
        {
            InputUtil.setCursorParameters(window.getHandle(), GLFW.GLFW_CURSOR_DISABLED, window.getWidth() / 2, window.getHeight() / 2);
        }
        else
        {
            InputUtil.setCursorParameters(window.getHandle(), GLFW.GLFW_CURSOR_NORMAL, window.getWidth() / 2, window.getHeight() / 2);
        }
    }

    private int getTick()
    {
        return this.panel.getRunner().ticks;
    }

    private Replay getReplay()
    {
        return this.panel.replays.replays.getCurrentFirst();
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public IEntity getCurrentEntity()
    {
        int index = this.panel.replays.replays.getIndex();

        if (CollectionUtils.inRange(this.entities, index))
        {
            return this.entities.get(index);
        }

        return null;
    }

    private int getPovMode()
    {
        return this.pov % 4;
    }

    public void setPov(int pov)
    {
        this.pov = pov;
    }

    private int getMouseMode()
    {
        return this.mouseMode % 6;
    }

    private void setMouseMode(int mode)
    {
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

    private boolean isMouseLookMode()
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

        UIContext context = this.panel.dashboard.context;

        this.entities.clear();

        Film film = this.panel.getData();

        if (context != null && film != null)
        {
            for (Replay replay : film.replays.getList())
            {
                World world = MinecraftClient.getInstance().world;
                IEntity entity = new StubEntity(world);

                entity.setForm(FormUtils.copy(replay.form.get()));
                replay.applyFrame(this.getTick(), entity);
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());

                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevPitch(entity.getPitch());
                entity.setPrevBodyYaw(entity.getBodyYaw());

                this.entities.add(entity);
            }
        }
    }

    /* Character control state */

    public void toggleControl()
    {
        if (this.controlled != null)
        {
            if (this.previousEntity != null)
            {
                this.controlled.setForm(this.playerForm);

                this.entities.set(this.entities.indexOf(this.controlled), this.previousEntity);
                this.previousEntity = null;
            }

            this.controlled = null;
        }
        else if (this.panel.replays.replays.isSelected())
        {
            this.controlled = this.getCurrentEntity();

            if (this.controlled != null)
            {
                MCEntity player = Morph.getMorph(MinecraftClient.getInstance().player).entity;

                this.playerForm = player.getForm();
                this.previousEntity = this.controlled;

                player.copy(this.controlled);
                this.entities.set(this.entities.indexOf(this.controlled), player);

                this.controlled = player;
            }
        }

        this.toggleMousePointer(this.controlled != null);

        if (this.controlled == null && this.recording)
        {
            this.stopRecording();
        }
    }

    private boolean canControl()
    {
        UIContext context = this.getContext();

        return this.controlled != null && context != null && !UIOverlay.has(context);
    }

    /* Recording */

    public boolean isRecording()
    {
        return this.recording;
    }

    public void startRecording(List<String> groups)
    {
        this.recordingTick = this.getTick();
        this.recording = true;
        this.recordingCountdown = 30;
        this.recordingGroups = groups;

        this.recordingOld = this.getReplay().keyframes.toData();

        if (groups != null)
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
        }

        if (this.controlled == null)
        {
            this.toggleControl();
        }

        this.toggleMousePointer(this.controlled != null);
    }

    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        this.recording = false;
        this.recordingGroups = null;

        if (this.controlled != null)
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
            for (BaseValue value : replay.keyframes.getAll())
            {
                if (value instanceof KeyframeChannel)
                {
                    ((KeyframeChannel) value).simplify();
                }
            }

            BaseType newData = replay.keyframes.toData();

            replay.keyframes.fromData(this.recordingOld);
            replay.keyframes.preNotifyParent();
            replay.keyframes.fromData(newData);
            replay.keyframes.postNotifyParent();

            this.recordingOld = null;
        }

        this.setMouseMode(0);
    }

    /* Input handling */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.canControl())
        {
            InputUtil.Key utilKey = InputUtil.Type.MOUSE.createFromCode(context.mouseButton);

            if (this.canControlWithKeyboard(utilKey))
            {
                KeyBinding.setKeyPressed(utilKey, true);
                KeyBinding.onKeyPressed(utilKey);
            }

            return true;
        }

        if (context.mouseButton == 0)
        {
            if (this.hoveredEntity != null)
            {
                int index = this.entities.indexOf(this.hoveredEntity);

                this.panel.replays.setReplay(this.panel.getData().replays.getList().get(index));

                if (!this.panel.replays.isVisible())
                {
                    this.panel.showPanel(this.panel.replays);
                }

                return true;
            }
        }
        else if (context.mouseButton == 2)
        {
            Area area = this.panel.getFramebufferViewport();

            if (area.isInside(context) && this.orbit.enabled)
            {
                this.orbit.start(context);

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = this.panel.getFramebufferViewport();

        if (area.isInside(context) && this.orbit.enabled)
        {
            this.orbit.handleDistance(context);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.canControl())
        {
            InputUtil.Key utilKey = InputUtil.Type.MOUSE.createFromCode(context.mouseButton);

            if (this.canControlWithKeyboard(utilKey))
            {
                KeyBinding.setKeyPressed(utilKey, false);
            }

            return true;
        }

        this.orbit.stop();

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.canControl())
        {
            int key = context.getKeyCode();

            if (context.getKeyAction() == KeyAction.PRESSED && context.getKeyCode() >= GLFW.GLFW_KEY_1 && context.getKeyCode() <= GLFW.GLFW_KEY_6)
            {
                /* Switch mouse input mode */
                this.setMouseMode(context.getKeyCode() - GLFW.GLFW_KEY_1);

                return true;
            }

            InputUtil.Key utilKey = InputUtil.fromKeyCode(context.getKeyCode(), context.getScanCode());

            if (this.canControlWithKeyboard(utilKey))
            {
                if (context.getKeyAction() == KeyAction.RELEASED)
                {
                    KeyBinding.setKeyPressed(utilKey, false);
                }
                else
                {
                    KeyBinding.setKeyPressed(utilKey, true);
                    KeyBinding.onKeyPressed(utilKey);
                }

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    private boolean canControlWithKeyboard(InputUtil.Key utilKey)
    {
        GameOptions options = MinecraftClient.getInstance().options;

        return options.forwardKey.getDefaultKey() == utilKey
            || options.backKey.getDefaultKey() == utilKey
            || options.leftKey.getDefaultKey() == utilKey
            || options.rightKey.getDefaultKey() == utilKey
            || options.attackKey.getDefaultKey() == utilKey
            || options.useKey.getDefaultKey() == utilKey
            || options.sneakKey.getDefaultKey() == utilKey
            || options.sprintKey.getDefaultKey() == utilKey
            || options.dropKey.getDefaultKey() == utilKey
            || options.jumpKey.getDefaultKey() == utilKey;
    }

    public void pickRecording()
    {
        if (this.recording)
        {
            this.stopRecording();

            return;
        }

        this.toggleMousePointer(false);

        UIOverlay.addOverlay(this.getContext(), new UIRecordOverlayPanel(
            UIKeys.FILM_CONTROLLER_RECORD_TITLE,
            UIKeys.FILM_CONTROLLER_RECORD_DESCRIPTION,
            this::startRecording
        ));
    }

    private void toggleOrbit()
    {
        this.orbit.enabled = !this.orbit.enabled;
    }

    public void handleCamera(Camera camera, float transition)
    {
        if (this.orbit.enabled)
        {
            int mode = this.getPovMode();

            if (mode == 0)
            {
                this.orbit.setup(camera, transition);
            }
            else
            {
                this.handleFirstThirdPerson(camera, transition, mode);
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
        float distance = this.orbit.getDistance();
        boolean back = mode == 2;

        position.set(controller.getPrevX(), controller.getPrevY(), controller.getPrevZ());
        position.lerp(new Vector3d(controller.getX(), controller.getY(), controller.getZ()), transition);
        position.y += controller.getEyeHeight();

        rotation.set(controller.getPrevPitch(), controller.getPrevHeadYaw(), 0);
        rotation.lerp(new Vector3f(controller.getPitch(), controller.getHeadYaw(), 0), transition);

        rotation.x = MathUtils.toRad(rotation.x);
        rotation.y = MathUtils.toRad(rotation.y);

        camera.fov = BBSSettings.getFov();

        if (mode == 1)
        {
            camera.position.set(position);
            camera.rotation.set(rotation.x, rotation.y, 0F);

            return;
        }

        Vector3f rotate = Matrices.rotation(rotation.x * (back ? 1 : -1), (back ? 0F : MathUtils.PI) - rotation.y);
        World world = MinecraftClient.getInstance().world;

        HitResult result = RayTracing.rayTraceEntity(
            world,
            RayTracing.fromVector3d(position),
            RayTracing.fromVector3f(rotate),
            distance
        );

        if (result.getType() == HitResult.Type.BLOCK)
        {
            distance = (float) position.distance(result.getPos().x, result.getPos().y, result.getPos().z) - 0.1F;
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
            else if (this.mouseMode == 5) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA1);

            final List<String> groups = chosenGroups;

            BaseValue.edit(replay.keyframes, (keyframes) ->
            {
                keyframes.record(this.getTick(), this.getCurrentEntity(), groups);
            });

            UIUtils.playClick();
        }
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
        UIContext context = this.getContext();

        this.handleRecording(runner);
        this.updateEntities(film, runner, context);

        if (this.canControl())
        {
            this.updateControls();
        }

        if (this.canControl())
        {
            MinecraftClientInvoker mc = (MinecraftClientInvoker) MinecraftClient.getInstance();
            int attackCooldown = mc.bbs$getAttackCooldown();

            mc.bbs$handleInputEvents();
            mc.bbs$handleBlockBreaking(MinecraftClient.getInstance().options.attackKey.isPressed());

            if (attackCooldown > 0)
            {
                mc.bbs$setAttackCooldown(attackCooldown - 1);
            }
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

            if (this.recordingCountdown <= 0 && !runner.isRunning())
            {
                this.stopRecording();
            }
        }
    }

    private void updateEntities(Film film, RunnerCameraController runner, UIContext context)
    {
        for (int i = 0; i < this.entities.size(); i++)
        {
            IEntity entity = this.entities.get(i);

            if (context == null || !UIOverlay.has(context))
            {
                if (!(entity instanceof MCEntity))
                {
                    entity.update();

                    if (entity.getForm() != null)
                    {
                        entity.getForm().update(entity);
                    }
                }
            }

            List<Replay> replays = film.replays.getList();

            if (CollectionUtils.inRange(replays, i))
            {
                Replay replay = replays.get(i);
                int ticks = runner.ticks;

                if (entity != this.controlled || (this.recording && this.recordingCountdown <= 0 && this.recordingGroups != null))
                {
                    replay.applyFrame(ticks, entity, entity == this.controlled ? this.recordingGroups : null);
                }

                if (entity == this.controlled && this.recording && runner.isRunning())
                {
                    replay.keyframes.record(ticks, entity, this.recordingGroups);
                }

                replay.applyProperties(ticks, entity.getForm(), runner.isRunning());
            }
        }
    }

    private void updateControls()
    {
        IEntity controller = this.controlled;

        if (!this.isMouseLookMode())
        {
            int index = this.getMouseMode() - 1;

            controller.getExtraVariables()[index * 2] = this.mouseStick.y;
            controller.getExtraVariables()[index * 2 + 1] = this.mouseStick.x;
        }
    }

    /* Render */

    public void renderHUD(UIContext context, Area area)
    {
        int mode = this.getMouseMode();

        if (this.controlled != null)
        {
            FontRenderer font = context.batcher.getFont();

            /* Render helpful guides for sticks and triggers controls */
            if (mode > 0)
            {
                String label = "Left stick";

                if (mode == 2)
                {
                    label = "Right stick";
                }
                else if (mode == 3)
                {
                    label = "Triggers";
                }
                else if (mode == 4)
                {
                    label = "Extra 1";
                }
                else if (mode == 5)
                {
                    label = "Extra 2";
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

            /* Render reording overlay */
            if (this.recording)
            {
                int x = area.x + 5 + 16;
                int y = area.y + 5;

                context.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y, 1F, 0F);

                if (this.recordingCountdown <= 0)
                {
                    context.batcher.textCard(this.getTick() + " ticks", x + 3, y + 4, Colors.WHITE, Colors.A50);
                }
                else
                {
                    context.batcher.textCard(String.valueOf(this.recordingCountdown / 20F), x + 3, y + 4, Colors.WHITE, Colors.A50);
                }
            }

            context.batcher.outlinedIcon(Icons.POSE, area.ex() - 5, area.y + 5, 1F, 0F);
        }

        this.renderPickingPreview(context, area);

        this.orbit.handleOrbiting(context);
    }

    private void renderPickingPreview(UIContext context, Area area)
    {
        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        ShaderProgram previewProgram = BBSShaders.getPickerPreviewProgram();
        GlUniform target = previewProgram.getUniform("Target");

        if (target != null)
        {
            target.set(index);
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, h, w, 0, w, h);

        if (pair != null)
        {
            String label = pair.a.getIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    public void renderFrame(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (IEntity entity : this.entities)
        {
            if (this.getPovMode() == 1 && entity == getCurrentEntity() && this.orbit.enabled)
            {
                continue;
            }

            this.renderEntity(context, entity, null);
        }

        this.rayTraceEntity(context);
        this.renderStencil(context, this.getContext());

        Mouse mouse = MinecraftClient.getInstance().mouse;
        int x = (int) mouse.getX();
        int y = (int) mouse.getY();

        if (this.canControl())
        {
            if (this.isMouseLookMode())
            {
                MinecraftClient.getInstance().player.changeLookDirection((x - this.lastMouse.x) / 2F, (y - this.lastMouse.y) / 2F);
            }
            else
            {
                /* Control sticks and triggers variables */
                float sensitivity = 100F;

                float xx = (y - this.lastMouse.y) / sensitivity;
                float yy = (x - this.lastMouse.x) / sensitivity;

                this.mouseStick.add(xx, yy);
                this.mouseStick.x = MathUtils.clamp(this.mouseStick.x, -1F, 1F);
                this.mouseStick.y = MathUtils.clamp(this.mouseStick.y, -1F, 1F);
            }
        }

        this.lastMouse.set(x, y);

        RenderSystem.disableDepthTest();
    }

    private void renderEntity(WorldRenderContext context, IEntity entity, StencilMap map)
    {
        Form form = entity.getForm();

        if (form != null)
        {
            Vector3d position = Vectors.TEMP_3D.set(entity.getPrevX(), entity.getPrevY(), entity.getPrevZ())
                .lerp(new Vector3d(entity.getX(), entity.getY(), entity.getZ()), context.tickDelta());
            int light = WorldRenderer.getLightmapCoordinates(entity.getWorld(), new BlockPos((int) position.x, (int) (position.y + 0.5D), (int) position.z));

            FormRenderingContext formContext = FormRenderingContext
                .set(entity, context.matrixStack(), light, context.tickDelta())
                .camera(context.camera())
                .stencilMap(map);

            AnchorProperty.Anchor value = form.anchor.get();
            AnchorProperty.Anchor last = form.anchor.getLast();

            if (value != null && last != null)
            {
                Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, context.camera(), context.tickDelta());
                Matrix4f matrix = this.getEntityMatrix(context, value, defaultMatrix);
                Matrix4f lastMatrix = this.getEntityMatrix(context, last, defaultMatrix);

                if (matrix != null && lastMatrix != null && matrix != lastMatrix)
                {
                    float factor = form.anchor.getTweenFactorInterpolated(context.tickDelta());

                    context.matrixStack().push();
                    MatrixStackUtils.multiply(context.matrixStack(), Matrices.lerp(lastMatrix, matrix, factor));
                    FormUtilsClient.render(form, formContext);

                    context.matrixStack().pop();

                    return;
                }
            }

            context.matrixStack().push();
            MatrixStackUtils.multiply(context.matrixStack(), getMatrixForRenderWithRotation(entity, context.camera(), context.tickDelta()));
            FormUtilsClient.render(form, formContext);

            context.matrixStack().pop();
        }
    }

    private Matrix4f getEntityMatrix(WorldRenderContext context, AnchorProperty.Anchor selector, Matrix4f defaultMatrix)
    {
        int entityIndex = selector.actor;

        if (CollectionUtils.inRange(this.entities, entityIndex))
        {
            IEntity entity = this.entities.get(entityIndex);
            Matrix4f basic = new Matrix4f(getMatrixForRenderWithRotation(entity, context.camera(), context.tickDelta()));

            Map<String, Matrix4f> map = new HashMap<>();
            MatrixStack stack = new MatrixStack();

            Form form = entity.getForm();

            if (form != null)
            {
                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, map, "", context.tickDelta());

                Matrix4f matrix = map.get(selector.attachment);

                if (matrix != null)
                {
                    basic.mul(matrix);
                }
            }

            return basic;
        }

        return defaultMatrix;
    }

    private Matrix4f getMatrixForRenderWithRotation(IEntity entity, net.minecraft.client.render.Camera camera, float tickDelta)
    {
        double x = Interpolations.lerp(entity.getPrevX(), entity.getX(), tickDelta) - camera.getPos().x;
        double y = Interpolations.lerp(entity.getPrevY(), entity.getY(), tickDelta) - camera.getPos().y;
        double z = Interpolations.lerp(entity.getPrevZ(), entity.getZ(), tickDelta) - camera.getPos().z;

        Matrix4f matrix = new Matrix4f();

        float bodyYaw = Interpolations.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        matrix.translate((float) x, (float) y, (float) z);
        matrix.rotateY(MathUtils.toRad(-bodyYaw));

        return matrix;
    }

    private void rayTraceEntity(WorldRenderContext context)
    {
        this.hoveredEntity = null;

        if (!Window.isAltPressed() || this.panel.recorder.isRecording())
        {
            return;
        }

        UIContext c = this.getContext();
        Area area = this.panel.getFramebufferViewport();

        if (!area.isInside(c))
        {
            return;
        }

        List<IEntity> entities = new ArrayList<>();
        Camera camera = this.panel.getCamera();
        Vector3f mouseDirection = camera.getMouseDirection(c.mouseX, c.mouseY, area.x, area.y, area.w, area.h);

        for (IEntity entity : this.entities)
        {
            AABB aabb = entity.getPickingHitbox();

            if (aabb.intersectsRay(camera.position, mouseDirection))
            {
                entities.add(entity);
            }
        }

        if (!entities.isEmpty())
        {
            entities.sort((a, b) ->
            {
                double distanceA = new Vector3d(a.getX(), a.getY(), a.getZ()).distance(camera.position);
                double distanceB = new Vector3d(b.getX(), b.getY(), b.getZ()).distance(camera.position);

                return (int) (distanceA - distanceB);
            });

            this.hoveredEntity = entities.get(0);
        }

        if (this.hoveredEntity != null)
        {
            AABB aabb = this.hoveredEntity.getPickingHitbox();

            context.matrixStack().push();
            context.matrixStack().translate(aabb.x - camera.position.x, aabb.y - camera.position.y, aabb.z - camera.position.z);

            Draw.renderBox(context.matrixStack(), 0D, 0D, 0D, aabb.w, aabb.h, aabb.d, 0F, 0.5F, 1F);

            context.matrixStack().pop();
        }
    }

    private void renderStencil(WorldRenderContext renderContext, UIContext context)
    {
        Area viewport = this.panel.getFramebufferViewport();

        if (!viewport.isInside(context) || this.controlled != null)
        {
            this.stencil.clearPicking();

            return;
        }

        IEntity entity = this.getCurrentEntity();

        if (entity == null)
        {
            return;
        }

        this.ensureStencilFramebuffer();

        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();

        this.stencilMap.setup();
        this.stencil.apply();
        this.renderEntity(renderContext, entity, this.stencilMap);

        int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
        int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);

        this.stencil.pick(x, y);
        this.stencil.unbind(this.stencilMap);

        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
    }

    private void ensureStencilFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_film"));

        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
        int w = BBSSettings.videoWidth.get();
        int h = BBSSettings.videoHeight.get();

        if (mainTexture.width != w || mainTexture.height != h)
        {
            this.stencil.resizeGUI(w, h);
        }
    }
}