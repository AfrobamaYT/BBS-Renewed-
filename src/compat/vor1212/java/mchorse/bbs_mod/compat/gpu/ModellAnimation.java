package mchorse.bbs_mod.compat.gpu;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/** Vor 1.21.2: setupAnim nimmt die Entitaet und ihre Gang-/Blickwerte, nicht den Zustand. */
final class ModellAnimation
{
    private ModellAnimation()
    {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void einrichten(Object modell, Object zustand)
    {
        if (modell instanceof EntityModel entityModel && zustand instanceof EntityRenderState s && s.bbsEntitaet != null)
        {
            LivingEntityRenderState l = s instanceof LivingEntityRenderState lebend ? lebend : null;
            Entity e = s.bbsEntitaet;

            entityModel.setupAnim(e, l == null ? 0F : l.walkAnimationPos, l == null ? 0F : l.walkAnimationSpeed, s.ageInTicks,
                l == null ? 0F : l.yRot, l == null ? e.getXRot() : l.xRot);
        }
    }
}
