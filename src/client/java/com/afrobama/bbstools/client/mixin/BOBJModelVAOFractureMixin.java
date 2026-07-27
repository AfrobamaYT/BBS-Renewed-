package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntPredicate;

/**
 * Skips drawing triangles whose dominant bone has been "fractured" —
 * detached from the actor and re-spawned as an independent PhysicsMod
 * rigidbody. Done in two complementary injections so we cover BOTH the
 * bulk-draw fast path AND the per-bone-texture slow path.
 *
 * <h2>Injection 1 — wrap the IntPredicate in {@code drawTriangles}</h2>
 * The slow path ({@code drawTriangles(predicate)}) is used when the
 * model has bones with texture overrides. We intercept the predicate
 * at HEAD and {@code AND} it with our fracture filter — fractured
 * bones return false, so their triangles are skipped in the per-bone
 * GL submission.
 *
 * <h2>Injection 2 — redirect the bulk {@code glDrawArrays} in {@code render}</h2>
 * The fast path ({@code glDrawArrays(GL_TRIANGLES, 0, count)} drawing
 * EVERY triangle) is hit when the model has no per-bone texture
 * overrides. We redirect that single call: if the active actor has any
 * fractured bones, route through a per-bone loop that skips them;
 * otherwise let the original bulk draw run unchanged (zero overhead
 * for actors that aren't being fractured).
 *
 * <p><b>Identity tracking:</b> the IEntity being rendered is captured
 * via {@link FractureRenderContext} (set by
 * {@code ModelFormRendererFractureCaptureMixin} at render3D HEAD/TAIL).
 * If null (e.g. preview render outside the actor pipeline) both
 * mixins fall back to "no filter" and the model renders normally.</p>
 */
@Mixin(BOBJModelVAO.class)
public abstract class BOBJModelVAOFractureMixin
{
    @ModifyVariable(method = "drawTriangles(Ljava/util/function/IntPredicate;)V",
                    at = @At("HEAD"),
                    argsOnly = true,
                    require = 0)
    private IntPredicate bbstools$wrapPredicateWithFractureFilter(IntPredicate original)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.BOBJModelVAOFractureLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. */
        Object ergebnis = MixinBruecke.rufe("BOBJModelVAOFractureLogik",
            "wrapPredicateWithFractureFilter", original);

        return ergebnis instanceof IntPredicate ? (IntPredicate) ergebnis : original;
    }

    /**
     * The bulk fast-path call inside {@code render(...)}. When NO bones
     * are fractured we keep the original bulk draw (single GL call,
     * zero overhead). When SOME bones are fractured we have to route
     * through a per-triangle loop — implemented inline rather than
     * calling the private drawTriangles, because @Redirect target's
     * "this" reference would need an accessor and inline avoids that
     * complexity.
     */
    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lorg/lwjgl/opengl/GL30;glDrawArrays(III)V",
                       ordinal = 0),
              require = 0)
    private void bbstools$conditionalBulkDraw(int mode, int first, int count)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.BOBJModelVAOFractureLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. BOBJModelVAOAccessor ist ein
         * mixin-accessor-interface im stabilen loader, kein kernel-typ.
         *
         * ⚠ DER FALLBACK IST PFLICHT (befund aus dem adversarischen review 2026-07-26).
         * Dies ist ein @Redirect: der ERSETZTE aufruf ist `glDrawArrays`. Kommt die brücke
         * nicht durch, zeichnet niemand — das modell verschwindet lautlos. Ein @Redirect ohne
         * fallback verhält sich bei einem brücken-fehler SCHLECHTER als gar kein mixin.
         *
         * `rufe` liefert bei erfolg `Boolean.TRUE` (die kernel-methode hat gezeichnet), bei
         * jedem fehler `null`. Nur dann holen wir den bulk-draw hier nach. */
        Object gezeichnet = MixinBruecke.rufe("BOBJModelVAOFractureLogik", "conditionalBulkDraw",
            (BOBJModelVAOAccessor)(Object) this, mode, first, count);

        if (!(gezeichnet instanceof Boolean b) || !b)
        {
            org.lwjgl.opengl.GL30.glDrawArrays(mode, first, count);
        }
    }

}
