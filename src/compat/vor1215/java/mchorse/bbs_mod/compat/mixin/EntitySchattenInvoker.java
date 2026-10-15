package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** vor1215: 1.21.4s Schatten einer Entity (privat) fuer Modellblock- und Darstellerschatten. */
@Mixin(EntityRenderDispatcher.class)
public interface EntitySchattenInvoker
{
    @Invoker("renderShadow")
    static void bbs$schatten(PoseStack pose, MultiBufferSource puffer, EntityRenderState zustand, float staerke, float teilTick, LevelReader welt, float radius)
    {
        throw new AssertionError();
    }
}
