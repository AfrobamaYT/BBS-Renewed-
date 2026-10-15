// Coded by Afrobama/Claude — /bbstools sky freeze|unfreeze for stable
// cinematic lighting. The 1m pillager-manhunt cinematic requires the sun
// position, ambient color and weather to stay nailed-down across the
// dozens of takes it takes to land a hero shot — any drift between takes
// makes the colour-grade pass diverge and forces re-shoots. Vanilla
// /gamerule doDaylightCycle false + /gamerule doWeatherCycle false +
// /weather clear together cover this but typing all three plus a long
// duration mid-shoot is friction, and unfreezing means typing three more.
// This command bundles them into a single toggle.
//
//   /bbstools sky freeze     — freezes time + weather + clears sky
//   /bbstools sky unfreeze   — re-enables daylight + weather cycles
//
// What "freeze" actually does:
//   1. GameRule DO_DAYLIGHT_CYCLE = false  → time-of-day stops advancing
//      so the sun stays at its current angle. We also re-set the world's
//      timeOfDay to its current value so anyone who already advanced the
//      tick this frame doesn't slip by one step on the way out.
//   2. GameRule DO_WEATHER_CYCLE = false   → no random rain/thunder rolls
//   3. setWeather(1_000_000, 0, false, false) → forces clear sky now,
//      with a 1m-tick (≈833 minutes IRL) clear duration so even if the
//      user manually flips DO_WEATHER_CYCLE back on later it stays clear
//      well past any realistic recording session. (Vanilla /weather clear
//      <seconds> takes seconds; ServerWorld.setWeather takes ticks — both
//      arguments here are TICKS.)
//
// "Unfreeze" inverts 1 + 2 — we deliberately do NOT roll back the
// already-applied clear-sky duration because the user typically WANTS
// the weather to stay clear for the rest of the session even after
// re-enabling cycles; if they want rain back they can /weather rain.
//
// Architectural note: this file lives in src/client/java for the same
// reason as DeleteGrassCommand — Loom's splitEnvironmentSourceSets
// blocks main → client refs so the server-entry wiring asked for in the
// spec isn't reachable. CommandRegistrationCallback.EVENT (non-client)
// still fires on the integrated server inside singleplayer, so the
// command is server-authoritative despite the package. Registration is
// driven from BBSToolsModClient.
package com.afrobama.bbstools.client.cinematic;

import com.afrobama.bbstools.BBSToolsMod;

import com.mojang.brigadier.Command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

public final class SkyFreezeCommand
{
    /** One million ticks ≈ 13h 53m IRL — comfortably longer than any
     *  realistic recording session. Matches the spec's "/weather clear
     *  1000000" intent. ServerWorld.setWeather takes ticks (vanilla's
     *  /weather command takes seconds and multiplies by 20), so this is
     *  the value passed directly into the API. */
    private static final int CLEAR_DURATION_TICKS = 1_000_000;

    private SkyFreezeCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
        {
            dispatcher.register(Commands.literal("bbstools")
                .then(Commands.literal("sky")
                    .then(Commands.literal("freeze")
                        .executes(ctx -> doFreeze(ctx.getSource())))
                    .then(Commands.literal("unfreeze")
                        .executes(ctx -> doUnfreeze(ctx.getSource())))));
        });
    }

    private static int doFreeze(CommandSourceStack src)
    {
        ServerLevel world = src.getLevel();
        MinecraftServer server = src.getServer();

        if (world == null || server == null)
        {
            src.sendSuccess(() -> Component.literal(
                "§c[sky] §fno world/server available — must be run in-game"), false);
            return 0;
        }

        /* 1. Stop the daylight cycle. GameRules.set(rule, value, server) so
         *  the server broadcasts the change to listeners (commands, the
         *  /gamerule readout, etc.). Without the server arg the rule
         *  mutates silently which trips up debug tooling that reads it.
         *
         *  26.2: die Regeln heissen anders und der Zugriff ist flacher.
         *  RULE_DAYLIGHT -> ADVANCE_TIME, RULE_WEATHER_CYCLE -> ADVANCE_WEATHER,
         *  und statt getRule(K).set(wert, server) gibt es set(K, wert, server)
         *  direkt (GameRule<Boolean> statt Key<BooleanValue>). */
        GameRules rules = world.getGameRules();
        rules.set(GameRules.ADVANCE_TIME, false, server);
        rules.set(GameRules.ADVANCE_WEATHER, false, server);

        /* 2. Re-anchor the timeOfDay to its current value. Belt-and-
         *  suspenders: if a same-tick handler already advanced the time
         *  before this command ran, we snap back to the visible angle
         *  the user is framing for, not the half-step in between.
         *
         *  ⚠️ 26.2: getDayTime/setDayTime gibt es NICHT mehr -- die Tageszeit ist
         *  keine Zahl an der Welt mehr, sondern eine benannte UHR
         *  (net.minecraft.world.clock). Jede Dimension nennt ihre eigene:
         *  dimensionType().defaultClock() ist ein Optional<Holder<WorldClock>>,
         *  im Nether z.B. leer -- dort gibt es nichts zu verankern, deshalb der
         *  ifPresent statt eines orElseThrow.
         *
         *  Level.getDefaultClockTime() ist exakt clockManager().getTotalTicks(
         *  defaultClock) (im Bytecode nachgesehen), setTotalTicks ist also der
         *  passende Gegenpart -- kein Vermischen von Tageszeit und Gesamtticks. */
        //? if >=26.1 {
        long now = world.getDefaultClockTime();

        world.dimensionType().defaultClock().ifPresent(
            uhr -> world.clockManager().setTotalTicks(uhr, world.getDefaultClockTime()));
        //?} else {
        /*/^* Vor 26.1 steht die Zeit an der Welt selbst (setDayTime). *^/
        long now = world.getDayTime();

        world.setDayTime(now);
        *///?}

        /* 3. Force-clear the sky. setWeatherParameters takes TICKS for
         *  the clear-duration arg (the vanilla /weather command converts
         *  seconds → ticks internally). We pass raining=false +
         *  thundering=false explicitly to ensure that even if it was
         *  currently raining the moment freeze runs, we stop it.
         *
         *  26.2: die Methode ist von ServerLevel an den MinecraftServer gewandert
         *  (so ruft WeatherCommand.setClear sie auch auf). Sie wirkt jetzt auf
         *  ALLE Welten des Servers statt nur auf diese -- fuer einen Dreh ist das
         *  die gewollte Wirkung, aber es ist ein Unterschied zum Vorher. */
        //? if >=26.1 {
        server.setWeatherParameters(CLEAR_DURATION_TICKS, 0, false, false);
        //?} else {
        /*/^* Vor 26.2 sitzt die Methode an der Welt (wirkt nur auf diese). *^/
        world.setWeatherParameters(CLEAR_DURATION_TICKS, 0, false, false);
        *///?}

        final long timeFinal = now;
        src.sendSuccess(() -> Component.literal(
            "§a[sky] §fFROZEN §7— daylight cycle off, weather cycle off, sky cleared"), true);
        src.sendSuccess(() -> Component.literal(
            "§7  timeOfDay=" + timeFinal + " (mod 24000=" + (timeFinal % 24000L) + ")"), false);
        src.sendSuccess(() -> Component.literal(
            "§7  use §f/bbstools sky unfreeze §7to resume"), false);

        BBSToolsMod.LOG.info("/bbstools sky freeze — timeOfDay={} (tick-of-day={}), weather cleared for {} ticks",
            timeFinal, timeFinal % 24000L, CLEAR_DURATION_TICKS);

        return Command.SINGLE_SUCCESS;
    }

    private static int doUnfreeze(CommandSourceStack src)
    {
        ServerLevel world = src.getLevel();
        MinecraftServer server = src.getServer();

        if (world == null || server == null)
        {
            src.sendSuccess(() -> Component.literal(
                "§c[sky] §fno world/server available — must be run in-game"), false);
            return 0;
        }

        /* Inverse of freeze for cycle gamerules only. The already-applied
         *  clear-sky duration is left alone: if the user wants rain back
         *  they can run /weather rain — but most of the time after a
         *  shoot they want the sky to stay clear so they can review
         *  takes without lighting changes between them. */
        GameRules rules = world.getGameRules();
        rules.set(GameRules.ADVANCE_TIME, true, server);
        rules.set(GameRules.ADVANCE_WEATHER, true, server);

        src.sendSuccess(() -> Component.literal(
            "§e[sky] §fUNFROZEN §7— daylight cycle on, weather cycle on (sky stays clear until natural roll)"), true);

        BBSToolsMod.LOG.info("/bbstools sky unfreeze — daylight + weather cycles re-enabled (clear-sky timer left intact)");

        return Command.SINGLE_SUCCESS;
    }

}
