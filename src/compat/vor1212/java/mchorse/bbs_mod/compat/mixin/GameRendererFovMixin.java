package mchorse.bbs_mod.compat.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 computes the world and the hand field of view in Camera.calculateFov/calculateHudFov,
 * where the shared CameraMixin, SprintFOVMixin and GameRendererTrackerSprintFovMixin hook in.
 * Before 26.1 both come from GameRenderer.getFov(camera, partialTick, useFovSetting): true is
 * the world, false the hand. Same bridge calls in the order of those mixins' priorities
 * (1000, 2000, 3000); the two sprint mixins only touch the world value.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin
{
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bbs$blickwinkel(Camera kamera, float teilTick, boolean welt, CallbackInfoReturnable<Double> info)
    {
        /* vor1212: getFov liefert noch double. */
        float wert = info.getReturnValue().floatValue();
        Object neu = MixinBruecke.rufe("KameraLogik", "blickwinkel", welt, wert);

        if (neu instanceof Float f) wert = f;

        if (welt)
        {
            neu = MixinBruecke.rufe("SprintFovLogik", "apply", Minecraft.getInstance(), true, teilTick, (double) wert);

            if (neu instanceof Double d) wert = d.floatValue();

            neu = MixinBruecke.rufe("SprintFovTrackerLogik", "apply",
                com.afrobama.bbstools.client.tracker.SprintFovState.MANUAL_SPRINT, Minecraft.getInstance(), teilTick, (double) wert);

            if (neu instanceof Double d) wert = d.floatValue();
        }

        info.setReturnValue((double) wert);
    }
}
