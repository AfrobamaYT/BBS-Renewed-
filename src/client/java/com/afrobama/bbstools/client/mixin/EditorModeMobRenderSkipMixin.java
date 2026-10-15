// Coded by Afrobama/Claude — Phase 17: skip mob renders during editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.editormode.EditorMode;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Companion to {@code EditorModeMobTickSkipMixin} on the client side.
 * Skips the vanilla mob renderer entirely when editor mode is on —
 * villagers + zombies + the rest don't take a draw call. BBS replays
 * are unaffected (they use BBS's form pipeline, not the vanilla entity
 * dispatcher).
 *
 * <p>⚠️ 26.2 — DER HAKEN SITZT JETZT IN DER SAMMEL-PHASE, NICHT MEHR IM ZEICHNEN.
 * {@code EntityRenderDispatcher.render(entity, x,y,z, yaw, teilTick, stapel, verbraucher,
 * licht)} gibt es nicht mehr; der Zeichenzweig heisst {@code submit(zustand, ...)} und kennt
 * die Entity gar nicht mehr. Statt sie dort ueber einen Umweg zurueckzuholen, wird hier eine
 * Ebene frueher entschieden — an {@code shouldRender}, dem Sichtbarkeitstor:
 *
 * <pre>
 *   LevelExtractor.isEntityVisible(...)          &lt;-- einziger Rufer, am Bytecode geprueft
 *     -&gt; dispatcher.shouldRender(...)  == false  =&gt; keine Extraktion, kein Auftrag, nichts
 * </pre>
 *
 * <p>Das ist MEHR gespart als vorher: bis 1.21.1 fiel nur der Zeichenaufruf weg, jetzt auch
 * das Einsammeln des Render-Zustands (in 26.2 der teurere Teil).
 *
 * <p>⚠️ VERHALTENSAENDERUNG, GEMELDET: {@code isEntityVisible} laesst eine Entity trotz
 * {@code shouldRender == false} durch, wenn der Spieler auf ihr sitzt
 * ({@code hasIndirectPassenger}). Ein Mob, den der Spieler reitet, bleibt im Editor-Modus
 * also sichtbar — vorher verschwand er.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EditorModeMobRenderSkipMixin
{
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void bbstools$skipMobRenderInEditorMode(Entity entity, Frustum frustum,
                                                     //? if >=26.3 {
                                                     /*double x, double y, double z, float partialTick,
                                                     *///?} else {
                                                     double x, double y, double z,
                                                     //?}
                                                     CallbackInfoReturnable<Boolean> cir)
    {
        if (MixinBruecke.rufeUndAbbrechen("EditorModeMobRenderSkipLogik", "ueberspringen", entity))
        {
            cir.setReturnValue(false);
        }
    }

}
