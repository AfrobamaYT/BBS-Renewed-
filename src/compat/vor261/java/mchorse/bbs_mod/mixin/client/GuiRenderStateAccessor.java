package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Before 26.1 (replaces the shared accessor on Gui): the live GUI render state belongs to the
 * GameRenderer. The shared code's "(GuiRenderStateAccessor) mc.gui" becomes mc.gameRenderer
 * there (stonecutter-regeln.gradle).
 */
@Mixin(GameRenderer.class)
public interface GuiRenderStateAccessor
{
    @Accessor("guiRenderState")
    GuiRenderState bbs$getGuiRenderState();
}
