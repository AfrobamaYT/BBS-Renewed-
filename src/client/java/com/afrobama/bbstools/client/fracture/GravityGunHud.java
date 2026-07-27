package com.afrobama.bbstools.client.fracture;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Tiny HUD overlay that shows next to the crosshair when the player
 * is looking at a grabbable fracture body, OR when a body is currently
 * being held by the gravity-gun. Subtle text + colored dot — nothing
 * intrusive that would clutter cinematic shots; the user can disable
 * it by simply not having any fracture active.
 */
public final class GravityGunHud
{
    private GravityGunHud()
    {
    }

    public static void register()
    {
        HudRenderCallback.EVENT.register(GravityGunHud::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) return;

        TextRenderer fr = client.textRenderer;
        if (fr == null) return;

        int screenW = ctx.getScaledWindowWidth();
        int screenH = ctx.getScaledWindowHeight();
        int cx = screenW / 2;
        int cy = screenH / 2;

        if (GravityGunHandler.isHolding())
        {
            /* Held — show "Holding [bone]  [release G]" hint. */
            String label = "§a● §fHolding · release G to throw";
            int w = fr.getWidth(label);
            ctx.drawTextWithShadow(fr, Text.literal(label), cx - w / 2, cy + 12, 0xFFFFFF);
        }
        else
        {
            GravityGunHandler.HoverInfo hover = GravityGunHandler.getHoverInfo();
            if (hover != null)
            {
                String label = String.format("§e● §fG · grab '%s' (%.1fm)", hover.boneName, hover.distance);
                int w = fr.getWidth(label);
                ctx.drawTextWithShadow(fr, Text.literal(label), cx - w / 2, cy + 12, 0xFFFFFF);
            }
        }
    }

}
