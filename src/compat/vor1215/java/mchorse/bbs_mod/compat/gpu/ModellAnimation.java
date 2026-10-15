package mchorse.bbs_mod.compat.gpu;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Modellpose aus dem Render-Zustand (1.21.4: setupAnim(Zustand)); vor 1.21.2 ersetzt vor1212 diese Datei. */
final class ModellAnimation
{
    private ModellAnimation()
    {}

    @SuppressWarnings("unchecked")
    static void einrichten(Object modell, Object zustand)
    {
        if (modell instanceof EntityModel<?> entityModel && zustand instanceof EntityRenderState s)
        {
            ((EntityModel<EntityRenderState>) entityModel).setupAnim(s);
        }
    }
}
