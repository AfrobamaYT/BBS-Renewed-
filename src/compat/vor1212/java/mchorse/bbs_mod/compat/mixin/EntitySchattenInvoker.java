package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** vor1212 (ersetzt vor1215): 1.21.1 nimmt die Entitaet statt des Zustands. Schatten einer Entity (privat) fuer Modellblock- und Darstellerschatten. */
@Mixin(EntityRenderDispatcher.class)
public interface EntitySchattenInvoker
{
    @Invoker("renderShadow")
    static void bbs$schatten(PoseStack pose, MultiBufferSource puffer, net.minecraft.world.entity.Entity entitaet, float staerke, float teilTick, LevelReader welt, float radius)
    {
        throw new AssertionError();
    }
}
