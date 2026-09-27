package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Comparator;

/**
 * Tauscht den Vergleich der GUI-Elementsortierung gegen denselben ohne Boxing.
 *
 * <p>Logik und Begruendung: {@code com.afrobama.blendertransform.GuiSortierungLogik}. Faellt
 * die Bruecke aus, bleibt Vanillas Vergleich.</p>
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererSortierungMixin
{
    @SuppressWarnings("unchecked")
    @ModifyArg(method = "prepare", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;sortElements(Ljava/util/Comparator;)V"))
    private Comparator<GuiElementRenderState> bbstools$sortierungOhneBoxing(Comparator<GuiElementRenderState> vanilla)
    {
        Object eigener = MixinBruecke.rufe("GuiSortierungLogik", "vergleicher", vanilla);

        return eigener instanceof Comparator<?> vergleich ? (Comparator<GuiElementRenderState>) vergleich : vanilla;
    }
}
