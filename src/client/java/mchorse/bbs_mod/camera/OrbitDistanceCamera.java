package mchorse.bbs_mod.camera;

import mchorse.bbs_mod.utils.Factor;
import org.joml.Vector3d;

public class OrbitDistanceCamera extends OrbitCamera
{
    public final Factor distance = new Factor();

    @Override
    public Vector3d getFinalPosition()
    {
        return this.finalPosition.set(this.position).add(this.rotateVector(0, 0, -1).mul((float) this.distance.getValue()));
    }

    @Override
    public boolean scroll(int scroll)
    {
        if (this.dragging >= 0)
        {
            return false;
        }

        int factor = this.distance.getX();

        this.distance.addX(scroll);

        return this.distance.getX() != factor;
    }
}