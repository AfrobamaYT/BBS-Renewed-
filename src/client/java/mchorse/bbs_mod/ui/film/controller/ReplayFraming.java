package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicBoundingBoxRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.AABB;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Rest geometry and camera distance used when framing a replay. */
final class ReplayFraming
{
    private ReplayFraming()
    {}

    static float bobjDistance(IEntity entity, Camera camera, Area viewport, float margin)
    {
        if (!(entity.getForm() instanceof ModelForm form))
        {
            return 0F;
        }

        ModelInstance instance = ModelFormRenderer.getModel(form);

        if (instance == null || !(instance.getModel() instanceof BOBJModel))
        {
            return 0F;
        }

        AABB box = worldBounds(entity);

        if (box == null)
        {
            return 0F;
        }

        /* A surrounding sphere includes mesh depth at every orbit angle. */
        float radius = (float) new Vector3d(box.w, box.h, box.d).length() * 0.5F * margin;
        float aspect = viewport.w / (float) Math.max(1, viewport.h);
        float halfAngle = camera.fov * 0.5F;

        if (aspect < 1F)
        {
            halfAngle = (float) Math.atan(Math.tan(halfAngle) * aspect);
        }

        return radius / (float) Math.sin(halfAngle);
    }

    static AABB worldBounds(IEntity entity)
    {
        Form form = entity.getForm();

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance instance = ModelFormRenderer.getModel(modelForm);

            if (instance != null)
            {
                AABB box = instance.getModel() instanceof Model cubic
                    ? CubicBoundingBoxRenderer.messen(cubic)
                    : instance.getModel() instanceof BOBJModel bobj ? bobjBounds(bobj) : null;

                if (box != null)
                {
                    Vector3f modelScale = instance.getScale();
                    Vector3f formScale = FormUtilsClient.getRenderer(form).createTransform().scale;
                    float sx = modelScale.x * formScale.x;
                    float sy = modelScale.y * formScale.y;
                    float sz = modelScale.z * formScale.z;

                    return new AABB(box.x * sx, box.y * sy, box.z * sz,
                        box.w * sx, box.h * sy, box.d * sz);
                }
            }
        }

        AABB hitbox = entity.getPickingHitbox();

        return hitbox == null || hitbox.h <= 0 ? null
            : new AABB(-hitbox.w / 2, 0, -hitbox.d / 2, hitbox.w, hitbox.h, hitbox.d);
    }

    /** Rest mesh bounds, in the same model space as BOBJ's position buffer. */
    private static AABB bobjBounds(BOBJModel model)
    {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f point = new Vector3f();
        boolean geometry = false;

        for (BOBJLoader.CompiledData mesh : model.getMeshes())
        {
            float[] positions = mesh.posData;

            for (int i = 0; i + 2 < positions.length; i += 3)
            {
                point.set(positions[i], positions[i + 1], positions[i + 2]);
                min.min(point);
                max.max(point);
                geometry = true;
            }
        }

        return geometry ? new AABB(min.x, min.y, min.z, max.x - min.x, max.y - min.y, max.z - min.z) : null;
    }
}
