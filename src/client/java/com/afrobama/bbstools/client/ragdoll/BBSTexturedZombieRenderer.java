// Coded by Afrobama/Claude — drop-in zombie renderer that honours
// the BBS RagdollTextureRegistry so /ragdollswap Player's zombie
// substitute carries the player's BBS skin into the PhysicsMod
// ragdoll.
package com.afrobama.bbstools.client.ragdoll;

import mchorse.bbs_mod.staff.RagdollTextureRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Drop-in replacement for vanilla {@code ZombieEntityRenderer} that
 * returns a BBS-derived {@link Identifier} when the entity has a
 * per-UUID override registered via {@link RagdollTextureRegistry}.
 *
 * <p>BBS already ships the same wrapper for {@code Villager} and
 * {@code Pillager} renderers via its staff PNG flow. Zombies were
 * missing — which is exactly the entity type
 * {@code /ragdollswap Player} substitutes to, so the BBS skin
 * override never landed on the resulting ragdoll. This wrapper
 * closes that gap.</p>
 *
 * <p>For zombies WITHOUT an override registered, this falls through
 * to the vanilla zombie texture — every other zombie in the world
 * still renders correctly.</p>
 */
public class BBSTexturedZombieRenderer extends ZombieRenderer
{
    public BBSTexturedZombieRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(Zombie entity)
    {
        Identifier override = RagdollTextureRegistry.get(entity.getUUID());

        return override != null ? override : super.getTextureLocation(entity);
    }

}
