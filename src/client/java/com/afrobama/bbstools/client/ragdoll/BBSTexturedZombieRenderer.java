// Coded by Afrobama/Claude — drop-in zombie renderer that honours
// the BBS RagdollTextureRegistry so /ragdollswap Player's zombie
// substitute carries the player's BBS skin into the PhysicsMod
// ragdoll.
package com.afrobama.bbstools.client.ragdoll;

import mchorse.bbs_mod.staff.RagdollTextureRegistry;
import mchorse.bbs_mod.compat.gpu.ZustandsSchluessel;
import mchorse.bbs_mod.compat.gpu.ZustandsSchluessel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Drop-in replacement for vanilla {@code ZombieEntityRenderer} that
 * returns a BBS-derived {@link ResourceLocation} when the entity has a
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
 *
 * <p>⚠️ 26.2 — DIE TEXTUR HAENGT AM ZUSTAND, NICHT MEHR AN DER ENTITY.
 * {@code getTextureLocation} bekommt einen {@code ZombieRenderState}, und der traegt keine
 * UUID. Nachschlagen kann man also nur beim EINSAMMELN, wo die Entity noch da ist. Der
 * Fund wird dazu an den Zustand geheftet (Fabrics {@code mchorse.bbs_mod.compat.gpu.ZustandsSchluessel}) und beim
 * Zeichnen wieder gelesen — das ist das vorgesehene Muster fuer eigene Zustandsdaten und
 * kommt ohne einen zusaetzlichen Zustands-Untertyp aus.</p>
 */
public class BBSTexturedZombieRenderer extends ZombieRenderer
{
    /** Die fuer diesen Zombie in DIESEM Bild gefundene Override-Textur, oder {@code null}. */
    private static final mchorse.bbs_mod.compat.gpu.ZustandsSchluessel<ResourceLocation> BBS_TEXTUR = mchorse.bbs_mod.compat.gpu.ZustandsSchluessel.create(() -> "bbstools:ragdoll_textur");

    public BBSTexturedZombieRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);
    }

    @Override
    public void extractRenderState(Zombie entity, ZombieRenderState zustand, float teilTick)
    {
        super.extractRenderState(entity, zustand, teilTick);

        /* ⚠️ IMMER setzen, auch auf null: Zustandsobjekte werden wiederverwendet. Ein nur bei
         * Treffer gesetzter Schluessel wuerde die Textur des vorherigen Zombies weitertragen. */
        mchorse.bbs_mod.compat.gpu.ZustandsSchluessel.setData(zustand, BBS_TEXTUR, RagdollTextureRegistry.get(entity.getUUID()));
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieRenderState zustand)
    {
        ResourceLocation override = mchorse.bbs_mod.compat.gpu.ZustandsSchluessel.getData(zustand, BBS_TEXTUR);

        return override != null ? override : super.getTextureLocation(zustand);
    }
}
