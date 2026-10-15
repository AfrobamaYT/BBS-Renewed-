package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chroma-Himmel ohne Gelaende: dann auch keine Block-Entities (Truhen, Banner, Schilder).
 *
 * <p>⚠️ 26.2 — AUS DREI HAKEN WIRD EINER, UND ER SITZT FRUEHER. Bis 1.21.1 hing hier je ein
 * Abbruch an {@code setupAndRender(...)}, an {@code render(...)} und an {@code renderItem}.
 * Alle drei Methoden gibt es nicht mehr:
 *
 * <ul>
 *   <li>{@code setupAndRender} und {@code render} sind durch das zweiphasige Modell ersetzt.
 *       Uebrig sind {@code tryExtractRenderState(...)} (EINSAMMELN) und {@code submit(...)}
 *       (EINREICHEN). Der Haken sitzt jetzt beim Einsammeln, weil sich dort entscheidet, was
 *       ueberhaupt in die Liste kommt: {@code LevelExtractor} prueft den Rueckgabewert an
 *       BEIDEN Aufrufstellen auf {@code null} und legt nur dann einen Zustand ab (am Bytecode
 *       geprueft). Damit faellt neben dem Zeichnen auch das Einsammeln weg — billiger als
 *       vorher, und {@code submit} braucht keinen zweiten Riegel, weil es keine andere Quelle
 *       fuer Block-Entity-Zustaende gibt.</li>
 *   <li>{@code renderItem} ist ERSATZLOS aus dieser Klasse verschwunden. Es zeichnete eine
 *       Block-Entity als GEGENSTAND (Truhe in der Hand, im Rahmen, in der Oberflaeche); dieser
 *       Weg liegt in 26.2 in {@code net.minecraft.client.renderer.special.*}
 *       ({@code ChestSpecialRenderer}, {@code SkullSpecialRenderer}, ...) — eine andere
 *       Klassenfamilie, kein Ziel dieses Mixins.
 *       ⚠️ VERHALTENSAENDERUNG, GEMELDET: eine gehaltene Truhe/ein Schaedel bleibt bei
 *       Chroma-Himmel ohne Gelaende jetzt SICHTBAR. Fuer das Gelaende selbst aendert sich
 *       nichts — das erledigt der Haken oben.</li>
 * </ul>
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin
{
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void onRenderMain(CallbackInfoReturnable<BlockEntityRenderState> info)
    {
        if (MixinBruecke.rufeUndAbbrechen("WeltUeberschreibungLogik", "blockentitiesUnterdruecken"))
        {
            info.setReturnValue(null);
        }
    }
}
