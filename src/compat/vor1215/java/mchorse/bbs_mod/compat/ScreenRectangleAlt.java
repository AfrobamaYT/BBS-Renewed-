package mchorse.bbs_mod.compat;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/** vor1215: 1.21.6+ ScreenRectangle.transformMaxBounds(2D-Pose): achsenparallele Huelle der Ecken. */
public interface ScreenRectangleAlt
{
    default ScreenRectangle transformMaxBounds(Matrix3x2fc pose)
    {
        ScreenRectangle r = (ScreenRectangle) this;
        Vector2f a = pose.transformPosition(r.left(), r.top(), new Vector2f());
        Vector2f b = pose.transformPosition(r.right(), r.top(), new Vector2f());
        Vector2f c = pose.transformPosition(r.left(), r.bottom(), new Vector2f());
        Vector2f d = pose.transformPosition(r.right(), r.bottom(), new Vector2f());
        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)), maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)), maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));

        return new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));
    }
}
