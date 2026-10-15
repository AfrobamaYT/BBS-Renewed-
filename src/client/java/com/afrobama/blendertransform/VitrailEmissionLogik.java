package com.afrobama.blendertransform;

import dev.vitrail.render.storage.BbsEmissionStorage;
import dev.vitrail.render.storage.StorageBuffers;

/** Required descriptor bridge; BBS allocations remain separate from pack-owned bufferObject declarations. */
public final class VitrailEmissionLogik
{
    private VitrailEmissionLogik() {}

    public static boolean named(boolean original, String name)
    {
        return original || BbsEmissionStorage.named(name);
    }

    public static StorageBuffers.Bound bound(StorageBuffers.Bound original, String name)
    {
        if (!BbsEmissionStorage.named(name)) return original;
        if (original != null) throw new IllegalStateException("Shaderpack collides with BBS emission storage");
        return BbsEmissionStorage.bound(name);
    }
}
