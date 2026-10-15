// Coded by Afrobama/Claude — Tier 2-i: thread-safe CubicModelAnimator scratch.
//
// BBS's mchorse.bbs_mod.cubic.CubicModelAnimator holds three static
// Vector3d scratch objects (p, s, r) reused for position/scale/rotation
// inside applyGroupAnimation / applyGroupAnimationPost. Single-threaded
// that's fine; when Tier 2-h runs the cubic animator in parallel (Phase A
// on ForkJoinPool), two replays clobber each other's p/s/r mid-pose →
// garbled bone transforms → a few replays stretch in constant random
// directions (the bug seen after the Matrices fix alone wasn't enough).
//
// The static p/s/r are assigned ONCE in the class initializer (effectively
// final refs to mutable Vector3d). Every GETSTATIC of them in the two using
// methods is redirected to return per-thread scratch from AnimatorScratch.
// Behaviour is identical single-threaded (one instance per thread, reused
// just like the static was), so no regression for BBS's serial render.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.AnimatorScratch;

import mchorse.bbs_mod.cubic.CubicModelAnimator;

import org.joml.Vector3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CubicModelAnimator.class, remap = false)
public class CubicModelAnimatorThreadSafeMixin
{
    /* BRÜCKEN-MUSTER (2026-07-26): siehe MixinBruecke-javadoc. Logik liegt in
     * com.afrobama.blendertransform.CubicModelAnimatorThreadSafeLogik und ist damit hotswapbar;
     * nur diese einzeiler werden beim klassenladen gewoben. THREAD-SICHERHEIT UNVERÄNDERT — die
     * kernel-methoden liefern weiterhin das pro-thread-scratch aus AnimatorScratch. */

    /**
     * Holt das pro-thread-scratch aus dem kernel — mit fallback auf das ORIGINALFELD.
     *
     * <p>⚠ WARUM DER FALLBACK ZWINGEND IST (befund aus dem adversarischen review 2026-07-26):
     * ein blosser cast {@code (Vector3d) MixinBruecke.rufe(...)} liesse bei jedem brücken-fehler
     * {@code null} durch. Dieser {@code null} ersetzt dann den vektor, mit dem
     * {@code applyGroupAnimation} sofort weiterrechnet — <b>NPE im render-thread, jeden frame</b>,
     * bis die brücke wieder greift. Das war die einzige stelle im ganzen umbau, an der ein
     * brücken-fehler das spiel abgeschossen hätte statt nur eine funktion auszusetzen.</p>
     *
     * <p>Der fallback greift direkt auf {@link AnimatorScratch} zu — dieselbe quelle, aus der
     * auch die kernel-methode liefert. {@code AnimatorScratch} liegt im STABILEN loader
     * ({@code com.afrobama.bbstools.perf}) und ist damit ohne brücke erreichbar. Die
     * thread-sicherheit bleibt dadurch auch im fehlerfall erhalten — anders als ein rückfall
     * auf die geteilten statischen originalfelder, die genau das problem sind, gegen das
     * dieser mixin existiert (und die ohnehin {@code private} sind).</p>
     */
    @org.spongepowered.asm.mixin.Unique
    private static Vector3d bbstools$sicher(String feld)
    {
        Object v = MixinBruecke.rufe("CubicModelAnimatorThreadSafeLogik", feld);

        if (v instanceof Vector3d vec)
        {
            return vec;
        }

        /* Brücke nicht erreichbar — direkt aus dem pro-thread-scratch holen. */
        AnimatorScratch s = AnimatorScratch.get();

        switch (feld)
        {
            case "p":  return s.p;
            case "s":  return s.s;
            default:   return s.r;
        }
    }

    /* ---- applyGroupAnimation: reads p, s, r ---- */

    @Redirect(method = "applyGroupAnimation", at = @At(value = "FIELD",
        target = "Lmchorse/bbs_mod/cubic/CubicModelAnimator;p:Lorg/joml/Vector3d;",
        opcode = Opcodes.GETSTATIC))
    private static Vector3d bbstools$animP() { return bbstools$sicher("p"); }

    @Redirect(method = "applyGroupAnimation", at = @At(value = "FIELD",
        target = "Lmchorse/bbs_mod/cubic/CubicModelAnimator;s:Lorg/joml/Vector3d;",
        opcode = Opcodes.GETSTATIC))
    private static Vector3d bbstools$animS() { return bbstools$sicher("s"); }

    @Redirect(method = "applyGroupAnimation", at = @At(value = "FIELD",
        target = "Lmchorse/bbs_mod/cubic/CubicModelAnimator;r:Lorg/joml/Vector3d;",
        opcode = Opcodes.GETSTATIC))
    private static Vector3d bbstools$animR() { return bbstools$sicher("r"); }

    /* ---- applyGroupAnimationPost: reads p, s, r ---- */

    @Redirect(method = "applyGroupAnimationPost", at = @At(value = "FIELD",
        target = "Lmchorse/bbs_mod/cubic/CubicModelAnimator;p:Lorg/joml/Vector3d;",
        opcode = Opcodes.GETSTATIC))
    private static Vector3d bbstools$animPostP() { return bbstools$sicher("p"); }

    @Redirect(method = "applyGroupAnimationPost", at = @At(value = "FIELD",
        target = "Lmchorse/bbs_mod/cubic/CubicModelAnimator;s:Lorg/joml/Vector3d;",
        opcode = Opcodes.GETSTATIC))
    private static Vector3d bbstools$animPostS() { return bbstools$sicher("s"); }

    @Redirect(method = "applyGroupAnimationPost", at = @At(value = "FIELD",
        target = "Lmchorse/bbs_mod/cubic/CubicModelAnimator;r:Lorg/joml/Vector3d;",
        opcode = Opcodes.GETSTATIC))
    private static Vector3d bbstools$animPostR() { return bbstools$sicher("r"); }

}
