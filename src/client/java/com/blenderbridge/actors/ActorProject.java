package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.FilmIdentity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Explicit durable binding. Reads never create an identity or write a film. */
public final class ActorProject
{
    public static final String ENGINE = UUID.randomUUID().toString();

    private ActorProject()
    {}

    static JsonObject journal(Film film)
    {
        return film.bridgeJournal.isEmpty() ? new JsonObject()
            : JsonParser.parseString(film.bridgeJournal).getAsJsonObject();
    }

    public static String identity(Film film)
    {
        if (film == null || film.bridgeJournal.isEmpty() || !Minecraft.getInstance().hasSingleplayerServer())
        {
            return "";
        }
        try
        {
            String file = BBSMod.getFilms().getFile(film.getId()).toPath().toRealPath().toString();
            String token = journal(film).get("token").getAsString();
            return UUID.nameUUIDFromBytes((file + "\0" + token).getBytes(StandardCharsets.UTF_8)).toString();
        }
        catch (Exception exception)
        {
            return "";
        }
    }

    static Film require(JsonObject fields)
    {
        Film film = FilmIdentity.resolveOpenFilm();
        if (!Minecraft.getInstance().hasSingleplayerServer() || Minecraft.getInstance().level == null
            || film == null || film.getId().isEmpty()
            || !film.getId().equals(BridgeProtocol.getFieldString(fields, "film", "")))
        {
            throw new IllegalStateException("durable editing requires the named local-world film");
        }
        return film;
    }

    public static JsonObject open(JsonObject fields)
    {
        JsonObject out = ActorDurableTransactions.result(fields, false, "", "");
        try
        {
            Film film = require(fields);
            ActorLeases.install();
            if (film.bridgeJournal.isEmpty())
            {
                JsonObject journal = new JsonObject();
                journal.addProperty("version", 1);
                journal.addProperty("token", UUID.randomUUID().toString());
                journal.addProperty("generation", 0L);
                journal.add("clients", new JsonObject());
                journal.add("receipts", new JsonObject());
                Film staged = BBSMod.getFilms().create(film.getId(), film.toData().asMap());
                staged.bridgeJournal = journal.toString();
                if (!BBSMod.getFilms().save(film.getId(), staged.toData().asMap()))
                {
                    throw new IllegalStateException("could not persist project identity; reload before retry");
                }
                film.bridgeJournal = staged.bridgeJournal;
            }
            String project = identity(film);
            if (project.isEmpty())
            {
                throw new IllegalStateException("no durable project identity");
            }
            out.addProperty("ok", true);
            out.addProperty("project", project);
            out.addProperty("film", film.getId());
            out.addProperty("engine", ENGINE);
        }
        catch (Exception exception)
        {
            out.addProperty("error", exception.getMessage());
        }
        return out;
    }

    /** Verify disk and live journal agree before accepting any durable transaction. */
    static Film bound(JsonObject fields)
    {
        Film film = require(fields);
        String project = identity(film);
        if (project.isEmpty() || !project.equals(BridgeProtocol.getFieldString(fields, "project", "")))
        {
            throw new IllegalStateException("project mismatch; no write or receipt adoption allowed");
        }
        Film disk = BBSMod.getFilms().load(film.getId());
        if (disk == null || !journal(disk).equals(journal(film)))
        {
            throw new IllegalStateException("live/disk journal mismatch; explicit reload required");
        }
        return film;
    }
}
