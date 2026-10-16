package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;

/** The placement gizmo's handles: scaling around the anchor, which stays put on screen (BBS FS 894caa1eb, Wemppy4/bbs-fs, MIT) */
final class PlacementSkalieren
{
    private static final float MIN_SCALE_DISTANCE = 4F;

    private PlacementSkalieren()
    {}

    /** Writes the scale for the pointer at (unitX, unitY) into {@code edited}; false while the grip is too close to the anchor */
    static boolean skalieren(int mode, Placement start, Placement edited, float startUnitX, float startUnitY, float unitX, float unitY, float unitWidth)
    {
            /* Scale around the anchor point, which stays put on screen */
            float anchorX = unitWidth * start.windowX + start.offsetX;
            float anchorY = Placement.HEIGHT * start.windowY + start.offsetY;

            if (mode == UIPlacementGizmo.MODE_SCALE)
            {
                if (UIPlacement.isChained())
                {
                    float startDistance = (float) Math.hypot(startUnitX - anchorX, startUnitY - anchorY);
                    float distance = (float) Math.hypot(unitX - anchorX, unitY - anchorY);

                    if (startDistance < MIN_SCALE_DISTANCE)
                    {
                        return false;
                    }

                    float ratio = distance / startDistance;

                    edited.scaleX = start.scaleX * ratio;
                    edited.scaleY = start.scaleY * ratio;
                }
                else
                {
                    /* With shift held the corner stretches each axis on its own */
                    float startX = Math.abs(startUnitX - anchorX);
                    float startY = Math.abs(startUnitY - anchorY);

                    if (startX >= MIN_SCALE_DISTANCE)
                    {
                        edited.scaleX = start.scaleX * (Math.abs(unitX - anchorX) / startX);
                    }

                    if (startY >= MIN_SCALE_DISTANCE)
                    {
                        edited.scaleY = start.scaleY * (Math.abs(unitY - anchorY) / startY);
                    }
                }
            }
            else if (mode == UIPlacementGizmo.MODE_SCALE_X)
            {
                float startDistance = Math.abs(startUnitX - anchorX);

                if (startDistance < MIN_SCALE_DISTANCE)
                {
                    return false;
                }

                float ratio = Math.abs(unitX - anchorX) / startDistance;

                edited.scaleX = start.scaleX * ratio;

                if (UIPlacement.isChained())
                {
                    edited.scaleY = start.scaleY * ratio;
                }
            }
            else
            {
                float startDistance = Math.abs(startUnitY - anchorY);

                if (startDistance < MIN_SCALE_DISTANCE)
                {
                    return false;
                }

                float ratio = Math.abs(unitY - anchorY) / startDistance;

                edited.scaleY = start.scaleY * ratio;

                if (UIPlacement.isChained())
                {
                    edited.scaleX = start.scaleX * ratio;
                }
            }

        return true;
    }
}
