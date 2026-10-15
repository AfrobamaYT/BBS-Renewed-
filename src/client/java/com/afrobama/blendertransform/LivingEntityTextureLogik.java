package com.afrobama.blendertransform;

import mchorse.bbs_mod.staff.RagdollTextureRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Logik des textur-redirect-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code LivingEntityRendererTextureMixin} (2026-07-26, brücken-umbau).
 * Der handler dort bleibt ein einzeiler, der hierher delegiert und den rückgabewert direkt
 * durchreicht.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link #wähleTextur} nimmt nur Minecraft-typen.</p>
 */
public final class LivingEntityTextureLogik
{
    private LivingEntityTextureLogik() {}

    /**
     * Wählt zwischen der vanilla-textur und einer per-UUID-override (RagdollSwap / Texture
     * Staff), falls für dieses entity registriert.
     *
     * @param entity  das entity aus dem redirect (roher {@link Entity}-typ, wie im original)
     * @param vanilla die von {@code LivingEntityRenderer#getTexture} gelieferte textur
     */
    public static Identifier wähleTextur(Entity entity, Identifier vanilla)
    {
        if (entity instanceof LivingEntity living)
        {
            Identifier override = RagdollTextureRegistry.get(living.getUUID());
            if (override != null) return override;
        }

        return vanilla;
    }
}
