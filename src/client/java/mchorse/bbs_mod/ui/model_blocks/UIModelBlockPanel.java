package mchorse.bbs_mod.ui.model_blocks;

import net.minecraft.client.renderer.state.level.CameraRenderState;
/* 26.2: com.mojang.blaze3d.shaders.Uniform ist ersatzlos entfallen -- es gibt keine
 * Einzel-Uniforms mehr, nur noch Uniform-BLOECKE, die ein Zeichendurchgang bindet. Der Typ
 * wird in dieser Datei nicht mehr benutzt; nur der Import stand noch. */
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.forms.UIToggleEditorEvent;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.model_blocks.camera.ImmersiveModelBlockCameraController;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UIModelBlockPanel extends UIDashboardPanel implements IFlightSupported, GizmoViewport
{
    public static boolean toggleRendering;

    public UIScrollView scrollView;
    private ModelBlockKoerper koerper;
    final ModelBlockVerlauf verlauf = new ModelBlockVerlauf(this);
    public UIElement editor;
    public UIModelBlockEntityList modelBlocks;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UIToggle shadow;
    public UIToggle global;
    public UIToggle lookAt;
    public UIPropTransform transform;

    private final StencilFormFramebuffer gizmoStencil = new StencilFormFramebuffer();
    private final StencilMap gizmoStencilMap = new StencilMap();
    private final GizmoInteraction gizmo = new GizmoInteraction(this);
    private final mchorse.bbs_mod.camera.Camera gizmoCamera = new mchorse.bbs_mod.camera.Camera();
    private final Matrix4f gizmoProjection = new Matrix4f();

    private ModelBlockEntity modelBlock;
    private ModelBlockEntity hovered;
    private Vector3f mouseDirection = new Vector3f();

    private Set<ModelBlockEntity> toSave = new HashSet<>();

    private ImmersiveModelBlockCameraController cameraController;
    private UIElement keyDude;

    public UIModelBlockPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.keyDude = new UIElement().noCulling();
        this.keyDude.keys().register(Keys.MODEL_BLOCKS_MOVE_TO, () ->
        {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = mc.gameRenderer.mainCamera();
            /* 26.2: das oeffentliche Feld Camera.pos ist zur Methode position() geworden --
             * gleicher Wert (Vec3), nur nicht mehr direkt lesbar. ⚠️ Nicht zu verwechseln mit
             * CameraRenderState.pos, das es als FELD weiterhin gibt (siehe renderInWorld). */
            BlockHitResult blockHitResult = RayTracing.rayTrace(mc.level, camera.position(), RayTracing.fromVector3f(this.mouseDirection), 512F);

            if (blockHitResult.getType() != HitResult.Type.MISS)
            {
                Vec3 hit = blockHitResult.getLocation();
                BlockPos pos = this.modelBlock.getBlockPos();

                this.verlauf.aendern(null, () -> this.modelBlock.getProperties().getTransform().translate.set(hit.x - pos.getX() - 0.5F, hit.y - pos.getY(), hit.z - pos.getZ() - 0.5F));
                this.fillData();
            }
        }).active(() -> this.modelBlock != null);

        this.modelBlocks = new UIModelBlockEntityList((l) -> this.fill(l.get(0), false));
        this.modelBlocks.context((menu) ->
        {
            if (this.modelBlock != null) menu.action(UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, this::teleport);
        });
        this.modelBlocks.background();
        this.modelBlocks.h(UIStringList.DEFAULT_HEIGHT * 9);

        this.pickEdit = new UINestedEdit((editing) ->
        {
            UIFormPalette palette = UIFormPalette.open(this, editing, this.modelBlock.getProperties().getForm(), (f) ->
            {
                this.pickEdit.setForm(f);

                if (this.modelBlock != null) this.verlauf.aendern(null, () -> this.modelBlock.getProperties().setForm(f));
            });

            palette.immersive();
            palette.editor.keys().register(Keys.MODEL_BLOCKS_TOGGLE_RENDERING, () -> toggleRendering = !toggleRendering);
            palette.editor.renderer.full(dashboard.getRoot());
            palette.editor.renderer.setTarget(this.modelBlock.getEntity());
            palette.editor.renderer.setRenderForm(() -> !toggleRendering);
            palette.getEvents().register(UIToggleEditorEvent.class, (e) ->
            {
                if (e.editing)
                {
                    this.addCameraController(palette);
                }
                else
                {
                    this.removeCameraController();
                }
            });
            palette.getEvents().register(UIRemovedEvent.class, (e) ->
            {
                this.scrollView.setVisible(true);
            });

            palette.resize();

            if (editing)
            {
                this.addCameraController(palette);
            }

            this.scrollView.setVisible(false);
        });
        this.pickEdit.keybinds();

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.verlauf.aendern(null, () -> this.modelBlock.getProperties().setEnabled(b.getValue())));
        this.shadow = new UIToggle(UIKeys.MODEL_BLOCKS_SHADOW, (b) -> this.verlauf.aendern(null, () -> this.modelBlock.getProperties().setShadow(b.getValue())));
        this.global = new UIToggle(UIKeys.MODEL_BLOCKS_GLOBAL, (b) ->
        {
            this.verlauf.aendern(null, () -> this.modelBlock.getProperties().setGlobal(b.getValue()));

            /* 26.2: allChanged() heisst jetzt invalidateCompiledGeometry(...) und bekommt die
             * vier Dinge, die es frueher selbst am Weltzeichner fand: Welt, Optionen, Kamera
             * und Blockfarben. Wirkung unveraendert -- Abschnitts-Uebersetzer neu bauen, alle
             * Abschnitte neu backen. Das braucht der Schalter, weil "global" bestimmt, ob der
             * Modellblock im Abschnittsnetz landet.
             * ⚠️ Die Welt kommt jetzt vom Aufrufer statt aus dem Weltzeichner, also muss der
             * Aufrufer sie pruefen -- die alte Methode tat das intern. */
            Minecraft mc = Minecraft.getInstance();

            if (mc.level != null)
            {
                mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
            }
        });
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, (b) -> this.verlauf.aendern(null, () -> this.modelBlock.getProperties().setLookAt(b.getValue())));

        this.transform = new UIPropTransform();
        this.transform.enableHotkeys();
        this.transform.hotkeyDrag(this::buildGizmoDrag);
        this.transform.callbacks(this.verlauf::transformAnfang, this.verlauf::transformEnde, this.verlauf.undo::markLastUndoNoMerging);

        this.editor = UI.column(this.pickEdit, this.enabled, this.shadow, this.global, this.lookAt, this.transform);

        this.scrollView = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, this.modelBlocks, this.editor, (this.koerper = new ModelBlockKoerper(this)).section, this.koerper.ausruestung.section);
        this.scrollView.scroll.opposite().cancelScrolling();
        this.scrollView.relative(this).w(200).h(1F);

        this.fill(null, false);

        this.keys().register(Keys.MODEL_BLOCKS_TELEPORT, this::teleport);

        this.add(this.scrollView, new mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable((ctx) -> mchorse.bbs_mod.ui.dashboard.panels.UIAuswahlLeer.zeichnen(ctx, this.area, this.modelBlock == null, mchorse.bbs_mod.ui.utils.icons.Icons.BLOCK, "No model block selected", "Pick one from the list on the left")), this.verlauf.tasten.full(this));
    }

    private void teleport()
    {
        if (this.modelBlock != null)
        {
            BlockPos pos = this.modelBlock.getBlockPos();

            PlayerUtils.teleport(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            UIUtils.playClick();
        }
    }

    @Override
    public boolean supportsRollFOVControl()
    {
        return false;
    }

    @Override
    public void appear()
    {
        super.appear();

        this.getContext().menu.main.add(this.keyDude);
        this.dashboard.orbitKeysUI.setEnabled(() -> this.getChildren(UIFormPalette.class).isEmpty());

        if (this.cameraController != null)
        {
            BBSModClient.getCameraController().add(this.cameraController);
        }
    }

    @Override
    public void disappear()
    {
        super.disappear();

        this.keyDude.removeFromParent();
        this.dashboard.orbitKeysUI.setEnabled(null);
        this.gizmo.stop();

        if (this.cameraController != null)
        {
            BBSModClient.getCameraController().remove(this.cameraController);
        }
    }

    public ModelBlockEntity getModelBlock()
    {
        return this.modelBlock;
    }

    /* Gizmo (editing the selected model block's transform in the world) */

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.gizmoStencil;
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.gizmoProjection;
    }

    /**
     * The screen region the gizmo actually renders into: the full UI viewport, NOT this panel's own
     * area. The dashboard shrinks a panel by the 20px taskbar ({@code h(1F, -20)}), but the block
     * gizmo is drawn straight onto Minecraft's full-screen world — so its on-screen projection,
     * trackball sphere highlight and sphere pick must map against the whole screen. Using the shorter
     * panel area squishes/shifts them by that 20px (a constant, camera-independent offset).
     */
    @Override
    public Area getGizmoArea()
    {
        UIContext context = this.getContext();

        return context != null ? context.menu.viewport : this.area;
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        if (this.modelBlock == null)
        {
            return false;
        }

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, this.transform, this.buildGizmoDrag());
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        /* The model block gizmo only renders its own handles into the stencil,
         * so the deferred sphere-vs-form pick never resolves to a form here. */
    }

    /**
     * Ray-drag context for the model block gizmo. Translation is one world unit
     * per local unit, so the Jacobian comes out as identity — but the rotation
     * handles still need {@link GizmoDrag#computeRotateAxes}: the transform's
     * Euler angles compose ({@code Rz·Ry·Rx·Rz2·Ry2·Rx2}), so {@code rotate.x/y/z}
     * do not turn about the world axes once the block is rotated. Sampling the
     * block's actual rotation matrix recovers the real per-component axes, which
     * is what keeps the arcball and trackball accurate at any orientation.
     */
    private GizmoDrag buildGizmoDrag()
    {
        if (this.modelBlock == null)
        {
            return null;
        }

        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.gizmoCamera, this.getGizmoArea());
        Transform transform = this.modelBlock.getProperties().getTransform();

        if (drag != null && transform != null)
        {
            BlockPos pos = this.modelBlock.getBlockPos();

            drag.setJacobian(GizmoDrag.computeTranslateJacobian(
                transform,
                () -> new Vector3f(
                    pos.getX() + 0.5F + transform.translate.x,
                    pos.getY() + transform.translate.y,
                    pos.getZ() + 0.5F + transform.translate.z
                )
            ));
            drag.setRotateAxes(GizmoDrag.computeRotateAxes(
                transform,
                () -> MatrixStackUtils.stripScale(transform.createMatrix())
            ));
        }

        return drag;
    }

    /**
     * Whether the interactive gizmo is shown for {@code entity} — the selected
     * block, with gizmos enabled and the form palette closed. Lets the block
     * renderer drop its plain axes in favour of the gizmo.
     */
    public boolean isShowingGizmo(ModelBlockEntity entity)
    {
        return this.modelBlock == entity && this.canShowGizmo();
    }

    private boolean canShowGizmo()
    {
        return this.modelBlock != null
            && BBSSettings.gizmos.get()
            && !UIBaseMenu.isHideGizmoHeld()
            && this.getChildren(UIFormPalette.class).isEmpty();
    }

    private void renderGizmo(LevelRenderContext context, Vec3 cameraPos)
    {
        if (!this.canShowGizmo())
        {
            return;
        }

        PoseStack stack = context.poseStack();

        /* Capture the on-screen camera frame for the drag math and the deferred UI
         * passes: the gizmo's visual and its pick stencil are both drawn later in
         * the UI pass (GizmoInteraction.renderGizmo / renderGizmoStencilInterface)
         * from this frame, so rendering, picking and dragging share one coordinate
         * frame. */
        /* 26.2: lesbar nur noch im eingefrorenen Kamerazustand, siehe UIFilmPanel. */
        this.gizmoProjection.set(context.levelState().cameraRenderState.projectionMatrix);
        this.gizmoCamera.projection.set(this.gizmoProjection);
        /* 1.21.1 carries the camera view rotation in positionMatrix(); the context's
         * MatrixStack base is just an identity stack for entity-relative rendering, so
         * reading the view from it would drop the camera angle and break picking/dragging.
         *
         * 26.2: positionMatrix() gibt es an Fabrics LevelRenderContext nicht mehr. Derselbe
         * Wert steht im eingefrorenen Kamerazustand als viewRotationMatrix -- genau die
         * Matrix, die Camera.getViewRotationMatrix liefert und die der Weltzeichner als
         * Positionsmatrix hereinbekommt. Wer stattdessen poseStack() liest, bekommt die
         * Einheitsmatrix und zielt an allem vorbei. */
        this.gizmoCamera.view.set(context.levelState().cameraRenderState.viewRotationMatrix);
        this.gizmoCamera.position.set(cameraPos.x, cameraPos.y, cameraPos.z);

        /* Record the model-view at the block's gizmo origin so the UI-pass visual
         * and stencil (both read Gizmo#lastRenderMatrix) draw at the right place. */
        stack.pushPose();
        this.applyGizmoOrigin(stack, cameraPos);
        /* A block's parent is the world; GIMBAL additionally needs its full channel rotation.
         * One capture feeds both the visual and the pick stencil. */
        TransformSpace space = this.transform.getSpace();

        if (space == TransformSpace.GIMBAL)
        {
            mchorse.bbs_mod.ui.utils.GizmoGimbal.merken(false, new Matrix4f(), new Matrix4f(this.modelBlock.getProperties().getTransform().createRotationMatrix()));
        }
        /* This gizmo edits the BLOCK's own transform, which composes straight
         * onto the world, so GLOBAL keeps meaning the plain world axes (null) —
         * turning the block must not turn the frame its own rotation is edited
         * in. The form INSIDE the block is a different story: it is drawn under
         * this transform, so its editor takes GLOBAL from the preview's scene
         * axes (UIModelRenderer#getSceneAxes) and follows the block. */
        Gizmo.INSTANCE.reorientForSpace(stack, space == TransformSpace.PARENT ? TransformSpace.GLOBAL : space, this.gizmoCamera.view, null);
        Gizmo.INSTANCE.captureVisual(stack);
        stack.popPose();
    }

    /**
     * Move the stack to where the gizmo handles are drawn: the block's
     * transformed origin (camera-relative). In global mode the handles stay
     * world-aligned (position only); in local mode they follow the block's
     * rotation, matching how the form editor's {@code getOrigin} behaves.
     * Caller owns the surrounding {@code push}/{@code pop}.
     */
    private void applyGizmoOrigin(PoseStack stack, Vec3 cameraPos)
    {
        BlockPos pos = this.modelBlock.getBlockPos();
        Transform transform = this.modelBlock.getProperties().getTransform();

        stack.translate(
            pos.getX() + 0.5D + transform.translate.x - cameraPos.x,
            pos.getY() + transform.translate.y - cameraPos.y,
            pos.getZ() + 0.5D + transform.translate.z - cameraPos.z
        );

        if (this.transform.isLocal())
        {
            MatrixStackUtils.multiply(stack, new Matrix4f().set(transform.createRotationMatrix()));
        }
    }

    /**
     * Render the gizmo handles into the picking framebuffer in the UI pass (the
     * new way) and read the handle under the cursor. Uses the same area /
     * projection / captured matrix as the visual ({@link Gizmo#renderInterface}),
     * so the picked pixel matches the drawn handle. The main framebuffer is handed
     * back afterwards so the rest of the UI keeps rendering normally.
     */
    private void renderGizmoStencilInterface(UIContext context)
    {
        if (!this.canShowGizmo())
        {
            this.gizmoStencil.clearPicking();

            return;
        }

        Minecraft mc = Minecraft.getInstance();

        this.gizmoStencil.setup(Link.bbs("stencil_model_block"));

        int w = mc.getWindow().getScreenWidth();
        int h = mc.getWindow().getScreenHeight();
        Texture texture = this.gizmoStencil.getFramebuffer().getMainTexture();

        if (texture.width != w || texture.height != h)
        {
            this.gizmoStencil.resize(w, h);
        }

        this.gizmoStencilMap.setup();

        /* Flush queued UI before binding the pick buffer, so pending batches go to
         * the screen and not into the stencil. */
        context.batcher.flush();
        this.gizmoStencil.apply();

        Gizmo.INSTANCE.renderStencilInterface(context, this.gizmoProjection, this.getGizmoArea(), this.gizmoStencilMap);

        this.gizmoStencil.pick((int) mc.mouseHandler.xpos(), (int) (h - mc.mouseHandler.ypos()), Math.round(BBSSettings.gizmoHoverTolerance.get() * BBSModClient.getGUIScale()), Gizmo.STENCIL_MAX);
        this.gizmoStencil.unbind(this.gizmoStencilMap);

        /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
         * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
         * zeichnen will, gibt das Ziel dort an. */
    }

    private void addCameraController(UIFormPalette palette)
    {
        if (this.cameraController == null)
        {
            this.cameraController = new ImmersiveModelBlockCameraController(palette.editor.renderer, this.modelBlock);

            BBSModClient.getCameraController().add(this.cameraController);

            Transform transform = this.modelBlock.getProperties().getTransform().copy();

            transform.translate.set(0F, 0F, 0F);
            palette.editor.renderer.setTransform(transform.createMatrix());
        }
    }

    private void removeCameraController()
    {
        if (this.cameraController != null)
        {
            BBSModClient.getCameraController().remove(this.cameraController);

            this.cameraController = null;
        }
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public void open()
    {
        super.open();

        this.updateList();

        if (this.modelBlock != null && this.modelBlock.isRemoved())
        {
            this.fill(null, true);
        }
    }

    @Override
    public void close()
    {
        super.close();

        this.gizmo.stop();
        this.removeCameraController();

        for (ModelBlockEntity entity : this.toSave)
        {
            this.save(entity);
        }

        this.toSave.clear();
    }

    private void updateList()
    {
        this.modelBlocks.clear();

        for (ModelBlockEntity modelBlock : BBSRendering.capturedModelBlocks)
        {
            this.modelBlocks.add(modelBlock);
        }

        this.fill(this.modelBlock, true);
    }

    public void fill(ModelBlockEntity modelBlock, boolean select)
    {
        this.modelBlock = modelBlock;

        if (modelBlock != null)
        {
            this.toSave.add(modelBlock);
            this.fillData();
        }

        this.editor.setVisible(modelBlock != null);
        this.koerper.fill(modelBlock);

        if (select)
        {
            this.modelBlocks.setCurrentScroll(modelBlock);
        }
    }

    private void fillData()
    {
        ModelProperties properties = this.modelBlock.getProperties();

        this.pickEdit.setForm(properties.getForm());
        this.transform.setTransform(properties.getTransform());
        this.enabled.setValue(properties.isEnabled());
        this.shadow.setValue(properties.isShadow());
        this.global.setValue(properties.isGlobal());
        this.lookAt.setValue(properties.isLookAt());
    }

    void save(ModelBlockEntity modelBlock)
    {
        if (modelBlock != null)
        {
            ClientNetwork.sendModelBlockForm(modelBlock.getBlockPos(), modelBlock);
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (super.subMouseClicked(context))
        {
            return true;
        }

        /* Gizmo handles first. The trackball sphere is otherwise deferred to the end so its screen
         * disc doesn't block a click on the model block under it - but while the sphere is hovered the
         * block fill is skipped, so the rotate disc can actually be grabbed (the gizmo sits on the
         * selected block, so this.hovered is almost always non-null and would steal the click). */
        if (this.canShowGizmo() && this.gizmo.mouseClickedHandle(context))
        {
            return true;
        }

        boolean sphereHovered = this.canShowGizmo() && this.gizmo.isSphereHovered();

        if (this.hovered != null && context.mouseButton == 0 && BBSSettings.clickModelBlocks.get() && !sphereHovered)
        {
            this.fill(this.hovered, true);

            return false;
        }

        return this.canShowGizmo() && this.gizmo.mouseClickedSphere(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        boolean consumed = this.canShowGizmo() && this.gizmo.mouseReleased(context);

        this.gizmo.stop();

        return super.subMouseReleased(context) || consumed;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        /* Pick first (UI pass): the stencil must be read before the visual's hover
         * (gizmo.update / renderGizmoHover both consume the picked index). */
        this.renderGizmoStencilInterface(context);

        if (this.canShowGizmo())
        {
            this.gizmo.renderGizmo(context);
            this.gizmo.update(context);
        }

        String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.dashboard.orbit.speed.getValue()).get();
        FontRenderer font = context.batcher.getFont();
        int w = font.getWidth(label);
        int x = this.area.w - w - 5;
        int y = this.area.ey() - font.getHeight() - 5;

        context.batcher.textCard(label, x, y, Colors.WHITE, Colors.A50);
        super.render(context);

        this.renderGizmoHover(context);

        if (this.canShowGizmo())
        {
            this.gizmo.renderSphereHighlight(context);
            this.gizmo.renderReadout(context);
        }
    }

    /**
     * Highlight the gizmo handle under the cursor by painting the picking
     * framebuffer back over the viewport through the picker-preview shader,
     * which recolours the pixels matching the hovered stencil index — the same
     * hover overlay the film and form editors draw.
     */
    private void renderGizmoHover(UIContext context)
    {
        if (!this.canShowGizmo() || !this.gizmoStencil.hasPicked())
        {
            return;
        }

        Texture texture = this.gizmoStencil.getFramebuffer().getMainTexture();
        int w = texture.width;
        int h = texture.height;

        RenderPipeline previewProgram = BBSShaders.getPickerPreviewProgram();
        /* Die Hervorhebung wird VORHER in ein eigenes Bild gerechnet, nicht beim Zeichnen
         * in die Oberflaeche -- ein GUI-Element kann die fuenf Werte nicht binden.
         * Entscheidung und verworfene Auswege stehen in Bildfilter. */
        /* Die hervorzuhebende Kennung ist die zuletzt gepickte des Stencil-Puffers -- dieselbe
         * Quelle wie in UIPickableFormRenderer und Gizmo. Sie stand frueher als Uniform
         * "Index" am Vorschau-Shader; jetzt reist sie als Argument in den Filterdurchgang. */
        mchorse.bbs_mod.graphics.Bildfilter.kennungHervorheben(texture,
            this.gizmoStencil.zwischenbild(), this.gizmoStencil.getIndex(),
            BBSSettings.stencilHighlightColor.get());

        /* Das Mischen kommt aus der Pipeline der Kachel: der Batcher zeichnet sie ueber
         * BBSPipelines (von RenderPipelines.GUI_TEXTURED geerbt), und deren ColorTargetState
         * traegt BlendFunction.TRANSLUCENT. Ein enableBlend davor gibt es in 26.2 nicht mehr. */
        context.batcher.texturedBox(() -> net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.gizmoStencil.zwischenbild(), Colors.WHITE, 0, 0, context.menu.width, context.menu.height, 0, h, w, 0, w, h);
    }

    @Override
    public void renderInWorld(LevelRenderContext context)
    {
        super.renderInWorld(context);

        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 pos = camera.pos;

        Minecraft mc = Minecraft.getInstance();
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();

        /* 26.2: positionMatrix() ist entfallen -- die Kameradrehung steht als
         * viewRotationMatrix im eingefrorenen Kamerazustand. Siehe renderInWorld-Zwilling
         * weiter oben; ein Griff auf poseStack() gaebe die Einheitsmatrix und der Mausstrahl
         * zeigte immer geradeaus. */
        this.mouseDirection.set(CameraUtils.getMouseDirection(
            mchorse.bbs_mod.graphics.Projektion.lesen(),
            context.levelState().cameraRenderState.viewRotationMatrix,
            (int) x, (int) y, 0, 0, mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()
        ));
        this.hovered = this.getClosestObject(new Vector3d(pos.x, pos.y, pos.z), this.mouseDirection);

        /* Die Umrandungen unten sollen von der Welt verdeckt werden — das ist der normale
         * Tiefentest, den in 26.2 die Pipeline von Draw.renderBox traegt (DepthStencilState
         * mit GREATER_THAN_OR_EQUAL und Tiefenschreiben, das umgekehrte Z ist eingerechnet).
         * Einen globalen Schalter, den man hier einschalten koennte, gibt es nicht mehr. */

        for (ModelBlockEntity entity : this.modelBlocks.getList())
        {
            BlockPos blockPos = entity.getBlockPos();

            if (!this.isEditing(entity))
            {
                context.poseStack().pushPose();
                context.poseStack().translate(blockPos.getX() - pos.x, blockPos.getY() - pos.y, blockPos.getZ() - pos.z);

                ModelBlockKoerper.umriss(context.poseStack(), entity, this.hovered == entity || entity == this.modelBlock);

                context.poseStack().popPose();
            }
        }

        /* Das Gegenstueck zum Abschalten oben faellt ebenfalls weg. renderGizmo zeichnet hier
         * nichts, es faengt nur die Matrizen ein — die Handgriffe kommen spaeter im
         * UI-Durchgang mit ihrer eigenen Pipeline. Es gibt also weder etwas zu uebergeben
         * noch einen globalen Zustand, den man zuruecklassen koennte. */

        this.renderGizmo(context, pos);
    }

    private ModelBlockEntity getClosestObject(Vector3d finalPosition, Vector3f mouseDirection)
    {
        ModelBlockEntity closest = null;

        for (ModelBlockEntity object : this.modelBlocks.getList())
        {
            AABB aabb = this.getHitbox(object);

            if (aabb.intersectsRay(finalPosition, mouseDirection))
            {
                if (closest == null)
                {
                    closest = object;
                }
                else
                {
                    AABB aabb2 = this.getHitbox(closest);

                    if (finalPosition.distanceSquared(aabb.x, aabb.y, aabb.z) < finalPosition.distanceSquared(aabb2.x, aabb2.y, aabb2.z))
                    {
                        closest = object;
                    }
                }
            }
        }
        return closest;
    }

    private AABB getHitbox(ModelBlockEntity closest)
    {
        return ModelBlockKoerper.trefferBox(closest);
    }

    public boolean isEditing(ModelBlockEntity entity)
    {
        if (this.modelBlock == entity)
        {
            List<UIFormPalette> children = this.getChildren(UIFormPalette.class);

            if (!children.isEmpty())
            {
                return children.get(0).editor.isEditing();
            }
        }

        return false;
    }
}
