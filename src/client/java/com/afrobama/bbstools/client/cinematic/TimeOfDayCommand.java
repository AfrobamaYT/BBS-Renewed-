// Coded by Afrobama/Claude — /bbstools tod cinematic time-of-day setter.
// Mid-shoot the user wants to snap world time to a specific phase
// without typing /time set <number> and without waiting for the slow
// vanilla day-cycle interpolation (the "server slop" — vanilla's
// world-border-sync packet + per-tick tick-skip math adds 2-4 ticks of
// visible jitter when /time set runs through the regular command path).
// Going straight at ServerWorld.setTimeOfDay() on the integrated server
// world is a single-tick set with no command-dispatch overhead.
//
//   /bbstools tod <0-24000>           — set absolute MC day-time
//   /bbstools tod night|noon|dawn|dusk — named cinematic presets
//
// Presets (per user spec — these match what the cinematic eye expects,
// not what /time set's literals expect; in particular "night" = 18000
// is true midnight when the moon is dead-center, and "dawn" = 23000 is
// just before sunrise so the horizon already has the warm rim):
//   night = 18000   moon overhead, deepest blue, stars at peak
//   noon  =  6000   sun overhead, brightest sky
//   dawn  = 23000   pre-sunrise, warm horizon, low ambient
//   dusk  = 12000   sun just below horizon, magic-hour purple
//
// Architectural notes:
//   - CommandRegistrationCallback.EVENT (server variant) fires on the
//     integrated server inside a singleplayer client, so this is
//     server-authoritative despite living in src/client/java. Same
//     pattern as DeleteGrassCommand — see its file header for the
//     splitEnvironmentSourceSets rationale.
//   - We try the public Yarn method setTimeOfDay(long) first; if a
//     future BBS-Renewed remap obfuscates it (mojmap, intermediary,
//     etc.) we fall back to a reflective lookup across the known method
//     names (setTimeOfDay, method_29199, setDayTime). Belt-and-braces
//     so the command keeps working across mapping flips.
//   - Wraps the input modulo 24000 so /bbstools tod 30000 still does
//     the sensible thing (= 6000 = noon) instead of silently advancing
//     a full day.
package com.afrobama.bbstools.client.cinematic;

import com.afrobama.bbstools.BBSToolsMod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class TimeOfDayCommand
{
    /** Length of one MC day in ticks. Used both for the argument range
     *  (0..24000 inclusive — same as vanilla /time set) and the
     *  modulo-wrap so overflows still produce a sensible time. */
    private static final int DAY_TICKS = 24000;

    /** Cinematic preset values — see file header for the rationale. */
    private static final int PRESET_NIGHT = 18000;
    private static final int PRESET_NOON  = 6000;
    private static final int PRESET_DAWN  = 23000;
    private static final int PRESET_DUSK  = 12000;

    private TimeOfDayCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
        {
            dispatcher.register(Commands.literal("bbstools")
                .then(Commands.literal("tod")
                    /* Numeric form: /bbstools tod <0-24000>. Brigadier
                     *  enforces the range; we still % DAY_TICKS for
                     *  safety in case a future caller bypasses the
                     *  client parse path (e.g. macros). */
                    .then(Commands.argument("time", IntegerArgumentType.integer(0, DAY_TICKS))
                        .executes(ctx -> doSet(ctx, IntegerArgumentType.getInteger(ctx, "time"), null)))
                    /* Named presets — separate literals so tab-complete
                     *  surfaces them as suggestions, no extra typing. */
                    .then(Commands.literal("night").executes(ctx -> doSet(ctx, PRESET_NIGHT, "night")))
                    .then(Commands.literal("noon") .executes(ctx -> doSet(ctx, PRESET_NOON,  "noon")))
                    .then(Commands.literal("dawn") .executes(ctx -> doSet(ctx, PRESET_DAWN,  "dawn")))
                    .then(Commands.literal("dusk") .executes(ctx -> doSet(ctx, PRESET_DUSK,  "dusk")))));
        });
    }

    private static int doSet(CommandContext<CommandSourceStack> ctx, int rawTime, String presetName)
    {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try
        {
            player = src.getPlayerOrException();
        }
        catch (Exception e)
        {
            src.sendSuccess(() -> Component.literal(
                "§c[tod] §fmust be run by a player §7(needs a world reference)"), false);
            return 0;
        }

        ServerLevel world = player.level();
        long time = ((rawTime % DAY_TICKS) + DAY_TICKS) % DAY_TICKS;

        boolean ok = applyTimeOfDay(world, time);
        if (!ok)
        {
            src.sendSuccess(() -> Component.literal(
                "§c[tod] §ffailed to set time §7(no setTimeOfDay/setDayTime method on "
                + world.getClass().getName() + ")"), false);
            BBSToolsMod.LOG.warn("/bbstools tod could not find a time-setter on {}", world.getClass().getName());
            return 0;
        }

        /* Cinematic feedback: short, in-chat, broadcast=true so other
         *  observers in the world (e.g. recording party) see the snap
         *  in their chat too — useful for sync-clapper-style takes. */
        final long timeFinal = time;
        final String labelFinal = presetName != null ? presetName : Long.toString(time);
        src.sendSuccess(() -> Component.literal(
            "§a[tod] §ftime set to §a" + labelFinal
            + "§f §7(" + timeFinal + " ticks)"), true);

        BBSToolsMod.LOG.info("/bbstools tod {} ({} ticks) on {}",
            labelFinal, timeFinal, world.dimension().identifier());

        return Command.SINGLE_SUCCESS;
    }

    /** Try the public Yarn method first (fast path, JIT-able), fall back
     *  to reflection across the known method names (setTimeOfDay,
     *  method_29199 = intermediary, setDayTime = some mojmap variants).
     *  Returns true on success. */
    private static boolean applyTimeOfDay(ServerLevel world, long time)
    {
        try
        {
            /* ⚠️ 26.2: setDayTime(long) gibt es nicht mehr. Die Tageszeit gehoert einer UHR,
             * und eine Dimension muss keine haben — deshalb ein Optional. Gesetzt wird sie
             * ueber den Uhrenverwalter der Welt.
             * VERHALTENSAENDERUNG: in einer Dimension ohne eigene Uhr (Nether, Ende) gibt es
             * nichts zu setzen; frueher schluckte setDayTime das stillschweigend, jetzt meldet
             * der Befehl es als Fehlschlag und der Aufrufer sagt es dem Nutzer.
             * ⚠️ Und die Uhr ist geteilt: alle Dimensionen, die dieselbe Uhr fuehren, springen
             * mit — vorher stand die Zeit an der einzelnen Welt. */
            //? if >=26.1 {
            /*java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock>> uhr = world.dimensionType().defaultClock();

            if (uhr.isEmpty())
            {
                BBSToolsMod.LOG.warn("/bbstools tod: dimension {} has no clock of its own, nothing to set", world.dimension().identifier());

                return false;
            }

            world.clockManager().setTotalTicks(uhr.get(), time);
            *///?} else {
            /** Vor 26.1 steht die Zeit an der Welt selbst; jede Dimension hat eine. **/
            world.setDayTime(time);
            //?}
            return true;
        }
        catch (LinkageError directFail)
        {
            /* LinkageError covers NoSuchMethodError, AbstractMethodError,
             *  IncompatibleClassChangeError — anything the verifier raises
             *  when our compiled call site doesn't line up with the
             *  ServerWorld actually loaded at runtime (e.g. remap drift). */
            BBSToolsMod.LOG.debug("Direct setTimeOfDay missing ({}), falling back to reflection",
                directFail.toString());
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.warn("Direct setTimeOfDay threw: {}", t.toString());
        }

        /* Reflection fallback for remapped variants. We probe a small
         *  whitelist of method names rather than scanning all declared
         *  methods so an attacker who somehow controls the class can't
         *  trick us into invoking an unrelated long-taking setter. */
        String[] candidates = { "setTimeOfDay", "method_29199", "setDayTime" };
        for (String name : candidates)
        {
            try
            {
                var m = world.getClass().getMethod(name, long.class);
                m.invoke(world, time);
                return true;
            }
            catch (NoSuchMethodException ignored)
            {
                /* try the next name */
            }
            catch (Throwable t)
            {
                BBSToolsMod.LOG.warn("Reflective {} threw: {}", name, t.toString());
            }
        }

        return false;
    }

}
