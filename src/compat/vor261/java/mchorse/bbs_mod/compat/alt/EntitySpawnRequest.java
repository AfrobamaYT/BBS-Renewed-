package mchorse.bbs_mod.compat.alt;

import net.minecraft.world.entity.EntitySpawnReason;

/**
 * 26.2's EntitySpawnRequest; before 26.1 EntityType.create takes the reason alone, so the shared
 * code's "new EntitySpawnRequest(reason, ignoreChecks)" becomes {@link #grund} there
 * (stonecutter-regeln.gradle). Not injected into EntityType: that class is common code, and the
 * client-only compat layer must not leak into the server source set.
 */
public final class EntitySpawnRequest
{
    private EntitySpawnRequest()
    {}

    public static EntitySpawnReason grund(EntitySpawnReason reason, boolean ignoreChecks)
    {
        return reason;
    }
}
