package com.afrobama.bbstools.client.fracture;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
        /* 26.2: HudRenderCallback ist gestrichen, Nachfolger HudElementRegistry (Begruendung
         * in BBSModClient). Der Hinweis steht ueber allem, also addLast -- er soll auch dann
         * lesbar bleiben, wenn das Fadenkreuz drunter liegt. */
        HudRenderCallback.EVENT.register(GravityGunHud::onHudRender);
    }

    private static void onHudRender(GuiGraphics ctx, net.minecraft.client.DeltaTracker tickCounter)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || mchorse.bbs_mod.client.HudSichtbarkeit.istVersteckt()) return;

        Font fr = client.font;
        if (fr == null) return;

        int screenW = ctx.guiWidth();
        int screenH = ctx.guiHeight();
        int cx = screenW / 2;
        int cy = screenH / 2;

        if (GravityGunHandler.isHolding())
        {
            /* Held — show "Holding [bone]  [release G]" hint. */
            String label = "§a● §fHolding · release G to throw";
            int w = fr.width(label);
            ctx.text(fr, Component.literal(label), cx - w / 2, cy + 12, 0xFFFFFF);
        }
        else
        {
            GravityGunHandler.HoverInfo hover = GravityGunHandler.getHoverInfo();
            if (hover != null)
            {
                String label = String.format("§e● §fG · grab '%s' (%.1fm)", hover.boneName, hover.distance);
                int w = fr.width(label);
                ctx.text(fr, Component.literal(label), cx - w / 2, cy + 12, 0xFFFFFF);
            }
        }
    }

}
