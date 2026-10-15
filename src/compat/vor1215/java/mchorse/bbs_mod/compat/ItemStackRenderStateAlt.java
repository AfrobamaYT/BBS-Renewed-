package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** ItemStackRenderState.submit von 1.21.9+ vor 1.21.5: zeichnet sofort. Injiziert. */
public interface ItemStackRenderStateAlt
{
    default void submit(PoseStack pose, SubmitNodeCollector sammler, int licht, int overlay, int umriss)
    {
        ((ItemStackRenderState) this).render(pose, SofortSammler.puffer(sammler), licht, overlay);
    }
}
