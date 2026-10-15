package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before 26.1 (replaces the shared GuiItemAtlasMixin, which targets 26.2's GuiItemAtlas): the GUI
 * item atlas lives in GuiRenderer, a slot is drawn by renderItemToAtlas(state, pose, x, y, size).
 * Same bridge calls as the shared mixin, around the item's submit up to the end of the batch
 * (1.21.11 draws the submits right after, still inside the method).
 */
@Mixin(GuiRenderer.class)
public abstract class GuiItemAtlasMixin
{
    @Shadow
    private GpuTexture itemsAtlas;
    @Shadow
    private GpuTextureView itemsAtlasView;
    @Shadow
    private GpuTextureView itemsAtlasDepthView;

    @Inject(method = "renderItemToAtlas", require = 1, at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
        target = "Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void bbs$vorDemSlot(TrackingItemStackRenderState zustand, PoseStack pose, int x, int y, int groesse, CallbackInfo info)
    {
        MixinBruecke.rufe("GegenstandsAtlasLogik", "vorDemSlot", this.itemsAtlasView, this.itemsAtlasDepthView,
            x / groesse, y / groesse, groesse, this.itemsAtlas.getHeight(0));
    }

    @Inject(method = "renderItemToAtlas", require = 1, at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableScissorForRenderTypeDraws()V"))
    private void bbs$nachDemSlot(TrackingItemStackRenderState zustand, PoseStack pose, int x, int y, int groesse, CallbackInfo info)
    {
        MixinBruecke.rufe("GegenstandsAtlasLogik", "nachDemSlot");
    }
}
