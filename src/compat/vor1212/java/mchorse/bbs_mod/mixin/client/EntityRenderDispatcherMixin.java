package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.alt.Zustaende;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import mchorse.bbs_mod.compat.gpu.ZustandsLicht;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vor 1.21.2 (ersetzt die vor1215-Fassung): 1.21.1 zeichnet in EntityRenderer.render(Entitaet, ...).
 * Ein Zustand aus der Entitaet (Zustaende) traegt beide Haelften des gemeinsamen Hakens:
 * merken und gezeichnet.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin
{
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void bbs$umschliesseRender(EntityRenderer<Entity> renderer, Entity entitaet, float gierung, float teilTick, PoseStack matrices,
        MultiBufferSource puffer, int licht, Operation<Void> original)
    {
        EntityRenderState zustand = Zustaende.fuer(entitaet, teilTick, licht);

        MixinBruecke.rufe("EntitaetVersandLogik", "merken", zustand, entitaet);
        ZustandsLicht.aktuell = licht;

        if (!MixinBruecke.rufeUndAbbrechen("EntitaetVersandLogik", "gezeichnet", zustand, matrices, new SofortSammler(puffer)))
        {
            original.call(renderer, entitaet, gierung, teilTick, matrices, puffer, licht);
        }
    }
}
