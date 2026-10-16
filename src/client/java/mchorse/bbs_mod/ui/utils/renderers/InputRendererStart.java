package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Tutorial settings in the world: the key caps are drawn over the HUD while no screen is open, so
 * a video shows the keys that get you somewhere (open the dashboard, fly, record) and not only
 * those pressed inside a BBS screen. Screens draw their own (see {@link InputRenderer}).
 */
public final class InputRendererStart implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "keystrokes"), (ctx, tick) ->
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null && mc.gui.screen() == null)
            {
                InputRenderer.INSTANCE.renderWorld(new Batcher2D(ctx), ctx.guiWidth(), ctx.guiHeight());
            }
        });
    }
}
