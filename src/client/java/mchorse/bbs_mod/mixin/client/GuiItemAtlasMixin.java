package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventar-Symbole (Modellblock, Gun, BBS-Reiter): {@code drawToSlot} ruft den Sonderzeichner
 * mit Projektion und Pose des Atlas-Slots, BBS zeichnet dort aber SOFORT — ohne Ziel landete
 * das im Hauptziel und das Symbol blieb leer (gemessen 2026-10-12). Logik: GegenstandsAtlasLogik.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin
{
    @Shadow @Final private GpuTextureView textureView;
    @Shadow @Final private GpuTextureView depthTextureView;
    @Shadow @Final private int textureSize;
    @Shadow @Final private int slotTextureSize;

    @Inject(method = "drawToSlot", require = 1, at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void bbs$vorDemSlot(int spalte, int zeile, boolean loeschen, ItemStackRenderState zustand, CallbackInfo info)
    {
        MixinBruecke.rufe("GegenstandsAtlasLogik", "vorDemSlot", this.textureView, this.depthTextureView, spalte, zeile, this.slotTextureSize, this.textureSize);
    }

    @Inject(method = "drawToSlot", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void bbs$nachDemSlot(int spalte, int zeile, boolean loeschen, ItemStackRenderState zustand, CallbackInfo info)
    {
        MixinBruecke.rufe("GegenstandsAtlasLogik", "nachDemSlot");
    }
}
