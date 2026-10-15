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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

        AABB getBox(Vec3 entityPos)
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

            return new AABB(cx - this.sizeX, cy, cz - this.sizeZ,
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTree()
    {
        return Commands.literal("hitbox")
            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("damage", FloatArgumentType.floatArg(0F))
                        .then(Commands.argument("sizeX", DoubleArgumentType.doubleArg(0.1D, 50D))
                            .then(Commands.argument("sizeY", DoubleArgumentType.doubleArg(0.1D, 50D))
                                .then(Commands.argument("sizeZ", DoubleArgumentType.doubleArg(0.1D, 50D))
                                    .executes(ctx -> createHitbox(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        FloatArgumentType.getFloat(ctx, "damage"),
                                        DoubleArgumentType.getDouble(ctx, "sizeX"),
                                        DoubleArgumentType.getDouble(ctx, "sizeY"),
                                        DoubleArgumentType.getDouble(ctx, "sizeZ")))))))))
            .then(Commands.literal("move")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                .executes(ctx -> moveHitbox(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    DoubleArgumentType.getDouble(ctx, "x"),
                                    DoubleArgumentType.getDouble(ctx, "y"),
                                    DoubleArgumentType.getDouble(ctx, "z"))))))))
            .then(Commands.literal("scale")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("sizeX", DoubleArgumentType.doubleArg(0.1D, 50D))
                        .then(Commands.argument("sizeY", DoubleArgumentType.doubleArg(0.1D, 50D))
                            .then(Commands.argument("sizeZ", DoubleArgumentType.doubleArg(0.1D, 50D))
                                .executes(ctx -> scaleHitbox(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    DoubleArgumentType.getDouble(ctx, "sizeX"),
                                    DoubleArgumentType.getDouble(ctx, "sizeY"),
                                    DoubleArgumentType.getDouble(ctx, "sizeZ"))))))))
            .then(Commands.literal("damage")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("amount", FloatArgumentType.floatArg(0F))
                        .executes(ctx -> setDamage(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"),
                            FloatArgumentType.getFloat(ctx, "amount"))))))
            .then(Commands.literal("attach")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("entity", StringArgumentType.greedyString())
                        .executes(ctx -> attachHitbox(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "entity"))))))
            .then(Commands.literal("detach")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> detachHitbox(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("offset")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                .executes(ctx -> offsetHitbox(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    DoubleArgumentType.getDouble(ctx, "x"),
                                    DoubleArgumentType.getDouble(ctx, "y"),
                                    DoubleArgumentType.getDouble(ctx, "z"))))))))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> toggleHitbox(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> removeHitbox(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("clear")
                .executes(ctx -> clearHitboxes(ctx.getSource())))
            .then(Commands.literal("list")
                .executes(ctx -> listHitboxes(ctx.getSource())))
            .then(Commands.literal("show")
                .executes(ctx ->
                {
                    showDebug = true;
                    FilmHitboxBridge.showWireframe = true;
                    ctx.getSource().sendSuccess(() -> Component.literal("§aHitbox debug particles + wireframe ON"), false);

                    return Command.SINGLE_SUCCESS;
                }))
            .then(Commands.literal("hide")
                .executes(ctx ->
                {
                    showDebug = false;
                    FilmHitboxBridge.showWireframe = false;
                    ctx.getSource().sendSuccess(() -> Component.literal("§cHitbox debug particles + wireframe OFF"), false);

                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server)
    {
        long tick = server.getTickCount();

        /* Drain film-driven boxes pushed by DamageHitboxClip during the last
           render cycle. Snapshot so the wireframe renderer can read a stable
           list while applyClip continues writing to activeBoxes. */
        List<double[]> filmBoxes = new ArrayList<>(FilmHitboxBridge.activeBoxes);
        FilmHitboxBridge.lastFrameBoxes = filmBoxes;
        FilmHitboxBridge.activeBoxes.clear();

        if (!filmBoxes.isEmpty())
        {
            for (ServerLevel world : server.getAllLevels())
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

                    AABB box = new AABB(fx - fsx, fy, fz - fsz, fx + fsx, fy + fsy * 2D, fz + fsz);

                    if (fdmg > 0F)
                    {
                        Map<UUID, Long> cooldowns = COOLDOWNS.computeIfAbsent("__film__", k -> new ConcurrentHashMap<>());

                        /* (Entity) null -- ohne den cast ist der aufruf unter Mojang-Mappings MEHRDEUTIG:
                         * null passt sowohl auf getEntities(Entity, AABB, Predicate) als auch auf
                         * <T>getEntities(EntityTypeTest, AABB, Predicate). Gemeint ist "kein
                         * ausgeschlossenes entity", also die erste. */
                        for (Entity e : world.getEntities((Entity) null, box,
                            ent -> ent instanceof LivingEntity && !(ent instanceof ServerPlayer)))
                        {
                            LivingEntity living = (LivingEntity) e;

                            if (!living.isAlive())
                            {
                                continue;
                            }

                            UUID eid = living.getUUID();
                            Long lastHit = cooldowns.get(eid);

                            if (lastHit != null && (tick - lastHit) < cooldownTicks)
                            {
                                continue;
                            }

                            living.hurt(world.damageSources().generic(), fdmg);
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

        for (ServerLevel world : server.getAllLevels())
        {
            for (HitboxData hb : HITBOXES.values())
            {
                if (!hb.active)
                {
                    continue;
                }

                Vec3 entityPos = null;

                if (hb.attachedEntity != null)
                {
                    entityPos = findEntityPosition(world, hb.attachedEntity);

                    if (entityPos == null)
                    {
                        continue;
                    }
                }

                AABB box = hb.getBox(entityPos);

                Map<UUID, Long> cooldowns = COOLDOWNS.computeIfAbsent(hb.name, k -> new ConcurrentHashMap<>());

                /* (Entity) null -- ohne den cast ist der aufruf unter Mojang-Mappings MEHRDEUTIG:
                 * null passt sowohl auf getEntities(Entity, AABB, Predicate) als auch auf
                 * <T>getEntities(EntityTypeTest, AABB, Predicate). Gemeint ist "kein
                 * ausgeschlossenes entity", also die erste. */
                for (Entity e : world.getEntities((Entity) null, box,
                    ent -> ent instanceof LivingEntity && !(ent instanceof ServerPlayer)))
                {
                    LivingEntity living = (LivingEntity) e;

                    if (!living.isAlive() || hb.damage <= 0F)
                    {
                        continue;
                    }

                    UUID eid = living.getUUID();
                    Long lastHit = cooldowns.get(eid);

                    if (lastHit != null && (tick - lastHit) < DAMAGE_COOLDOWN_TICKS)
                    {
                        continue;
                    }

                    living.hurt(world.damageSources().generic(), hb.damage);
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

    private static Vec3 findEntityPosition(ServerLevel world, String name)
    {
        for (Entity e : world.getAllEntities())
        {
            if (e == null)
            {
                continue;
            }

            if (e.hasCustomName() && name.equals(e.getCustomName().getString()))
            {
                return e.position();
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
                                return e.position();
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

    private static void spawnBoxParticles(ServerLevel world, AABB box)
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

    private static void spawnParticle(ServerLevel world, double x, double y, double z)
    {
        world.sendParticles(ParticleTypes.POOF, x, y, z, 1, 0D, 0D, 0D, 0D);
    }

    private static int createHitbox(CommandSourceStack source, String name, float damage,
        double sizeX, double sizeY, double sizeZ)
    {
        if (HITBOXES.containsKey(name))
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' already exists"), false);

            return 0;
        }

        Vec3 pos = source.getPosition();
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

        source.sendSuccess(() -> Component.literal(String.format(
            "§aHitbox '%s' created (%.1f DMG, %.1fx%.1fx%.1f)", name, damage, sizeX, sizeY, sizeZ)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int moveHitbox(CommandSourceStack source, String name, double x, double y, double z)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.x = x;
        hb.y = y;
        hb.z = z;
        source.sendSuccess(() -> Component.literal(String.format("§aHitbox '%s' moved to %.2f %.2f %.2f", name, x, y, z)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int scaleHitbox(CommandSourceStack source, String name, double sizeX, double sizeY, double sizeZ)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.sizeX = sizeX;
        hb.sizeY = sizeY;
        hb.sizeZ = sizeZ;
        source.sendSuccess(() -> Component.literal(String.format("§aHitbox '%s' size: %.1fx%.1fx%.1f", name, sizeX, sizeY, sizeZ)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int setDamage(CommandSourceStack source, String name, float damage)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.damage = damage;
        source.sendSuccess(() -> Component.literal(String.format("§aHitbox '%s' damage: %.1f", name, damage)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int attachHitbox(CommandSourceStack source, String name, String entityName)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        boolean found = false;

        for (ServerLevel world : source.getServer().getAllLevels())
        {
            if (findEntityPosition(world, entityName) != null)
            {
                found = true;
                break;
            }
        }

        if (!found)
        {
            source.sendSuccess(() -> Component.literal("§cEntity '" + entityName + "' not found"), false);

            return 0;
        }

        hb.attachedEntity = entityName;
        source.sendSuccess(() -> Component.literal(String.format("§aHitbox '%s' attached to '%s'", name, entityName)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int detachHitbox(CommandSourceStack source, String name)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        for (ServerLevel world : source.getServer().getAllLevels())
        {
            if (hb.attachedEntity != null)
            {
                Vec3 entityPos = findEntityPosition(world, hb.attachedEntity);

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
        source.sendSuccess(() -> Component.literal("§aHitbox '" + name + "' detached"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int offsetHitbox(CommandSourceStack source, String name, double x, double y, double z)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.offsetX = x;
        hb.offsetY = y;
        hb.offsetZ = z;
        source.sendSuccess(() -> Component.literal(String.format("§aHitbox '%s' offset: %.2f %.2f %.2f", name, x, y, z)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int toggleHitbox(CommandSourceStack source, String name)
    {
        HitboxData hb = HITBOXES.get(name);

        if (hb == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        hb.active = !hb.active;
        String state = hb.active ? "§aenabled" : "§cdisabled";
        source.sendSuccess(() -> Component.literal("§eHitbox '" + name + "' " + state), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int removeHitbox(CommandSourceStack source, String name)
    {
        if (HITBOXES.remove(name) == null)
        {
            source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' not found"), false);

            return 0;
        }

        COOLDOWNS.remove(name);
        source.sendSuccess(() -> Component.literal("§cHitbox '" + name + "' removed"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int clearHitboxes(CommandSourceStack source)
    {
        int count = HITBOXES.size();
        HITBOXES.clear();
        COOLDOWNS.clear();
        source.sendSuccess(() -> Component.literal("§c" + count + " hitboxes removed"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int listHitboxes(CommandSourceStack source)
    {
        if (HITBOXES.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("§7No hitboxes"), false);

            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.literal("§6=== Hitboxes ==="), false);

        for (HitboxData hb : HITBOXES.values())
        {
            String attached = hb.attachedEntity != null ? " -> '" + hb.attachedEntity + "'" : "";
            String status = hb.active ? "§a●" : "§c●";
            final String fAttached = attached;
            final HitboxData fHb = hb;
            source.sendSuccess(() -> Component.literal(String.format(
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
