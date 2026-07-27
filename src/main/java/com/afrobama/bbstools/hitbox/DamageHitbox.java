package com.afrobama.bbstools.hitbox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Server-side damage tick handler + /hitbox commands.
 *
 * Two sources of hitboxes:
 * <ol>
 *   <li>Manually created via /hitbox create — persistent, can be moved,
 *       attached to a named entity, scaled, toggled, etc.</li>
 *   <li>Film-driven via {@link DamageHitboxClip} — pushed onto
 *       {@link FilmHitboxBridge#activeBoxes} on each clip apply tick,
 *       drained and processed once per server tick.</li>
 * </ol>
 *
 * Each hitbox has a per-entity damage cooldown so a single mob isn't melted
 * 20×/sec — default 10 ticks (0.5s) between hits per entity.
 *
 * Ported from NeoForge BBSTools.
 */
public final class DamageHitbox
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/damage-hitbox");

    private static final Map<String, HitboxData> HITBOXES = new ConcurrentHashMap<>();
    private static final Map<String, Map<UUID, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final int DAMAGE_COOLDOWN_TICKS = 10;
    private static final int PARTICLE_INTERVAL = 5;

    private static volatile boolean showDebug = false;

    private DamageHitbox()
    {
    }

    static class HitboxData
    {
        String name;
        double x;
        double y;
        double z;
        double sizeX;
        double sizeY;
        double sizeZ;
        float damage;
        String attachedEntity;
        double offsetX;
        double offsetY;
        double offsetZ;
        boolean active = true;

        Box getBox(Vec3d entityPos)
        {
            double cx;
            double cy;
            double cz;

            if (this.attachedEntity != null && entityPos != null)
            {
                cx = entityPos.x + this.offsetX;
                cy = entityPos.y + this.offsetY;
                cz = entityPos.z + this.offsetZ;
            }
            else
            {
                cx = this.x;
                cy = this.y;
                cz = this.z;
            }

            return new Box(cx - this.sizeX, cy, cz - this.sizeZ,
                cx + this.sizeX, cy + this.sizeY * 2D, cz + this.sizeZ);
        }
    }

    public static void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(DamageHitbox::onServerTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
        {
            dispatcher.register(buildTree());
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildTree()
    {
        return CommandManager.literal("hitbox")
            .requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("damage", FloatArgumentType.floatArg(0F))
                        .then(CommandManager.argument("sizeX", DoubleArgumentType.doubleArg(0.1D, 50D))
                            .then(CommandManager.argument("sizeY", DoubleArgumentType.doubleArg(0.1D, 50D))
                                .then(CommandManager.argument("sizeZ", DoubleArgumentType.doubleArg(0.1D, 50D))
                                    .executes(ctx -> createHitbox(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        FloatArgumentType.getFloat(ctx, "damage"),
                                        DoubleArgumentType.getDouble(ctx, "sizeX"),
                                        DoubleArgumentType.getDouble(ctx, "sizeY"),
                                        DoubleArgumentType.getDouble(ctx, "sizeZ")))))))))
            .then(CommandManager.literal("move")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                            .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                .executes(ctx -> moveHitbox(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    DoubleArgumentType.getDouble(ctx, "x"),
                                    DoubleArgumentType.getDouble(ctx, "y"),
                                    DoubleArgumentType.getDouble(ctx, "z"))))))))
            .then(CommandManager.literal("scale")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("sizeX", DoubleArgumentType.doubleArg(0.1D, 50D))
                        .then(CommandManager.argument("sizeY", DoubleArgumentType.doubleArg(0.1D, 50D))
                            .then(CommandManager.argument("sizeZ", DoubleArgumentType.doubleArg(0.1D, 50D))
                                .executes(ctx -> scaleHitbox(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    DoubleArgumentType.getDouble(ctx, "sizeX"),
                                    DoubleArgumentType.getDouble(ctx, "sizeY"),
                                    DoubleArgumentType.getDouble(ctx, "sizeZ"))))))))
            .then(CommandManager.literal("damage")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0F))
                        .executes(ctx -> setDamage(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"),
                            FloatArgumentType.getFloat(ctx, "amount"))))))
            .then(CommandManager.literal("attach")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("entity", StringArgumentType.greedyString())
                        .executes(ctx -> attachHitbox(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "entity"))))))
            .then(CommandManager.literal("detach")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> detachHitbox(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))
            .then(CommandManager.literal("offset")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                            .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                .executes(ctx -> offsetHitbox(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    DoubleArgumentType.getDouble(ctx, "x"),
                                    DoubleArgumentType.getDouble(ctx, "y"),
                                    DoubleArgumentType.getDouble(ctx, "z"))))))))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> toggleHitbox(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> removeHitbox(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))
            .then(CommandManager.literal("clear")
                .executes(ctx -> clearHitboxes(ctx.getSource())))
            .then(CommandManager.literal("list")
                .executes(ctx -> listHitboxes(ctx.getSource())))
            .then(CommandManager.literal("show")
                .executes(ctx ->
                {
                    showDebug = true;
                    FilmHitboxBridge.showWireframe = true;
                    ctx.getSource().sendFeedback(() -> Text.literal("§aHitbox debug particles + wireframe ON"), false);

                    return Command.SINGLE_SUCCESS;
                }))
            .then(CommandManager.literal("hide")
                .executes(ctx ->
                {
                    showDebug = false;
                    FilmHitboxBridge.showWireframe = false;
                    ctx.getSource().sendFeedback(() -> Text.literal("§cHitbox debug particles + wireframe OFF"), false);

                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server)
    {
        long tick = server.getTicks();

        /* Drain film-driven boxes pushed by DamageHitboxClip during the last
           render cycle. Snapshot so the wireframe renderer can read a stable
           list while applyClip continues writing to activeBoxes. */
        List<double[]> filmBoxes = new ArrayList<>(FilmHitboxBridge.activeBoxes);
        FilmHitboxBridge.lastFrameBoxes = filmBoxes;
        FilmHitboxBridge.activeBoxes.clear();

        if (!filmBoxes.isEmpty())
        {
            for (ServerWorld world : server.getWorlds())
            {
                for (double[] fb : filmBoxes)
                {
                    double fx = fb[0];
                    double fy = fb[1];
                    double fz = fb[2];
                    double fsx = fb[3];
                    double fsy = fb[4];
                    double fsz = fb[5];
                    float fdmg = (float) fb[6];
                    int cooldownTicks = fb.length > 7 ? (int) fb[7] : DAMAGE_COOLDOWN_TICKS;

                    if (cooldownTicks < 1)
                    {
                        cooldownTicks = 1;
                    }

                    Box box = new Box(fx - fsx, fy, fz - fsz, fx + fsx, fy + fsy * 2D, fz + fsz);

                    if (fdmg > 0F)
                    {
                        Map<UUID, Long> cooldowns = COOLDOWNS.computeIfAbsent("__film__", k -> new ConcurrentHashMap<>());

                        for (Entity e : world.getOtherEntities(null, box,
                            ent -> ent instanceof LivingEntity && !(ent instanceof ServerPlayerEntity)))
                        {
                            LivingEntity living = (LivingEntity) e;

                            if (!living.isAlive())
                            {
                                continue;
                            }

                            UUID eid = living.getUuid();
                            Long lastHit = cooldowns.get(eid);

                            if (lastHit != null && (tick - lastHit) < cooldownTicks)
                            {
                                continue;
                            }

                            living.damage(world.getDamageSources().generic(), fdmg);
                            cooldowns.put(eid, tick);
                        }
                    }
                }
            }
        }

        if (HITBOXES.isEmpty())
        {
            return;
        }

        for (ServerWorld world : server.getWorlds())
        {
            for (HitboxData hb : HITBOXES.values())
            {
                if (!hb.active)
                {
                    continue;
                }

                Vec3d entityPos = null;

                if (hb.attachedEntity != null)
                {
                    entityPos = findEntityPosition(world, hb.attachedEntity);

                    if (entityPos == null)
                    {
                        continue;
                    }
                }

                Box box = hb.getBox(entityPos);

                Map<UUID, Long> cooldowns = COOLDOWNS.computeIfAbsent(hb.name, k -> new ConcurrentHashMap<>());

                for (Entity e : world.getOtherEntities(null, box,
                    ent -> ent instanceof LivingEntity && !(ent instanceof ServerPlayerEntity)))
                {
                    LivingEntity living = (LivingEntity) e;

                    if (!living.isAlive() || hb.damage <= 0F)
                    {
                        continue;
                    }

                    UUID eid = living.getUuid();
                    Long lastHit = cooldowns.get(eid);

                    if (lastHit != null && (tick - lastHit) < DAMAGE_COOLDOWN_TICKS)
                    {
                        continue;
                    }

                    living.damage(world.getDamageSources().generic(), hb.damage);
                    cooldowns.put(eid, tick);
                }

                if (tick % 200L == 0L)
                {
                    cooldowns.entrySet().removeIf(entry -> (tick - entry.getValue()) > 600L);
                }

                if (showDebug && tick % PARTICLE_INTERVAL == 0L)
                {
                    spawnBoxParticles(world, box);
                }
            }
        }
    }

    private static Vec3d findEntityPosition(ServerWorld world, String name)
    {
        for (Entity e : world.iterateEntities())
        {
            if (e == null)
            {
                continue;
            }

            if (e.hasCustomName() && name.equals(e.getCustomName().getString()))
            {
                return e.getPos();
            }

            /* BBS ActorEntity replay nameTag (private field — reflection access). */
            try
            {
                Class<?> actorClass = actorKlasse();

                if (actorClass != null && actorClass.isInstance(e))
                {
                    Field replayField = actorClass.getDeclaredField("replay");
                    replayField.setAccessible(true);
                    Object replay = replayField.get(e);

                    if (replay != null)
                    {
                        Field nameTagField = replay.getClass().getField("nameTag");
                        Object nameTagValue = nameTagField.get(replay);

                        if (nameTagValue != null)
                        {
                            Method getValue = nameTagValue.getClass().getMethod("get");
                            Object result = getValue.invoke(nameTagValue);

                            if (result instanceof String s && name.equals(s))
                            {
                                return e.getPos();
                            }
                        }
                    }
                }
            }
            /* Der ClassNotFoundException-zweig ist weg: die suche liegt jetzt in
             * actorKlasse() und wird dort einmal behandelt (siehe unten). */
            catch (Exception ex)
            {
                /* Reflection errors are not interesting at this volume. */
            }
        }

        return null;
    }

    private static void spawnBoxParticles(ServerWorld world, Box box)
    {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        int steps = 4;

        for (int i = 0; i <= steps; i++)
        {
            double t = (double) i / steps;
            spawnParticle(world, minX + t * (maxX - minX), minY, minZ);
            spawnParticle(world, minX + t * (maxX - minX), minY, maxZ);
            spawnParticle(world, minX, minY, minZ + t * (maxZ - minZ));
            spawnParticle(world, maxX, minY, minZ + t * (maxZ - minZ));
            spawnParticle(world, minX + t * (maxX - minX), maxY, minZ);
            spawnParticle(world, minX + t * (maxX - minX), maxY, maxZ);
            spawnParticle(world, minX, maxY, minZ + t * (maxZ - minZ));
            spawnParticle(world, maxX, maxY, minZ + t * (maxZ - minZ));
            spawnParticle(world, minX, minY + t * (maxY - minY), minZ);
            spawnParticle(world, maxX, minY + t * (maxY - minY), minZ);
            spawnParticle(world, minX, minY + t * (maxY - minY), maxZ);
            spawnParticle(world, maxX, minY + t * (maxY - minY), maxZ);
        }
    }

    private static void spawnParticle(ServerWorld world, double x, double y, double z)
    {
        world.spawnParticles(ParticleTypes.DUST_PLUME, x, y, z, 1, 0D, 0D, 0D, 0D);
    }

    private static int createHitbox(ServerCommandSource source, String name, float damage,
        double sizeX, double sizeY, double sizeZ)
    {
        if (HITBOXES.containsKey(name))
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' already exists"), false);

            return 0;
        }

        Vec3d pos = source.getPosition();
        HitboxData hb = new HitboxData();
        hb.name = name;
        hb.x = pos.x;
        hb.y = pos.y;
        hb.z = pos.z;
        hb.sizeX = sizeX;
        hb.sizeY = sizeY;
        hb.sizeZ = sizeZ;
        hb.damage = damage;
        HITBOXES.put(name, hb);

        source.sendFeedback(() -> Text.literal(String.format(
            "§aHitbox '%s' created (%.1f DMG, %.1fx%.1fx%.1f)", name, damage, sizeX, sizeY, sizeZ)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int moveHitbox(ServerCommandSource source, String name, double x, double y, double z)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.x = x;
        hb.y = y;
        hb.z = z;
        source.sendFeedback(() -> Text.literal(String.format("§aHitbox '%s' moved to %.2f %.2f %.2f", name, x, y, z)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int scaleHitbox(ServerCommandSource source, String name, double sizeX, double sizeY, double sizeZ)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.sizeX = sizeX;
        hb.sizeY = sizeY;
        hb.sizeZ = sizeZ;
        source.sendFeedback(() -> Text.literal(String.format("§aHitbox '%s' size: %.1fx%.1fx%.1f", name, sizeX, sizeY, sizeZ)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int setDamage(ServerCommandSource source, String name, float damage)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.damage = damage;
        source.sendFeedback(() -> Text.literal(String.format("§aHitbox '%s' damage: %.1f", name, damage)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int attachHitbox(ServerCommandSource source, String name, String entityName)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        boolean found = false;

        for (ServerWorld world : source.getServer().getWorlds())
        {
            if (findEntityPosition(world, entityName) != null)
            {
                found = true;
                break;
            }
        }

        if (!found)
        {
            source.sendFeedback(() -> Text.literal("§cEntity '" + entityName + "' not found"), false);

            return 0;
        }

        hb.attachedEntity = entityName;
        source.sendFeedback(() -> Text.literal(String.format("§aHitbox '%s' attached to '%s'", name, entityName)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int detachHitbox(ServerCommandSource source, String name)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        for (ServerWorld world : source.getServer().getWorlds())
        {
            if (hb.attachedEntity != null)
            {
                Vec3d entityPos = findEntityPosition(world, hb.attachedEntity);

                if (entityPos != null)
                {
                    hb.x = entityPos.x + hb.offsetX;
                    hb.y = entityPos.y + hb.offsetY;
                    hb.z = entityPos.z + hb.offsetZ;
                }
            }
        }

        hb.attachedEntity = null;
        hb.offsetX = 0D;
        hb.offsetY = 0D;
        hb.offsetZ = 0D;
        source.sendFeedback(() -> Text.literal("§aHitbox '" + name + "' detached"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int offsetHitbox(ServerCommandSource source, String name, double x, double y, double z)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.offsetX = x;
        hb.offsetY = y;
        hb.offsetZ = z;
        source.sendFeedback(() -> Text.literal(String.format("§aHitbox '%s' offset: %.2f %.2f %.2f", name, x, y, z)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHitbox(ServerCommandSource source, String name)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.active = !hb.active;
        String state = hb.active ? "§aenabled" : "§cdisabled";
        source.sendFeedback(() -> Text.literal("§eHitbox '" + name + "' " + state), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int removeHitbox(ServerCommandSource source, String name)
    {
        if (HITBOXES.remove(name) == null)
        {
            source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        COOLDOWNS.remove(name);
        source.sendFeedback(() -> Text.literal("§cHitbox '" + name + "' removed"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int clearHitboxes(ServerCommandSource source)
    {
        int count = HITBOXES.size();
        HITBOXES.clear();
        COOLDOWNS.clear();
        source.sendFeedback(() -> Text.literal("§c" + count + " hitboxes removed"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int listHitboxes(ServerCommandSource source)
    {
        if (HITBOXES.isEmpty())
        {
            source.sendFeedback(() -> Text.literal("§7No hitboxes"), false);

            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(() -> Text.literal("§6=== Hitboxes ==="), false);

        for (HitboxData hb : HITBOXES.values())
        {
            String attached = hb.attachedEntity != null ? " -> '" + hb.attachedEntity + "'" : "";
            String status = hb.active ? "§a●" : "§c●";
            final String fAttached = attached;
            final HitboxData fHb = hb;
            source.sendFeedback(() -> Text.literal(String.format(
                "%s §e%s §7(%.1f DMG, %.1fx%.1fx%.1f)%s",
                status, fHb.name, fHb.damage, fHb.sizeX, fHb.sizeY, fHb.sizeZ, fAttached)), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    /* AUTONOMIE 2026-07-27: klassen-suche einmal, nicht pro entity.
     *
     * Gleiche falle wie in KernelBruecke (dort 87 %% CPU gemessen): Class.forName in einer
     * schleife, exception geschluckt. Hier laeuft es nur bei aktivem hitbox-command statt
     * pro frame, aber die such-schleife geht ueber ALLE entities der welt. Das ergebnis
     * aendert sich nie — BBS ist geladen oder nicht. */
    private static final Class<?> KEINE_KLASSE = Void.class;
    private static volatile Class<?> actorKlasseCache;

    private static Class<?> actorKlasse()
    {
        Class<?> gemerkt = actorKlasseCache;

        if (gemerkt == null)
        {
            try
            {
                gemerkt = Class.forName("mchorse.bbs_mod.entity.ActorEntity");
            }
            catch (Throwable ohneBbs)
            {
                gemerkt = KEINE_KLASSE;
            }

            actorKlasseCache = gemerkt;
        }

        return gemerkt == KEINE_KLASSE ? null : gemerkt;
    }

}
