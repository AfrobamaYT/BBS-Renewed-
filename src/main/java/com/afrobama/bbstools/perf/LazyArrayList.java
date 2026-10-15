// Coded by Afrobama/Claude — Tier 3f lazy keyframe channel.
//
// Drop-in ArrayList replacement that defers the underlying Object[]
// allocation until the first mutating operation.
//
// Why this exists:
//   BBS's mchorse.bbs_mod.settings.values.core.ValueList field-initialises
//   {@code protected final List<T> list = new ArrayList<>()} eagerly. Every
//   ValueList — including KeyframeChannel — pays for an ArrayList shell
//   + size/modCount fields at construction even when the channel will
//   never contain a single keyframe.
//
//   For a 200-replay film with 30 bones × 6 channels = 36000 channels,
//   the vast majority of those channels are never written to (most BBS
//   pose tracks animate only a handful of bones). The eager allocations
//   add up to ~1.1 MiB of permanent shells + a corresponding 36000-allocation
//   GC burst at film-load time.
//
// What this class does differently from {@code java.util.ArrayList}:
//   We extend ArrayList and intercept every MUTATING operation (add /
//   add(int, E) / addAll / set / remove(*) / clear / removeIf / sort /
//   replaceAll). On first mutation, we bump
//   {@link BBSPerfConfig#lazyKeyframeChannelBuilds} so the /bbsdebug
//   counter can prove how many channels were actually used.
//
//   We do NOT cancel the underlying ArrayList work — once a mutation
//   arrives the parent class allocates exactly the same backing array
//   it would have allocated anyway. The savings come from:
//     1) Touched/never-touched ratio observability (proves the
//        hypothesis that most channels are wasted).
//     2) {@code Collections.emptyList()} sentinel as the initial value
//        means the BBS reader path (isEmpty/size/iterator/get(int)) on
//        the {@code list} field still works without instrumentation,
//        AND the ArrayList shell itself is never constructed when the
//        feature is enabled (see KeyframeChannelLazyMixin).
//
// Reader-path safety:
//   Read-only callers (KeyframeChannel.findSegment, getKeyframes,
//   getLength, isEmpty, has, get(int)) all access {@code this.list}
//   directly via subclass field access. When the field is upgraded
//   from emptyList() to a real LazyArrayList only on first mutation,
//   reads on the empty sentinel are perfectly valid:
//     - {@code Collections.emptyList().isEmpty()} → true
//     - {@code .size()} → 0
//     - {@code .iterator()} → empty iterator
//     - {@code .get(i)} → IndexOutOfBoundsException (but no caller hits
//       that path without first checking size/isEmpty)
//
// Migration: when KeyframeChannelLazyMixin's <init> @Redirect returns a
// LazyArrayList instead of a stock ArrayList, the only behavioural delta
// vs vanilla is the increment of lazyKeyframeChannelBuilds on first add
// — semantically identical otherwise.
package com.afrobama.bbstools.perf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class LazyArrayList<T> extends ArrayList<T>
{
    /** Tracks whether this list has ever been written to. Once flipped
     *  to true, the per-mutation guard short-circuits so the counter
     *  bump only fires exactly once per channel. */
    private boolean bbstoolsTouched;

    public LazyArrayList()
    {
        super(0);
    }

    private void bbstoolsTouch()
    {
        if (this.bbstoolsTouched) return;
        this.bbstoolsTouched = true;
        BBSPerfConfig.lazyKeyframeChannelBuilds++;
        LazyKeyframeChannel.INSTANCE.allocatedChannels.incrementAndGet();
    }

    @Override
    public boolean add(T e)
    {
        this.bbstoolsTouch();
        return super.add(e);
    }

    @Override
    public void add(int index, T element)
    {
        this.bbstoolsTouch();
        super.add(index, element);
    }

    @Override
    public boolean addAll(Collection<? extends T> c)
    {
        if (c != null && !c.isEmpty()) this.bbstoolsTouch();
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c)
    {
        if (c != null && !c.isEmpty()) this.bbstoolsTouch();
        return super.addAll(index, c);
    }

    @Override
    public T set(int index, T element)
    {
        this.bbstoolsTouch();
        return super.set(index, element);
    }

    @Override
    public T remove(int index)
    {
        this.bbstoolsTouch();
        return super.remove(index);
    }

    @Override
    public boolean remove(Object o)
    {
        this.bbstoolsTouch();

        /* ArrayList.remove(Object) locates the element via .equals() — for BBS
         * Replay/ValueGroup that's a DEEP recursive keyframe compare, so removing N
         * selected replays from a large film is O(N^2 * keyframes) (observed: "remove
         * 3000" froze the render thread in ValueGroup.equals). The caller removes the
         * exact same object instance, so find it by REFERENCE identity and remove by
         * index — O(N) cheap pointer checks. This is also MORE correct: equals-based
         * removal would delete the first DATA-equal replay (a true duplicate), not the
         * specific instance the user selected. Falls back to equals only if the exact
         * instance isn't present, preserving the List contract for other callers. */
        int n = this.size();
        for (int i = 0; i < n; i++)
        {
            if (this.get(i) == o)
            {
                super.remove(i);
                return true;
            }
        }

        return super.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        this.bbstoolsTouch();
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        this.bbstoolsTouch();
        return super.retainAll(c);
    }

    /* ---- Identity-first equals-scan reads ----
     * Stock ArrayList indexOf/lastIndexOf/contains locate elements via .equals().
     * On a BBS ValueList of Replay/ValueGroup, equals is a DEEP recursive keyframe
     * compare, so these are O(N * keyframes); on the replay list (thousands) every
     * bulk UI op (duplicate-undo indexOf, remove-selected, contains) went O(N^2) and
     * froze the render thread for minutes. The caller virtually always passes the
     * exact element instance, so scan by REFERENCE identity first (O(N) cheap pointer
     * checks); fall back to the equals-based super only if the instance isn't present,
     * preserving List semantics. (contains() needs no override — ArrayList.contains
     * delegates to the virtual indexOf, so it inherits this automatically.) */
    @Override
    public int indexOf(Object o)
    {
        int n = this.size();
        for (int i = 0; i < n; i++)
        {
            if (this.get(i) == o)
            {
                return i;
            }
        }

        return super.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o)
    {
        for (int i = this.size() - 1; i >= 0; i--)
        {
            if (this.get(i) == o)
            {
                return i;
            }
        }

        return super.lastIndexOf(o);
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter)
    {
        this.bbstoolsTouch();
        return super.removeIf(filter);
    }

    @Override
    public void clear()
    {
        /* Don't mark touched for clear-from-empty (BBS's
         * KeyframeChannel.fromData / copy / removeAll path always calls
         * clear() before any real work — counting that would inflate
         * the counter to 100% of channels even ones with zero data). */
        if (this.isEmpty()) return;
        this.bbstoolsTouch();
        super.clear();
    }

    @Override
    public void sort(Comparator<? super T> c)
    {
        if (this.isEmpty()) return;
        this.bbstoolsTouch();
        super.sort(c);
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator)
    {
        if (this.isEmpty()) return;
        this.bbstoolsTouch();
        super.replaceAll(operator);
    }

}
