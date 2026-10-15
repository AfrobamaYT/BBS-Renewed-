package com.afrobama.bbstools.client.ragdoll;

import mchorse.bbs_mod.staff.RagdollTextureRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Vor 1.21.2 (ersetzt die gemeinsame Fassung): kein Render-Zustand, die Textur wird direkt mit
 * der Entitaet erfragt. Dieselbe Quelle (RagdollTextureRegistry), Vanilla als Rueckfall.
 */
public class BBSTexturedZombieRenderer extends ZombieRenderer
{
    public BBSTexturedZombieRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity)
    {
        ResourceLocation override = RagdollTextureRegistry.get(entity.getUUID());

        return override != null ? override : super.getTextureLocation(entity);
    }
}
