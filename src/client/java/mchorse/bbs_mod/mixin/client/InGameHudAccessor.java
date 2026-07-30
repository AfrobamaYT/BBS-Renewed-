package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to InGameHud.renderMainHud for the film viewport.
 *
 * render() bails out when a screen covers the display, which is always the case with the
 * editor open. renderMainHud draws hotbar, health, hunger and crosshair without those
 * guards — exactly what ends up in the exported video.
 */
@Mixin(Gui.class)
public interface InGameHudAccessor
{
    @Invoker("renderHotbarAndDecorations")
    void bbs$renderMainHud(GuiGraphics context, DeltaTracker counter);
}
