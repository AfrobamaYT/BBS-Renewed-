package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePosition;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.modules.UIAngleModule;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointsModule;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.interps.Interps;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Vector2d;

/**
 * Path clip panel
 *
 * This panel has the most modules used. It's responsible for editing path
 * clip. It uses point and angle modules to edit a position which is picked
 * from the points module. Interpolation module is used to modify path clip's
 * interpolation methods.
 */
public class UIPathClip extends UIClip<PathClip>
{
    public UIPointModule point;
    public UIAngleModule angle;
    public UIButton interpPoint;
    public UIButton interpAngle;

    public UIToggle autoCenter;
    public UITrackpad circularX;
    public UITrackpad circularZ;

    public UIPointsModule points;

    public ValuePosition position;

    public UIPathClip(PathClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.point = new UIPointModule(editor);
        this.angle = new UIAngleModule(editor);
        this.interpPoint = new UIButton(UIKeys.CAMERA_PANELS_POINT, (b) ->
        {
            UICameraUtils.interps(this.getContext(), Interps.MAP.values(), this.clip.interpolationPoint.get(), this.clip.interpolationPoint::set);
        });
        this.interpPoint.tooltip(new InterpolationTooltip(1F, 0.5F, () -> this.clip.interpolationPoint.get()));
        this.interpAngle = new UIButton(UIKeys.CAMERA_PANELS_ANGLE, (b) ->
        {
            UICameraUtils.interps(this.getContext(), Interps.MAP.values(), this.clip.interpolationAngle.get(), this.clip.interpolationAngle::set);
        });
        this.interpAngle.tooltip(new InterpolationTooltip(1F, 0.5F, () -> this.clip.interpolationAngle.get()));

        this.autoCenter = new UIToggle(UIKeys.CAMERA_PANELS_AUTO_CENTER, (b) ->
        {
            this.clip.circularAutoCenter.set(b.getValue());

            if (!b.getValue())
            {
                Vector2d center = this.clip.calculateCenter(new Vector2d());

                this.circularX.setValue(center.x);
                this.circularZ.setValue(center.y);
                this.clip.circularX.set(center.x);
                this.clip.circularZ.set(center.y);
            }
        });

        this.circularX = new UITrackpad((value) -> this.clip.circularX.set(value));
        this.circularX.tooltip(UIKeys.CAMERA_PANELS_CIRCULAR_X);
        this.circularZ = new UITrackpad((value) -> this.clip.circularZ.set(value));
        this.circularZ.tooltip(UIKeys.CAMERA_PANELS_CIRCULAR_Z);

        this.points = new UIPointsModule(this.editor, this::pickPoint);
        this.points.h(20);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UIClip.label(UIKeys.CAMERA_PANELS_PATH_POINTS).marginTop(12));
        this.panels.add(this.points, UI.row(this.interpPoint, this.interpAngle).marginBottom(6));
        this.panels.add(this.point.marginTop(12), this.angle.marginTop(6));
        this.panels.add(UIClip.label(UIKeys.CAMERA_PANELS_CIRCULAR).marginTop(6), this.autoCenter, UI.row(this.circularX, this.circularZ));
        this.panels.context((menu) -> UICameraUtils.positionContextMenu(menu, editor, this.position));
    }

    private void updateSpeedPanel()
    {
        this.resize();
    }

    private ValuePosition getPosition(int index)
    {
        BaseValue value = this.clip.points.getAll().get(index);

        return value instanceof ValuePosition ? (ValuePosition) value : null;
    }

    public void pickPoint(int index)
    {
        this.points.setIndex(index);
        this.position = this.getPosition(index);

        this.point.fill(this.position.getPoint());
        this.angle.fill(this.position.getAngle());

        if (!Window.isCtrlPressed())
        {
            int offset = this.clip.getTickForPoint(index);

            if (offset == this.clip.duration.get())
            {
                offset -= 1;
            }

            this.editor.setCursor(this.clip.tick.get() + offset);
        }
    }

    @Override
    public void editClip(Position position)
    {
        if (this.position != null)
        {
            this.position.set(position);

            super.editClip(position);
        }
    }

    @Override
    public void fillData()
    {
        super.fillData();

        int duration = this.clip.duration.get();
        int offset = MathUtils.clamp(this.editor.getCursor() - this.clip.tick.get(), 0, duration);
        int points = this.clip.size();
        int index = (int) ((offset / (float) duration) * points);

        index = MathUtils.clamp(index, 0, points - 1);

        this.points.fill(this.clip);
        this.position = this.getPosition(index);
        this.points.index = index;

        this.point.fill(this.position.getPoint());
        this.angle.fill(this.position.getAngle());
        this.updateSpeedPanel();

        this.autoCenter.setValue(this.clip.circularAutoCenter.get());
        this.circularX.setValue(this.clip.circularX.get());
        this.circularZ.setValue(this.clip.circularZ.get());

        this.points.index = index;
    }
}