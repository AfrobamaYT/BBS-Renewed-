package mchorse.bbs_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;

public class BBSCommands
{
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment)
    {
        LiteralArgumentBuilder<ServerCommandSource> bbs = CommandManager.literal("bbs").requires((source) -> source.hasPermissionLevel(2));

        registerMorphCommand(bbs, environment);
        registerFilmsCommand(bbs, environment);

        dispatcher.register(bbs);
    }

    private static void registerMorphCommand(LiteralArgumentBuilder<ServerCommandSource> bbs, CommandManager.RegistrationEnvironment environment)
    {
        LiteralArgumentBuilder<ServerCommandSource> morph = CommandManager.literal("morph");
        RequiredArgumentBuilder<ServerCommandSource, EntitySelector> target = CommandManager.argument("target", EntityArgumentType.player());
        RequiredArgumentBuilder<ServerCommandSource, String> form = CommandManager.argument("form", StringArgumentType.greedyString());

        morph.then(target
            .executes(BBSCommands::morphCommandDemorph)
            .then(form.executes(BBSCommands::morphCommandMorph)));

        bbs.then(morph);
    }

    private static void registerFilmsCommand(LiteralArgumentBuilder<ServerCommandSource> bbs, CommandManager.RegistrationEnvironment environment)
    {
        LiteralArgumentBuilder<ServerCommandSource> scene = CommandManager.literal("films");
        LiteralArgumentBuilder<ServerCommandSource> play = CommandManager.literal("play");
        LiteralArgumentBuilder<ServerCommandSource> stop = CommandManager.literal("stop");
        RequiredArgumentBuilder<ServerCommandSource, EntitySelector> target = CommandManager.argument("target", EntityArgumentType.players());
        RequiredArgumentBuilder<ServerCommandSource, String> playFilm = CommandManager.argument("film", StringArgumentType.word());
        RequiredArgumentBuilder<ServerCommandSource, String> stopFilm = CommandManager.argument("film", StringArgumentType.word());
        RequiredArgumentBuilder<ServerCommandSource, Boolean> camera = CommandManager.argument("camera", BoolArgumentType.bool());

        playFilm.suggests((ctx, builder) ->
        {
            for (String key : BBSMod.getFilms().getKeys())
            {
                builder.suggest(key);
            }

            return builder.buildFuture();
        });

        stopFilm.suggests((ctx, builder) ->
        {
            for (String key : BBSMod.getFilms().getKeys())
            {
                builder.suggest(key);
            }

            return builder.buildFuture();
        });

        scene.then(
            target.then(
                play.then(
                    playFilm.executes((source) -> sceneCommandPlay(source, true))
                        .then(
                            camera.executes((source) -> sceneCommandPlay(source, BoolArgumentType.getBool(source, "camera")))
                        )
                )
            )
            .then(
                stop.then(
                    stopFilm.executes(BBSCommands::sceneCommandStop)
                )
            )
        );

        bbs.then(scene);
    }

    /**
     * /bbs morph McHorseYT - demorph (remove morph) player McHorseYT
     */
    private static int morphCommandDemorph(CommandContext<ServerCommandSource> source) throws CommandSyntaxException
    {
        ServerPlayerEntity entity = EntityArgumentType.getPlayer(source, "target");

        ServerNetwork.sendMorphToTracked(entity, null);
        Morph.getMorph(entity).form = null;

        return 1;
    }

    /**
     * /bbs morph McHorse {id:"bbs:model",model:"butterfly",texture:"assets:models/butterfly/yellow.png"}
     *
     * Morphs player McHorseYT into a butterfly model with yellow skin
     */
    private static int morphCommandMorph(CommandContext<ServerCommandSource> source) throws CommandSyntaxException
    {
        ServerPlayerEntity entity = EntityArgumentType.getPlayer(source, "target");
        String formData = StringArgumentType.getString(source, "form");

        try
        {
            Form form = FormUtils.fromData(DataToString.mapFromString(formData));

            ServerNetwork.sendMorphToTracked(entity, form);
            Morph.getMorph(entity).form = FormUtils.copy(form);

            return 1;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * /bbs film McHorseYT play test - Plays a film (with camera) to McHorseYT
     * /bbs film @a play test false - Plays a film (without camera) to all players
     */
    private static int sceneCommandPlay(CommandContext<ServerCommandSource> source, boolean withCamera) throws CommandSyntaxException
    {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(source, "target");
        String filmId = StringArgumentType.getString(source, "film");

        for (ServerPlayerEntity player : players)
        {
            ServerNetwork.sendPlayFilm(player, filmId, withCamera);
        }

        return 1;
    }

    /**
     * /bbs film McHorseYT stop test - Stops film playback
     */
    private static int sceneCommandStop(CommandContext<ServerCommandSource> source) throws CommandSyntaxException
    {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(source, "target");
        String filmId = StringArgumentType.getString(source, "film");

        for (ServerPlayerEntity player : players)
        {
            ServerNetwork.sendStopFilm(player, filmId);
        }

        return 1;
    }
}
