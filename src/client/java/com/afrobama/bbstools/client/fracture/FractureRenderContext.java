package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.forms.entities.IEntity;

/**
 * Static holder for the {@link IEntity} currently being rendered, so the
 * BOBJ renderer's per-triangle filter mixin can look up the actor's
 * fractured-bone set in {@link FracturedBoneRegistry}.
 *
 * <p>Why static: the BOBJ render path is several layers deep — by the
 * time {@code BOBJModelVAO.drawTriangles(predicate)} is called, the
 * {@link IEntity} parameter is no longer in scope. Mixin-injecting an
 * entity argument all the way down would require redirects on multiple
 * methods. A simple static holder pushed at {@code ModelFormRenderer
 * .render3D} entry and popped at exit is much cleaner.</p>
 *
 * <p>Thread safety: the entire BBS render path runs on the client/render
 * thread. We don't synchronise — single-threaded by design.</p>
 *
 * <p>Re-entrancy: {@code render3D} can call back into itself for child
 * forms (sub-models, attachments). We use a tiny stack (capacity 16)
 * so nested renders don't lose the parent's entity. In practice nesting
 * stays at depth 1-2, but the stack costs nothing and prevents subtle
 * bugs.</p>
 */
public final class FractureRenderContext
{
    private static final IEntity[] STACK = new IEntity[16];
    private static int depth;

    private FractureRenderContext()
    {
    }

    public static void push(IEntity entity)
    {
        if (depth >= STACK.length)
        {
            /* Defensive: drop the push to prevent corruption. Should never
             *  happen in practice — render-pass nesting stays shallow. */
            return;
        }
        STACK[depth++] = entity;
    }

    public static void pop()
    {
        if (depth <= 0) return;
        STACK[--depth] = null;
    }

    /** Returns the IEntity currently being rendered, or null if no
     *  render pass is active. */
    public static IEntity current()
    {
        if (depth <= 0) return null;
        return STACK[depth - 1];
    }

    /** Reset the stack — called on world unload to prevent stale
     *  references surviving between worlds. */
    public static void reset()
    {
        for (int i = 0; i < STACK.length; i++) STACK[i] = null;
        depth = 0;
    }

}
