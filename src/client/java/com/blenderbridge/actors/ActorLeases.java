package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.RigPropertyPath;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread, expiring property leases. The token is boot-scoped; it is never durable ownership. */
public final class ActorLeases
{
    static final Map<String, Lease> LEASES = new HashMap<>();
    static final Set<String> ROOT = Set.of("x", "y", "z", "yaw", "bodyYaw", "scale_x", "scale_y", "scale_z");
    static final Set<String> ITEMS = Set.of(StringChannels.ITEM_CHANNELS);

    private ActorLeases()
    {}

    static void install()
    {
        Film.editGuard = ActorLeases::checkNative;
    }

    static void expire()
    {
        long now = System.nanoTime();
        LEASES.values().removeIf(lease -> lease.expires <= now);
    }

    static String key(String uuid, String section, String path)
    {
        if (section.equals("channels") && path.equals("bodyYaw"))
        {
            path = "yaw";
        }

        return uuid + "/" + section + "/" + path;
    }

    static String leaseKey(String uuid, String section, String path)
    {
        return key(uuid, section, section.equals("shapeKeys") ? "*" : path);
    }

    public static JsonObject handle(JsonObject fields)
    {
        if ("NATIVE_BBS".equals(BridgeProtocol.getFieldString(fields, "client", "")))
        {
            return ActorDurableTransactions.result(fields, false, "native owner is not a socket identity", "");
        }
        return nativeHandle(fields);
    }

    static synchronized JsonObject nativeHandle(JsonObject fields)
    {
        JsonObject out = ActorDurableTransactions.result(fields, false, "", "");
        try
        {
            Film film = ActorProject.bound(fields);
            String project = ActorProject.identity(film);
            String owner = fields.get("client").getAsString();
            if (!owner.matches("[a-zA-Z0-9_-]{1,64}"))
            {
                throw new IllegalArgumentException("invalid lease owner");
            }
            expire();
            install();
            String action = fields.get("action").getAsString();
            String token = BridgeProtocol.getFieldString(fields, "leaseToken", "");
            long ttl = Math.max(100L, Math.min(30000L, BridgeProtocol.getLong(fields, "ttlMs", 10000L)));
            Lease lease = LEASES.get(token);
            if (action.equals("acquire"))
            {
                if (LEASES.size() >= 128 || fields.getAsJsonArray("paths").size() > 128)
                {
                    throw new Blocked("lease capacity reached");
                }
                List<Target> targets = new ArrayList<>();
                for (JsonElement element : fields.getAsJsonArray("paths"))
                {
                    JsonObject path = element.getAsJsonObject();
                    String id = path.get("uuid").getAsString();
                    String section = path.get("section").getAsString();
                    String name = path.get("path").getAsString();
                    Replay replay = film.replays.getList().stream().filter(r -> r.uuid.get().equals(id)).findFirst().orElseThrow();
                    if (!(section.equals("channels") && ROOT.contains(name))
                        && !(section.equals("strings") && ITEMS.contains(name))
                        && !(section.equals("bones") && !name.isEmpty() && name.length() <= 256)
                        && !(section.equals("rigProperties") && name.length() <= 768
                            && rootRigPath(name))
                        && !(section.equals("shapeKeys") && !name.isEmpty() && name.length() <= 256)
                        && !(section.equals(VisibilityPatch.SECTION)
                            && name.equals(VisibilityPatch.PATH)))
                    {
                        throw new IllegalArgumentException("unsupported lease path");
                    }
                    Target target = new Target(replay.uuid.get(), section, name);
                    for (Lease held : LEASES.values())
                    {
                        if (held.project.equals(project) && held.targets.stream().anyMatch(t -> t.key().equals(target.key())))
                        {
                            throw new Blocked("path already leased: " + target.key());
                        }
                    }
                    targets.add(target);
                }
                if (targets.isEmpty())
                {
                    throw new IllegalArgumentException("empty lease");
                }
                token = ActorProject.ENGINE + ":" + UUID.randomUUID();
                lease = new Lease(project, owner, targets, System.nanoTime() + ttl * 1000000L);
                LEASES.put(token, lease);
            }
            else
            {
                if (lease == null || !lease.project.equals(project) || !lease.owner.equals(owner))
                {
                    throw new Blocked("lease expired or belongs to another owner");
                }
                if (action.equals("release"))
                {
                    LEASES.remove(token);
                }
                else if (action.equals("renew"))
                {
                    lease.expires = System.nanoTime() + ttl * 1000000L;
                }
                else
                {
                    throw new IllegalArgumentException("invalid lease action");
                }
            }
            out.addProperty("ok", true);
            out.addProperty("leaseToken", token);
            out.addProperty("ttlMs", ttl);
            out.addProperty("engine", ActorProject.ENGINE);
            out.addProperty("project", project);
        }
        catch (Exception exception)
        {
            out.addProperty("error", exception.getMessage());
            out.addProperty("leaseBusy", true);
        }
        return out;
    }

    public static synchronized void checkNative(Film film, BaseValue value)
    {
        expire();

        if (ActorLeaseAccess.isValidatedWrite())
        {
            return;
        }

        if (!LEASES.isEmpty())
        {
            String project = ActorProject.identity(film);

            for (Lease lease : LEASES.values())
            {
                if (!lease.project.equals(project) || ActorNativeLease.owns(lease))
                {
                    continue;
                }

                for (Target target : lease.targets)
                {
                    Replay replay = film.replays.getList().stream()
                        .filter(r -> r.uuid.get().equals(target.uuid)).findFirst().orElse(null);

                    if (replay == null)
                    {
                        continue;
                    }

                    BaseValue channel = target.section.equals("bones")
                        ? replay.properties.get("pose.bones." + target.path)
                        : target.section.equals("rigProperties")
                        ? replay.properties.get(target.path)
                        : target.section.equals("shapeKeys")
                        ? replay.properties.get("shape_keys") : replay.keyframes.get(target.path);
                    if (target.section.equals(VisibilityPatch.SECTION))
                    {
                        channel = replay.enabled;
                    }
                    boolean pose = target.section.equals("bones") && related(value, replay.properties.get("pose"));
                    boolean yaw = target.section.equals("channels")
                        && (target.path.equals("yaw") || target.path.equals("bodyYaw"))
                        && (related(value, replay.keyframes.get("yaw"))
                            || related(value, replay.keyframes.get("bodyYaw")));

                    if (related(value, channel) || pose || yaw || value == replay.form || value == replay.uuid)
                    {
                        throw new Blocked("property leased by " + lease.owner + ": " + target.key());
                    }
                }
            }
        }

        ActorNativeRevisions.noteNative(film, value);
    }

    private static boolean rootRigPath(String path)
    {
        RigPropertyPath parsed = RigPropertyPath.parse(path);

        return parsed != null && parsed.formPath().isEmpty();
    }

    private static boolean related(BaseValue a, BaseValue b)
    {
        if (a == null || b == null)
        {
            return false;
        }
        for (BaseValue p = a; p != null; p = p.getParent())
        {
            if (p == b)
            {
                return true;
            }
        }
        for (BaseValue p = b; p != null; p = p.getParent())
        {
            if (p == a)
            {
                return true;
            }
        }
        return false;
    }

    static final class Lease
    {
        final String project, owner;
        final List<Target> targets;
        long expires;
        Lease(String project, String owner, List<Target> targets, long expires)
        {
            this.project = project;
            this.owner = owner;
            this.targets = targets;
            this.expires = expires;
        }
    }

    record Target(String uuid, String section, String path)
    {
        String key()
        {
            return ActorLeases.leaseKey(this.uuid, this.section, this.path);
        }
    }

    public static final class Blocked extends RuntimeException
    {
        public Blocked(String message)
        {
            super(message);
        }
    }
}
