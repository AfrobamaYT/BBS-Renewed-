// Coded by Afrobama/Claude — /bbstools fx command for the new
// FX pipeline. Subcommands:
//   /bbstools fx enable              add our pipelines to Veil
//   /bbstools fx disable             remove our pipelines from Veil
//   /bbstools fx status              print pipeline ENABLED + active state
//   /bbstools fx supernova           trigger at player position
//   /bbstools fx supernova <x y z>   trigger at world position
//   /bbstools fx nuke                trigger at player position
//   /bbstools fx nuke <x y z>        trigger at world position
//   /bbstools fx meteor              trigger meteor strike at player pos
//   /bbstools fx meteor <x y z>      trigger meteor strike at impact pt
//   /bbstools fx glitch              REALITY GLITCH — corruption tear
//   /bbstools fx glitch <x y z>      REALITY GLITCH at world position
//   /bbstools fx antimatter          ANTIMATTER IMPLOSION — suck then release
//   /bbstools fx antimatter <x y z>  ANTIMATTER IMPLOSION at world position
//   /bbstools fx crystal             CRYSTALLIZATION WAVE — icy expansion
//   /bbstools fx crystal <x y z>     CRYSTALLIZATION WAVE at world position
//   /bbstools fx demo                one-keystroke showcase chain
//                                      meteor → nuke → supernova
//   /bbstools fx help                print full command tree in-game
//   /bbstools fx shadow-interval <N> throttle shadowmap re-render
//                                      1=every frame, 0=matrices-only
//   /bbstools fx destroying-mc       one-button: enable + apply the
//                                      destroying-mc default look preset
//   /bbstools fx cancel              cancel any active effect
//   /bbstools fx vibrance <0..1>     set vibrance amount
//   /bbstools fx exposure <0.1..5>   set tonemap exposure
//   /bbstools fx sundir <x> <z>      set sun rotation (degrees) for shadows
//   /bbstools fx shake <intensity>   manual camera shake (0.5..3.0)
//   /bbstools fx preset <name>       color grade preset:
//                                      cinematic | neutral | moody | action
package com.afrobama.bbstools.client.fx;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.effects.AntimatterEffect;
import com.afrobama.bbstools.client.fx.effects.CrystalEffect;
import com.afrobama.bbstools.client.fx.effects.DemoSequencer;
import com.afrobama.bbstools.client.fx.effects.GlitchEffect;
import com.afrobama.bbstools.client.fx.effects.MeteorEffect;
import com.afrobama.bbstools.client.fx.effects.NukeEffect;
import com.afrobama.bbstools.client.fx.effects.RevealLightEffect;
import com.afrobama.bbstools.client.fx.effects.SupernovaEffect;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class BBSFxCommand
{
    private BBSFxCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommands.literal("bbstools")
                .then(ClientCommands.literal("fx")
                    .then(ClientCommands.literal("enable")
                        .executes(c -> {
                            BBSFxPipelineRegistrar.setEnabled(true);
                            c.getSource().sendFeedback(Component.literal("§a[bbstools fx] pipelines ENABLED — shadows + bloom + final tonemap will engage next tick"));
                            if (isIrisActive())
                            {
                                c.getSource().sendFeedback(Component.literal("§e[bbstools fx] WARN: Iris shaderpack is active. Iris owns the world render path; Veil-based FX may be inhibited or look wrong. Disable the shaderpack (or pick \"(internal)\") if FX doesn't appear."));
                            }
                            return 1;
                        }))
                    .then(ClientCommands.literal("disable")
                        .executes(c -> {
                            BBSFxPipelineRegistrar.setEnabled(false);
                            c.getSource().sendFeedback(Component.literal("§e[bbstools fx] pipelines DISABLED — vanilla look restored next tick"));
                            return 1;
                        }))
                    .then(ClientCommands.literal("status")
                        .executes(c -> {
                            c.getSource().sendFeedback(Component.literal("§b[bbstools fx] ENABLED=" + BBSFxPipelineRegistrar.ENABLED
                                + " vibrance=" + BBSFxState.vibranceAmount
                                + " exposure=" + BBSFxState.tonemapExposure
                                + " supernovaTimer=" + BBSFxState.supernovaTimer));
                            return 1;
                        }))
                    .then(ClientCommands.literal("supernova")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            SupernovaEffect.trigger((float) p.x, (float) p.y + 30F, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§a[bbstools fx] supernova triggered 30 b above you"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        SupernovaEffect.trigger(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§a[bbstools fx] supernova triggered at (" + x + "," + y + "," + z + ")"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("nuke")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            NukeEffect.trigger((float) p.x, (float) p.y, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§a[bbstools fx] nuke triggered at your position"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        NukeEffect.trigger(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§a[bbstools fx] nuke triggered at (" + x + "," + y + "," + z + ")"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("reveal-light")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            RevealLightEffect.start((float) p.x, (float) p.y, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§b[bbstools fx] reveal-light ON at your position — /bbstools fx reveal-light-off to fade out"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        RevealLightEffect.start(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§b[bbstools fx] reveal-light ON at (" + x + "," + y + "," + z + ") — /bbstools fx reveal-light-off to fade out"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("reveal-light-off")
                        .executes(c -> {
                            RevealLightEffect.stop();
                            c.getSource().sendFeedback(Component.literal("§e[bbstools fx] reveal-light fading out (1s)"));
                            return 1;
                        }))
                    .then(ClientCommands.literal("crystal")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            CrystalEffect.trigger((float) p.x, (float) p.y, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§b[bbstools fx] CRYSTALLIZATION WAVE expanding from your position"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        CrystalEffect.trigger(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§b[bbstools fx] CRYSTALLIZATION WAVE at (" + x + "," + y + "," + z + ")"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("antimatter")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            AntimatterEffect.trigger((float) p.x, (float) p.y, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§9[bbstools fx] ANTIMATTER implosion forming at your position — light bending inward"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        AntimatterEffect.trigger(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§9[bbstools fx] ANTIMATTER implosion at (" + x + "," + y + "," + z + ")"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("glitch")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            GlitchEffect.trigger((float) p.x, (float) p.y, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§5[bbstools fx] REALITY GLITCH opened at your position — corruption expanding outward"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        GlitchEffect.trigger(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§5[bbstools fx] REALITY GLITCH opened at (" + x + "," + y + "," + z + ")"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("meteor")
                        .executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            Vec3 p = mc.player.position();
                            MeteorEffect.trigger((float) p.x, (float) p.y, (float) p.z);
                            c.getSource().sendFeedback(Component.literal("§a[bbstools fx] meteor incoming — impact at your position in 2.5s"));
                            return 1;
                        })
                        .then(ClientCommands.argument("x", FloatArgumentType.floatArg())
                            .then(ClientCommands.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommands.argument("z", FloatArgumentType.floatArg())
                                    .executes(c -> {
                                        float x = FloatArgumentType.getFloat(c, "x");
                                        float y = FloatArgumentType.getFloat(c, "y");
                                        float z = FloatArgumentType.getFloat(c, "z");
                                        MeteorEffect.trigger(x, y, z);
                                        c.getSource().sendFeedback(Component.literal("§a[bbstools fx] meteor incoming — impact at (" + x + "," + y + "," + z + ") in 2.5s"));
                                        return 1;
                                    })))))
                    .then(ClientCommands.literal("demo")
                        .executes(c -> {
                            DemoSequencer.start();
                            c.getSource().sendFeedback(Component.literal("§a[bbstools fx] demo started — meteor → nuke → supernova chain (~24s)"));
                            return 1;
                        }))
                    .then(ClientCommands.literal("help")
                        .executes(c -> {
                            printHelp(c.getSource());
                            return 1;
                        }))
                    .then(ClientCommands.literal("destroying-mc")
                        .executes(c -> {
                            /* One-button "give me the destroying-mc shader look":
                             *  enable pipelines + lock the look preset that
                             *  matches destroying-mc's default visual treatment
                             *  (warm vivid lighting with the soft deferred shadow
                             *  pass + bloom + cinematic tonemap). */
                            BBSFxPipelineRegistrar.setEnabled(true);
                            BBSFxState.vibranceAmount  = 0.30F;
                            BBSFxState.tonemapExposure = 1.10F;
                            BBSFxState.sunRotXDeg      = 20F;
                            BBSFxState.sunRotZDeg      = -75F;
                            c.getSource().sendFeedback(Component.literal("§a[bbstools fx] §6destroying-mc §alook applied — pipelines ON, vibrance 0.30, exposure 1.10, sun 20°/-75°"));
                            if (isIrisActive())
                            {
                                c.getSource().sendFeedback(Component.literal("§e[bbstools fx] WARN: Iris shaderpack active — disable it (or pick \"(internal)\") to see the Veil-based FX look."));
                            }
                            return 1;
                        }))
                    .then(ClientCommands.literal("cancel")
                        .executes(c -> {
                            SupernovaEffect.cancel();
                            NukeEffect.cancel();
                            MeteorEffect.cancel();
                            GlitchEffect.cancel();
                            AntimatterEffect.cancel();
                            CrystalEffect.cancel();
                            RevealLightEffect.cancel();
                            DemoSequencer.cancel();
                            c.getSource().sendFeedback(Component.literal("§e[bbstools fx] cancelled all effects"));
                            return 1;
                        }))
                    .then(ClientCommands.literal("vibrance")
                        .then(ClientCommands.argument("amount", FloatArgumentType.floatArg(0F, 1F))
                            .executes(c -> {
                                BBSFxState.vibranceAmount = FloatArgumentType.getFloat(c, "amount");
                                c.getSource().sendFeedback(Component.literal("§a[bbstools fx] vibrance = " + BBSFxState.vibranceAmount));
                                return 1;
                            })))
                    .then(ClientCommands.literal("exposure")
                        .then(ClientCommands.argument("value", FloatArgumentType.floatArg(0.1F, 5F))
                            .executes(c -> {
                                BBSFxState.tonemapExposure = FloatArgumentType.getFloat(c, "value");
                                c.getSource().sendFeedback(Component.literal("§a[bbstools fx] exposure = " + BBSFxState.tonemapExposure));
                                return 1;
                            })))
                    .then(ClientCommands.literal("sundir")
                        .then(ClientCommands.argument("xDeg", FloatArgumentType.floatArg(-180F, 180F))
                            .then(ClientCommands.argument("zDeg", FloatArgumentType.floatArg(-180F, 180F))
                                .executes(c -> {
                                    BBSFxState.sunRotXDeg = FloatArgumentType.getFloat(c, "xDeg");
                                    BBSFxState.sunRotZDeg = FloatArgumentType.getFloat(c, "zDeg");
                                    c.getSource().sendFeedback(Component.literal("§a[bbstools fx] sundir = X" + BBSFxState.sunRotXDeg + "° Z" + BBSFxState.sunRotZDeg + "° (next frame)"));
                                    return 1;
                                }))))
                    .then(ClientCommands.literal("shake")
                        .then(ClientCommands.argument("intensity", FloatArgumentType.floatArg(0.0F, 3.0F))
                            .executes(c -> {
                                float i = FloatArgumentType.getFloat(c, "intensity");
                                CameraShakeManager.addShake(i);
                                c.getSource().sendFeedback(Component.literal("§a[bbstools fx] shake +" + i + " (decays ~88%/frame)"));
                                return 1;
                            })))
                    .then(ClientCommands.literal("preset")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                            .executes(c -> applyPreset(c.getSource(), StringArgumentType.getString(c, "name")))))
                    .then(ClientCommands.literal("shadow-interval")
                        .then(ClientCommands.argument("frames", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 10))
                            .executes(c -> {
                                int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "frames");
                                BBSFxShadowmapPrePass.shadowInterval = n;
                                String desc = n == 0 ? "OFF (matrices only)" : "every " + n + " frame(s)";
                                c.getSource().sendFeedback(Component.literal("§a[bbstools fx] shadow re-render: " + desc));
                                return 1;
                            })))
                )
            ));
    }

    /** Apply a named color-grade preset. Knobs touched: vibranceAmount
     *  + tonemapExposure. Designed for the cinematic use-case where
     *  the user wants to lock a consistent look per shot without
     *  remembering exact numbers. */
    private static int applyPreset(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, String name)
    {
        switch (name.toLowerCase())
        {
            case "cinematic":
                BBSFxState.vibranceAmount = 0.30F;
                BBSFxState.tonemapExposure = 1.10F;
                src.sendFeedback(Component.literal("§a[bbstools fx] preset cinematic — vibrance 0.30, exposure 1.10"));
                return 1;
            case "neutral":
                BBSFxState.vibranceAmount = 0.00F;
                BBSFxState.tonemapExposure = 1.00F;
                src.sendFeedback(Component.literal("§a[bbstools fx] preset neutral — vibrance 0.00, exposure 1.00"));
                return 1;
            case "moody":
                BBSFxState.vibranceAmount = 0.15F;
                BBSFxState.tonemapExposure = 0.85F;
                src.sendFeedback(Component.literal("§a[bbstools fx] preset moody — vibrance 0.15, exposure 0.85"));
                return 1;
            case "action":
                BBSFxState.vibranceAmount = 0.45F;
                BBSFxState.tonemapExposure = 1.25F;
                src.sendFeedback(Component.literal("§a[bbstools fx] preset action — vibrance 0.45, exposure 1.25"));
                return 1;
            default:
                src.sendFeedback(Component.literal("§c[bbstools fx] unknown preset '" + name + "' — try cinematic / neutral / moody / action"));
                return 0;
        }
    }

    /** Print the full /bbstools fx command tree in-game so the user
     *  doesn't have to dig through source comments to discover what
     *  exists. Grouped by purpose; mirrors the file-top javadoc. */
    private static void printHelp(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src)
    {
        src.sendFeedback(Component.literal("§6=== /bbstools fx commands ==="));
        src.sendFeedback(Component.literal("§6 One-button look:"));
        src.sendFeedback(Component.literal("  §a/bbstools fx destroying-mc §7- enable + apply default look"));
        src.sendFeedback(Component.literal("§e Pipeline:"));
        src.sendFeedback(Component.literal("  §a/bbstools fx enable §7- add post-FX pipelines to Veil"));
        src.sendFeedback(Component.literal("  §a/bbstools fx disable §7- remove them (vanilla look)"));
        src.sendFeedback(Component.literal("  §a/bbstools fx status §7- print current FX state"));
        src.sendFeedback(Component.literal("§e Catastrophes:"));
        src.sendFeedback(Component.literal("  §a/bbstools fx supernova [x y z] §7- expanding bright burst"));
        src.sendFeedback(Component.literal("  §a/bbstools fx nuke [x y z] §7- flash + raymarched mushroom cloud"));
        src.sendFeedback(Component.literal("  §a/bbstools fx meteor [x y z] §7- sky tracer + impact handoff to nuke"));
        src.sendFeedback(Component.literal("  §5/bbstools fx glitch [x y z] §7- REALITY GLITCH corruption tear (original)"));
        src.sendFeedback(Component.literal("  §9/bbstools fx antimatter [x y z] §7- ANTIMATTER implosion (suck→release, original)"));
        src.sendFeedback(Component.literal("  §b/bbstools fx crystal [x y z] §7- CRYSTALLIZATION wave (icy expansion, original)"));
        src.sendFeedback(Component.literal("  §a/bbstools fx demo §7- chain meteor→nuke→supernova (~24s)"));
        src.sendFeedback(Component.literal("  §a/bbstools fx cancel §7- stop all active effects"));
        src.sendFeedback(Component.literal("§e Cinematic look:"));
        src.sendFeedback(Component.literal("  §a/bbstools fx vibrance <0..1> §7- saturation boost amount"));
        src.sendFeedback(Component.literal("  §a/bbstools fx exposure <0.1..5> §7- tonemap exposure"));
        src.sendFeedback(Component.literal("  §a/bbstools fx sundir <xDeg> <zDeg> §7- shadow sun rotation"));
        src.sendFeedback(Component.literal("  §a/bbstools fx preset <name> §7- cinematic|neutral|moody|action"));
        src.sendFeedback(Component.literal("  §a/bbstools fx shake <0..3> §7- manual camera shake beat"));
        src.sendFeedback(Component.literal("§e Performance:"));
        src.sendFeedback(Component.literal("  §a/bbstools fx shadow-interval <N> §7- shadow re-render frames"));
        src.sendFeedback(Component.literal("§7  (0=off, 1=every frame [default], 2-3=cheap, 4+=visible lag)"));
    }

    /** Probe Iris for an active shaderpack. Reflective + try-catch so
     *  modpacks without Iris don't crash here. We can't import Iris
     *  classes directly because Iris isn't a compile-time dependency. */
    private static boolean isIrisActive()
    {
        try
        {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApi.getMethod("getInstance").invoke(null);
            Object isInUse = irisApi.getMethod("isShaderPackInUse").invoke(instance);
            return isInUse instanceof Boolean b && b;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

}
