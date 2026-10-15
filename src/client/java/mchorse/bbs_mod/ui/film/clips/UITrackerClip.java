package mchorse.bbs_mod.ui.film.clips;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.l10n.keys.IKey;
import org.joml.Vector3d;

/* MERGE fsgh/2.5: upstream hat den typ-parameter auf TrackerClientClip gezogen, weil das
 * neue editClip ueber isActive/isEvaluated/getUnderneath rechnet — methoden, die nur die
 * CLIENT-klasse hat. Wir bleiben bei TrackerClip, denn bbstools' BBSToolsAddCameraClip liegt
 * in src/main und erbt von TrackerClip; er KANN kein TrackerClientClip sein, benutzt aber
 * denselben editor (UIBBSToolsAddCameraClip). Statt des typ-parameters entscheidet darum
 * {@link #tracker()}: kein client-clip -> keine kamera-inversion, der rest der maske bleibt. */
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

    public UIToggle showHand;
    public UIToggle showHud;

    public UITrackerClip(TrackerClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    /**
     * The clip as the client-side tracker, or {@code null} when this editor is
     * driving a subclass that only reaches {@link TrackerClip} (bbstools' extra
     * camera clip). Everything the camera inversion needs — the active bitmask,
     * the evaluated position of the clips underneath — lives on the client class.
     */
    private TrackerClientClip tracker()
    {
        return this.clip instanceof TrackerClientClip client ? client : null;
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
        this.fov = new UITrackpad((v) -> this.editor.editMultiple(this.clip.fov, (value) -> value.set(v.floatValue())));
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, b -> this.editor.editMultiple(this.clip.lookAt, (v) -> v.set(b.getValue())));
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, b -> this.editor.editMultiple(this.clip.relative, (v) -> v.set(b.getValue())));
        this.active = new UIBitToggle((value) -> this.editor.editMultiple(this.clip.active, (v) -> v.set(value))).all();

        this.showHand = new UIToggle(IKey.constant("Show hand"), b -> this.editor.editMultiple(this.clip.showHand, (v) -> v.set(b.getValue())));
        this.showHud = new UIToggle(IKey.constant("Show HUD"), b -> this.editor.editMultiple(this.clip.showHud, (v) -> v.set(b.getValue())));
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
    public boolean authorsCamera()
    {
        return true;
    }

    /**
     * Turn a camera the user placed (by flying, or by orbiting the viewport) into
     * the offset and angle that reproduce it, so the tracker is authored the same
     * way overwriting clips are, instead of through its trackpads only.
     *
     * <p>The bone's frame is resolved fresh against the pose on screen, so
     * scrubbing while flying stays honest. Relative mode is the exception: the
     * camera's travel is measured against the clips underneath, which aren't
     * evaluated while flying, so the last evaluated one is used.</p>
     */
    @Override
    public void editClip(Position position)
    {
        this.applyCameraPosition(position);

        super.editClip(position);
    }

    private void applyCameraPosition(Position position)
    {
        TrackerFrame frame = this.resolveFrame(position);

        if (frame == null || this.tracker() == null)
        {
            return;
        }

        Point offset = this.clip.offset.get();
        Point angle = this.clip.angle.get();

        boolean lookAt = this.clip.lookAt.get();

        /* Look-at doesn't place the camera — its offset moves the point being
         * looked at — so only the framing offset is authorable there */
        if (!lookAt && this.isActive(0, 1, 2))
        {
            Vector3d current = frame.position(offset);
            Point solved = frame.solveOffset(
                this.pick(0, position.point.x, current.x),
                this.pick(1, position.point.y, current.y),
                this.pick(2, position.point.z, current.z)
            );

            if (solved != null)
            {
                this.clip.offset.set(solved);
            }
        }

        if (this.isActive(3, 4, 5))
        {
            Angle current = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
            float yaw = this.pick(3, position.angle.yaw, current.yaw);
            float pitch = this.pick(4, position.angle.pitch, current.pitch);
            float roll = this.pick(5, position.angle.roll, current.roll);

            this.clip.angle.set(lookAt ? frame.solveLookAtAngles(offset, yaw, pitch, roll) : frame.solveAngles(yaw, pitch, roll));
        }

        /* Only when it actually moved: an unchanged value still notifies, and a
         * value that joins and leaves the frame's edit breaks undo merging */
        if (this.tracker().isActive(6) && position.angle.fov != this.clip.fov.get())
        {
            this.clip.fov.set(position.angle.fov);
        }
    }

    private TrackerFrame resolveFrame(Position position)
    {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        UIContext context = this.getContext();
        TrackerClientClip tracker = this.tracker();
        int selector = this.clip.selector.get();

        if (panel == null || context == null || tracker == null || selector < 0)
        {
            return null;
        }

        IntObjectMap<IEntity> entities = panel.getController().getEntities();
        TrackerFrame frame = TrackerFrame.resolve(
            entities,
            entities.get(selector),
            this.clip.group.get(),
            position.point.x,
            position.point.y,
            position.point.z,
            context.getTransition()
        );

        if (frame != null && this.clip.relative.get())
        {
            if (!tracker.isEvaluated())
            {
                return null;
            }

            frame.relative(tracker.getUnderneath(), this.clip.position);
        }

        return frame;
    }

    /**
     * A channel the tracker doesn't drive ({@link TrackerClientClip#isActive})
     * shows whatever the clips underneath put there, so solving against it would
     * fold a foreign value into the offset. Those channels keep what the tracker
     * currently produces instead, which the inverse maps back to itself.
     */
    /** Whether the tracker drives any of the given channels at all. */
    private boolean isActive(int... bits)
    {
        TrackerClientClip tracker = this.tracker();

        if (tracker == null)
        {
            return false;
        }

        for (int bit : bits)
        {
            if (tracker.isActive(bit))
            {
                return true;
            }
        }

        return false;
    }

    private float pick(int bit, float camera, float current)
    {
        return this.isActive(bit) ? camera : current;
    }

    private double pick(int bit, double camera, double current)
    {
        return this.isActive(bit) ? camera : current;
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
