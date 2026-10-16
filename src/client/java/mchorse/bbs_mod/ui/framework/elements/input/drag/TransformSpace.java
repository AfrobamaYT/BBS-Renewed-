package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * The reference frame a gizmo edit operates in &mdash; Blender's transform
 * orientation, reduced to the frames that make sense for a per-bone editor.
 * It drives both which axes an X/Y/Z-constrained drag turns/slides along and,
 * for the gizmo, which frame its handles are drawn in.
 *
 * <p>{@link #LOCAL} is the historical behaviour: the gizmo aligns to the bone's
 * own axes and a constrained edit runs along them (the panel also switches to
 * relative local nudges here). {@link #GLOBAL} aligns to the scene's flat axes
 * (the replay's own facing in a film, the model block's rotation when editing a
 * form inside one), {@link #WORLD} to the map's fixed axes regardless, and
 * {@link #VIEW} to the camera's right/up/forward. {@link #PARENT} aligns to
 * the frame the bone's channels compose in &mdash; its parent bone (or the
 * model root / world for a top-level bone). The non-local gizmo placement
 * already carries that frame: the matrix cache's origin flavour is the bone's
 * frame BEFORE its own rotation, so PARENT simply keeps the placed axes
 * (see {@code Gizmo.reorientForSpace}); the model block maps it to GLOBAL,
 * its transform composing straight onto the world. The four-way cycle
 * replaces the old local/global boolean, so {@code space == LOCAL} is exactly
 * the former {@code local} flag and every consumer that only distinguished
 * local from not-local keeps working with {@link #isLocal()}.
 */
public enum TransformSpace
{
    /** The bone's own axes — the gizmo and constrained edits follow the pose. */
    LOCAL(true, Icons.SPACE_LOCAL, 0xF0A63C, UIKeys.TRANSFORMS_SPACE_LOCAL),

    /** The scene's flat axes — a constrained edit runs along fixed X/Y/Z that
     *  never follow the pose. In a film those are the edited replay's OWN axes:
     *  the world frame turned by the replay's facing
     *  ({@code BaseFilmController.getReplayWorldAxes}), so X stays the actor's
     *  left/right however the actor was placed on the map. Hosts with no replay
     *  to face (form editor, model blocks) keep the plain world axes. */
    GLOBAL(true, Icons.SPACE_GLOBAL, 0x4C8DFF, UIKeys.TRANSFORMS_SPACE_GLOBAL),

    /** The camera's right/up/forward — a constrained edit runs in screen space. */
    VIEW(true, Icons.SPACE_VIEW, 0x43C67A, UIKeys.TRANSFORMS_SPACE_VIEW),

    /** The parent's frame — the frame the bone's own channels compose in, and the
     *  frame the parent bone is drawn in. A ring turns the bone about that parent
     *  axis like any other space, so one ring generally moves more than one euler
     *  channel: the channels are a nested stack and only the outermost is a plain
     *  parent axis. The single-channel turn it used to make is {@link #GIMBAL}. */
    PARENT(true, Icons.SPACE_PARENT, 0xB27BE0, UIKeys.TRANSFORMS_SPACE_PARENT),

    /** The map's own axes, indifferent to what the edited thing sits inside —
     *  north stays north however the replay is turned or the model block is
     *  rotated. This is what {@link #GLOBAL} used to be before it was tied to
     *  the container; kept as its own frame because both are genuinely useful:
     *  GLOBAL to work along the actor, WORLD to line something up with the
     *  scene. Declared LAST on purpose — {@code BBSSettings.transformSpace}
     *  persists the ordinal, so a new constant may only be appended. */
    WORLD(true, Icons.GLOBE, 0x2FBFD9, UIKeys.TRANSFORMS_SPACE_WORLD),

    /** The euler channels' own axes, as Maya's and Blender's gimbal orientation: a
     *  ring turns exactly one channel, and the rings are drawn on those axes -- Z
     *  fixed, Y turned by Z, X by Y and Z ({@code GizmoGimbal}) -- so the bone
     *  follows the ring it was grabbed by. Placement, moving and scaling are
     *  PARENT's. Clean curves: one ring, one channel. */
    GIMBAL(true, Icons.ORBIT, 0xE0607A, L10n.lang("bbs.ui.transforms.space.gimbal"));

    /** Whether the frame math is wired up; unimplemented spaces are shown but not selectable. */
    public final boolean implemented;

    /** How the picker shows it: icon (WORLD the globe -- no space_* sprite exists for it), accent colour (RGB, no alpha) and name */
    public final Icon icon;
    public final int color;
    public final IKey label;

    TransformSpace(boolean implemented, Icon icon, int color, IKey label)
    {
        this.implemented = implemented;
        this.icon = icon;
        this.color = color;
        this.label = label;
    }

    /**
     * The order the picker lists the frames in: {@link #PARENT} leads (it is the
     * default, and the frame the channels natively compose in), then the rest,
     * with {@link #WORLD} last as the specialist of the set. Deliberately NOT
     * the enum's own order — {@code BBSSettings.transformSpace} persists the
     * ordinal, so reordering the constants would silently remap everyone's
     * stored choice, while this list is free to change.
     */
    public static final List<TransformSpace> DISPLAY_ORDER = List.of(PARENT, GIMBAL, LOCAL, GLOBAL, VIEW, WORLD);

    /** Whether this is the local frame; the single distinction older consumers make. */
    public boolean isLocal()
    {
        return this == LOCAL;
    }
}
