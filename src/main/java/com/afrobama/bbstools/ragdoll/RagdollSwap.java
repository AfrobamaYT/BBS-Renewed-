package com.afrobama.bbstools.ragdoll;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;



/**
 * /ragdollswap [type] [x±N] [y±N] [z±N]
 *
 * Picks the nearest non-army living entity within 50 blocks, extracts its BBS
 * form mob-type + texture link via reflection, discards the original, spawns
 * a vanilla mob of that type (or the explicit type) at the same position with
 * the given velocity, registers the BBS texture for the new UUID so any
 * client-side renderer override hands the ragdolled mob the original BBS form
 * skin, then kills the new mob (PhysicsMod sees the death and creates a
 * physics ragdoll).
 *
 * PhysicsMod is the intended consumer; without it the command still spawns
 * and kills the mob, just no ragdoll appears.
 *
 * Ported from NeoForge BBSTools.
 */
public final class RagdollSwap
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/ragdoll-swap");
    private static final Pattern AXIS_PATTERN = Pattern.compile("([xyz])(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE);
    private static final double SEARCH_RADIUS = 50.0D;

    /* 1-tick kill delay: long enough for the async texture-override
     *  registration (mc.execute on client thread) to drain before
     *  PhysicsMod's ragdoll render hook fires, but short enough that
     *  the user perceives the swap as instantaneous. User-requested
     *  default — earlier 3-tick value felt sluggish. */
    private static final int KILL_DELAY_TICKS = 1;

    private static final Set<UUID> RAGDOLLED = new HashSet<>();

    private record PendingKill(UUID uuid, ServerWorld world, long killAtTick, double vx, double vy, double vz)
    {
    }

    private static final ConcurrentLinkedQueue<PendingKill> PENDING_KILLS = new ConcurrentLinkedQueue<>();

    /* Coded by Afrobama/Claude — restore-state for /ragdollplayer. The
     *  source player gets shoved into spectator while a clone-actor takes
     *  the visible beating. Once the clone settles (or the timer fires)
     *  the player is teleported to the clone's resting position and put
     *  back into their previous game mode, then the clone despawns. */
    private record PendingPlayerRestore(
        UUID playerUuid,
        UUID actorUuid,
        ServerWorld world,
        long restoreAtTick,
        net.minecraft.world.GameMode previousGameMode)
    {
    }

    private static final ConcurrentLinkedQueue<PendingPlayerRestore> PENDING_RESTORES = new ConcurrentLinkedQueue<>();

    /* Coded by Afrobama/Claude — fake players spawned by /ragdollswap
     *  Player don't auto-remove on death (the vanilla ServerPlayerEntity
     *  death path expects a respawn confirm). We schedule their final
     *  discard a few ticks after the death-kill so PhysicsMod has time
     *  to capture the model on its render hook. */
    private record PendingDiscard(UUID uuid, ServerWorld world, long discardAtTick)
    {
    }

    private static final ConcurrentLinkedQueue<PendingDiscard> PENDING_FAKE_PLAYER_DISCARDS = new ConcurrentLinkedQueue<>();

    private RagdollSwap()
    {
    }

    public static void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(RagdollSwap::onServerTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
        {
            dispatcher.register(CommandManager.literal("ragdollswap")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> executeFromArgs(ctx.getSource(), ""))
                .then(CommandManager.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> executeFromArgs(ctx.getSource(),
                        StringArgumentType.getString(ctx, "args")))));

            /* New /ragdoll command — preserves the BBS Actor with current
             *  pose + textures; verlet physics applies on top. Vanilla mob
             *  fallback when target is not an ActorEntity. */
            dispatcher.register(CommandManager.literal("ragdoll")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> executeRagdoll(ctx.getSource(), ""))
                .then(CommandManager.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> executeRagdoll(ctx.getSource(),
                        StringArgumentType.getString(ctx, "args")))));

            /* /ragdolly — swaps the actor's form to the bundled cml_ragdoll
             *  rig (predictable bones: body/head/left_arm/right_arm/
             *  left_leg/right_leg) with the actor's current texture, then
             *  starts the verlet ragdoll. Cross-rig answer to "every model
             *  has its own bones": this rig is shipped with BBS so the
             *  per-limb path always matches. */
            dispatcher.register(CommandManager.literal("ragdolly")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> executeRagdolly(ctx.getSource(), ""))
                .then(CommandManager.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> executeRagdolly(ctx.getSource(),
                        StringArgumentType.getString(ctx, "args")))));

            /* Coded by Afrobama/Claude — /ragdollplayer: spawn an Actor
             *  clone wearing the source player's current BBS morph (skin
             *  + form), apply ragdoll velocity to the clone, put the real
             *  player in spectator for a few seconds so the visual sells
             *  as "you got thrown". After the delay the player is teleported
             *  to the clone's resting position and restored to their prior
             *  game mode. The clone despawns. */
            dispatcher.register(CommandManager.literal("ragdollplayer")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> executeRagdollPlayer(ctx.getSource(), ""))
                .then(CommandManager.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> executeRagdollPlayer(ctx.getSource(),
                        StringArgumentType.getString(ctx, "args")))));
        });
    }

    private static void onServerTick(MinecraftServer server)
    {
        /* Always step active actor-ragdolls regardless of pending kills. */
        ActorRagdollRuntime.tick();

        processPendingPlayerRestores(server);
        processPendingFakePlayerDiscards(server);

        if (PENDING_KILLS.isEmpty())
        {
            return;
        }

        long currentTick = server.getTicks();
        Iterator<PendingKill> it = PENDING_KILLS.iterator();

        while (it.hasNext())
        {
            PendingKill pk = it.next();

            if (currentTick >= pk.killAtTick())
            {
                it.remove();
                Entity e = pk.world().getEntity(pk.uuid());

                if (e instanceof LivingEntity living && living.isAlive())
                {
                    living.setVelocity(pk.vx(), pk.vy(), pk.vz());
                    living.velocityModified = true;
                    LOG.info("Killing {} after delay", living.getType());
                    living.kill();
                }
                else
                {
                    LOG.warn("Entity {} not found or already dead for delayed kill", pk.uuid());
                }
            }
        }
    }

    private static int executeRagdoll(ServerCommandSource source, String args)
    {
        System.out.println("[BBS-DBG] /ragdoll invoked — args=\"" + args + "\" — ragdoll v2 build is running.");

        /* Same axis-arg parsing as /ragdollswap — picks vx vy vz. */
        String[] parts = args.trim().split("\\s+");
        double vx = 0D, vy = 0D, vz = 0D;

        for (String part : parts)
        {
            if (part.isEmpty()) continue;
            Matcher m = AXIS_PATTERN.matcher(part);
            if (m.matches())
            {
                double val = Double.parseDouble(m.group(2));
                switch (m.group(1).toLowerCase())
                {
                    case "x" -> vx = val;
                    case "y" -> vy = val;
                    case "z" -> vz = val;
                }
            }
        }

        ServerWorld world = source.getWorld();
        Vec3d sourcePos = source.getPosition();
        double searchRadiusSq = SEARCH_RADIUS * SEARCH_RADIUS;

        Class<?> actorClass = null;
        try { actorClass = Class.forName("mchorse.bbs_mod.entity.ActorEntity"); }
        catch (ClassNotFoundException ignored) {}

        LivingEntity nearestActor = null;
        double nearestDist = Double.MAX_VALUE;

        int totalEntities = 0;
        int livingCount = 0;
        int actorCount = 0;
        for (Entity e : world.iterateEntities())
        {
            totalEntities++;
            if (!(e instanceof LivingEntity le)) continue;
            livingCount++;
            if (e instanceof PlayerEntity) continue;
            if (RAGDOLLED.contains(e.getUuid())) continue;
            if (actorClass == null || !actorClass.isInstance(le)) continue;
            actorCount++;

            double d = le.getPos().squaredDistanceTo(sourcePos);
            if (d > searchRadiusSq) continue;
            if (d < nearestDist)
            {
                nearestDist = d;
                nearestActor = le;
            }
        }

        final int finalTotal = totalEntities;
        final int finalLiving = livingCount;
        final int finalActor = actorCount;
        final boolean classOk = actorClass != null;
        source.sendFeedback(() -> Text.literal("§7[ragdoll-debug] scan: total=" + finalTotal
            + " living=" + finalLiving + " actors=" + finalActor + " actorClass="
            + (classOk ? "OK" : "MISSING")), false);

        if (nearestActor == null)
        {
            source.sendFeedback(() -> Text.literal("§cNo BBS Actor within " + (int) SEARCH_RADIUS + " blocks. Use /ragdollswap for vanilla mobs."), false);
            return 0;
        }

        org.joml.Vector3f vel = new org.joml.Vector3f((float) vx, (float) vy, (float) vz);
        ActorRagdollRuntime.startTemplate(
            nearestActor.getUuid(),
            (float) nearestActor.getX(),
            (float) nearestActor.getY(),
            (float) nearestActor.getZ(),
            nearestActor.getYaw(),
            vel);

        final LivingEntity finalTarget = nearestActor;
        source.sendFeedback(() -> Text.literal("§aRagdoll started on Actor "
            + finalTarget.getUuid().toString().substring(0, 8) + " (active: " + ActorRagdollRuntime.activeCount() + ")"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int executeRagdolly(ServerCommandSource source, String args)
    {
        System.out.println("[BBS-DBG] /ragdolly invoked — args=\"" + args + "\".");

        String[] parts = args.trim().split("\\s+");
        double vx = 0D, vy = 0D, vz = 0D;
        for (String part : parts)
        {
            if (part.isEmpty()) continue;
            Matcher m = AXIS_PATTERN.matcher(part);
            if (m.matches())
            {
                double val = Double.parseDouble(m.group(2));
                switch (m.group(1).toLowerCase())
                {
                    case "x" -> vx = val;
                    case "y" -> vy = val;
                    case "z" -> vz = val;
                }
            }
        }

        ServerWorld world = source.getWorld();
        Vec3d sourcePos = source.getPosition();
        double searchRadiusSq = SEARCH_RADIUS * SEARCH_RADIUS;

        Class<?> actorClass = null;
        try { actorClass = Class.forName("mchorse.bbs_mod.entity.ActorEntity"); }
        catch (ClassNotFoundException ignored) {}

        LivingEntity nearestActor = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : world.iterateEntities())
        {
            if (!(e instanceof LivingEntity le)) continue;
            if (e instanceof PlayerEntity) continue;
            if (RAGDOLLED.contains(e.getUuid())) continue;
            if (actorClass == null || !actorClass.isInstance(le)) continue;

            double d = le.getPos().squaredDistanceTo(sourcePos);
            if (d > searchRadiusSq) continue;
            if (d < nearestDist)
            {
                nearestDist = d;
                nearestActor = le;
            }
        }

        if (nearestActor == null)
        {
            source.sendFeedback(() -> Text.literal("§cNo BBS Actor within " + (int) SEARCH_RADIUS + " blocks."), false);
            return 0;
        }

        /* Read current texture (best-effort; null is OK — model renders
         *  with default missing-texture pattern). */
        String texLinkStr = getBBSFormTextureLink(nearestActor);

        /* Swap the actor's form to a fresh ModelForm pointing at the
         *  bundled cml_ragdoll rig + the original texture. setForm
         *  triggers onMorph/onDemorph on the server side. */
        try
        {
            mchorse.bbs_mod.forms.forms.ModelForm newForm = new mchorse.bbs_mod.forms.forms.ModelForm();
            newForm.model.set("cml_ragdoll");
            if (texLinkStr != null && !texLinkStr.isEmpty())
            {
                mchorse.bbs_mod.resources.Link texLink = mchorse.bbs_mod.resources.Link.create(texLinkStr);
                if (texLink != null) newForm.texture.set(texLink);
            }

            Class<?> ac = Class.forName("mchorse.bbs_mod.entity.ActorEntity");
            java.lang.reflect.Method setForm = ac.getMethod("setForm", mchorse.bbs_mod.forms.forms.Form.class);
            setForm.invoke(nearestActor, newForm);

            /* setForm only updates server-side state; the client renderer
             *  keeps the cached old form and would still walk the original
             *  rig's groups. Push the new form to every tracking player so
             *  the client-side ActorEntity flips to cml_ragdoll. */
            for (net.minecraft.server.network.ServerPlayerEntity p : world.getPlayers())
            {
                mchorse.bbs_mod.network.ServerNetwork.sendEntityForm(p,
                    (mchorse.bbs_mod.entity.IEntityFormProvider) nearestActor);
            }
        }
        catch (Throwable t)
        {
            LOG.warn("[ragdolly] form-swap failed: {}", t.toString());
            source.sendFeedback(() -> Text.literal("§cForm-swap failed: " + t.getClass().getSimpleName()), false);
            return 0;
        }

        /* Run the existing verlet ragdoll on the now-cml_ragdoll-shaped actor. */
        org.joml.Vector3f vel = new org.joml.Vector3f((float) vx, (float) vy, (float) vz);
        ActorRagdollRuntime.startTemplate(
            nearestActor.getUuid(),
            (float) nearestActor.getX(),
            (float) nearestActor.getY(),
            (float) nearestActor.getZ(),
            nearestActor.getYaw(),
            vel);

        final LivingEntity finalTarget = nearestActor;
        final String finalTex = texLinkStr;
        source.sendFeedback(() -> Text.literal("§aRagdolly: actor "
            + finalTarget.getUuid().toString().substring(0, 8)
            + " swapped to cml_ragdoll (texture="
            + (finalTex == null ? "default" : finalTex) + ")"), false);

        return Command.SINGLE_SUCCESS;
    }

    /* Coded by Afrobama/Claude — /ragdollplayer flow.
     *  Args: "x±N y±N z±N [d=ticks]" — velocity components + optional
     *  spectator duration in server ticks (default 100 = 5 sec). */
    private static final int DEFAULT_RAGDOLL_PLAYER_DURATION_TICKS = 100;

    private static int executeRagdollPlayer(ServerCommandSource source, String args)
    {
        net.minecraft.server.network.ServerPlayerEntity player;
        try { player = source.getPlayerOrThrow(); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e)
        {
            source.sendFeedback(() -> Text.literal("§c/ragdollplayer must be run as a player."), false);
            return 0;
        }

        double vx = 0D, vy = 0D, vz = 0D;
        int durationTicks = DEFAULT_RAGDOLL_PLAYER_DURATION_TICKS;

        String[] parts = args.trim().split("\\s+");
        for (String part : parts)
        {
            if (part.isEmpty()) continue;
            if (part.toLowerCase().startsWith("d="))
            {
                try { durationTicks = Math.max(10, Integer.parseInt(part.substring(2))); }
                catch (NumberFormatException ignored) {}
                continue;
            }
            Matcher m = AXIS_PATTERN.matcher(part);
            if (m.matches())
            {
                double val = Double.parseDouble(m.group(2));
                switch (m.group(1).toLowerCase())
                {
                    case "x" -> vx = val;
                    case "y" -> vy = val;
                    case "z" -> vz = val;
                }
            }
        }

        /* Default: small upward + backward toss if no axes provided so
         *  the operator gets a visible result on first try. */
        if (vx == 0D && vy == 0D && vz == 0D)
        {
            Vec3d look = player.getRotationVec(1F);
            vx = -look.x * 1.5D;
            vy = 0.8D;
            vz = -look.z * 1.5D;
        }

        ServerWorld world = source.getWorld();

        /* Build the clone actor. Reflectively look up
         *  mchorse.bbs_mod.entity.ActorEntity + BBSMod.ACTOR_ENTITY,
         *  attach the player's current BBS morph form so the clone wears
         *  the right skin, copy the player's pose. */
        Object actorObj;
        Class<?> actorClass;
        try
        {
            actorClass = Class.forName("mchorse.bbs_mod.entity.ActorEntity");
            Class<?> bbsModClass = Class.forName("mchorse.bbs_mod.BBSMod");
            Object actorType = bbsModClass.getField("ACTOR_ENTITY").get(null);
            actorObj = actorClass.getConstructor(EntityType.class, net.minecraft.world.World.class).newInstance(actorType, world);
        }
        catch (Throwable t)
        {
            LOG.warn("Could not build ActorEntity clone: {}", t.toString());
            source.sendFeedback(() -> Text.literal("§cFailed to spawn clone actor — BBS classes missing?"), false);
            return 0;
        }

        if (!(actorObj instanceof LivingEntity clone))
        {
            source.sendFeedback(() -> Text.literal("§cActorEntity is not a LivingEntity."), false);
            return 0;
        }

        /* Copy player's BBS form onto the clone via Morph.getMorph(player). */
        try
        {
            Class<?> morphClass = Class.forName("mchorse.bbs_mod.morphing.Morph");
            Object morph = morphClass.getMethod("getMorph", Entity.class).invoke(null, player);
            if (morph != null)
            {
                Object form = morphClass.getMethod("getForm").invoke(morph);
                if (form != null)
                {
                    Class<?> formUtilsClass = Class.forName("mchorse.bbs_mod.forms.FormUtils");
                    Object copiedForm = formUtilsClass.getMethod("copy", Class.forName("mchorse.bbs_mod.forms.forms.Form")).invoke(null, form);
                    actorClass.getMethod("setForm", Class.forName("mchorse.bbs_mod.forms.forms.Form")).invoke(clone, copiedForm);
                }
            }
        }
        catch (Throwable t)
        {
            LOG.info("Player has no BBS morph form — clone will use default appearance ({}).", t.toString());
        }

        /* Position + orient the clone at the player's exact spot. */
        clone.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        clone.setHeadYaw(player.getHeadYaw());
        clone.setBodyYaw(player.bodyYaw);
        clone.setVelocity(vx, vy, vz);
        clone.velocityModified = true;

        if (!world.spawnEntity(clone))
        {
            source.sendFeedback(() -> Text.literal("§cFailed to spawn clone in world."), false);
            return 0;
        }

        /* Snapshot player's prior game mode and shove them into spectator
         *  so they're invisible + non-interfering while the clone takes
         *  the hit. The clone is the "ragdoll target" — apply the
         *  existing ActorRagdollRuntime template so verlet physics +
         *  bone-chain (if available) kick in just like /ragdoll. */
        net.minecraft.world.GameMode prevMode = player.interactionManager.getGameMode();
        player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);

        try
        {
            org.joml.Vector3f vel = new org.joml.Vector3f((float) vx, (float) vy, (float) vz);
            ActorRagdollRuntime.startTemplate(
                clone.getUuid(),
                (float) clone.getX(),
                (float) clone.getY(),
                (float) clone.getZ(),
                clone.getYaw(),
                vel);
        }
        catch (Throwable t)
        {
            LOG.warn("ActorRagdollRuntime.startTemplate failed for player-clone: {}", t.toString());
        }

        PENDING_RESTORES.add(new PendingPlayerRestore(
            player.getUuid(),
            clone.getUuid(),
            world,
            world.getServer().getTicks() + durationTicks,
            prevMode));

        final double fvx = vx, fvy = vy, fvz = vz;
        final int fduration = durationTicks;
        source.sendFeedback(() -> Text.literal(String.format(
            "§aRagdoll-player: clone tossed (%.2f, %.2f, %.2f), restore in %d ticks (%.1fs).",
            fvx, fvy, fvz, fduration, fduration / 20F)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static void processPendingPlayerRestores(MinecraftServer server)
    {
        if (PENDING_RESTORES.isEmpty()) return;

        long currentTick = server.getTicks();
        Iterator<PendingPlayerRestore> it = PENDING_RESTORES.iterator();

        while (it.hasNext())
        {
            PendingPlayerRestore pr = it.next();
            if (currentTick < pr.restoreAtTick()) continue;
            it.remove();

            net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(pr.playerUuid());
            Entity actor = pr.world().getEntity(pr.actorUuid());

            double tpX, tpY, tpZ;
            if (actor != null)
            {
                tpX = actor.getX();
                tpY = actor.getY();
                tpZ = actor.getZ();
                actor.discard();
            }
            else if (player != null)
            {
                tpX = player.getX();
                tpY = player.getY();
                tpZ = player.getZ();
            }
            else
            {
                continue;
            }

            if (player != null)
            {
                player.teleport(pr.world(), tpX, tpY, tpZ, player.getYaw(), player.getPitch());
                player.changeGameMode(pr.previousGameMode());
                player.sendMessage(Text.literal("§7[ragdoll-player] restored to "
                    + pr.previousGameMode().getName() + " at clone's resting position."), false);
            }
        }
    }

    private static void processPendingFakePlayerDiscards(MinecraftServer server)
    {
        if (PENDING_FAKE_PLAYER_DISCARDS.isEmpty()) return;

        long currentTick = server.getTicks();
        Iterator<PendingDiscard> it = PENDING_FAKE_PLAYER_DISCARDS.iterator();

        while (it.hasNext())
        {
            PendingDiscard pd = it.next();
            if (currentTick < pd.discardAtTick()) continue;
            it.remove();

            Entity e = pd.world().getEntity(pd.uuid());
            if (e instanceof net.minecraft.server.network.ServerPlayerEntity sp)
            {
                /* Use removePlayer / discardSilently rather than kill —
                 *  the player is already dead, we just need to clear
                 *  the entity + player-list registration so the corpse
                 *  doesn't linger in the world. */
                try
                {
                    server.getPlayerManager().remove(sp);
                }
                catch (Throwable t)
                {
                    LOG.debug("PlayerManager.remove failed for fake player {}: {}", pd.uuid(), t.toString());
                }
                FakePlayerSpawner.discardSilently(sp);
                LOG.info("Discarded fake-player entity {}", pd.uuid());
            }
            else if (e != null)
            {
                e.discard();
            }
        }
    }

    private static int executeFromArgs(ServerCommandSource source, String args)
    {
        String[] parts = args.trim().split("\\s+");
        String entityType = null;
        double vx = 0D;
        double vy = 0D;
        double vz = 0D;

        for (String part : parts)
        {
            if (part.isEmpty())
            {
                continue;
            }

            Matcher m = AXIS_PATTERN.matcher(part);

            if (m.matches())
            {
                double val = Double.parseDouble(m.group(2));

                switch (m.group(1).toLowerCase())
                {
                    case "x" -> vx = val;
                    case "y" -> vy = val;
                    case "z" -> vz = val;
                }
            }
            else
            {
                entityType = part;
            }
        }

        return execute(source, vx, vy, vz, entityType);
    }

    private static int execute(ServerCommandSource source, double vx, double vy, double vz, String entityTypeOverride)
    {
        LOG.info("Command executed! vx={} vy={} vz={} type={}", vx, vy, vz, entityTypeOverride);
        ServerWorld world = source.getWorld();

        List<LivingEntity> bbsActors = new ArrayList<>();
        List<LivingEntity> allEntities = new ArrayList<>();
        Class<?> actorClass = null;

        try
        {
            actorClass = Class.forName("mchorse.bbs_mod.entity.ActorEntity");
        }
        catch (ClassNotFoundException ignored)
        {
        }

        Vec3d sourcePos = source.getPosition();
        double searchRadiusSq = SEARCH_RADIUS * SEARCH_RADIUS;

        for (Entity e : world.iterateEntities())
        {
            if (e == null || !(e instanceof LivingEntity le))
            {
                continue;
            }

            if (e instanceof PlayerEntity)
            {
                continue;
            }

            if (RAGDOLLED.contains(e.getUuid()))
            {
                continue;
            }

            {
                continue;
            }

            if (le.getPos().squaredDistanceTo(sourcePos) > searchRadiusSq)
            {
                continue;
            }

            allEntities.add(le);

            if (actorClass != null && actorClass.isInstance(le))
            {
                bbsActors.add(le);
            }
        }

        LOG.info("Found {} BBS actors, {} entities within {} blocks", bbsActors.size(), allEntities.size(), SEARCH_RADIUS);

        List<LivingEntity> candidates = bbsActors.isEmpty() ? allEntities : bbsActors;

        if (candidates.isEmpty())
        {
            LOG.warn("No entities found within radius!");
            source.sendFeedback(() -> Text.literal("§cNo entity within " + (int) SEARCH_RADIUS + " blocks (army mobs ignored)"), false);

            return 0;
        }

        candidates.sort(Comparator.comparingDouble(e -> e.getPos().squaredDistanceTo(sourcePos)));
        LivingEntity target = candidates.get(0);
        LOG.info("Selected target: {} class={} at {}", target.getType(), target.getClass().getName(), target.getPos());

        Vec3d spawnPos = target.getPos();
        float yaw = target.getYaw();

        EntityType<?> entityType;

        if (entityTypeOverride != null)
        {
            String typeIdLower = entityTypeOverride.toLowerCase();
            final String typeId = typeIdLower.contains(":") ? typeIdLower : "minecraft:" + typeIdLower;
            Identifier id = Identifier.tryParse(typeId);

            if (id == null || !Registries.ENTITY_TYPE.containsId(id))
            {
                source.sendFeedback(() -> Text.literal("§cUnknown entity type: " + typeId), false);

                return 0;
            }

            entityType = Registries.ENTITY_TYPE.get(id);
        }
        else
        {
            EntityType<?> bbsType = getBBSFormEntityType(target);
            LOG.info("BBS form entity type: {}", bbsType);
            entityType = bbsType != null ? bbsType : target.getType();
        }

        /* Player-type: spawn a Zombie with the BBS player-skin
         *  texture override. The fake-ServerPlayerEntity path was
         *  prototyped (see {@link FakePlayerSpawner}) but PhysicsMod
         *  doesn't produce a visible ragdoll from a kill-same-tick
         *  fake player — the entity is gone before the ragdoll
         *  renders. Zombie reuses the existing
         *  {@link mchorse.bbs_mod.staff.RagdollTextureRegistry} path
         *  that already works for every other mob substitute. User-
         *  confirmed 2026-05-13: arms-up pose acceptable, skin must
         *  carry. */
        boolean substitutedPlayer = false;
        net.minecraft.server.network.ServerPlayerEntity fakePlayer = null;

        if (entityType == EntityType.PLAYER)
        {
            LOG.info("Player type requested — substituting Zombie with player-skin texture override.");
            entityType = EntityType.ZOMBIE;
            substitutedPlayer = true;
        }

        String entityTypeId = Registries.ENTITY_TYPE.getId(entityType).toString();
        LOG.info("Final entity type: {}", entityTypeId);

        String bbsTextureLink = getBBSFormTextureLink(target);
        LOG.info("BBS form texture link: {}", bbsTextureLink);

        final double finalVx = vx;
        final double finalVy = vy;
        final double finalVz = vz;

        /* For fake player: already spawned + positioned by
         *  FakePlayerSpawner; use it directly. For everything else:
         *  build the entity BEFORE discarding the original — if
         *  create() returns null we keep the original actor. */
        Entity spawned;
        if (fakePlayer != null)
        {
            spawned = fakePlayer;
        }
        else
        {
            spawned = entityType.create(world);

            if (spawned == null)
            {
                LOG.error("Failed to create entity of type {}", entityTypeId);
                source.sendFeedback(() -> Text.literal("§cEntity could not be created! Original actor kept."), false);

                return 0;
            }
        }

        target.discard();
        LOG.info("Discarded original entity");

        LOG.info("Spawning {} at {} with velocity {},{},{}", entityTypeId, spawnPos, finalVx, finalVy, finalVz);

        if (fakePlayer == null)
        {
            /* Vanilla mob path: position + setup + spawn. Fake player
             *  was already positioned + added to world by
             *  FakePlayerSpawner.spawn() so we skip these calls. */
            spawned.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, yaw, 0F);
            spawned.setSilent(true);

            if (spawned instanceof MobEntity mob)
            {
                mob.setAiDisabled(true);
                /* Hostile mobs (Zombie, Husk, Skeleton…) self-discard on
                 *  the NEXT tick after spawn when world difficulty is
                 *  PEACEFUL — via HostileEntity.mobTick(). This kills the
                 *  ragdoll path before our scheduled kill even fires.
                 *  setPersistent(true) bypasses several of those despawn
                 *  flows (e.g. random distance-based despawn); for the
                 *  peaceful-difficulty case the only reliable fix is to
                 *  kill same-tick (see below). */
                mob.setPersistent();
            }

            /* Invulnerable: prevents stray damage (sunlight burn, fall,
             *  void, etc.) from killing the mob before PhysicsMod sees
             *  our outOfWorld kill. {@code kill()} uses outOfWorld damage
             *  which bypasses invulnerability flags. */
            spawned.setInvulnerable(true);
        }

        spawned.setVelocity(finalVx, finalVy, finalVz);

        RAGDOLLED.add(spawned.getUuid());

        if (fakePlayer == null)
        {
            boolean added = world.spawnEntity(spawned);
            LOG.info("spawnEntity result: {}", added);
        }
        else
        {
            LOG.info("Fake player already in world via PlayerManager.onPlayerConnect");
        }

        /* Schedule client-side BBS texture registration so the (vanilla) ragdoll
           inherits the original BBS form's skin via the LivingEntityRenderer
           texture-override path (RagdollTextureRegistry). */
        if (bbsTextureLink != null && !bbsTextureLink.isEmpty())
        {
            UUID spawnedUuid = spawned.getUuid();

            try
            {
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
                {
                    Class<?> regCls = Class.forName("mchorse.bbs_mod.staff.RagdollTextureRegistry");
                    Method m = regCls.getMethod("scheduleRegister", UUID.class, String.class);
                    m.invoke(null, spawnedUuid, bbsTextureLink);
                }
            }
            catch (Throwable t)
            {
                LOG.warn("Could not schedule ragdoll texture registration: {}", t.toString());
            }
        }

        if (spawned instanceof LivingEntity living)
        {
            /* Unified delayed-kill path: every substitute (including
             *  Player→Zombie) uses the same KILL_DELAY_TICKS so the
             *  async texture-override registration has time to land
             *  before PhysicsMod's render hook captures the ragdoll
             *  state. The same delay is used for every mob ragdoll
             *  so behaviour is consistent. */
            if (KILL_DELAY_TICKS <= 0)
            {
                living.setVelocity(finalVx, finalVy, finalVz);
                living.velocityModified = true;
                LOG.info("Killing {} immediately (same tick)", living.getType());
                living.kill();
            }
            else
            {
                long killAt = world.getServer().getTicks() + KILL_DELAY_TICKS;
                PENDING_KILLS.add(new PendingKill(spawned.getUuid(), world, killAt, finalVx, finalVy, finalVz));
                LOG.info("Scheduled kill at tick {} (current: {}, substitutedPlayer={})",
                    killAt, world.getServer().getTicks(), substitutedPlayer);
            }
        }

        final String finalTypeId = entityTypeId;
        final Vec3d finalSpawnPos = spawnPos;
        final boolean finalSubbed = substitutedPlayer;
        final boolean finalFake = (fakePlayer != null);
        source.sendFeedback(() -> Text.literal("§aRagdoll: " + finalTypeId
            + (finalFake ? " §7(fake player)" : "")
            + (finalSubbed ? " §7(player→zombie fallback)" : "")
            + " @ " + String.format("%.1f %.1f %.1f", finalSpawnPos.x, finalSpawnPos.y, finalSpawnPos.z)
            + " v=(" + String.format("%.1f, %.1f, %.1f", finalVx, finalVy, finalVz) + ")"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static EntityType<?> getBBSFormEntityType(LivingEntity entity)
    {
        try
        {
            Class<?> actorClass = Class.forName("mchorse.bbs_mod.entity.ActorEntity");

            if (!actorClass.isInstance(entity))
            {
                return null;
            }

            Method getForm = actorClass.getMethod("getForm");
            Object form = getForm.invoke(entity);

            if (form == null)
            {
                LOG.info("getForm() returned null");

                return null;
            }

            LOG.info("Form class: {}", form.getClass().getName());

            Class<?> mobFormClass = Class.forName("mchorse.bbs_mod.forms.forms.MobForm");

            if (!mobFormClass.isInstance(form))
            {
                LOG.info("Form is not MobForm: {}", form.getClass().getSimpleName());

                return null;
            }

            Field mobIDField = mobFormClass.getField("mobID");
            Object mobIDValue = mobIDField.get(form);
            Method getValue = mobIDValue.getClass().getMethod("get");
            Object mobIdObj = getValue.invoke(mobIDValue);
            String mobId = mobIdObj != null ? mobIdObj.toString() : null;

            LOG.info("MobForm.mobID = {}", mobId);

            if (mobId == null || mobId.isEmpty())
            {
                return null;
            }

            Identifier id = Identifier.tryParse(mobId);

            if (id != null && Registries.ENTITY_TYPE.containsId(id))
            {
                return Registries.ENTITY_TYPE.get(id);
            }
        }
        catch (ClassNotFoundException e)
        {
            /* BBS not installed (shouldn't happen since this lives inside BBS). */
        }
        catch (Exception e)
        {
            LOG.error("Failed to read BBS form entity type", e);
        }

        return null;
    }

    private static String getBBSFormTextureLink(LivingEntity entity)
    {
        try
        {
            Class<?> actorClass = Class.forName("mchorse.bbs_mod.entity.ActorEntity");

            if (!actorClass.isInstance(entity))
            {
                return null;
            }

            Method getForm = actorClass.getMethod("getForm");
            Object form = getForm.invoke(entity);

            if (form == null)
            {
                return null;
            }

            return readTextureLinkFromForm(form);
        }
        catch (ClassNotFoundException e)
        {
            return null;
        }
        catch (Exception e)
        {
            LOG.warn("Failed to read BBS form texture: {}", e.toString());

            return null;
        }
    }

    private static String readTextureLinkFromForm(Object form)
    {
        Class<?> cls = form.getClass();

        while (cls != null && cls != Object.class)
        {
            try
            {
                Field texField = cls.getDeclaredField("texture");
                texField.setAccessible(true);
                Object value = texField.get(form);
                String link = unwrapLink(value);

                if (link != null && !link.isEmpty())
                {
                    return link;
                }
            }
            catch (NoSuchFieldException ignored)
            {
            }
            catch (Exception e)
            {
                LOG.warn("readTextureLinkFromForm field-access failed: {}", e.toString());
            }

            cls = cls.getSuperclass();
        }

        return null;
    }

    private static String unwrapLink(Object value)
    {
        if (value == null)
        {
            return null;
        }

        Object inner = value;

        try
        {
            Method getM = value.getClass().getMethod("get");
            Object got = getM.invoke(value);

            if (got != null)
            {
                inner = got;
            }
        }
        catch (Throwable ignored)
        {
        }

        if (inner == null)
        {
            return null;
        }

        if (inner instanceof CharSequence cs)
        {
            return cs.toString();
        }

        String s = inner.toString();

        if (s != null && s.contains(":"))
        {
            return s;
        }

        try
        {
            Method getSource = inner.getClass().getMethod("getSource");
            Method getPath = inner.getClass().getMethod("getPath");
            Object src = getSource.invoke(inner);
            Object path = getPath.invoke(inner);

            if (src != null && path != null)
            {
                return src + ":" + path;
            }
        }
        catch (Throwable ignored)
        {
        }

        return s;
    }

}
