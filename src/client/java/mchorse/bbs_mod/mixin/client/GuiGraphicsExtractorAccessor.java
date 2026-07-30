package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gibt die {@link GuiRenderState} des Extractors heraus.
 *
 * <p>26.2 zeichnet die GUI in zwei Phasen: {@code GuiGraphicsExtractor} SAMMELT nur und
 * legt Elemente in eine {@code GuiRenderState}, die {@code GuiRenderer.render()} spaeter
 * abarbeitet. BBS zeichnet seine eigene Oberflaeche selbst und muss deshalb an genau diese
 * Liste, um eigene {@code GuiElementRenderState}-Objekte einzureihen.
 *
 * <p>Das Feld ist privat und hat keinen Getter (javap -p:
 * {@code private final net.minecraft.client.renderer.state.gui.GuiRenderState guiRenderState;}).
 *
 * <p>⛔ WARUM NICHT REFLECTION: der erste Anlauf las das Feld per {@code getDeclaredField}.
 * Das laeuft, aber es bindet BBS an einen Feldnamen, den kein Compiler prueft — und wenn
 * Mojang ihn umbenennt, verschwindet die gesamte BBS-Oberflaeche. Mit dem Accessor faellt
 * derselbe Fall beim Bau auf, nicht beim Nutzer.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor
{
    @Accessor("guiRenderState")
    GuiRenderState bbs$getGuiRenderState();
}
