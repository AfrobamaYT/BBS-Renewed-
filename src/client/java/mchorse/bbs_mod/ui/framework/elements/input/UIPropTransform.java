package mchorse.bbs_mod.ui.framework.elements.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Matrix4f;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragCursor;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory;
import mchorse.bbs_mod.ui.framework.elements.input.drag.MittelklickAchse;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformNumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Transform editor that drives the gizmo and hotkey (G/S/R) edits. The
 * editor itself is a thin coordinator: it owns the edit session (what is
 * being edited, the start snapshot, accept/reject) while all per-gesture
 * state and math live in the active {@link DragStrategy}, created through
 * {@link DragStrategyFactory} when an edit starts and dropped when it ends.
 */
public class UIPropTransform extends UITransform
{
    /**
     * Liefert maus-strahl und gizmo-matrix fuer das picking.
     *
     * MERGE 2026-07-27: aus dem CML-upstream — GizmoRayFrame/GizmoController haengen daran.
     */
    public interface IGizmoRayProvider
    {
        public boolean getMouseRay(UIContext context, int mouseX, int mouseY, Vector3d rayOrigin, Vector3f rayDirection);
        public boolean getGizmoMatrix(Matrix4f matrix);
    }

    private static final double[] CURSOR_X = new double[1];
    private static final double[] CURSOR_Y = new double[1];

    private static final Vector3f ZERO_RING_VEC = new Vector3f();

    private Transform transform;
    private Runnable preCallback;
    private Runnable postCallback;
    private Runnable endCallback;

    private boolean editing;
    private Axis axis = Axis.X;
    private Axis axis2;
    private Transform cache = new Transform();
    Timer checker = new Timer(30);   /* paketprivat: UITransformHandler treibt verborgen */

    private boolean model;

    /** The reference frame the gizmo and constrained edits operate in. Replaces
     *  the old local/global boolean; {@code space == LOCAL} is the former {@code local}. */
    private TransformSpace space;

    /** Dropdown trigger for {@link #space}; shows the active frame's icon and name. */
    private UISpaceButton spaceButton;

    /* Quaternion rotation pads (w, x, y, z), shown in place of the euler x/y/z pads
     * while the edited bone is in QUATERNION mode. Editing any of them rebuilds a
     * normalised quaternion from all four and commits it through setRQuat. */
    private UITrackpad qw;
    private UITrackpad qx;
    private UITrackpad qy;
    private UITrackpad qz;

    /** Whether the rotate row currently shows the four quaternion pads (vs the three euler pads). */
    private boolean quatFields;

    /** Drag snapshot the active gesture works against (kept for the gizmo's pie preview). */
    GizmoDrag drag;
    private boolean hotkeyMode;
    private Supplier<GizmoDrag> hotkeyDragSupplier;

    /** Drag snapshot the middle-click axis pick measures against — see {@link #axisPickDrag}. */
    Supplier<GizmoDrag> axisPickDragSupplier;

    /** Whether the edited bone's rotation is owned by an enabled IK chain
     *  (wired by hosts that have an IK concept; see {@link #rotationConstrained}). */
    private Supplier<Boolean> rotationConstrainedSupplier;

    /** The live gesture; non-null exactly while {@link #editing}. */
    private DragStrategy strategy;
    private final Bridge bridge = new Bridge();

    final TransformNumericInput numeric = new TransformNumericInput();   /* paketprivat, s. checker */

    /* Fine-drag (Shift) precision: a virtual cursor that lags the real one,
     * advancing at {@link DragStrategy#FINE_DRAG_FACTOR} speed while Shift is
     * held, so every ray gesture slows uniformly without per-mode code. The
     * lag is the accumulated offset between the two. */
    /** The cursor the running gesture reads (Shift damping, cursorless start) — see DragCursor. */
    final DragCursor cursor = new DragCursor();   /* paketprivat: Fuehler im Handler */

    /** Blender's middle-click axis pick; live only while an edit runs — see MittelklickAchse.
     *  Declared after {@link #bridge}, which supplies its host hooks; package-private for
     *  {@link UITransformHandler}, which feeds it the button events. */
    final MittelklickAchse middleAxis = new MittelklickAchse(this.bridge);

    private UITransformHandler handler;

    public UIPropTransform()
    {
        this.handler = new UITransformHandler(this);
        this.space = loadSpace();

        this.buildQuaternionFields();

        this.context((menu) ->
        {
            /* Per-bone rotation mode (Blender's rotation_mode); the label names the
             * mode the action switches TO, so the current one is always readable. */
            if (this.transform != null)
            {
                boolean quat = this.transform.rotationMode == Transform.RotationMode.QUATERNION;

                menu.action(
                    Icons.CONVERT,
                    quat ? UIKeys.TRANSFORMS_CONTEXT_MODE_EULER : UIKeys.TRANSFORMS_CONTEXT_MODE_QUATERNION,
                    this::toggleRotationMode
                );
            }
        });

        /* The rotation-row icon toggles the bone's rotation storage (euler / quaternion);
         * the active state is drawn as a highlight in render(), like the other toggles.
         * It keeps the base's CONTROL_HEIGHT box, same as the equally clickable
         * uniform-scale icon next to it: an oversized box on this one alone made it
         * bulge out of the set and pushed its row taller than the others. */
        this.iconR.callback = (b) -> this.toggleRotationMode();
        this.iconR.tooltip(UIKeys.TRANSFORMS_ROTATION_MODE_TOOLTIP);
        this.iconR.setEnabled(true);

        /* The space picker is a dropdown on its own row above T/S/R (it replaced the
         * old click-to-cycle on the translate-row icon, which is decorative again). */
        this.spaceButton = new UISpaceButton();
        this.spaceButton.tooltip(UIKeys.TRANSFORMS_SPACE_TOOLTIP);
        this.prepend(UI.labelRow(UIKeys.TRANSFORMS_SPACE_TITLE, this.spaceButton));
        /* Four uniform rows: the space picker above translate / scale / rotate.
         * (Was 3×CONTROL_HEIGHT + 20 — the 20 being the rotate row, which its
         * oversized toggle icon pushed past the others.) */
        this.h(4 * UIConstants.CONTROL_HEIGHT);
        this.updateSpaceLabel();

        /* Each finished value-field drag closes the current undo block, so dragging a
         * field several times in a row undoes one drag at a time (see endGesture). */
        for (UITrackpad field : new UITrackpad[]{this.tx, this.ty, this.tz, this.sx, this.sy, this.sz, this.rx, this.ry, this.rz, this.qw, this.qx, this.qy, this.qz})
        {
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.endGesture());
        }

        /* The deferred uniform-scale row sync (see setTransform). Mouse events traverse
         * children by index, so restructuring the row here is safe, unlike mid-render. */
        for (UITrackpad field : new UITrackpad[]{this.sx, this.sy, this.sz})
        {
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.syncUniformScaleRow());
        }

        this.noCulling();
    }

    /** Build the four quaternion pads mirrored on the rotate row in quaternion mode. */
    private void buildQuaternionFields()
    {
        IKey raw = IKey.constant("%s (%s)");

        this.qw = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qw.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, IKey.constant("W")));
        this.qw.textbox.setColor(Colors.LIGHTEST_GRAY);
        this.qx = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qx.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, UIKeys.GENERAL_X));
        this.qx.textbox.setColor(Colors.RED);
        this.qy = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qy.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, UIKeys.GENERAL_Y));
        this.qy.textbox.setColor(Colors.GREEN);
        this.qz = new UITrackpad((v) -> this.setQuatFromFields()).onlyNumbers().values(0.01D);
        this.qz.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATION_QUATERNION, UIKeys.GENERAL_Z));
        this.qz.textbox.setColor(Colors.BLUE);
    }

    public UIPropTransform callbacks(Supplier<IValueNotifier> notifier)
    {
        return this.callbacks(
            () -> notifier.get().preNotify(),
            () -> notifier.get().postNotify(),
            () -> notifier.get().preNotify(IValueListener.FLAG_UNMERGEABLE)
        );
    }

    public UIPropTransform callbacks(Runnable pre, Runnable post)
    {
        return this.callbacks(pre, post, null);
    }

    public UIPropTransform callbacks(Runnable pre, Runnable post, Runnable end)
    {
        this.preCallback = pre;
        this.postCallback = post;
        this.endCallback = end;

        return this;
    }

    public void preCallback()
    {
        if (this.preCallback != null) this.preCallback.run();
    }

    public void postCallback()
    {
        if (this.postCallback != null) this.postCallback.run();
    }

    /**
     * Close the current undo block so the next transform gesture starts a fresh,
     * separately-undoable entry. Fired at each gesture boundary — a value-field drag
     * end and the gizmo commit — rather than per value change, so one continuous drag
     * still merges into a single undo while consecutive drags stay distinct.
     */
    public void endGesture()
    {
        if (this.endCallback != null) this.endCallback.run();
    }

    public void setModel()
    {
        this.model = true;
    }

    public UIPropTransform hotkeyDrag(Supplier<GizmoDrag> supplier)
    {
        this.hotkeyDragSupplier = supplier;

        return this;
    }

    /**
     * Snapshot the middle-click axis pick measures screen directions against, for hosts whose
     * {@link #hotkeyDrag} supplier deliberately withholds one (the film's does, with the
     * camera sitting on the edited bone — the PICK survives an unprojectable origin, see
     * {@code AchsenNaehe}). Hosts that never withhold their drag can leave this unset; the
     * pick then reads the live one.
     */
    public UIPropTransform axisPickDrag(Supplier<GizmoDrag> supplier)
    {
        this.axisPickDragSupplier = supplier;

        return this;
    }

    /** Wire the IK-ownership probe for the edited bone's rotation (hosts with an IK concept). */
    public UIPropTransform rotationConstrained(Supplier<Boolean> supplier)
    {
        this.rotationConstrainedSupplier = supplier;

        return this;
    }

    /**
     * Whether the edited bone's rotation is owned by an enabled IK chain: the
     * render follows the solve there, so the rotation gestures refuse to start
     * and the gizmo dims its rings (the value pads still edit the FK channels —
     * the blend base and the pose IK falls back to).
     */
    public boolean isRotationConstrained()
    {
        return this.rotationConstrainedSupplier != null && Boolean.TRUE.equals(this.rotationConstrainedSupplier.get());
    }

    public boolean isLocal()
    {
        return this.space == TransformSpace.LOCAL;
    }

    /** The reference frame the gizmo and constrained edits operate in. */
    public TransformSpace getSpace()
    {
        return this.space;
    }

    @Override
    protected Transform getEditedTransform()
    {
        return this.transform;
    }

    public Axis getAxis2()
    {
        return this.axis2;
    }

    public boolean isScreenTranslate()
    {
        return this.strategy != null && this.strategy.isScreenTranslate();
    }

    /** Old-logic no-op: kept so hosts that gave the spaces bar a backdrop still compile. */
    public UIPropTransform barBackground()
    {
        return this;
    }

    protected boolean supportsMirror()
    {
        return false;
    }

    public boolean isMirrorEdit()
    {
        return BBSSettings.poseMirrorEdit.get();
    }

    public boolean isAlternateInvert()
    {
        return BBSSettings.poseAlternateInvert.get();
    }

    /** The space remembered from the last session, guarded against an out-of-range
     *  or not-yet-implemented stored value (then falls back to the default: PARENT,
     *  or LOCAL when the {@code default_local} toggle is on). */
    private static TransformSpace loadSpace()
    {
        TransformSpace[] values = TransformSpace.values();
        TransformSpace space = values[MathUtils.clamp(BBSSettings.transformSpace.get(), 0, values.length - 1)];

        if (!space.implemented)
        {
            return BBSSettings.defaultLocalTransform.get() ? TransformSpace.LOCAL : TransformSpace.PARENT;
        }

        return space;
    }

    /** Switch to a specific frame (dropdown pick / hotkey) and remember it globally. */
    private void selectSpace(TransformSpace space)
    {
        if (space == null || !space.implemented)
        {
            return;
        }

        this.space = space;
        BBSSettings.transformSpace.set(space.ordinal());
        this.updateSpaceLabel();
    }

    /**
     * Open the clip-style space list: each implemented frame with its icon and
     * colour (a not-yet-implemented frame would show greyed out and inert), in
     * the picker's own order ({@link TransformSpace#DISPLAY_ORDER}, PARENT
     * first). The list is auto-keyed, so the hotkey that opens it at the cursor
     * turns picking a frame into a two-stroke gesture (open, then press the
     * frame's number).
     */
    private void openSpaceMenu()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        context.replaceContextMenu((menu) ->
        {
            menu.autoKeys();

            for (TransformSpace space : TransformSpace.DISPLAY_ORDER)
            {
                if (space.implemented)
                {
                    menu.action(this.spaceIcon(space), this.spaceLabel(space), this.spaceColor(space), () -> this.selectSpace(space));
                }
                else
                {
                    menu.action(this.spaceIcon(space), UIKeys.TRANSFORMS_SPACE_WIP.format(this.spaceLabel(space)), Colors.GRAY & Colors.RGB, () -> {});
                }
            }
        });
    }

    /** Refresh the space picker's label to the active frame. The translate pads
     *  read and write {@code transform.translate} directly in every frame now,
     *  like the scale and rotate pads (the former LOCAL relative-nudge fields are
     *  gone; the gizmo still drags along the local axes). */
    private void updateSpaceLabel()
    {
        if (this.spaceButton != null)
        {
            this.spaceButton.label = this.spaceLabel(this.space);
        }
    }

    /** The dedicated icon for a space (used on the dropdown trigger and in its list). */
    private Icon spaceIcon(TransformSpace space)
    {
        return space.icon;
    }

    /** The accent colour a space is tagged with in the picker. RGB only (no alpha):
     *  the colourful menu action builds its own bar + gradient from it. */
    private int spaceColor(TransformSpace space)
    {
        return space.color;
    }

    private IKey spaceLabel(TransformSpace space)
    {
        return space.label;
    }

    private Vector3f calculateLocalVector(double factor, Axis axis)
    {
        if (this.transform == null)
        {
            return new Vector3f();
        }

        Vector3f vector3f = new Vector3f(
            (float) (axis == Axis.X ? factor : 0D),
            (float) (axis == Axis.Y ? factor : 0D),
            (float) (axis == Axis.Z ? factor : 0D)
        );
        /* I have no fucking idea why I have to rotate it 180 degrees by X axis... but it works! */
        Matrix3f matrix = new Matrix3f()
            .rotateX(this.model ? MathUtils.PI : 0F)
            .mul(this.transform.createRotationMatrix());

        matrix.transform(vector3f);

        return vector3f;
    }

    public UIPropTransform enableHotkeys()
    {
        return this.enableHotkeys(() -> true);
    }

    public UIPropTransform enableHotkeys(Supplier<Boolean> enabled)
    {
        IKey category = UIKeys.TRANSFORMS_KEYS_CATEGORY;
        Supplier<Boolean> active = () -> enabled.get() && this.editing;

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, () -> mchorse.bbs_mod.ui.film.replays.IKGriff.greifen(this)).active(() -> enabled.get() && !mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.ueberKeys(this.getContext())).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SCALE, () -> this.enableMode(TransformOp.SCALE)).active(() -> enabled.get() && !mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.ueberKeys(this.getContext())).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATE, () -> this.enableMode(TransformOp.ROTATE)).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_COMBINED, () -> Gizmo.INSTANCE.toggleCombined()).strict().active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_X, () -> this.setEditingAxis(Axis.X)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Y, () -> this.setEditingAxis(Axis.Y)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_Z, () -> this.setEditingAxis(Axis.Z)).active(active).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_SPACE_MENU, this::openSpaceMenu).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_ROTATION_MODE, this::toggleRotationMode).active(enabled).category(category);
        this.keys().register(Keys.TRANSFORMATIONS_RESET, () -> { this.reset(); this.refillTransform(); }).active(() -> enabled.get() && !this.editing && this.transform != null).strict().category(category);
        return this;
    }

    public Transform getTransform()
    {
        return mchorse.bbs_mod.ui.film.FilmAutoKey.transform(this, this.transform);
    }

    public boolean isEditing()
    {
        return this.editing;
    }

    public Axis getAxis()
    {
        return this.axis;
    }

    /** The active edit's operation, or {@code null} when nothing is being edited. */
    public TransformOp getOp()
    {
        return this.strategy == null ? null : this.strategy.op();
    }

    /**
     * The live gesture driving the edit, or {@code null}. Every (re)start —
     * including an axis switch mid-edit — builds a fresh instance, so the
     * gizmo uses its identity to scope per-gesture state (the ring freeze).
     */
    public DragStrategy getStrategy()
    {
        return this.strategy;
    }

    /** Whether the active rotation is one of the sphere's kinds (trackball or arcball). */
    public boolean isSphereRotate()
    {
        return this.strategy != null && this.strategy.isSphere();
    }

    public boolean isViewRotate()
    {
        return this.strategy != null && this.strategy.isView();
    }

    /** Whether the active scale drives all three axes off one lever (centre scale
     *  handle or an unconstrained S). Distinct from {@link #isUniformScale()}, which
     *  is the trackpad's scale-field linking. */
    public boolean isScaleAll()
    {
        return this.strategy != null && this.strategy.isScaleAll();
    }

    public Vector3f getInitialDragRingVec()
    {
        Vector3f vec = this.strategy == null ? null : this.strategy.initialRingVec();

        return vec == null ? ZERO_RING_VEC : vec;
    }

    public float getAccumulatedRotateDeg()
    {
        return this.strategy == null ? 0F : this.strategy.accumulatedRotateDeg();
    }

    /** Screen-space start edge of the view sweep pie (radians, Y-down convention). */
    public float getViewGrabScreenAngle()
    {
        return this.strategy == null ? 0F : this.strategy.viewGrabScreenAngle();
    }

    /** Signed screen-space span of the view sweep, in radians. */
    public float getViewScreenSweepRad()
    {
        return this.strategy == null ? 0F : this.strategy.viewScreenSweepRad();
    }

    /**
     * A short summary of what the active drag has changed so far, for the gizmo's
     * on-screen readout: degrees for a rotation (axis or view ring by swept angle,
     * the 3D sphere by net turn), the per-axis offset for a move, the per-axis
     * factor delta for a scale. Returns {@code null} when there is nothing to show.
     */
    public String getDragReadout()
    {
        if (!this.editing || this.transform == null || this.strategy == null)
        {
            return null;
        }

        return this.numeric.isActive() ? this.numeric.display() + (this.getOp() == TransformOp.ROTATE ? "°" : "") : this.strategy.readout(); /* typed, not the mouse sweep (read 0.0° while typing) */
    }

    public GizmoDrag getDrag()
    {
        return this.drag;
    }

    /** Stencil-Mapping ausgelagert: die Geste kennt ihre Achsbindung selbst
     *  ({@link DragStrategy#stencilIndex()}, inkl. Akte 11: uniformes S ohne Linie). */
    public int getDebugLineStencilIndex()
    {
        return this.editing && this.strategy != null ? this.strategy.stencilIndex() : -1;
    }

    public void refillTransform()
    {
        this.setTransform(this.getTransform());
    }

    private boolean isScaleFieldDragging()
    {
        return this.sx.isDragging() || this.sy.isDragging() || this.sz.isDragging();
    }

    /**
     * Collapse the scale row when all three scale coordinates are equal, expand it when
     * they differ (the {@link BBSSettings#uniformScale} option). Compared against the
     * row's own state — not {@link #isUniformScale()}, which is the SPACE/RMB field
     * linking — so matching states are a no-op instead of a blind toggle.
     */
    private void syncUniformScaleRow()
    {
        if (this.transform == null || !BBSSettings.uniformScale.get())
        {
            return;
        }

        Vector3f scale = this.transform.scale;

        if ((scale.x == scale.y && scale.y == scale.z) != this.isScaleRowCollapsed())
        {
            this.toggleUniformScale();
        }
    }

    public void setTransform(Transform transform)
    {
        this.transform = transform;

        /* Match the rotate row to how the bone stores its rotation (three euler
         * pads or four quaternion pads) before filling the fields below. */
        this.syncRotationMode();

        if (transform == null)
        {
            this.disable();
            this.fillT(0, 0, 0);
            this.fillS(1, 1, 1);
            this.fillR(0, 0, 0);

            return;
        }

        /* The uniform-scale auto-sync restructures the scale row (removeAll/add), and a
         * scale trackpad applies its drag from inside render() (through the delta editor
         * this loops right back here): mutating the element tree mid-traversal throws
         * ConcurrentModificationException. So the sync is deferred past any live gesture —
         * a gizmo/hotkey edit (editing) or a scale-field drag — and runs when a transform
         * is loaded into the panel, plus once more when the gesture ends (disable() for
         * hotkey edits, the drag-end listeners in the constructor for field drags). */
        if (!this.editing && !this.isScaleFieldDragging())
        {
            this.syncUniformScaleRow();
        }

        this.fillT(transform.translate.x, transform.translate.y, transform.translate.z);
        this.fillS(transform.scale.x, transform.scale.y, transform.scale.z);

        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.fillQ(transform.quat.x, transform.quat.y, transform.quat.z, transform.quat.w);

            /* Keep the (hidden) euler pads mirroring the quaternion's ZYX equivalent so
             * the euler-based readers — clipboard copy, the drag value card — stay correct. */
            Vector3f euler = Matrices.toEulerZYXRadians(transform.quat, new Vector3f());

            this.fillR(MathUtils.toDeg(euler.x), MathUtils.toDeg(euler.y), MathUtils.toDeg(euler.z));
        }
        else
        {
            this.fillR(MathUtils.toDeg(transform.rotate.x), MathUtils.toDeg(transform.rotate.y), MathUtils.toDeg(transform.rotate.z));
        }
    }

    /**
     * Show the rotate row in the mode the current transform stores its rotation in:
     * three euler-degree pads, or four raw quaternion pads (with the toggle icon lit).
     * Only rebuilds the row when the mode actually flips, so the per-frame
     * {@link #setTransform} stays cheap.
     */
    private void syncRotationMode()
    {
        boolean quat = this.transform != null && this.transform.rotationMode == Transform.RotationMode.QUATERNION;

        if (quat == this.quatFields)
        {
            return;
        }

        this.quatFields = quat;
        this.rotateRow.removeAll();

        if (quat)
        {
            this.rotateRow.add(this.iconR, this.qw, this.qx, this.qy, this.qz);
        }
        else
        {
            this.rotateRow.add(this.iconR, this.rx, this.ry, this.rz);
        }

        /* Re-lay the row's new children within the panel (same pattern as the
         * uniform-scale swap); only runs on an actual mode flip, not per frame. */
        UIElement parentContainer = this.getParentContainer();

        if (parentContainer != null)
        {
            parentContainer.resize();
        }
    }

    /** Fill the quaternion pads (raw x/y/z/w, as stored) without notifying the callback. */
    private void fillQ(float x, float y, float z, float w)
    {
        this.qx.setValue(x);
        this.qy.setValue(y);
        this.qz.setValue(z);
        this.qw.setValue(w);
    }

    /**
     * Commit the four quaternion pads as one rotation: rebuild the quaternion from
     * the fields, renormalise it (raw component edits drift off the unit sphere,
     * exactly like Blender's W/X/Y/Z fields), and route it through the normal
     * quaternion write so the delta editors still fan it across a selection.
     */
    private void setQuatFromFields()
    {
        if (this.transform == null)
        {
            return;
        }

        Quaternionf quat = new Quaternionf((float) this.qx.value, (float) this.qy.value, (float) this.qz.value, (float) this.qw.value);

        if (quat.lengthSquared() < 1.0E-8F)
        {
            /* All-zero is not a rotation; ignore until the user types something real. */
            return;
        }

        this.setRQuat(quat.normalize());
    }

    /**
     * Flip the edited bone between euler and quaternion rotation storage
     * (Blender's per-bone {@code rotation_mode}), converting its rotation data
     * once. Quaternion mode is gimbal-free; euler keeps &gt;360° spins and
     * per-component curves.
     */
    public void toggleRotationMode()
    {
        if (this.transform == null)
        {
            return;
        }

        boolean quaternion = this.transform.rotationMode != Transform.RotationMode.QUATERNION;

        this.preCallback();
        this.applyRotationMode(quaternion);
        this.postCallback();
        this.setTransform(this.transform);
        this.endGesture();
        UIUtils.playClick();
    }

    /**
     * Apply the storage-mode flip of {@link #toggleRotationMode}. The base
     * editor converts the single edited transform; the delta editors override
     * this to fan the flip across the whole selection (selected keyframes of a
     * limb track, selected bones with their mirror partners) — a bone's mode is
     * a property of the TRACK, and leaving unselected keyframes behind in euler
     * would quietly keep the track on mixed interpolation.
     */
    protected void applyRotationMode(boolean quaternion)
    {
        if (quaternion)
        {
            this.transform.setModeQuaternion();
        }
        else
        {
            this.transform.setModeEuler();
        }
    }

    /* Edit entry points. The mouse path (a gizmo handle pick) supplies the
     * axes directly and never switches the gizmo's display mode; the keyboard
     * path walks the user-configured hotkey orders and switches the displayed
     * handles on the first press. Both funnel into startEdit. */

    public void enableMode(TransformOp op)
    {
        GizmoDrag drag = this.getHotkeyDrag();
        boolean ray = BBSSettings.transformHotkeys3dRay.get() && drag != null;

        /* G/S/R walk their handles in the user-configured order (the
         * *_hotkey_order settings), wrapping past the end back to the first
         * step. Steps whose handle is unavailable drop out: the ray-driven
         * ones without a rendered gizmo, the sphere when it's turned off.
         * Scale's uniform three-axis lever is a step of that walk like any
         * other (Blender's plain S, first in the default order) — it used to
         * short-circuit the whole method, which left every repeat press of S
         * restarting it and the scale order setting driving nothing. */
        HotkeyTarget target = this.nextHotkeyTarget(op, ray);

        if (target == HotkeyTarget.VIEW)
        {
            this.enableViewRotate(drag, true);
        }
        else if (target == HotkeyTarget.SPHERE)
        {
            this.enableSphereRotate(drag, true);
        }
        else if (target == HotkeyTarget.SCREEN)
        {
            this.enableScreenTranslate(drag, true);
        }
        else if (target == HotkeyTarget.ALL)
        {
            this.enableUniformScale(drag, true);
        }
        else
        {
            this.enableHotkeyAxis(op, target.axis, drag);
        }
    }

    /** The walk step the active edit corresponds to ({@code null} when not editing this op). */
    private HotkeyTarget currentHotkeyTarget(TransformOp op)
    {
        if (!this.editing || this.getOp() != op)
        {
            return null;
        }

        if (this.isViewRotate()) return HotkeyTarget.VIEW;
        if (this.isSphereRotate()) return HotkeyTarget.SPHERE;
        if (this.isScreenTranslate()) return HotkeyTarget.SCREEN;
        /* Before the axis checks: the uniform lever parks on Axis.X, so reading
         * the axis alone would report it as the X step and the walk would skip
         * straight past X on the next press. */
        if (this.isScaleAll()) return HotkeyTarget.ALL;
        if (this.axis == Axis.Y) return HotkeyTarget.Y;
        if (this.axis == Axis.Z) return HotkeyTarget.Z;

        return HotkeyTarget.X;
    }

    private HotkeyTarget nextHotkeyTarget(TransformOp op, boolean ray)
    {
        ValueOrder order = op == TransformOp.TRANSLATE ? BBSSettings.translateHotkeyOrder : (op == TransformOp.SCALE ? BBSSettings.scaleHotkeyOrder : BBSSettings.rotateHotkeyOrder);
        List<HotkeyTarget> steps = new ArrayList<>();

        for (String token : order.get())
        {
            HotkeyTarget target = HotkeyTarget.byToken(token);

            if (target == null || (target.needsRay && !ray))
            {
                continue;
            }

            if (target == HotkeyTarget.SPHERE && !BBSSettings.rotate3dSphere.get())
            {
                continue;
            }

            steps.add(target);
        }

        if (steps.isEmpty())
        {
            return HotkeyTarget.X;
        }

        int index = steps.indexOf(this.currentHotkeyTarget(op));

        return steps.get((index + 1) % steps.size());
    }

    /**
     * Start (or switch to) a hotkey-driven operation along a specific axis.
     * Unlike the mouse path this keeps the hotkey semantics (numeric input,
     * accept/reject overlay, the display-mode switch on the first press);
     * the axis comes from the configured hotkey order rather than a fixed
     * cycle.
     */
    private void enableHotkeyAxis(TransformOp op, Axis axis, GizmoDrag drag)
    {
        if (this.switchGizmoDisplayMode(op))
        {
            return;
        }

        this.startEdit(op, axis, null, DragStrategyFactory.Variant.AXIS, drag, true);
    }

    public void enableMode(TransformOp op, Axis axis)
    {
        this.enableMode(op, axis, null, null);
    }

    public void enableMode(TransformOp op, Axis axis, Axis axis2)
    {
        this.enableMode(op, axis, axis2, null);
    }

    /**
     * Start an operation from a mouse handle pick: the axes come straight
     * from the picked handle, so this never cycles and never switches the
     * gizmo's display mode. The keyboard path goes through
     * {@link #enableMode(TransformOp)} and the configured hotkey orders instead.
     */
    public void enableMode(TransformOp op, Axis axis, Axis axis2, GizmoDrag drag)
    {
        this.startEdit(op, axis == null ? Axis.X : axis, axis2, DragStrategyFactory.Variant.AXIS, drag, axis == null);
    }

    public void enableSphereRotate(GizmoDrag drag)
    {
        this.enableSphereRotate(drag, false);
    }

    /** Start whichever free rotation the sphere is configured to drive. */
    public void enableSphereRotate(GizmoDrag drag, boolean hotkeyMode)
    {
        if (BBSSettings.rotate3dSphereMode.get() == 1) this.enableArcball(drag, hotkeyMode);
        else this.enableTrackball(drag, hotkeyMode);
    }

    public void enableTrackball(GizmoDrag drag)
    {
        this.enableTrackball(drag, false);
    }

    public void enableTrackball(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && this.switchGizmoDisplayMode(TransformOp.ROTATE))
        {
            return;
        }

        this.startEdit(TransformOp.ROTATE, null, null, DragStrategyFactory.Variant.TRACKBALL, drag, hotkeyMode);
    }

    public void enableArcball(GizmoDrag drag)
    {
        this.enableArcball(drag, false);
    }

    public void enableArcball(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && this.switchGizmoDisplayMode(TransformOp.ROTATE))
        {
            return;
        }

        this.startEdit(TransformOp.ROTATE, null, null, DragStrategyFactory.Variant.ARCBALL, drag, hotkeyMode);
    }

    public void enableViewRotate(GizmoDrag drag)
    {
        this.enableViewRotate(drag, false);
    }

    public void enableViewRotate(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && this.switchGizmoDisplayMode(TransformOp.ROTATE))
        {
            return;
        }

        this.startEdit(TransformOp.ROTATE, null, null, DragStrategyFactory.Variant.VIEW, drag, hotkeyMode);
    }

    /**
     * Start a uniform (three-axis) scale: one lever axis drives all three, the
     * same math Ctrl+axis-scale uses. A mouse pick ({@code hotkeyMode == false})
     * never switches the gizmo's display mode; as the S-key walk step it switches
     * to scale mode on the first press like the other hotkey starters.
     */
    public void enableUniformScale(GizmoDrag drag)
    {
        this.enableUniformScale(drag, false);
    }

    public void enableUniformScale(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && this.switchGizmoDisplayMode(TransformOp.SCALE))
        {
            return;
        }

        this.startEdit(TransformOp.SCALE, Axis.X, null, DragStrategyFactory.Variant.UNIFORM_SCALE, drag, hotkeyMode);
    }

    /**
     * Start a screen-space (view-plane) translate: the object moves along the
     * camera's right/up axes in the plane facing the camera. Grabbing the
     * centre cube with the mouse never switches the gizmo's display mode
     * (like the other handle picks); as a hotkey walk step the first press
     * switches it like the rest of the hotkey starters.
     */
    public void enableScreenTranslate(GizmoDrag drag)
    {
        this.enableScreenTranslate(drag, false);
    }

    public void enableScreenTranslate(GizmoDrag drag, boolean hotkeyMode)
    {
        if (hotkeyMode && this.switchGizmoDisplayMode(TransformOp.TRANSLATE))
        {
            return;
        }

        this.startEdit(TransformOp.TRANSLATE, Axis.X, Axis.Y, DragStrategyFactory.Variant.SCREEN, drag, hotkeyMode);
    }

    /**
     * The hotkey starters switch the gizmo's displayed handles to their
     * operation on the first press; when that happens the press is consumed
     * by the switch and no edit starts. In combined mode there is nothing to
     * switch, so the edit always starts.
     */
    private boolean switchGizmoDisplayMode(TransformOp op)
    {
        Gizmo.Mode target = op == TransformOp.TRANSLATE ? Gizmo.Mode.TRANSLATE : (op == TransformOp.SCALE ? Gizmo.Mode.SCALE : Gizmo.Mode.ROTATE);

        return Gizmo.INSTANCE.getMode() != Gizmo.Mode.COMBINED && Gizmo.INSTANCE.setMode(target);
    }

    /**
     * The one edit-start ritual every entry point funnels into: close any
     * previous edit, snapshot the transform, build the strategy for the
     * request and anchor it at the cursor, then raise the accept/reject
     * overlay.
     */
    private void startEdit(TransformOp op, Axis axis, Axis axis2, DragStrategyFactory.Variant variant, GizmoDrag drag, boolean hotkeyMode)
    {
        UIContext context = this.getContext();

        if (context == null || this.transform == null)
        {
            return;
        }

        this.numeric.clear();

        if (this.editing)
        {
            this.restore();
        }

        this.editing = true;
        this.axis = axis;
        this.axis2 = axis2;
        this.hotkeyMode = hotkeyMode;
        this.drag = drag;

        /* Scope the IK solve dump to this gesture — the log then holds exactly
         * the drag being investigated (see ModelIKRuntime#logGesture). */
        ModelIKRuntime.logGesture(true);

        this.cache.copy(this.transform = this.getTransform());
        Gizmo.INSTANCE.trackTransform(this);

        this.strategy = DragStrategyFactory.create(this.bridge, op, axis, axis2, variant, hotkeyMode);
        this.strategy.begin(context.mouseX, context.mouseY);

        /* A host can hold the cursor back on the frame an edit starts — controlling an actor
         * pins it to -1/-1 and only hands it over once one is running, so the anchor above
         * comes from a position the pointer was never at (see DragCursor#armReanchor). */
        this.cursor.armReanchor(context.mouseX, context.mouseY);
        this.middleAxis.bearbeitungBegonnen(context.mouseX, context.mouseY);

        if (!this.handler.hasParent())
        {
            context.menu.overlay.add(this.handler);
        }
    }

    private GizmoDrag getHotkeyDrag()
    {
        return this.hotkeyDragSupplier == null ? null : this.hotkeyDragSupplier.get();
    }

    /**
     * Constrain the live edit to an axis (or, with Shift, to the plane
     * perpendicular to it): rewind to the start values and rebuild the
     * gesture as a plain axis drag of the same operation.
     */
    private void setEditingAxis(Axis axis)
    {
        this.setEditingAxis(axis, Window.isShiftPressed());
    }

    /**
     * The same, with the plane variant handed in rather than read off the live keyboard.
     * {@link MittelklickAchse} latches Shift the moment its button goes down and keeps
     * re-picking while it is held — reading the key again on a later frame would let releasing
     * Shift change the meaning of a gesture that is already running.
     */
    private void setEditingAxis(Axis axis, boolean plane)
    {
        if (plane)
        {
            Axis[] paar = MittelklickAchse.ebenenPaar(axis);

            this.axis = paar[0];
            this.axis2 = paar[1];
        }
        else
        {
            this.axis = axis;
            this.axis2 = null;
        }

        if (!this.editing)
        {
            return;
        }

        TransformOp op = this.getOp();

        this.restore();

        UIContext context = this.getContext();

        if (context != null && op != null)
        {
            /* Blenders Umprojizieren beim Constraint-Wechsel — Anker und Sofort-
             * Anwendung stehen in {@link DragStrategyFactory#wechselnMitGriff}. */
            this.strategy = DragStrategyFactory.wechselnMitGriff(this.bridge, op, this.axis, this.axis2, this.hotkeyMode,
                this.middleAxis.ursprungX(), this.middleAxis.ursprungY(), context.mouseX, context.mouseY, this.cursor);
        }

        /* Re-route an in-progress typed amount onto the freshly picked axis. */
        if (this.numeric.isActive())
        {
            this.applyNumericInput();
        }
    }

    /** Rewind every channel to the values captured when the edit began. */
    private void restore()
    {
        this.setT(null, this.cache.translate.x, this.cache.translate.y, this.cache.translate.z);
        this.setS(null, this.cache.scale.x, this.cache.scale.y, this.cache.scale.z);

        if (this.cache.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.setRQuat(new Quaternionf(this.cache.quat));
        }
        else
        {
            this.setR(null, MathUtils.toDeg(this.cache.rotate.x), MathUtils.toDeg(this.cache.rotate.y), MathUtils.toDeg(this.cache.rotate.z));
        }
    }

    private void disable()
    {
        ModelIKRuntime.logGesture(false);

        this.editing = false;
        this.axis2 = null;
        this.hotkeyMode = false;
        this.strategy = null;
        this.drag = null;
        this.cursor.forget();
        this.middleAxis.bearbeitungBeendet();
        this.numeric.clear();
        Gizmo.INSTANCE.clearTrackedTransform(this);

        if (this.handler.hasParent())
        {
            this.handler.removeFromParent();
        }
    }

    public void acceptChanges()
    {
        mchorse.bbs_mod.ui.film.GestenKeys.behalten();
        this.disable();
        this.setTransform(this.transform);
        this.endGesture();
    }

    public void rejectChanges()
    {
        if (this.transform == null)
        {
            this.disable();

            return;
        }

        /* Rewind BEFORE tearing down: restore() reverts a pivot session through its per-bone
         * snapshots, which disable() nulls; the other order fans the primary's values onto the
         * whole selection. Keys the gesture created at the playhead go too (GestenKeys). */
        this.restore();
        mchorse.bbs_mod.ui.film.GestenKeys.verwerfen();
        this.disable();
        this.setTransform(this.transform);
    }

    /** Route a wheel event into the live gesture (depth move, sphere roll). */
    public boolean scrollDrag(UIContext context)
    {
        return this.editing && this.transform != null && this.strategy != null && this.strategy.scroll(context);
    }

    /* Numeric (keyboard) input for hotkey-driven transforms */

    /**
     * Numeric input only rides on the GSR keyboard operations ({@link #hotkeyMode}),
     * never on a mouse handle drag; the active gesture additionally has a say
     * (the screen-space grab spreads one drag across two camera axes, so a
     * single typed scalar is ambiguous there).
     */
    private boolean acceptsNumericInput()
    {
        return this.editing && this.hotkeyMode && this.transform != null
            && this.strategy != null && this.strategy.acceptsNumeric();
    }

    /**
     * Feed one key into the live numeric buffer: digits and the decimal point
     * extend it, {@code -} flips the sign, backspace trims it (and hands control
     * back to the cursor once everything is erased). Returns whether the key was
     * consumed as numeric input.
     */
    private boolean handleNumericInputKey(UIContext context)
    {
        if (!this.acceptsNumericInput())
        {
            return false;
        }

        KeyAction action = context.getKeyAction();

        if (action != KeyAction.PRESSED && action != KeyAction.REPEAT)
        {
            return false;
        }

        int key = context.getKeyCode();

        /* While typing on the sphere, X/Y aim the typed angle at the
         * horizontal (screen-up axis) or vertical (screen-right axis) turn.
         * Without typed digits they must fall through to the axis keybinds
         * and constrain to a ring — otherwise they read as dead keys. */
        if (this.numeric.isActive() && this.strategy.handleNumericAxisKey(key))
        {
            this.applyNumericInput();

            return true;
        }

        switch (this.numeric.feedKey(key))
        {
            case EMPTIED:
                this.stopNumericInput(context);

                return true;

            case CHANGED:
                this.applyNumericInput();

                return true;

            case CONSUMED:
                return true;

            default:
                return false;
        }
    }

    /**
     * Erasing the whole buffer cancels numeric mode: rewind to the operation's
     * start and re-anchor the cursor drag at the current pointer so mouse
     * control resumes without a jump.
     */
    private void stopNumericInput(UIContext context)
    {
        this.numeric.clear();
        this.restore();

        /* The cursor was free to roam while typing; re-anchor the precision
         * tracking here so the resumed drag doesn't inherit a stale lag. */
        this.cursor.anchor(context.mouseX, context.mouseY);

        if (this.strategy != null)
        {
            this.strategy.begin(context.mouseX, context.mouseY);
        }

        this.setTransform(this.transform);
    }

    /** Recompute the transform from the start snapshot plus the typed amount. */
    private void applyNumericInput()
    {
        if (this.transform == null || this.strategy == null)
        {
            return;
        }

        this.strategy.applyNumeric(this.numeric.value());
        this.setTransform(this.transform);
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.translate.set((float) x, (float) y, (float) z);
        this.postCallback();
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.scale.set((float) x, (float) y, (float) z);
        this.postCallback();
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();

        /* A quaternion-mode bone has no euler channel to write, so typed angles
         * fold straight into its quaternion (leaving it gimbal-free storage). */
        if (this.transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            this.transform.quat.set(Matrices.toQuaternionZYXDegrees((float) x, (float) y, (float) z));
        }
        else
        {
            this.transform.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), MathUtils.toRad((float) z));
        }

        this.postCallback();
    }

    /**
     * Store a full rotation as a quaternion (the gizmo drag's gimbal-free commit
     * path for a quaternion-mode bone). Overridden by the delta editors to fan a
     * quaternion delta across the selection.
     */
    @Override
    public void setRQuat(Quaternionf quat)
    {
        if (this.transform == null)
        {
            return;
        }

        this.preCallback();
        this.transform.quat.set(quat);
        this.transform.rotationMode = Transform.RotationMode.QUATERNION;
        this.postCallback();
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.editing)
        {
            if (context.isPressed(InputConstants.KEY_RETURN))
            {
                this.acceptChanges();

                return true;
            }
            else if (context.isPressed(InputConstants.KEY_ESCAPE))
            {
                this.rejectChanges();

                return true;
            }
            else if (this.handleNumericInputKey(context))
            {
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    /** Short label of what the active drag grabs: axis letters, the screen
     *  plane, the view ring, or one of the sphere's rotations. */
    private String editingTargetLabel()
    {
        String special = this.strategy == null ? null : this.strategy.editingTargetLabel();

        if (special != null)
        {
            return special;
        }

        if (this.getOp() == TransformOp.SCALE && (this.isScaleAll() || Window.isCtrlPressed()))
        {
            return "XYZ";
        }

        String label = this.axis == null ? "" : this.axis.name();

        if (this.axis2 != null)
        {
            label += this.axis2.name();
        }

        return label;
    }

    /** Axis letters tint to their gizmo colors; everything else stays white. */
    private int editingTargetColor()
    {
        boolean singleAxis = this.axis != null && this.axis2 == null
            && !this.isScreenTranslate()
            && !(this.getOp() == TransformOp.SCALE && (this.isScaleAll() || Window.isCtrlPressed()));

        if (!singleAxis)
        {
            return Colors.WHITE;
        }

        if (this.axis == Axis.X) return Colors.A100 | Colors.RED;
        if (this.axis == Axis.Y) return Colors.A100 | Colors.GREEN;

        return Colors.A100 | Colors.BLUE;
    }

    /** Space chip; scale ignores the space toggle, so it gets none. */
    private String editingSpaceLabel()
    {
        if (this.getOp() == TransformOp.SCALE)
        {
            return null;
        }

        return this.spaceLabel(this.space).get();
    }

    /** The live vector of the edited channel, for the cursor's value card. */
    private Vector3f getValue()
    {
        if (this.transform == null)
        {
            return new Vector3f();
        }

        TransformOp op = this.getOp();

        if (op == TransformOp.SCALE)
        {
            return this.transform.scale;
        }
        else if (op == TransformOp.ROTATE)
        {
            /* A quaternion bone's channels are stale; show its live rotation. */
            return this.transform.rotationMode == Transform.RotationMode.QUATERNION
                ? this.transform.getEulerRotation(new Vector3f())
                : this.transform.rotate;
        }

        return this.transform.translate;
    }

    /**
     * Advance the live gesture: wrap the cursor at the window edges (re-anchoring
     * the strategy at the teleported position) and feed the strategy the cursor —
     * virtual (Shift-slowed) for ray gestures, raw for the additive fallback,
     * which damps Shift through its step factor instead.
     */
    void updateDrag(UIContext context)   /* paketprivat: UITransformHandler treibt verborgen */
    {
        /* UIContext.mouseX can't be used because when cursor is outside of window
         * its position stops being updated. That's why it has to be queried manually
         * through GLFW...
         *
         * It gets updated outside the window only when one of mouse buttons is
         * being held! */
        Window.getCursorPosition(CURSOR_X, CURSOR_Y);

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getScreenWidth();

        double rawX = CURSOR_X[0];
        double fx = Math.ceil(w / (double) context.menu.width);
        int border = 5;
        int borderPadding = border + 1;

        /* Anchored without a cursor (see startEdit): re-anchor here instead of advancing, or
         * the whole distance to the pointer's real position arrives as one step. */
        if (this.cursor.claimReanchor(context.mouseX, context.mouseY))
        {
            if (this.strategy != null)
            {
                this.strategy.begin(context.mouseX, context.mouseY);
            }

            this.middleAxis.ursprungVersetzen(context.mouseX, context.mouseY);

            return;
        }

        this.cursor.advance(context.mouseX, context.mouseY, this.strategy != null ? this.strategy.fineFactor() : DragStrategy.FINE_DRAG_FACTOR);

        if (rawX <= border || rawX >= w - border)
        {
            int wrapX;

            if (rawX <= border)
            {
                Window.moveCursor(w - borderPadding, (int) mc.mouseHandler.ypos());
                wrapX = context.menu.width - (int) (borderPadding / fx);
            }
            else
            {
                Window.moveCursor(borderPadding, (int) mc.mouseHandler.ypos());
                wrapX = (int) (borderPadding / fx);
            }

            this.checker.mark();

            /* The wrap re-anchors the drag at the teleported position, so the
             * virtual cursor resets there too — no lag carries across the seam. */
            this.cursor.anchor(wrapX, context.mouseY);

            if (this.strategy != null)
            {
                this.strategy.begin(wrapX, context.mouseY);
            }

            this.middleAxis.ursprungVersetzen(wrapX, context.mouseY);

            return;
        }

        /* Mittelklick gehalten: die Achse folgt dem Zeiger. Ruft nur bei echter Aenderung
         * setEditingAxis, sonst wuerde die Bearbeitung jedes Bild auf ihre Startwerte
         * zurueckgespult. */
        this.middleAxis.bildAktualisieren(context.mouseX, context.mouseY);

        if (this.strategy != null)
        {
            if (this.strategy.usesFineCursor())
            {
                this.strategy.update(this.cursor.x(context.mouseX), this.cursor.y(context.mouseY));
            }
            else
            {
                this.strategy.update(context.mouseX, context.mouseY);
            }

            this.strategy.logDrag();
        }

        this.setTransform(this.transform);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.editing && !this.numeric.isActive() && this.checker.isTime())
        {
            this.updateDrag(context);
        }

        /* Quaternion mode lights up the rotation-row icon with the standard toggle
         * highlight, as a gradient down the icon's left edge. */
        if (this.transform != null && this.transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.iconR.area, Direction.LEFT);
        }

        super.render(context);

        if (this.editing)
        {
            FontRenderer font = context.batcher.getFont();
            TransformOp editOp = this.getOp();
            String op = (editOp == TransformOp.TRANSLATE ? UIKeys.TRANSFORMS_TRANSLATE : editOp == TransformOp.SCALE ? UIKeys.TRANSFORMS_SCALE : UIKeys.TRANSFORMS_ROTATE).get();
            String target = this.editingTargetLabel();
            String space = this.editingSpaceLabel();

            /* Chip row: the operation on the primary color, then what is
             * grabbed (axis letters in their gizmo colors), then the editing
             * space. The 5s account for textCard's box overhang at the
             * default card offset. */
            int gap = 2;
            int rowWidth = font.getWidth(op) + 5 + gap + font.getWidth(target) + 5;

            if (space != null)
            {
                rowWidth += gap + font.getWidth(space) + 5;
            }

            int x = this.area.mx(rowWidth) + 3;
            int y = this.area.my(font.getHeight());

            context.batcher.textCard(op, x, y, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));
            x += font.getWidth(op) + 5 + gap;
            context.batcher.textCard(target, x, y, this.editingTargetColor(), Colors.A50);

            if (space != null)
            {
                x += font.getWidth(target) + 5 + gap;
                context.batcher.textCard(space, x, y, Colors.LIGHTEST_GRAY, Colors.A50);
            }

            /* Label echoed both at the cursor and (when typing) under the info row. */
            String numericLabel = null;

            if (this.axis != null)
            {
                Vector3f v = this.getValue();
                float val = this.axis == Axis.X ? v.x : (this.axis == Axis.Y ? v.y : v.z);

                if (editOp == TransformOp.ROTATE)
                {
                    val = MathUtils.toDeg(val);
                }

                String valueLabel = String.format(java.util.Locale.US, "%.2f", val);

                if (this.axis2 != null)
                {
                    float val2 = this.axis2 == Axis.X ? v.x : (this.axis2 == Axis.Y ? v.y : v.z);

                    if (editOp == TransformOp.ROTATE)
                    {
                        val2 = MathUtils.toDeg(val2);
                    }

                    valueLabel += ", " + String.format(java.util.Locale.US, "%.2f", val2);
                }

                /* While typing, lead with the raw input so the user sees exactly
                 * what they've entered, with the resulting value in parentheses. */
                String cursorLabel = this.numeric.isActive()
                    ? this.numeric.display() + " (" + valueLabel + ")"
                    : valueLabel;

                if (this.numeric.isActive())
                {
                    numericLabel = cursorLabel;
                }

                context.batcher.textCard(cursorLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
            }
            else if (this.numeric.isActive())
            {
                /* The view ring and the sphere have no single axis component to
                 * echo, so show the typed angle, plus the aimed direction. */
                String prefix = this.strategy == null ? "" : this.strategy.numericPrefix();

                numericLabel = prefix + this.numeric.display() + "°";

                context.batcher.textCard(numericLabel, context.mouseX + 12, context.mouseY + 12, Colors.WHITE, Colors.A50);
            }

            /* Mirror the live numeric input on its own card right under the info row. */
            if (numericLabel != null)
            {
                int nx = this.area.mx(font.getWidth(numericLabel));
                int ny = y + font.getHeight() + 8;

                context.batcher.textCard(numericLabel, nx, ny, Colors.WHITE, BBSSettings.primaryColor(Colors.A50));
            }
        }
    }

    /**
     * Bridge the active {@link DragStrategy} works through: it exposes the
     * edit session's state and funnels every write back through the editor's
     * virtual {@code setT/setS/setR/setR2}, so the delta editors keep fanning
     * edits onto their selections.
     */
    private class Bridge implements DragContext, MittelklickAchse.Wirt
    {
        @Override
        public Transform transform()
        {
            return UIPropTransform.this.transform;
        }

        @Override
        public Transform cache()
        {
            return UIPropTransform.this.cache;
        }

        @Override
        public GizmoDrag drag()
        {
            return UIPropTransform.this.drag;
        }

        @Override
        public void setDrag(GizmoDrag drag)
        {
            UIPropTransform.this.drag = drag;
        }

        @Override
        public GizmoDrag freshHotkeyDrag()
        {
            return UIPropTransform.this.getHotkeyDrag();
        }

        @Override
        public boolean isLocal()
        {
            return UIPropTransform.this.isLocal();
        }

        @Override
        public TransformSpace space()
        {
            return UIPropTransform.this.space;
        }

        @Override
        public boolean isModel()
        {
            return UIPropTransform.this.model;
        }

        @Override
        public boolean rotationConstrained()
        {
            return UIPropTransform.this.isRotationConstrained();
        }

        /* Blender-style snapping: every gesture is free by default and snaps to
         * the configured step only while Ctrl is held. Typed numeric input is
         * exact already, so it never snaps. */
        @Override
        public boolean shouldSnap(TransformOp op)
        {
            return UIPropTransform.this.editing && UIPropTransform.this.getOp() == op
                && Window.isCtrlPressed() && !UIPropTransform.this.numeric.isActive();
        }

        @Override
        public float additiveFactor(TransformOp op)
        {
            UITrackpad reference = op == TransformOp.TRANSLATE ? UIPropTransform.this.tx : (op == TransformOp.SCALE ? UIPropTransform.this.sx : UIPropTransform.this.rx);

            return (float) reference.getValueModifier();
        }

        @Override
        public Vector3f localTranslateVector(double factor, Axis axis)
        {
            return UIPropTransform.this.calculateLocalVector(factor, axis);
        }

        @Override
        public float sphereWorldRadius()
        {
            return Gizmo.INSTANCE.getSphereWorldRadius();
        }

        @Override
        public void refreshFields()
        {
            UIPropTransform.this.setTransform(UIPropTransform.this.transform);
        }

        @Override
        public void writeTranslate(float x, float y, float z)
        {
            UIPropTransform.this.setT(null, x, y, z);
        }

        @Override
        public void writeScale(float x, float y, float z)
        {
            UIPropTransform.this.setS(null, x, y, z);
        }

        @Override
        public void writeRotateDeg(float xDeg, float yDeg, float zDeg)
        {
            UIPropTransform.this.setR(null, xDeg, yDeg, zDeg);
        }

        @Override
        public void writeRotationQuat(Quaternionf quat)
        {
            UIPropTransform.this.setRQuat(quat);
        }

        /* ── MittelklickAchse.Wirt ─────────────────────────────────────────────
         * Blender's middle-click axis pick. Reads only; the one write goes through
         * setEditingAxis, the very entry point the X/Y/Z keybinds use. */

        @Override
        public boolean istBearbeitung()
        {
            return UIPropTransform.this.editing;
        }

        @Override
        public GizmoDrag achsenwahlZug()
        {
            Supplier<GizmoDrag> supplier = UIPropTransform.this.axisPickDragSupplier;
            GizmoDrag pick = supplier == null ? null : supplier.get();

            /* Hosts without their own pick supplier (form editor, model blocks) never withhold
             * their drag, so the live one is exactly right there. */
            return pick != null ? pick : UIPropTransform.this.drag;
        }

        @Override
        public TransformSpace raum()
        {
            /* Blender's initSelectConstraint falls back to the SCENE's orientation — the BBS
             * twin is this picker's frame, a persisted editor-wide setting. Deliberately NOT
             * overridden by the gesture: the frame decides where the gizmo sits, and moving it
             * mid-drag would invalidate the running ray gesture's start snapshot. */
            return UIPropTransform.this.space;
        }

        @Override
        public void aufAchseZwingen(Axis achse, boolean ebene)
        {
            UIPropTransform.this.setEditingAxis(achse, ebene);
        }
    }

    /**
     * Dropdown trigger for the transform space: a normal button whose label is the
     * active frame's name (kept current by {@link #updateSpaceLabel}), with that frame's
     * coloured icon drawn on the left. Clicking opens the clip-style space list.
     */
    private class UISpaceButton extends UIButton
    {
        public UISpaceButton()
        {
            super(UIKeys.TRANSFORMS_SPACE_LOCAL, (b) -> UIPropTransform.this.openSpaceMenu());
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            super.renderSkin(context);

            /* The frame's icon, left-aligned and left in the default white — the colour
             * cue lives in the dropdown list, not on the trigger. */
            context.batcher.icon(
                UIPropTransform.this.spaceIcon(UIPropTransform.this.space),
                Colors.WHITE,
                this.area.x + 4, this.area.my(), 0F, 0.5F
            );
        }
    }

}
