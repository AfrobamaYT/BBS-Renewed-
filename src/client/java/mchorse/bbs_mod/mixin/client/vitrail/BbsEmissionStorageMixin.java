package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vitrail.render.storage.StorageBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StorageBuffers.class)
public abstract class BbsEmissionStorageMixin
{
    @ModifyReturnValue(method = "named", at = @At("RETURN"), remap = false)
    private static boolean bbs$named(boolean original, String name) throws Throwable
    { return (boolean) MixinBruecke.rufePflicht("VitrailEmissionLogik", "named", original, name); }

    @ModifyReturnValue(method = "bound", at = @At("RETURN"), remap = false)
    private static StorageBuffers.Bound bbs$bound(StorageBuffers.Bound original, String name) throws Throwable
    { return (StorageBuffers.Bound) MixinBruecke.rufePflicht("VitrailEmissionLogik", "bound", original, name); }
}
