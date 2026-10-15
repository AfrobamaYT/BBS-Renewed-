package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before 26.1 (replaces the shared GameRendererMixin): the same bridge calls on 1.21.11's
 * GameRenderer -- bobView/bobHurt take (PoseStack, partial tick), the world pass is
 * LevelRenderer.renderLevel, the GUI draws with GuiRenderer.render(fog). The film-buffer
 * redirect of 26.2's GameRenderer.mainRenderTarget() sits on Minecraft.getMainRenderTarget()
 * there (src/compat/vor261 MinecraftFilmpufferMixin).
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(PoseStack matrices, float teilTick, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielRendererLogik", "kopfwackeln", matrices))
        {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(PoseStack matrices, float teilTick, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielRendererLogik", "trefferneigung", matrices))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    public void onRenderHand(CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielRendererLogik", "handAnfang"))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    public void onRenderHandEnd(CallbackInfo info)
    {
        MixinBruecke.rufe("SpielRendererLogik", "handEnde");
    }

    @Inject(at = @At("HEAD"), method = "renderLevel")
    private void onWorldRenderBegin(CallbackInfo callbackInfo)
    {
        MixinBruecke.rufe("SpielRendererLogik", "weltAnfang");
    }

    @Inject(at = @At("RETURN"), method = "renderLevel")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        MixinBruecke.rufe("SpielRendererLogik", "weltEnde");
    }

    @Inject(method = "renderLevel", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = mchorse.bbs_mod.compat.alt.MixinZiele.WELT_ZEICHNEN))
    private void onAfterLevel(CallbackInfo info)
    {
        MixinBruecke.rufe("SpielRendererLogik", "nachDerWelt", ((GameRenderer) (Object) this).getLevelRenderState().cameraRenderState);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void onBeforeHudRendering(CallbackInfo info)
    {
        MixinBruecke.rufe("SpielRendererLogik", "vorDemBildschirm");
    }

    /* Vor 1.21.5 zeichnet die GUI ab dem neuen GuiGraphics bis zu dessen letztem flush (Ordinal 1). */
    @Inject(method = "render", require = 1, at = @At(value = "NEW", target = "net/minecraft/client/gui/GuiGraphics"))
    private void bbs$beforeGuiDraw(CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("SpielRendererLogik", "vorGuiZeichnen");
    }

    /* vor1212: 1.21.1 ruft flush in render nur einmal (am Ende), 1.21.4 zweimal. */
    @Inject(method = "render", require = 1, at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void bbs$afterGuiDraw(CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("SpielRendererLogik", "nachGuiZeichnen");
    }
}
