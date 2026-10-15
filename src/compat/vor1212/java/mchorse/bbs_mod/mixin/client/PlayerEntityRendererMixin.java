package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.alt.Zustaende;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/* Vor 1.21.2 bekommen Versatz und Haende den Spieler selbst, keinen Renderzustand. */
@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin
{
    @Inject(
        method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    public void onPositionOffset(AbstractClientPlayer spieler, float teilbild, CallbackInfoReturnable<Vec3> info)
    {
        EntityRenderState zustand = Zustaende.fuer(spieler, teilbild, 0);

        MixinBruecke.rufe("EntitaetVersandLogik", "merken", zustand, spieler);

        if (MixinBruecke.rufeUndAbbrechen("SpielerFormRendererLogik", "versatzUnterdruecken", zustand))
        {
            info.setReturnValue(Vec3.ZERO);
        }
    }

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    public void onRenderRightArmBegin(PoseStack matrices, MultiBufferSource puffer, int licht, AbstractClientPlayer spieler, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielerFormRendererLogik", "arm", matrices, new SofortSammler(puffer), licht, InteractionHand.MAIN_HAND))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    public void onRenderLeftArmBegin(PoseStack matrices, MultiBufferSource puffer, int licht, AbstractClientPlayer spieler, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielerFormRendererLogik", "arm", matrices, new SofortSammler(puffer), licht, InteractionHand.OFF_HAND))
        {
            info.cancel();
        }
    }
}
