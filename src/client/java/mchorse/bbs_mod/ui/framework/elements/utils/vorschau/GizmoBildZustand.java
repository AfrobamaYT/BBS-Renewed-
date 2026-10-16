package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import mchorse.bbs_mod.ui.utils.GizmoGimbal;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;

/** The submitted gizmo frame, including its own transform space and Euler ring axes. */
public record GizmoBildZustand(
    Matrix4f projektion,
    Matrix4f ansicht,
    float viewportSkalierung,
    int x0,
    int y0,
    int x1,
    int y1,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds,
    GizmoGimbal.Lage ausrichtung
) implements PictureInPictureRenderState
{
    @Override
    public float scale()
    {
        return mchorse.bbs_mod.BBSModClient.getGUIScale();
    }
}
