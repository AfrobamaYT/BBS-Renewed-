package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve the meaning of GLFW's saved unbound keyboard value after the SDL switch. */
@Mixin(InputConstants.class)
public abstract class InputConstantsLegacyUnknownMixin
{
    @ModifyReturnValue(method = "getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;",
        at = @At("RETURN"), require = 1)
    private static InputConstants.Key bbs$legacyUnknown(InputConstants.Key key) throws Throwable
    {
        return (InputConstants.Key) MixinBruecke.rufePflicht("InputKeyMigrationLogik", "legacyUnknown", key);
    }
}
