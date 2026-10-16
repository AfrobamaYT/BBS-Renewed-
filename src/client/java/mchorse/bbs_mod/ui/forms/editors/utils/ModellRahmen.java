package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicBoundingBoxRenderer;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.AABB;
import org.joml.Vector3f;

/**
 * Frames a model in a preview from its measured size: look at the middle of its rest-pose
 * bounds, from a distance that fits its largest side. The fixed start (distance 15 = 2.25
 * blocks, looking at y 1) only suits player-sized models -- a tall BOBJ showed only its feet
 * (audit 2026-10-13, finding 62). Cubic models are measured with
 * {@link CubicBoundingBoxRenderer#messen}, BOBJ models over their mesh vertices.
 */
public final class ModellRahmen
{
    private ModellRahmen()
    {}

    /** Frames {@code form}'s model in {@code vorschau}; false while the model is not loaded. */
    public static boolean rahmen(UIModelRenderer vorschau, ModelForm form)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);
        AABB box = instance == null ? null : messen(instance);

        if (box == null)
        {
            return instance != null;
        }

        Vector3f skal = instance.getScale();
        float w = (float) box.w * skal.x;
        float h = (float) box.h * skal.y;
        float d = (float) box.d * skal.z;
        float groesste = Math.max(h, Math.max(w, d));
        /* The preview's distance is x^2 / 100 blocks (UIModelRenderer.distance). One block at
         * least, so a tiny model is not pressed against the lens. */
        float abstand = Math.max(1F, groesste * 1.2F);

        vorschau.setPosition((float) (box.x + box.w / 2) * skal.x, (float) (box.y + box.h / 2) * skal.y,
            (float) (box.z + box.d / 2) * skal.z);
        vorschau.setDistance(Math.round((float) Math.sqrt(abstand * 100F)));

        return true;
    }

    private static AABB messen(ModelInstance instance)
    {
        if (instance.getModel() instanceof Model cubic)
        {
            return CubicBoundingBoxRenderer.messen(cubic);
        }

        if (instance.getModel() instanceof BOBJModel bobj)
        {
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            boolean etwas = false;

            for (BOBJLoader.CompiledData netz : bobj.getMeshes())
            {
                float[] pos = netz.posData;

                for (int i = 0; pos != null && i + 2 < pos.length; i += 3)
                {
                    for (int a = 0; a < 3; a++)
                    {
                        min[a] = Math.min(min[a], pos[i + a]);
                        max[a] = Math.max(max[a], pos[i + a]);
                    }

                    etwas = true;
                }
            }

            return etwas ? new AABB(min[0], min[1], min[2], max[0] - min[0], max[1] - min[1], max[2] - min[2]) : null;
        }

        return null;
    }
}
