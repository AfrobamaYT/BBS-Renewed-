package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Lease preflight surrounds one already validated bridge write, never all remote activity. */
public final class ActorLeaseAccess
{
    private static final ThreadLocal<Boolean> VALIDATED = ThreadLocal.withInitial(() -> false);
    private static long lastNotice;

    private ActorLeaseAccess()
    {}

    static boolean isValidatedWrite()
    {
        return VALIDATED.get();
    }

    public static JsonObject write(JsonObject fields, String token, Supplier<JsonObject> operation)
    {
        try
        {
            Set<String> requested = new HashSet<>();
            if (fields != null && fields.has("replays"))
            {
                for (JsonElement element : fields.getAsJsonArray("replays"))
                {
                    JsonObject replay = element.getAsJsonObject();
                    for (String section : new String[] {"channels", "strings", "bones", "shapeKeys"})
                    {
                        if (replay.has(section))
                        {
                            for (String path : replay.getAsJsonObject(section).keySet())
                            {
                                requested.add(ActorLeases.leaseKey(
                                    replay.get("uuid").getAsString(), section, path));
                            }
                        }
                    }
                    if (replay.has(ShapeTimelinePatch.SECTION))
                    {
                        requested.add(ActorLeases.leaseKey(
                            replay.get("uuid").getAsString(), "shapeKeys", "$timeline"));
                    }
                    if (replay.has(ShapeGridPatch.SECTION))
                    {
                        requested.add(ActorLeases.leaseKey(
                            replay.get("uuid").getAsString(), "shapeKeys", "$grid"));
                    }
                    if (replay.has(VisibilityPatch.SECTION))
                    {
                        requested.add(ActorLeases.leaseKey(
                            replay.get("uuid").getAsString(), VisibilityPatch.SECTION,
                            VisibilityPatch.PATH));
                    }
                }
            }
            synchronized (ActorLeases.class)
            {
                ActorLeases.expire();
                ActorLeases.Lease own = ActorLeases.LEASES.get(token);
                String client = BridgeProtocol.getFieldString(fields, "client", "");
                String project = ActorProject.identity(com.blenderbridge.FilmIdentity.resolveOpenFilm());
                if (!token.isEmpty() && (own == null || !own.owner.equals(client) || !own.project.equals(project)))
                {
                    throw new ActorLeases.Blocked("expired or foreign lease token; write fenced");
                }
                for (ActorLeases.Lease lease : ActorLeases.LEASES.values())
                {
                    if (lease.project.equals(project) && lease.targets.stream().anyMatch(t -> requested.contains(t.key()))
                        && lease != own)
                    {
                        throw new ActorLeases.Blocked("requested property is leased by another editor");
                    }
                }
            }
            VALIDATED.set(true);
            try
            {
                return operation.get();
            }
            finally
            {
                VALIDATED.remove();
            }
        }
        catch (ActorLeases.Blocked blocked)
        {
            JsonObject out = ActorDurableTransactions.result(fields, false, blocked.getMessage(), "");
            out.addProperty("leaseBusy", true);
            out.addProperty("notApplied", true);
            out.addProperty("retryAfterMs", 500);
            out.addProperty("project", ActorProject.identity(com.blenderbridge.FilmIdentity.resolveOpenFilm()));
            return out;
        }
        catch (Exception exception)
        {
            JsonObject out = ActorDurableTransactions.result(fields, false, exception.getMessage(), "");
            out.addProperty("receiptUnknown", true);
            return out;
        }
    }

    /** Preflight the complete native compound undo before its first child mutates anything. */
    public static void checkUndo(Film film, IUndo<ValueGroup> undo)
    {
        if (undo instanceof CompoundUndo<ValueGroup> compound)
        {
            for (IUndo<ValueGroup> child : compound.getUndos())
            {
                checkUndo(film, child);
            }
        }
        else if (undo instanceof ValueChangeUndo change)
        {
            BaseValue value = film.findRecursively(change.name);
            if (value != null)
            {
                ActorLeases.checkNative(film, value);
            }
        }
    }

    public static boolean ui(Supplier<Boolean> input)
    {
        try
        {
            return input.get();
        }
        catch (ActorLeases.Blocked blocked)
        {
            if (Minecraft.getInstance().player != null && System.nanoTime() - lastNotice > 1000000000L)
            {
                lastNotice = System.nanoTime();
                mchorse.bbs_mod.ui.framework.UIBaseMenu menu = mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();
                if (menu != null)
                {
                    menu.context.notifyError(mchorse.bbs_mod.l10n.keys.IKey.raw(blocked.getMessage()));
                }
                else
                {
                    Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(blocked.getMessage()), false);
                }
            }
            return false;
        }
    }
}
