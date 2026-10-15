package com.blenderbridge.actors;

import com.blenderbridge.FilmIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** User-controlled BBS-side hold. Not accessible as a native owner through the socket. */
public final class ActorNativeLease
{
    private static final String OWNER = "NATIVE_BBS";
    private static JsonObject held;
    private static long renew;
    private static String status = "no property held";

    private ActorNativeLease()
    {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommands.literal("bbslease")
                .then(ClientCommands.literal("hold")
                    .then(ClientCommands.argument("actor", IntegerArgumentType.integer(0))
                        .then(ClientCommands.argument("section", StringArgumentType.word())
                            .then(ClientCommands.argument("path", StringArgumentType.string())
                                .executes(ctx ->
                                {
                                    JsonObject result = hold(IntegerArgumentType.getInteger(ctx, "actor"),
                                        StringArgumentType.getString(ctx, "section"), StringArgumentType.getString(ctx, "path"));
                                    ctx.getSource().sendFeedback(Component.literal(status));
                                    return result.get("ok").getAsBoolean() ? 1 : 0;
                                })))))
                .then(ClientCommands.literal("release").executes(ctx ->
                {
                    release();
                    ctx.getSource().sendFeedback(Component.literal(status));
                    return 1;
                }))
                .then(ClientCommands.literal("status").executes(ctx ->
                {
                    ctx.getSource().sendFeedback(Component.literal(status));
                    return 1;
                }))));
    }

    public static JsonObject hold(int index, String section, String path)
    {
        JsonObject fields = new JsonObject();
        fields.addProperty("requestId", UUID.randomUUID().toString());
        try
        {
            Film film = FilmIdentity.resolveOpenFilm();
            if (film == null || held != null)
            {
                throw new IllegalStateException("open a film and release the previous hold first");
            }
            fields.addProperty("film", film.getId());
            JsonObject project = ActorProject.open(fields);
            if (!project.get("ok").getAsBoolean())
            {
                throw new IllegalStateException(project.get("error").getAsString());
            }
            Replay replay = film.replays.getList().stream().skip(index).findFirst().orElseThrow();
            fields.addProperty("project", project.get("project").getAsString());
            fields.addProperty("client", OWNER);
            fields.addProperty("action", "acquire");
            fields.addProperty("ttlMs", 10000L);
            JsonObject target = new JsonObject();
            target.addProperty("uuid", replay.uuid.get());
            target.addProperty("section", section);
            target.addProperty("path", path);
            JsonArray paths = new JsonArray();
            paths.add(target);
            fields.add("paths", paths);
            JsonObject out = ActorLeases.nativeHandle(fields);
            if (out.get("ok").getAsBoolean())
            {
                fields.addProperty("leaseToken", out.get("leaseToken").getAsString());
                held = fields;
                renew = System.nanoTime() + 3000000000L;
                status = "BBS holds " + replay.uuid.get() + "/" + section + "/" + path;
            }
            else
            {
                status = out.get("error").getAsString();
            }
            return out;
        }
        catch (Exception exception)
        {
            status = exception.getMessage();
            return ActorDurableTransactions.result(fields, false, status, "");
        }
    }

    static boolean owns(ActorLeases.Lease lease)
    {
        return held != null && lease.owner.equals(OWNER)
            && lease == ActorLeases.LEASES.get(held.get("leaseToken").getAsString());
    }

    public static void release()
    {
        if (held != null)
        {
            held.addProperty("action", "release");
            ActorLeases.nativeHandle(held);
        }
        held = null;
        status = "BBS property released";
    }

    public static void tick()
    {
        if (held == null)
        {
            return;
        }
        if (!held.get("project").getAsString().equals(ActorProject.identity(FilmIdentity.resolveOpenFilm())))
        {
            release();
        }
        else if (System.nanoTime() >= renew)
        {
            held.addProperty("action", "renew");
            JsonObject out = ActorLeases.nativeHandle(held);
            renew = System.nanoTime() + 3000000000L;
            if (!out.get("ok").getAsBoolean())
            {
                held = null;
                status = "BBS property lease lost; reacquire explicitly";
            }
        }
    }
}
