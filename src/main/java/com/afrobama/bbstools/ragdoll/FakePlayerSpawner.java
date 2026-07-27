// Coded by Afrobama/Claude — spawns ephemeral fake ServerPlayerEntity
// for /ragdollswap Player so PhysicsMod takes the real player-ragdoll
// path with arms-down humanoid pose + BBS skin override.
package com.afrobama.bbstools.ragdoll;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Spawns a transient {@link ServerPlayerEntity} backed by BBS's
 * {@link mchorse.bbs_mod.actions.SuperFakePlayerNetworkHandler} stub
 * so PhysicsMod's player-ragdoll path (correct arms-down humanoid
 * pose) kicks in on the next death hook.
 *
 * <p>Why we don't use {@code PlayerManager.onPlayerConnect}: that
 * path is built for real client connections; it sends packets via
 * the {@code ClientConnection} we'd have to fake, and several call
 * sites assume a non-null Channel. We instead:</p>
 * <ol>
 *   <li>Construct the {@code ServerPlayerEntity} directly,</li>
 *   <li>Assign BBS's pre-existing no-op {@code SuperFakePlayer-
 *       NetworkHandler} so {@code networkHandler.send(...)} stops
 *       being a death trap,</li>
 *   <li>Add the player via {@code world.spawnEntity} (in 1.21 this
 *       dispatches to {@code ServerWorld#addPlayer} for
 *       {@code ServerPlayerEntity} subclasses),</li>
 *   <li>Manually broadcast a {@link PlayerListS2CPacket} so every
 *       client adds the profile to their player list — this is what
 *       wires the {@code PlayerEntityRenderer} skin pipeline (even
 *       though we override the texture via
 *       {@code LivingEntityRendererTextureMixin}, the player still
 *       needs to be in the player-list for the renderer to
 *       construct itself correctly).</li>
 * </ol>
 *
 * <p>The same {@code RagdollTextureRegistry} → {@code LivingEntity-
 * RendererTextureMixin} path that works for mob ragdolls works here
 * too, because {@code PlayerEntityRenderer extends LivingEntity-
 * Renderer} and the mixin redirects {@code getTexture(entity)} for
 * every subclass.</p>
 */
public final class FakePlayerSpawner
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/fake-player-spawner");

    private static int spawnCounter;

    private FakePlayerSpawner()
    {
    }

    public static ServerPlayerEntity spawn(ServerWorld world,
                                           double x,
                                           double y,
                                           double z,
                                           float yaw,
                                           float pitch,
                                           String displayName)
    {
        MinecraftServer server = world.getServer();
        if (server == null)
        {
            LOG.warn("Cannot spawn fake player — server is null");
            return null;
        }

        spawnCounter++;
        String safeName = (displayName == null || displayName.isBlank()) ? "Ragdoll" : displayName;
        if (safeName.length() > 14) safeName = safeName.substring(0, 14);
        safeName = safeName + spawnCounter;

        GameProfile profile = new GameProfile(UUID.randomUUID(), safeName);
        SyncedClientOptions options = SyncedClientOptions.createDefault();

        /* Step 1: construct the player. Failure here means signature
         *  mismatch or BBS class missing — bail cleanly. */
        ServerPlayerEntity player;
        try
        {
            player = new ServerPlayerEntity(server, world, profile, options);
            LOG.info("Constructed ServerPlayerEntity '{}' OK", safeName);
        }
        catch (Throwable t)
        {
            LOG.warn("Step 1/4: ServerPlayerEntity constructor failed", t);
            return null;
        }

        player.refreshPositionAndAngles(x, y, z, yaw, pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        /* Step 2: install BBS's no-op network handler. This is what
         *  every other fake-player consumer in BBS uses. If we leave
         *  networkHandler null and let the player tick, the first
         *  packet send NPEs. */
        try
        {
            player.networkHandler = new mchorse.bbs_mod.actions.SuperFakePlayerNetworkHandler(player);
            LOG.info("Step 2/4: SuperFakePlayerNetworkHandler installed");
        }
        catch (Throwable t)
        {
            LOG.warn("Step 2/4: could not install BBS network handler — fake player would NPE on first tick. Bailing.", t);
            return null;
        }

        /* Step 3: physically add the player to the world.
         *  ServerWorld.addEntity in 1.21 special-cases
         *  ServerPlayerEntity and routes it through addPlayer. */
        boolean spawnedOk;
        try
        {
            spawnedOk = world.spawnEntity(player);
            LOG.info("Step 3/4: world.spawnEntity returned {}", spawnedOk);
        }
        catch (Throwable t)
        {
            LOG.warn("Step 3/4: world.spawnEntity threw — fake player not in world. Bailing.", t);
            return null;
        }

        if (!spawnedOk)
        {
            LOG.warn("Step 3/4: world.spawnEntity returned false — fake player rejected by world. Bailing.");
            return null;
        }

        /* Step 4a: broadcast PlayerListS2CPacket so every connected
         *  client adds this profile to their player list. Without
         *  this, clients won't construct a PlayerEntityRenderer for
         *  the entity and may render it with the default-fallback
         *  rendering instead. */
        try
        {
            PlayerListS2CPacket addPacket = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER),
                List.of(player)
            );
            server.getPlayerManager().sendToAll(addPacket);
            LOG.info("Step 4a/5: PlayerListS2CPacket(ADD_PLAYER) broadcast");
        }
        catch (Throwable t)
        {
            LOG.warn("Step 4a/5: could not broadcast PlayerListPacket", t);
        }

        /* Step 4b: register the player in {@link ServerWorld#players}
         *  via {@code onPlayerConnected}. Without this the player is
         *  in the entity manager but ServerWorld's player-specific
         *  iteration paths skip it. Reflection-based because
         *  {@code onPlayerConnected} is the public method that wires
         *  the chunk-loading bookkeeping. */
        try
        {
            world.onPlayerConnected(player);
            LOG.info("Step 4b/5: world.onPlayerConnected wired up");
        }
        catch (Throwable t)
        {
            LOG.warn("Step 4b/5: world.onPlayerConnected failed — chunk tracker may not know about fake player", t);
        }

        /* Step 5: manually broadcast EntitySpawnS2CPacket so every
         *  client constructs the entity in their world. {@code
         *  world.spawnEntity} for {@code ServerPlayerEntity} returns
         *  true (entity is in the server-side entity manager) but
         *  the chunk-tracker only auto-broadcasts spawn packets for
         *  normal entities — players are expected to arrive via
         *  {@code PlayerManager.onPlayerConnect}. So we send the
         *  packet ourselves. Without this step the fake player is
         *  invisible client-side and PhysicsMod's render-hook never
         *  fires, leaving no ragdoll. THIS is the missing link. */
        try
        {
            net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket spawnPacket =
                new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(
                    player.getId(),
                    player.getUuid(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getPitch(),
                    player.getYaw(),
                    player.getType(),
                    0,
                    player.getVelocity(),
                    player.getHeadYaw());
            server.getPlayerManager().sendToAll(spawnPacket);
            LOG.info("Step 5/5: EntitySpawnS2CPacket broadcast — fake player now visible to clients");
        }
        catch (Throwable t)
        {
            LOG.warn("Step 5/5: could not broadcast EntitySpawnS2CPacket — fake player will be invisible to clients", t);
        }

        /* Final touches that make kill() actually take effect:
         *  - SURVIVAL game mode (creative would ignore most damage)
         *  - explicit setInvulnerable(false). kill()'s outOfWorld
         *    damage source bypasses invulnerability anyway, but being
         *    explicit avoids any third-party mixin treating it
         *    differently. */
        try
        {
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        }
        catch (Throwable t)
        {
            LOG.debug("changeGameMode SURVIVAL failed (ignored)", t);
        }
        player.setInvulnerable(false);

        LOG.info("Fake player '{}' fully spawned at {},{},{}", safeName, x, y, z);
        return player;
    }

    /**
     * Discard a fake player after PhysicsMod has captured the death.
     * Players don't auto-remove on death (vanilla waits for the
     * client's respawn-confirm packet). We force the removal so the
     * dead-pose body doesn't linger.
     */
    public static void discardSilently(ServerPlayerEntity player)
    {
        if (player == null) return;
        try
        {
            /* In 1.20.2+ player removal uses a separate packet from
             *  the PlayerListS2CPacket additive ones. Broadcast it so
             *  clients drop the profile from their player list — keeps
             *  the lingering "ghost" entries out of subsequent ragdoll
             *  rendering. */
            try
            {
                if (player.getServer() != null)
                {
                    net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket removePacket =
                        new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(List.of(player.getUuid()));
                    player.getServer().getPlayerManager().sendToAll(removePacket);
                }
            }
            catch (Throwable t)
            {
                LOG.debug("Could not broadcast PlayerRemoveS2CPacket for {}: {}", player.getUuid(), t.toString());
            }

            player.discard();
        }
        catch (Throwable t)
        {
            LOG.debug("discardSilently failed for {}: {}", player.getName().getString(), t.toString());
        }
    }

}
