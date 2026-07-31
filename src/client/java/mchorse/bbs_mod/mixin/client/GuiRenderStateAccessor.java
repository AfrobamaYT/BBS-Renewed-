package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf den LEBENDEN GUI-Zeichenzustand.
 *
 * <p>26.2 zeichnet das GUI zweiphasig: erst sammelt {@code Gui.extractRenderState} alles in
 * einen {@link GuiRenderState}, danach zeichnet {@code GuiRenderer.render()} daraus. Wer
 * eigene Ueberlagerungen beisteuern will, braucht deshalb GENAU DIESEN Zustand -- ein selbst
 * gebauter waere zwar konstruierbar (der Leerkonstruktor ist oeffentlich), wuerde aber von
 * niemandem gezeichnet: das Ergebnis waere ein Aufruf ohne Bild, den kein Compiler meldet.</p>
 *
 * <p>{@code Gui} haelt ihn privat, {@code Minecraft.gui} ist oeffentlich -- daher dieser
 * Accessor statt eines Nachbaus.</p>
 *
 * <p>⚠️ ZEITPUNKT IST BEDINGUNG: wer hierueber zeichnet, muss das in der SAMMELPHASE tun,
 * also bevor der GuiRenderer den Zustand abarbeitet. Danach hineinzuschreiben ist folgenlos.
 * Das ist der Punkt, den man am laufenden Bild prueft, nicht am Compiler.</p>
 */
@Mixin(Gui.class)
public interface GuiRenderStateAccessor
{
    @Accessor("guiRenderState")
    GuiRenderState bbs$getGuiRenderState();
}
