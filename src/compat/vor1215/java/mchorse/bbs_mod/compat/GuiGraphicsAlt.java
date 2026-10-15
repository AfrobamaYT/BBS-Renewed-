package mchorse.bbs_mod.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 26.2's GuiGraphicsExtractor names (NeoForge primer 26.1, "Gui Extractor": draw/render
 * prefixes dropped, String becomes Text) on GuiGraphics, for the versions before 26.1.
 * Injected into GuiGraphics. Vor 1.21.5 dazu bbsPose(): die 2D-Pose von 1.21.6+ als Sicht
 * auf den PoseStack (Pose2D, gefuellt von GuiGraphicsAltMixin).
 */
public interface GuiGraphicsAlt
{
    default org.joml.Matrix3x2fStack bbsPose()
    {
        throw new UnsupportedOperationException("GuiGraphicsAltMixin fehlt");
    }

    /** Vor 1.21.6 zeichnet die GUI sofort in Aufrufreihenfolge: spaetere Elemente liegen schon oben. */
    default void nextStratum()
    {}

    default void text(Font font, String text, int x, int y, int color, boolean shadow)
    {
        ((GuiGraphics) this).drawString(font, text, x, y, color, shadow);
    }

    default void text(Font font, Component text, int x, int y, int color)
    {
        ((GuiGraphics) this).drawString(font, text, x, y, color);
    }

    default void text(Font font, Component text, int x, int y, int color, boolean shadow)
    {
        ((GuiGraphics) this).drawString(font, text, x, y, color, shadow);
    }

    default void item(ItemStack stack, int x, int y)
    {
        ((GuiGraphics) this).renderItem(stack, x, y);
    }

    default void itemDecorations(Font font, ItemStack stack, int x, int y)
    {
        ((GuiGraphics) this).renderItemDecorations(font, stack, x, y);
    }
}
