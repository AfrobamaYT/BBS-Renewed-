package com.afrobama.bbstools.client.fracture;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-only bridge to WorldEdit-Fabric so the user can pull
 * their current pos1/pos2 selection straight into a fracture clip
 * via a single button click — way better than per-corner
 * "Set from looked-at" clicks.
 *
 * <p>If WorldEdit isn't installed all calls return null and the
 * UI button is a no-op (with a chat hint). No hard dependency on
 * WorldEdit classes — the bridge fails gracefully when the mod
 * isn't loaded.</p>
 */
public final class WorldEditBridge
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/worldedit");

    private static volatile Boolean cachedAvailable;

    private WorldEditBridge()
    {
    }

    /** True if WorldEdit-Fabric is present + the API classes resolve. */
    public static boolean isAvailable()
    {
        if (cachedAvailable != null) return cachedAvailable;
        try
        {
            if (!FabricLoader.getInstance().isModLoaded("worldedit"))
            {
                cachedAvailable = false;
                return false;
            }
            Class.forName("com.sk89q.worldedit.WorldEdit");
            cachedAvailable = true;
            return true;
        }
        catch (Throwable t)
        {
            cachedAvailable = false;
            return false;
        }
    }

    /** Returns [min, max] BlockPos pair from the current player's
     *  WorldEdit selection, or null if no selection / WE missing. */
    public static BlockPos[] getCurrentSelection()
    {
        if (!isAvailable())
        {
            LOG.info("[worldedit] not available — install worldedit-mod or check fabric.mod.json id");
            return null;
        }

        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return null;
            if (mc.getSingleplayerServer() == null)
            {
                LOG.warn("[worldedit] no integrated server — selection import only works in singleplayer");
                return null;
            }

            Object serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (serverPlayer == null)
            {
                LOG.warn("[worldedit] couldn't resolve ServerPlayerEntity for current client player");
                return null;
            }

            /* Adapt client → FabricPlayer (which implements SessionOwner). */
            Class<?> adapterCls = Class.forName("com.sk89q.worldedit.fabric.FabricAdapter");
            Method adaptPlayer = adapterCls.getMethod("adaptPlayer",
                net.minecraft.server.level.ServerPlayer.class);
            Object actor = adaptPlayer.invoke(null, serverPlayer);
            if (actor == null)
            {
                LOG.warn("[worldedit] adaptPlayer returned null");
                return null;
            }

            /* WorldEdit.getInstance().getSessionManager().get(SessionOwner). */
            Class<?> weCls = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object we = weCls.getMethod("getInstance").invoke(null);
            Object sessionMgr = weCls.getMethod("getSessionManager").invoke(we);

            /* Use the explicitly-loaded SessionOwner class so we don't
             *  fish through getInterfaces()[0] which can be wrong. */
            Class<?> sessionOwnerCls = Class.forName("com.sk89q.worldedit.session.SessionOwner");
            Method getSession = sessionMgr.getClass().getMethod("get", sessionOwnerCls);
            Object session = getSession.invoke(sessionMgr, actor);
            if (session == null)
            {
                LOG.warn("[worldedit] session manager returned null session for player");
                return null;
            }

            /* getSelection(World) — find the right overload (some
             *  versions are zero-arg, others take the World). */
            Object weWorld = null;
            try
            {
                Method adaptWorld = adapterCls.getMethod("adapt", net.minecraft.world.level.Level.class);
                weWorld = adaptWorld.invoke(null, mc.level);
            }
            catch (Throwable ignored) { /* zero-arg getSelection variant */ }

            Method getSelection = null;
            for (Method m : session.getClass().getMethods())
            {
                if (!"getSelection".equals(m.getName())) continue;
                int pc = m.getParameterCount();
                if (pc == 1 && weWorld != null && m.getParameterTypes()[0].isInstance(weWorld))
                {
                    getSelection = m;
                    break;
                }
                if (pc == 0 && getSelection == null) getSelection = m;
            }
            if (getSelection == null)
            {
                LOG.warn("[worldedit] no getSelection() method found on LocalSession");
                return null;
            }

            Object region = null;
            Throwable lastEx = null;

            /* Try both overloads — some WE versions throw on the
             *  zero-arg one when there's no "current world", others
             *  throw on the World-arg one if the World adapt result
             *  doesn't match the session's last world. Try whichever
             *  succeeds. */
            for (Method m : session.getClass().getMethods())
            {
                if (!"getSelection".equals(m.getName())) continue;
                int pc = m.getParameterCount();
                if (pc > 1) continue;
                try
                {
                    region = pc == 0 ? m.invoke(session) : m.invoke(session, weWorld);
                    if (region != null) { getSelection = m; break; }
                }
                catch (java.lang.reflect.InvocationTargetException ite)
                {
                    lastEx = ite.getCause() != null ? ite.getCause() : ite;
                }
                catch (Throwable t) { lastEx = t; }
            }

            if (region == null)
            {
                String exClass = lastEx == null ? "(none)" : lastEx.getClass().getSimpleName();
                String exMsg = lastEx == null ? "" : (lastEx.getMessage() == null ? "" : ": " + lastEx.getMessage());
                LOG.warn("[worldedit] getSelection failed — [{}]{}", exClass, exMsg);

                /* Common WorldEdit exceptions:
                 *  - IncompleteRegionException → user hasn't set both pos1 + pos2
                 *  - NullPointerException on the World arg → wrong overload
                 *  - any other → unknown WE state */
                if (lastEx != null && "IncompleteRegionException".equals(lastEx.getClass().getSimpleName()))
                {
                    LOG.info("[worldedit] tip: use //pos1 and //pos2 with the wooden axe (default), then click 'Use WorldEdit selection' again");
                }
                return null;
            }

            if (region == null) return null;

            Method getMin = region.getClass().getMethod("getMinimumPoint");
            Method getMax = region.getClass().getMethod("getMaximumPoint");
            Object minBV = getMin.invoke(region);
            Object maxBV = getMax.invoke(region);

            BlockPos minP = blockVectorToPos(minBV);
            BlockPos maxP = blockVectorToPos(maxBV);
            LOG.info("[worldedit] selection imported: {} → {}", minP, maxP);
            return new BlockPos[]{ minP, maxP };
        }
        catch (Throwable t)
        {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.warn("[worldedit] selection read failed: [{}] {}",
                cause.getClass().getSimpleName(), cause.getMessage());
            return null;
        }
    }

    private static BlockPos blockVectorToPos(Object bv)
    {
        try
        {
            int x = ((Number) bv.getClass().getMethod("getBlockX").invoke(bv)).intValue();
            int y = ((Number) bv.getClass().getMethod("getBlockY").invoke(bv)).intValue();
            int z = ((Number) bv.getClass().getMethod("getBlockZ").invoke(bv)).intValue();
            return new BlockPos(x, y, z);
        }
        catch (Throwable t)
        {
            try
            {
                /* Older WE BlockVector exposes x/y/z fields. */
                Field xf = bv.getClass().getField("x"), yf = bv.getClass().getField("y"), zf = bv.getClass().getField("z");
                return new BlockPos(((Number) xf.get(bv)).intValue(),
                                    ((Number) yf.get(bv)).intValue(),
                                    ((Number) zf.get(bv)).intValue());
            }
            catch (Throwable ignored) { return BlockPos.ZERO; }
        }
    }

}
