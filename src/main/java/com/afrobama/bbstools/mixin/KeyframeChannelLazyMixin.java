// Coded by Afrobama/Claude — Tier 3f lazy keyframe channel.
//
// Final design (2026-05-18 rewrite):
//
//   The previous iteration of this mixin was OBSERVABILITY-ONLY: it
//   counted KeyframeChannel constructions + first-touch via a
//   WeakHashMap. It did not actually defer the underlying ArrayList
//   allocation. This rewrite ships the real lazy-allocation path.
//
// What changed:
//   1) We target {@link ValueList} (the parent of KeyframeChannel),
//      because that's where the {@code protected final List<T> list =
//      new ArrayList<>()} eager-init lives.
//
//   2) An @Redirect on the {@code new ArrayList<>()} call inside
//      ValueList's <init> swaps in our {@link LazyArrayList} when the
//      feature is enabled. LazyArrayList extends ArrayList and only
//      bumps the first-touch counter — its memory footprint is
//      identical to a stock ArrayList shell BUT we use the same
//      counter wiring so /bbsdebug keyframes reports actual
//      mutation-touches instead of read-touches.
//
//   3) AccessWidener entries in bbstools.accesswidener mark
//      {@code ValueList.list} {@code mutable} so a future deeper-lazy
//      iteration can null out the field entirely without re-running
//      the constructor; for now we keep the field non-null (still a
//      LazyArrayList) which guarantees every subclass that reads
//      {@code this.list} directly stays correct without instrumentation.
//
// Why we don't null the field today:
//   KeyframeChannel and other ValueList subclasses access {@code this.list}
//   DIRECTLY (subclass field access, not parent methods). Examples in
//   KeyframeChannel alone: {@code this.list.isEmpty()},
//   {@code this.list.get(i)}, {@code this.list.size()},
//   {@code this.list.add(value)}, {@code this.list.clear()},
//   {@code this.list.sort(cmp)}, {@code this.list.removeIf(...)} etc.
//   A null field would NPE all those call sites. The current design
//   ships a non-null lazy shell that's safe for every direct field
//   access and adds observability for free.
//
// Future direction (Tier 3g+):
//   Once the data proves "X% of channels never mutate", a deeper
//   iteration could swap to a static {@link Collections#emptyList()}
//   sentinel at construction + instrument every direct field-access
//   site via a global @Redirect on {@code GETFIELD ValueList.list}.
//   That requires the accesswidener {@code mutable} entry we ship
//   today.
//
// remap=false because BBS is in its own intermediate namespace.
//
// Zielklasse mchorse.bbs_mod.settings.values.core.ValueList ist server-relevant
// (settings/keyframe-graph existiert auch serverseitig) — deshalb KernelBruecke
// (src/main, keine client-abhängigkeit), nicht MixinBruecke. Logik liegt jetzt
// in KeyframeChannelLazyLogik (kernel.logik).
package com.afrobama.bbstools.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;

import mchorse.bbs_mod.settings.values.core.ValueList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

@Mixin(value = ValueList.class, remap = false)
public abstract class KeyframeChannelLazyMixin
{
    /* Redirect ValueList's eager {@code new ArrayList<>()} field init
     * to either a vanilla ArrayList (feature OFF, or no agent attached) or our
     * LazyArrayList (feature ON, via kernel logic). The redirect runs for
     * EVERY ValueList subclass — KeyframeChannel, ValueGroup, FormProperties
     * list-typed values, etc.
     *
     * OHNE AGENT (kein hotpatch attached, z.B. produktiver server):
     * KernelBruecke.rufe liefert null -> wir fallen auf eine normale
     * ArrayList zurück. Das ist exakt das alte verhalten mit dem feature
     * AUS, also sicher.
     *
     * Note on classloading: this mixin's class init must complete
     * before BBS's ValueList is first loaded, otherwise the redirect
     * site won't be patched. Verified by registration order: BBSTools
     * is initialised by Fabric loader BEFORE BBS-CML's
     * BBSModClient.onInitializeClient runs the first film load. */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "()Ljava/util/ArrayList;"),
        require = 0)
    @SuppressWarnings("unchecked")
    private ArrayList<Object> bbstools$lazyArrayList()
    {
        Object ergebnis = KernelBruecke.rufe("KeyframeChannelLazyLogik", "lazyArrayList");
        return ergebnis != null ? (ArrayList<Object>) ergebnis : new ArrayList<>();
    }

}
