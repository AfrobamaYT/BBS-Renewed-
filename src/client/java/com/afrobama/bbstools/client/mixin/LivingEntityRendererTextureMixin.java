package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the {@code this.getTextureLocation(...)} call inside
 * {@link LivingEntityRenderer}. When the entity has a per-UUID texture override registered
 * (via RagdollSwap or the Texture Staff), the override is substituted; otherwise the
 * renderer's own texture wins.
 *
 * <p>⚠️ 26.2 — ZWEI DINGE HABEN SICH VERSCHOBEN:
 *
 * <ul>
 *   <li><b>Die Aufrufstelle.</b> {@code getTextureLocation} wird nicht mehr aus {@code render}
 *       gerufen, sondern aus {@code getRenderType(zustand, ...)} (am Bytecode geprueft — in
 *       {@code submit}, dem Nachfolger von {@code render}, kommt sie gar nicht mehr vor). Ein
 *       Redirect, der weiter auf {@code render} zielte, haette still nichts mehr getroffen.
 *       ⚠️ Der Satz stand bis 2026-07-31 mit dem Selektor ausgeschrieben im Text — {@code
 *       tools/mixin-ziele.py} liest den Quelltext ohne Kommentare zu entfernen und meldete
 *       diese Datei deshalb als totes Ziel. Falschmeldung, keine Injektion: gemeldet.</li>
 *   <li><b>Das Argument.</b> {@code getTextureLocation} nimmt den RENDER-ZUSTAND statt der
 *       Entity. Der Zustand traegt aber weder Entity noch UUID, und die Auswahl haengt an der
 *       UUID — die Entity kommt darum aus der Zusatzablage, die
 *       {@code EntityRenderDispatcherMixin} beim Einsammeln fuellt. Fehlt sie (Zustand ausserhalb
 *       des Dispatchers gebaut), gilt die Vanilla-Textur.</li>
 * </ul>
 *
 * <p>BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in
 * LivingEntityRenderer gewoben und liegt damit im KnotClassLoader — vom kernel-swap
 * unerreichbar. Die override-auswahl liegt in
 * {@code com.afrobama.blendertransform.LivingEntityTextureLogik} und wird bei jedem swap
 * mitgetauscht.
 *
 * Migrated from BBS-CML-EDITION fork's
 * mchorse.bbs_mod.mixin.client.LivingEntityRendererTextureMixin.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTextureMixin
{
    /*
     * ⛔ KEIN require = 0 MEHR (2026-07-31). Das stand hier waehrend des Umhaengens und haette
     * aus einem lauten Weben-Fehler eine stille Wirkungslosigkeit gemacht — die Textur-
     * Ueberschreibung waere einfach nie wieder gelaufen, ohne eine Zeile im Log.
     * Das Ziel ist gemessen, nicht vermutet: getRenderType(S,ZZZ) beginnt mit
     * `invokevirtual getTextureLocation:(LivingEntityRenderState;)Identifier` (javap -c am
     * 26.2-jar). Bricht das kuenftig weg, SOLL der Start scheitern.
     */
    @Redirect(method = "getRenderType", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"))
    private Identifier bbs$redirectGetTexture(LivingEntityRenderer<?, ?, ?> self, LivingEntityRenderState zustand)
    {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Identifier vanilla = ((LivingEntityRenderer) self).getTextureLocation(zustand);

        Entity entity = MorphRenderer.entityVon(zustand);

        if (entity == null)
        {
            /* Ohne Entity gibt es keine UUID und damit keine Auswahl. Die Bruecke wuerde
             * ihre Signatur ueber die Laufzeittypen der Argumente aufloesen — ein null
             * wuerde sie gar nicht erst finden. */
            return vanilla;
        }

        Object ergebnis = MixinBruecke.rufe("LivingEntityTextureLogik", "wähleTextur", entity, vanilla);

        return ergebnis instanceof Identifier ? (Identifier) ergebnis : vanilla;
    }
}
