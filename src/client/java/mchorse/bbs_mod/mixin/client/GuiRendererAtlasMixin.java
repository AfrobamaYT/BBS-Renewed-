package mchorse.bbs_mod.mixin.client;

import com.afrobama.blendertransform.AtlasSchaerfeLogik;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item atlas scale for sharp viewport HUD icons. Logic: AtlasSchaerfeLogik. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererAtlasMixin
{
    @ModifyExpressionValue(method = "getGuiScaleInvalidatingItemAtlasIfChanged", require = 1, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/state/WindowRenderState;guiScale:I"))
    private int bbs$atlasStufe(int fenster)
    {
        return AtlasSchaerfeLogik.atlasStufe(fenster);
    }
}
