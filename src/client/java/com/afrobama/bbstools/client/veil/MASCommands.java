// Coded by Afrobama/Claude — /mas command tree. Each Post-FX effect
// is a MASPostFXFeature in a static registry; adding new effects is
// one line. Quasar particle subcommands hang off /mas quasar.
package com.afrobama.bbstools.client.veil;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MASCommands
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/commands");

    /**
     * All toggleable Post-FX effects in one ordered registry. Adding
     * a new shader pipeline is one line here — the command tree
     * generates dynamically.
     */
    private static final Map<String, MASPostFXFeature> EFFECTS = new LinkedHashMap<>();

    /** Curated multi-effect bundles enabled via /mas preset <name>. */
    private static final Map<String, List<String>> PRESETS = new LinkedHashMap<>();

    /** Category tags for /mas help output. Insertion order preserved. */
    private static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static
    {
        /* Veil built-in */
        EFFECTS.put("bloom",     new MASPostFXFeature("veil",     "core/bloom",          "Bloom"));
        /* Our pipelines */
        EFFECTS.put("cinematic", new MASPostFXFeature("bbstools", "cinematic",           "Cinematic (vignette + chromatic aberration)"));
        EFFECTS.put("grade",     new MASPostFXFeature("bbstools", "grade",               "Color Grade (teal-orange)"));
        EFFECTS.put("dof",       new MASPostFXFeature("bbstools", "dof",                 "Depth of Field (radial)"));
        EFFECTS.put("grain",     new MASPostFXFeature("bbstools", "grain",               "Film Grain"));
        EFFECTS.put("flare",     new MASPostFXFeature("bbstools", "flare",               "Lens Flare"));
        EFFECTS.put("vhs",       new MASPostFXFeature("bbstools", "vhs",                 "VHS / Found Footage"));
        /* Curated presets: one command activates a bundle of effects.
         *  Insertion order preserved by LinkedHashMap. */
        PRESETS.put("cinematic",  List.of("bloom", "cinematic", "grade", "dof", "grain"));
        PRESETS.put("horror",     List.of("vhs", "grade", "dof", "grain"));
        PRESETS.put("epic",       List.of("bloom", "grade", "cinematic", "flare", "godrays"));
        PRESETS.put("retro",      List.of("vhs", "grain", "grade"));
        PRESETS.put("dreamy",     List.of("bloom", "dof", "godrays", "grade"));
        PRESETS.put("cyberpunk",  List.of("bloom", "cyberpunk", "godrays", "grain"));
        PRESETS.put("noir",       List.of("bw", "vignette", "grain"));
        PRESETS.put("warm",       List.of("bloom", "sunset", "dof", "flare"));
        PRESETS.put("crt",        List.of("scanlines", "pixelate", "grain", "vignette"));
        PRESETS.put("gameboy",    List.of("pixelate", "posterize", "bw"));

        /* Catastrophe library — visible-spectacle effects */
        EFFECTS.put("supernova", new MASPostFXFeature("bbstools", "supernova",           "Supernova (HDR core + chromatic shockwave)"));
        EFFECTS.put("blackhole", new MASPostFXFeature("bbstools", "blackhole",           "Black Hole (gravitational lensing + accretion disk)"));
        EFFECTS.put("nuke",      new MASPostFXFeature("bbstools", "nuke",                "Nuke (flash + fireball + mushroom cloud, 6s loop)"));
        EFFECTS.put("laser",     new MASPostFXFeature("bbstools", "laser",               "Orbital Laser (charge/fire/decay beam, 6s loop)"));
        EFFECTS.put("planet",    new MASPostFXFeature("bbstools", "planet",              "Planet Destruction (quake + cracks + debris + flash, 7s loop)"));
        EFFECTS.put("meteor",    new MASPostFXFeature("bbstools", "meteor",              "Custom Meteor — diagonal trajectory, orange trail, shockwave + crater glow, 7s"));
        /* Volumetric */
        EFFECTS.put("godrays",   new MASPostFXFeature("bbstools", "godrays",             "God Rays / Sun Shafts (volumetric radial light streaks)"));
        /* Simple standalone looks */
        EFFECTS.put("vignette",  new MASPostFXFeature("bbstools", "vignette",            "Pure Vignette (corner darkening, no chromatic)"));
        EFFECTS.put("invert",    new MASPostFXFeature("bbstools", "invert",              "Color Invert (negative film)"));
        EFFECTS.put("sepia",     new MASPostFXFeature("bbstools", "sepia",               "Sepia (vintage brown monochrome)"));
        EFFECTS.put("bw",        new MASPostFXFeature("bbstools", "bw",                  "Black & White (pure grayscale)"));
        EFFECTS.put("posterize", new MASPostFXFeature("bbstools", "posterize",           "Posterize (cartoon / 6-level color depth)"));
        EFFECTS.put("cyberpunk", new MASPostFXFeature("bbstools", "cyberpunk",           "Cyberpunk (neon cyan shadows + magenta highlights + sat boost)"));
        EFFECTS.put("sunset",    new MASPostFXFeature("bbstools", "sunset",              "Sunset / Golden Hour (warm orange-pink)"));
        EFFECTS.put("cold",      new MASPostFXFeature("bbstools", "cold",                "Cold Night (cool blue + crushed shadows + desat)"));
        EFFECTS.put("pixelate",  new MASPostFXFeature("bbstools", "pixelate",            "Pixelate (256-wide retro chunky pixels)"));
        EFFECTS.put("scanlines", new MASPostFXFeature("bbstools", "scanlines",           "CRT Scanlines (alternating row darkening)"));
        EFFECTS.put("titleframe",new MASPostFXFeature("bbstools", "titleframe",          "Cinematic Letterbox (standard 12% bars)"));
        EFFECTS.put("widescreen",new MASPostFXFeature("bbstools", "widescreen",          "Anamorphic Widescreen (20% bars, 2.35:1)"));
        EFFECTS.put("titlefade", new MASPostFXFeature("bbstools", "titlefade",           "Fade-to-Black (use /mas intensity titlefade 0..1)"));
        EFFECTS.put("whiteout",  new MASPostFXFeature("bbstools", "whiteout",            "Fade-to-WHITE screen tint (use /mas intensity whiteout 0..1)"));
        EFFECTS.put("lightsource", new MASPostFXFeature("bbstools", "lightsource",       "Placeable blinding light source — spawns at look-target, blinds player as they approach. /mas intensity lightsource 0..1 dims it. Adjust radii in MASLightSourceEvent if needed."));
        EFFECTS.put("post",      new MASPostFXFeature("bbstools", "post_destroying",     "destroying-mc Post (DOF + glitch + black-screen). Use /mas intensity post 0..1 for glitch strength."));
        EFFECTS.put("masbloom",  new MASPostFXFeature("bbstools", "masbloom",            "Simple Bloom (single-pass, single-shader). Use /mas intensity masbloom 0..3 for glow strength."));
        EFFECTS.put("bloom_dm",  new MASPostFXFeature("bbstools", "bloom_dm",            "Real destroying-mc Bloom (mipmap-tree multi-pass HDR). Vibrant cinematic look, bright pixels glow."));
        EFFECTS.put("vibrant",   new MASPostFXFeature("bbstools", "vibrant_world",       "Vibrant World — round bright sun (sky-only) + saturation boost. Pair with /mas bloom_dm for the sun glow."));
        EFFECTS.put("crosshatch",new MASPostFXFeature("bbstools", "crosshatch",          "Crosshatch sketch (luma-driven ink lines, intensity blends with scene)"));
        EFFECTS.put("edge",      new MASPostFXFeature("bbstools", "edge",                "Edge Detection (Sobel comic-book ink outline)"));
        EFFECTS.put("halftone",  new MASPostFXFeature("bbstools", "halftone",            "Halftone (newspaper / Lichtenstein dot pattern)"));
        EFFECTS.put("tiltshift", new MASPostFXFeature("bbstools", "tiltshift",           "Tilt-Shift (fake miniature: sharp middle, blurred top+bottom)"));
        EFFECTS.put("oilpaint",  new MASPostFXFeature("bbstools", "oilpaint",            "Oil Paint (Kuwahara painterly brush-stroke flatten)"));
        EFFECTS.put("fisheye",   new MASPostFXFeature("bbstools", "fisheye",             "Fisheye (GoPro barrel-distort, intensity = bulge strength)"));
        EFFECTS.put("glitch",    new MASPostFXFeature("bbstools", "glitch",              "Glitch (datamosh band-shift + RGB aberration + noise)"));

        /* Categorisation for /mas help readout */
        CATEGORIES.put("Looks",         List.of("bloom", "cinematic", "grade", "dof", "tiltshift", "grain", "flare", "vhs", "vignette"));
        CATEGORIES.put("Color Tints",   List.of("invert", "sepia", "bw", "posterize", "cyberpunk", "sunset", "cold"));
        CATEGORIES.put("Retro",         List.of("pixelate", "scanlines", "crosshatch", "edge", "halftone", "oilpaint"));
        CATEGORIES.put("Lens & Glitch", List.of("fisheye", "glitch"));
        CATEGORIES.put("Cinematic Bars",List.of("titleframe", "widescreen", "titlefade", "whiteout"));
        CATEGORIES.put("Volumetric",    List.of("godrays"));
        CATEGORIES.put("Catastrophes",  List.of("supernova", "blackhole", "nuke", "laser", "planet"));
    }

    private MASCommands() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> registerTree(dispatcher));
        LOG.info("Registered /mas command tree with {} Post-FX effects + Quasar subcommands", EFFECTS.size());
    }

    private static void registerTree(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> root =
            ClientCommands.literal("mas");

        /* One subcommand per Post-FX effect with on/off/toggle. */
        for (Map.Entry<String, MASPostFXFeature> entry : EFFECTS.entrySet())
        {
            String name = entry.getKey();
            MASPostFXFeature fx = entry.getValue();

            root.then(ClientCommands.literal(name)
                .executes(ctx -> fxStatus(ctx, fx))
                .then(ClientCommands.literal("on")
                    .executes(ctx -> fxOn(ctx, fx)))
                .then(ClientCommands.literal("off")
                    .executes(ctx -> fxOff(ctx, fx)))
                .then(ClientCommands.literal("toggle")
                    .executes(ctx -> fxToggle(ctx, fx))));
        }

        /* /mas help — categorised overview of everything available */
        root.then(ClientCommands.literal("help")
            .executes(ctx ->
            {
                msg(ctx, "§6===== MAS / Veil Post-FX =====");
                int total = 0;
                for (Map.Entry<String, List<String>> cat : CATEGORIES.entrySet())
                {
                    msg(ctx, "§e" + cat.getKey() + ":");
                    StringBuilder line = new StringBuilder("  §7");
                    for (String name : cat.getValue())
                    {
                        boolean on = EFFECTS.containsKey(name) && EFFECTS.get(name).isActive();
                        line.append(on ? "§a" : "§f").append(name).append("§7 · ");
                        total++;
                    }
                    msg(ctx, line.toString());
                }
                msg(ctx, "§eParticles: §7/mas quasar test|smoke");
                msg(ctx, "§ePresets:   §7/mas preset " + String.join(" | ", PRESETS.keySet()) + " | reset");
                msg(ctx, "§eIntensity: §7/mas intensity <effect> <0..3>");
                msg(ctx, "§eShader:    §7/mas shader install <name> | template | list | docs | claude");
                msg(ctx, "§eUtilities: §7/mas list | /mas all off");
                msg(ctx, "§6===== " + total + " effects + " + PRESETS.size() + " presets =====");
                return 1;
            }));

        /* /mas list — show every effect + its current state */
        root.then(ClientCommands.literal("list")
            .executes(ctx ->
            {
                msg(ctx, "§6[MAS] Available Post-FX effects:");
                for (Map.Entry<String, MASPostFXFeature> entry : EFFECTS.entrySet())
                {
                    boolean on = entry.getValue().isActive();
                    msg(ctx, "  §" + (on ? "a" : "7") + (on ? "[ON]  " : "[off] ")
                            + "§f/mas " + entry.getKey() + " §7— " + entry.getValue().displayName());
                }
                return 1;
            }));

        /* /mas all off — kill every effect at once. Useful when stacking gets noisy. */
        root.then(ClientCommands.literal("all")
            .then(ClientCommands.literal("off")
                .executes(ctx ->
                {
                    int n = 0;
                    for (MASPostFXFeature fx : EFFECTS.values()) { if (fx.isActive()) { fx.disable(); n++; } }
                    msg(ctx, "§7[MAS] Disabled " + n + " active Post-FX effects.");
                    return 1;
                })));

        /* /mas preset <name> — apply a curated bundle. Default behaviour:
         *  disables every currently active effect first, then enables the
         *  preset's set. /mas preset list shows available presets. */
        var presetNode = ClientCommands.literal("preset");
        presetNode.executes(ctx ->
        {
            msg(ctx, "§6[MAS] Available presets:");
            for (Map.Entry<String, List<String>> e : PRESETS.entrySet())
            {
                msg(ctx, "  §f/mas preset " + e.getKey() + " §7— " + String.join(", ", e.getValue()));
            }
            msg(ctx, "  §f/mas preset reset §7— turns off every effect");
            return 1;
        });
        for (Map.Entry<String, List<String>> entry : PRESETS.entrySet())
        {
            String presetName = entry.getKey();
            List<String> presetEffects = entry.getValue();
            presetNode.then(ClientCommands.literal(presetName)
                .executes(ctx -> applyPreset(ctx, presetName, presetEffects)));
        }
        presetNode.then(ClientCommands.literal("reset")
            .executes(ctx ->
            {
                int n = 0;
                for (MASPostFXFeature fx : EFFECTS.values()) { if (fx.isActive()) { fx.disable(); n++; } }
                msg(ctx, "§7[MAS] Preset reset — disabled " + n + " effects.");
                return 1;
            }));

        root.then(presetNode);

        /* /mas intensity <effect> <value> — set intensity multiplier
         *  for shaders that read uniform MAS_Intensity. Currently:
         *  all 5 catastrophes (supernova, blackhole, nuke, laser,
         *  planet). Values 0..3 (0=off without removing pipeline,
         *  1=normal, 3=over-driven). */
        root.then(ClientCommands.literal("intensity")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                .<FabricClientCommandSource, String>argument(
                    "effect", com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<FabricClientCommandSource, Float>argument(
                        "value", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 3.0F))
                    .executes(ctx ->
                    {
                        String effectName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "effect");
                        float value = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "value");
                        MASPostFXFeature fx = EFFECTS.get(effectName);
                        if (fx == null)
                        {
                            msg(ctx, "§c[MAS intensity] unknown effect: " + effectName);
                            return 0;
                        }
                        MASShaderUniformPusher.setIntensity(fx.id(), value);
                        msg(ctx, "§a[MAS intensity] §f" + effectName + " = " + value
                                + " §7(uniform MAS_Intensity pushed; takes effect on next render)");
                        return 1;
                    }))));

        /* /mas shader — user-installed shader workflow.
         *   /mas shader list           → show files in config/bbstools/shaders/
         *   /mas shader install <name> → materialise pack at resourcepacks/bbstools-user-shaders/
         *   /mas shader template       → write a starter template.fsh
         *   /mas shader docs           → in-chat tutorial */
        root.then(ClientCommands.literal("shader")
            .then(ClientCommands.literal("list").executes(MASCommands::shaderList))
            .then(ClientCommands.literal("template").executes(MASCommands::shaderTemplate))
            .then(ClientCommands.literal("docs").executes(MASCommands::shaderDocs))
            .then(ClientCommands.literal("claude").executes(MASCommands::shaderClaude))
            .then(ClientCommands.literal("install")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<FabricClientCommandSource, String>argument(
                        "name", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx ->
                    {
                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                        String result = MASUserShaderInstaller.install(name);
                        msg(ctx, "§6[MAS shader] §f" + result);
                        return 1;
                    }))));

        /* /mas quasar test|smoke — spawn Quasar emitters at player */
        root.then(ClientCommands.literal("quasar")
            .then(ClientCommands.literal("test")
                .executes(MASCommands::quasarTest))
            .then(ClientCommands.literal("smoke")
                .executes(MASCommands::quasarTest))
            .then(ClientCommands.literal("status")
                .executes(MASCommands::quasarStatus)));

        /* /mas volcano — world-space eruption event. Spawns a Quasar
         * particle emitter 6 blocks ahead of the player at ground level.
         * User can walk around it freely; it's a real 3D effect, not
         * a screen overlay. Catastrophe-class effect — same family as
         * supernova/blackhole etc but world-space (those are still
         * screen-space pending world-space rebuild). */
        root.then(ClientCommands.literal("volcano")
            .executes(MASCommands::volcanoSpawn)
            .then(ClientCommands.literal("spawn")
                .executes(MASCommands::volcanoSpawn)));

        dispatcher.register(root);
    }

    /* === Post-FX command handlers === */

    private static int fxStatus(CommandContext<FabricClientCommandSource> ctx, MASPostFXFeature fx)
    {
        boolean a = fx.isActive();
        msg(ctx, (a ? "§a[" : "§7[") + fx.displayName() + "] " + (a ? "active" : "inactive")
                + " §7(/mas " + commandNameOf(fx) + " " + (a ? "off" : "on") + ")");
        return 1;
    }

    private static int fxOn(CommandContext<FabricClientCommandSource> ctx, MASPostFXFeature fx)
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            msg(ctx, "§c[MAS] Veil not loaded — feature unavailable.");
            return 0;
        }
        fx.enable();

        /* Event-driven catastrophes need state initialised when their
         * pipeline is enabled. Nuke = trigger explosion at player look
         * target so the ray-march has a valid NUKE_POS + smokeRiseTimer. */
        if ("nuke".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            net.minecraft.world.phys.Vec3 target = MASNukeEvent.triggerAtPlayerTarget();
            if (target != null)
            {
                msg(ctx, "§e[MAS nuke] §fDetonation at (" + (int)target.x + ", "
                        + (int)target.y + ", " + (int)target.z
                        + ") — mushroom cloud expands over 5s, auto-disables after 30s.");
            }
        }
        if ("blackhole".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            net.minecraft.world.phys.Vec3 target = MASBlackHoleEvent.triggerAtPlayerTarget();
            if (target != null)
            {
                msg(ctx, "§e[MAS blackhole] §fSpawned at (" + (int)target.x + ", "
                        + (int)target.y + ", " + (int)target.z
                        + ") — gravitational lensing + accretion disk, auto-disables after 60s.");
            }
        }
        if ("planet".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASPlanetEvent.trigger();
            msg(ctx, "§e[MAS planet] §fPlanet descent started — 15s fall to impact + 8s hold. Planet appears 20k blocks east of player.");
        }
        if ("supernova".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASSupernovaEvent.trigger();
            msg(ctx, "§e[MAS supernova] §fSupernova started — 3s sky fade + 5s explosion ray-march + 1s flash. Auto-disables after 10s.");
        }
        if ("laser".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            net.minecraft.world.phys.Vec3 target = MASLaserEvent.triggerAtPlayerTarget();
            if (target != null)
            {
                msg(ctx, "§e[MAS laser] §fOrbital laser firing at XZ (" + (int)target.x + ", " + (int)target.z
                        + ") — 2s charge + 6s cracks spreading + flash on impact. Auto-disables after 12s.");
            }
        }
        if ("meteor".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            net.minecraft.world.phys.Vec3 target = MASMeteorEvent.triggerAtPlayerTarget();
            if (target != null)
            {
                msg(ctx, "§e[MAS meteor] §fMeteor incoming — diagonal trajectory to ("
                        + (int)target.x + ", " + (int)target.y + ", " + (int)target.z
                        + "). 4s fall + 3s aftermath. Stand back!");
            }
        }
        if ("lightsource".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            net.minecraft.world.phys.Vec3 target = MASLightSourceEvent.triggerAtPlayerTarget();
            if (target != null)
            {
                msg(ctx, "§e[MAS lightsource] §fBlinding light placed at ("
                        + (int)target.x + ", " + (int)target.y + ", " + (int)target.z
                        + "). Walk closer to get blinded — outer 14b / inner 4b / intensity 1.0.");
                msg(ctx, "§7Tune with /mas intensity lightsource 0..1");
            }
        }

        msg(ctx, "§a[" + fx.displayName() + "] §fenabled");
        irisHint(ctx, fx);
        return 1;
    }

    private static int fxOff(CommandContext<FabricClientCommandSource> ctx, MASPostFXFeature fx)
    {
        fx.disable();

        /* Tear down event state alongside pipeline. */
        if ("nuke".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASNukeEvent.deactivate();
        }
        if ("blackhole".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASBlackHoleEvent.deactivate();
        }
        if ("planet".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASPlanetEvent.deactivate();
        }
        if ("supernova".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASSupernovaEvent.deactivate();
        }
        if ("laser".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASLaserEvent.deactivate();
        }
        if ("meteor".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASMeteorEvent.deactivate();
        }
        if ("lightsource".equals(fx.id().getPath()) && "bbstools".equals(fx.id().getNamespace()))
        {
            MASLightSourceEvent.deactivate();
        }

        msg(ctx, "§7[" + fx.displayName() + "] disabled");
        return 1;
    }

    private static int fxToggle(CommandContext<FabricClientCommandSource> ctx, MASPostFXFeature fx)
    {
        boolean now = fx.toggle();
        msg(ctx, (now ? "§a[" : "§7[") + fx.displayName() + "] toggled " + (now ? "ON" : "OFF"));
        irisHint(ctx, fx);
        return 1;
    }

    private static void irisHint(CommandContext<FabricClientCommandSource> ctx, MASPostFXFeature fx)
    {
        /* Veil auto-disables its own bloom + dynamic-buffers when Iris is
         *  active. Our custom pipelines (cinematic, grade, dof, …) run
         *  regardless, but the Veil core/bloom one doesn't — surface that
         *  for clarity. */
        if (fx.id().getNamespace().equals("veil") && VeilFeatureGate.isIrisLoaded())
        {
            msg(ctx, "§e[MAS] Iris is loaded — Veil's core bloom auto-disables in deference to Iris's shader pack.");
        }
    }

    private static String commandNameOf(MASPostFXFeature fx)
    {
        for (Map.Entry<String, MASPostFXFeature> e : EFFECTS.entrySet())
        {
            if (e.getValue() == fx) return e.getKey();
        }
        return "?";
    }

    /* === Quasar command handlers === */

    private static int quasarTest(CommandContext<FabricClientCommandSource> ctx)
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            msg(ctx, "§c[MAS quasar] Veil not loaded — feature unavailable.");
            return 0;
        }
        foundry.veil.api.quasar.particle.ParticleEmitter e = MASQuasarFeature.spawnSmokeAtPlayer();
        if (e == null)
        {
            msg(ctx, "§c[MAS quasar] Spawn failed — check bbstools-console.log for 'mas/quasar' lines.");
            return 0;
        }
        msg(ctx, "§a[MAS quasar] §fSpawned smoke_test emitter at your position. Active emitters: "
                + MASQuasarFeature.getEmitterCount() + ", particles: " + MASQuasarFeature.getParticleCount() + ".");
        return 1;
    }

    private static int quasarStatus(CommandContext<FabricClientCommandSource> ctx)
    {
        msg(ctx, "§6[MAS quasar] active emitters: " + MASQuasarFeature.getEmitterCount()
                + ", particles in flight: " + MASQuasarFeature.getParticleCount());
        return 1;
    }

    private static int volcanoSpawn(CommandContext<FabricClientCommandSource> ctx)
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            msg(ctx, "§c[MAS volcano] Veil not loaded — feature unavailable.");
            return 0;
        }
        foundry.veil.api.quasar.particle.ParticleEmitter e = MASQuasarFeature.spawnVolcanoAtTarget();
        if (e == null)
        {
            msg(ctx, "§c[MAS volcano] Spawn failed — check bbstools-console.log 'mas/quasar' lines.");
            return 0;
        }
        msg(ctx, "§a[MAS volcano] §fEruption spawned 6 blocks ahead. Walk around to view from any angle.");
        msg(ctx, "§7  Active emitters: " + MASQuasarFeature.getEmitterCount()
                + ", particles in flight: " + MASQuasarFeature.getParticleCount());
        return 1;
    }

    /* === User-shader command handlers === */

    private static int shaderList(CommandContext<FabricClientCommandSource> ctx)
    {
        java.util.List<String> shaders = MASUserShaderInstaller.listUserShaders();
        if (shaders.isEmpty())
        {
            msg(ctx, "§7[MAS shader] No .fsh files in " + MASUserShaderInstaller.getUserShadersDir());
            msg(ctx, "§7[MAS shader] Run §f/mas shader template §7to write a starter.");
            return 1;
        }
        msg(ctx, "§6[MAS shader] Found " + shaders.size() + " user shaders:");
        for (String name : shaders)
        {
            msg(ctx, "  §f" + name + " §7→ /mas shader install " + name);
        }
        return 1;
    }

    private static int shaderTemplate(CommandContext<FabricClientCommandSource> ctx)
    {
        msg(ctx, "§6[MAS shader] §f" + MASUserShaderInstaller.initTemplate());
        return 1;
    }

    private static int shaderClaude(CommandContext<FabricClientCommandSource> ctx)
    {
        msg(ctx, "§6[MAS shader] §fClaude→Shader bridge workflow:");
        msg(ctx, "§71. Open any Claude chat (claude.ai or Claude Code)");
        msg(ctx, "§72. Paste the system prompt from §fCLAUDE_SHADER_PROMPTS.md");
        msg(ctx, "§7   (in your BBSTools-Fabric folder)");
        msg(ctx, "§73. Describe your effect: §fe.g. 'wavy heat shimmer at bottom of screen'");
        msg(ctx, "§74. Claude returns a .fsh — save it under");
        msg(ctx, "§7   §fconfig/bbstools/shaders/<your-name>.fsh");
        msg(ctx, "§75. §f/mas shader install <your-name>");
        msg(ctx, "§76. Enable §fBBSTools User Shaders §7resource pack → F3+T");
        msg(ctx, "§77. §f/mas user_<your-name> on");
        msg(ctx, "§7CLAUDE_SHADER_PROMPTS.md has copy-pasteable system prompt + examples.");
        return 1;
    }

    private static int shaderDocs(CommandContext<FabricClientCommandSource> ctx)
    {
        msg(ctx, "§6[MAS shader] §fCustom shader workflow:");
        msg(ctx, "§71. §f/mas shader template §7— writes a starter template.fsh");
        msg(ctx, "§72. Edit/copy it at §fconfig/bbstools/shaders/<name>.fsh");
        msg(ctx, "§73. §f/mas shader install <name> §7— generates a resource pack");
        msg(ctx, "§74. Open Resource Pack menu, enable 'BBSTools User Shaders'");
        msg(ctx, "§75. Press §fF3+T §7to reload, then §f/mas user_<name> on");
        msg(ctx, "§7Uniforms available: §fDiffuseSampler0, MAS_GameTime, MAS_Intensity");
        return 1;
    }

    private static int applyPreset(CommandContext<FabricClientCommandSource> ctx, String presetName, List<String> effectNames)
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            msg(ctx, "§c[MAS] Veil not loaded — preset unavailable.");
            return 0;
        }
        /* Turn EVERY effect off first so the preset's bundle is the
         *  only thing active. Avoids stacking residue from previous
         *  presets. */
        for (MASPostFXFeature fx : EFFECTS.values())
        {
            if (fx.isActive()) fx.disable();
        }
        int enabled = 0;
        for (String name : effectNames)
        {
            MASPostFXFeature fx = EFFECTS.get(name);
            if (fx == null)
            {
                msg(ctx, "§e[MAS preset " + presetName + "] missing effect '" + name + "' — skipped.");
                continue;
            }
            fx.enable();
            enabled++;
        }
        msg(ctx, "§a[MAS preset " + presetName + "] §fapplied — " + enabled + " effects active: §7"
                + String.join(", ", effectNames));
        return 1;
    }

    private static void msg(CommandContext<FabricClientCommandSource> ctx, String text)
    {
        ctx.getSource().sendFeedback(Component.literal(text));
    }

}
