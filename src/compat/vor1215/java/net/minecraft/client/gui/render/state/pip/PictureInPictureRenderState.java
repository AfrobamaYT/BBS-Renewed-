package net.minecraft.client.gui.render.state.pip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.ScreenArea;
import org.joml.Matrix3x2f;

/** BBS-Shim fuer Minecraft vor 1.21.6: Zustand eines Bild-im-Bild-Elements (wie 1.21.11). */
public interface PictureInPictureRenderState extends ScreenArea
{
    Matrix3x2f IDENTITY_POSE = new Matrix3x2f();

    int x0();
    int x1();
    int y0();
    int y1();
    float scale();

    default Matrix3x2f pose()
    {
        return IDENTITY_POSE;
    }

    ScreenRectangle scissorArea();

    static ScreenRectangle getBounds(int x0, int y0, int x1, int y1, ScreenRectangle schere)
    {
        ScreenRectangle bereich = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);

        return schere != null ? schere.intersection(bereich) : bereich;
    }
}
