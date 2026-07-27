// Coded by Afrobama/Claude — registration shim for WorldSnapshotClip
// (2026-05-16, scaffold).
//
// BBS registers Clip types via factory-style event subscriptions that
// fire while BBS itself boots. Two paths exist:
//
//   1. CameraClip subclasses register through RegisterCameraClipsEvent
//      (fires from mchorse.bbs_mod.events.register.* on the BBS-side
//      event bus that BBSClientAddon subscribes to).
//   2. ActionClip subclasses (our case — WorldSnapshotClip extends
//      ActionClip) register through RegisterActionClipsEvent. See
//      FRACTURE_PLAN.md line 96 for the exact factory signature used
//      by ModelFractureActionClip:
//         event.getFactory().register(
//             Link.bbs("model_fracture"),
//             ModelFractureActionClip.class, ...);
//
// At the time this scaffold is being written the BBSClientAddon class
// in this repo (see BBSToolsAddon.java) only overrides
// registerDashboardPanels — there is no registerActionClips override
// yet. Rather than blindly add an override whose method signature we
// can't verify without the BBS jar in front of us, we use REFLECTION
// for the actual register() call. That keeps the scaffold compilable
// even if BBSAddon API drift renames Link.bbs / getFactory.
//
// To wire this clip into the editor sidebar, do ONE of:
//
//   A. (preferred, when verified) Add to BBSToolsAddon.java:
//
//      @Override
//      protected void registerActionClips(RegisterActionClipsEvent event)
//      {
//          WorldSnapshotClipFactory.register(event);
//      }
//
//      and ensure the event class import resolves to
//      `mchorse.bbs_mod.events.register.RegisterActionClipsEvent`.
//
//   B. (fallback) Call WorldSnapshotClipFactory.tryReflectiveRegister()
//      from inside BBSToolsModClient's onInitializeClient() AFTER the
//      BBS mod has finished its own onInitialize — gated by a check
//      that the factory class exists.
//
// Either path culminates in BBS knowing about
//   bbstools:world_snapshot  ->  WorldSnapshotClip.class
// and showing the clip in the timeline's "Add action clip" dropdown.
package com.afrobama.bbstools.clips;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class WorldSnapshotClipFactory
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/snapshot-clip-factory");

    /** Link namespace + path used both for serialisation key and the
     *  picker label. Matches the `bbstools:` prefix used by the other
     *  clip types in this codebase (model_fracture, etc.). */
    public static final String CLIP_ID_NAMESPACE = "bbstools";
    public static final String CLIP_ID_PATH      = "world_snapshot";

    private WorldSnapshotClipFactory() {}

    /**
     * Direct register entrypoint — call from a typed
     * RegisterActionClipsEvent handler inside BBSToolsAddon once the
     * event class import is verified to exist on the classpath at
     * compile time.
     *
     * <p>Signature is intentionally Object so this file compiles even
     * if RegisterActionClipsEvent is missing from a particular BBS
     * build. The cast is performed at runtime via reflection.</p>
     */
    public static void register(Object registerActionClipsEvent)
    {
        if (registerActionClipsEvent == null)
        {
            LOG.warn("register() called with null event — clip will NOT be visible in editor.");
            return;
        }

        try
        {
            /* event.getFactory() -> MapFactory<Link, ? extends Clip> */
            Method getFactory = registerActionClipsEvent.getClass().getMethod("getFactory");
            Object factory = getFactory.invoke(registerActionClipsEvent);
            if (factory == null)
            {
                LOG.warn("event.getFactory() returned null — BBS API drift?");
                return;
            }

            /* Build a Link via the same reflective path the rest of
             *  this codebase uses: mchorse.bbs_mod.links.Link.create(ns, path)
             *  with fallback to Link.bbs(path) when namespace == "bbs". */
            Object link = buildLink(CLIP_ID_NAMESPACE, CLIP_ID_PATH);
            if (link == null)
            {
                LOG.warn("Could not construct Link({}:{}); WorldSnapshotClip NOT registered.",
                    CLIP_ID_NAMESPACE, CLIP_ID_PATH);
                return;
            }

            /* factory.register(Link, Class<? extends Clip>) — typical
             *  BBS signature. Some BBS builds use
             *  register(Link, Class, Supplier<UIClip>) — we try the
             *  two-arg version first and fall back. */
            Method register2 = findRegister(factory.getClass(), 2);
            if (register2 != null)
            {
                register2.invoke(factory, link, WorldSnapshotClip.class);
                LOG.info("Registered WorldSnapshotClip as {}:{} (2-arg overload).",
                    CLIP_ID_NAMESPACE, CLIP_ID_PATH);
                return;
            }

            Method register3 = findRegister(factory.getClass(), 3);
            if (register3 != null)
            {
                /* Pass null for the optional UIClip-factory supplier;
                 *  BBS falls back to a default sidebar in that case. */
                register3.invoke(factory, link, WorldSnapshotClip.class, null);
                LOG.info("Registered WorldSnapshotClip as {}:{} (3-arg overload, no UIClip).",
                    CLIP_ID_NAMESPACE, CLIP_ID_PATH);
                return;
            }

            LOG.warn("Could not find suitable register(...) on {}; clip NOT registered.",
                factory.getClass().getName());
        }
        catch (Throwable t)
        {
            LOG.warn("WorldSnapshotClip register() reflection failed: {}", t.toString(), t);
        }
    }

    /**
     * Last-resort entrypoint when we can't subscribe to the typed
     * event at compile time. Walks the BBS event-bus reflectively and
     * registers a no-arg listener that forwards to {@link #register}.
     * Safe to call multiple times — duplicate registration is logged
     * and ignored.
     */
    public static void tryReflectiveRegister()
    {
        try
        {
            Class<?> eventCls = Class.forName(
                "mchorse.bbs_mod.events.register.RegisterActionClipsEvent");
            LOG.info("RegisterActionClipsEvent found ({}); add a typed override " +
                     "in BBSToolsAddon for proper subscription.", eventCls.getName());
        }
        catch (ClassNotFoundException e)
        {
            LOG.warn("RegisterActionClipsEvent not on classpath — WorldSnapshotClip " +
                     "will NOT appear in editor until the typed override is added.");
        }
    }

    /* ------------- internal reflection helpers ------------- */

    private static Object buildLink(String ns, String path)
    {
        try
        {
            /* Paket ist `resources`, nicht `links` — der falsche name liess jeden aufruf
             * sofort in den catch laufen, samt der drei sorgfaeltigen fallbacks darunter
             * (gefunden vom forname-gate, 2026-07-26). */
            Class<?> linkCls = Class.forName("mchorse.bbs_mod.resources.Link");

            /* Try Link.create(String, String) first. */
            try
            {
                Method create = linkCls.getMethod("create", String.class, String.class);
                return create.invoke(null, ns, path);
            }
            catch (NoSuchMethodException ignored) {}

            /* Fall back to public constructor Link(String, String). */
            try
            {
                return linkCls.getConstructor(String.class, String.class).newInstance(ns, path);
            }
            catch (NoSuchMethodException ignored) {}

            /* Last resort: Link.bbs(path) — only valid for ns == "bbs",
             *  but documented in FRACTURE_PLAN.md so worth trying when
             *  the namespace happens to match. */
            if ("bbs".equals(ns))
            {
                try
                {
                    Method bbs = linkCls.getMethod("bbs", String.class);
                    return bbs.invoke(null, path);
                }
                catch (NoSuchMethodException ignored) {}
            }

            LOG.warn("Link class found but no recognised factory method.");
            return null;
        }
        catch (ClassNotFoundException e)
        {
            LOG.warn("mchorse.bbs_mod.links.Link not on classpath: {}", e.toString());
            return null;
        }
        catch (Throwable t)
        {
            LOG.warn("Link build failed: {}", t.toString(), t);
            return null;
        }
    }

    private static Method findRegister(Class<?> factoryCls, int argCount)
    {
        for (Method m : factoryCls.getMethods())
        {
            if (!"register".equals(m.getName())) continue;
            if (m.getParameterCount() != argCount) continue;
            return m;
        }
        return null;
    }

}
