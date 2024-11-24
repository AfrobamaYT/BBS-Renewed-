package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

public class OrbitFilmCameraController implements ICameraController
{
    private UIFilmController controller;

    public boolean enabled;

    private boolean orbiting;
    private Vector2f rotation = new Vector2f();
    private Vector2i last = new Vector2i();
    private Vector3f position = new Vector3f();

    protected Vector3i velocityPosition = new Vector3i();

    protected float low = 0.05F;
    protected float normal = 0.25F;
    protected float high = 1F;

    public OrbitFilmCameraController(UIFilmController controller)
    {
        this.controller = controller;
    }

    public void start(UIContext context)
    {
        this.orbiting = true;
        this.last.set(context.mouseX, context.mouseY);
    }

    public void stop()
    {
        this.orbiting = false;
    }

    public boolean keyPressed(UIContext context)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

        int x = this.getFactor(context, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, this.velocityPosition.x);
        int y = this.getFactor(context, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN, this.velocityPosition.y);
        int z = this.getFactor(context, Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, this.velocityPosition.z);
        boolean changed = x != this.velocityPosition.x || y != this.velocityPosition.y || z != this.velocityPosition.z;

        this.velocityPosition.set(x, y, z);

        return changed;
    }

    protected int getFactor(UIContext context, KeyCombo positive, KeyCombo negative, int x)
    {
        if (context.isPressed(positive.getMainKey()))
        {
            return 1;
        }
        else if (context.isPressed(negative.getMainKey()))
        {
            return -1;
        }
        else if (
            (context.isReleased(positive.getMainKey()) && x > 0) ||
            (context.isReleased(negative.getMainKey()) && x < 0)
        ) {
            return 0;
        }

        return x;
    }

    public void handleOrbiting(UIContext context)
    {
        if (this.orbiting)
        {
            int x = context.mouseX;
            int y = context.mouseY;

            this.rotation.add(
                -(y - this.last.y) / (50F * (1 / this.getSpeed())),
                -(x - this.last.x) / (50F * (1 / this.getSpeed()))
            );

            this.last.set(x, y);
        }
    }

    public boolean update(UIContext context)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

        boolean changed = false;

        if (this.velocityPosition.lengthSquared() > 0)
        {
            this.position.add(this.rotateVector(-this.velocityPosition.x, this.velocityPosition.y, -this.velocityPosition.z, this.rotation.y, this.rotation.x).mul(this.getSpeed()));

            changed = true;
        }

        return changed;
    }

    protected float getSpeed()
    {
        return (Window.isCtrlPressed() ? this.high : (Window.isAltPressed() ? this.low : this.normal));
    }

    protected Vector3f rotateVector(float x, float y, float z)
    {
        return this.rotateVector(x, y, z, MathUtils.PI - this.rotation.y, this.rotation.x);
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch)
    {
        Matrix3f rotation = new Matrix3f();
        Vector3f rotate = new Vector3f(x, y, z);

        rotation.rotateY(yaw);

        if (!BBSSettings.editorHorizontalFlight.get())
        {
            rotation.rotateX(pitch);
        }

        rotation.transform(rotate);

        return rotate;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        IEntity entity = this.controller.getCurrentEntity();

        if (entity != null)
        {
            float renderYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition) + 180F);
            Vector3f offset = this.rotateVector(this.position.x, this.position.y, this.position.z, renderYaw, 0F);

            camera.position.set(entity.getPrevX(), entity.getPrevY(), entity.getPrevZ());
            camera.position.lerp(new Vector3d(entity.getX(), entity.getY(), entity.getZ()), transition);
            camera.position.add(offset);
            camera.position.add(0, entity.getPickingHitbox().h / 2, 0);
            camera.rotation.set(-this.rotation.x, -(this.rotation.y + renderYaw), 0);
        }
    }

    @Override
    public int getPriority()
    {
        return 20;
    }

    public void reset()
    {
        this.position.set(0F, 0F, -4F);
        this.rotation.set(0F, Math.PI);
    }
}