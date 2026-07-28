package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to InGameHud.renderMainHud for the film viewport.
 *
 * render() bails out when a screen covers the display, which is always the case with the
 * editor open. renderMainHud draws hotbar, health, hunger and crosshair without those
 * guards — exactly what ends up in the exported video.
 */
@Mixin(InGameHud.class)
public interface InGameHudAccessor
{
    @Invoker("renderMainHud")
    void bbs$renderMainHud(DrawContext context, RenderTickCounter counter);
}
