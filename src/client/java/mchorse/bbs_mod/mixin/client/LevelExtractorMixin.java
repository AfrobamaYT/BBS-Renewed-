package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin
{
    @WrapMethod(method = "extractPlayerState")
    private void bbs$extractTrackedHands(Camera camera, DeltaTracker delta, float partialTick,
        PlayerRenderState playerState, Operation<Void> original) throws Throwable
    {
        MixinBruecke.rufePflicht("TrackerHandExtraktionLogik", "extrahieren", camera, delta,
            partialTick, playerState, original);
    }
}
