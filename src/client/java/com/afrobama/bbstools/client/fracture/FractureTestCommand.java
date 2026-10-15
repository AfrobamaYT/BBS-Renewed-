package com.afrobama.bbstools.client.fracture;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/**
 * Debug command {@code /bbstools fracture-test} — exercises the
 * {@link FracturePhysicsBridge} reflection chain with synthetic data
 * (a simple 8-vertex cube hull) so we can verify the PhysicsMod-Pro
 * spawn pipeline works on the user's runtime BEFORE attempting a
 * full BOBJ-bone fracture.
 *
 * <p>What it does:</p>
 * <ol>
 *   <li>Logs whether the bridge initialised successfully</li>
 *   <li>Spawns a 1×1×1 convex cube at the player's eye position with
 *       upward initial velocity</li>
 *   <li>Reports the spawn handle (or null on failure) in chat</li>
 *   <li>The body persists per the standard PhysicsMod lifecycle —
 *       gravity pulls it down, it physics-collides with the world</li>
 * </ol>
 *
 * <p>If the spawn returns null, check the BBSTools log for
 * {@code spawnConvex failed: ...} or {@code FracturePhysicsBridge init
 * failed} — those messages identify which reflection step is broken
 * and let us iterate on the bridge's reflection paths.</p>
 *
 * <p>This is a Phase-0 acceptance test. Once a visible cube spawns
 * here, the per-bone fracture pipeline (Phase 1+) should work using
 * the same bridge.</p>
 */
public final class FractureTestCommand
{
    private FractureTestCommand()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("bbstools")
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fracture-test")
                .executes(ctx -> {
                    runTest(ctx.getSource());
                    return 1;
                }))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fracture-debug")
                .executes(ctx -> {
                    runDebug(ctx.getSource());
                    return 1;
                }))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fracture-clear")
                .executes(ctx -> {
                    com.afrobama.bbstools.client.fracture.FractureRegistry.clearAll();
                    com.afrobama.bbstools.client.fracture.BonePhysicsSimulator.clearAll();
                    com.afrobama.bbstools.client.fracture.CollisionWatcher.clearAll();
                    com.afrobama.bbstools.client.fracture.VelocityDropWatcher.clearAll();
                    com.afrobama.bbstools.client.fracture.FracturedBoneRegistry.clearAll();
                    com.afrobama.bbstools.client.fracture.BlockShatterPropagator.clearAll();
                    int restored = com.afrobama.bbstools.client.fracture.BlockSnapshotRegistry.restoreAll();
                    ctx.getSource().sendFeedback(Component.literal("§aAll fracture state cleared (" + restored + " blocks restored)"));
                    return 1;
                }))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fracture-restore-blocks")
                .executes(ctx -> {
                    int restored = com.afrobama.bbstools.client.fracture.BlockSnapshotRegistry.restoreAll();
                    ctx.getSource().sendFeedback(Component.literal("§a" + restored + " blocks restored"));
                    return 1;
                }))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fracture-help")
                .executes(ctx -> {
                    runHelp(ctx.getSource());
                    return 1;
                }))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("verbose-chat")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("on")
                    .executes(ctx -> {
                        com.afrobama.bbstools.perf.BBSPerfConfig.VERBOSE_DESTRUCTION_CHAT = true;
                        ctx.getSource().sendFeedback(Component.literal("§a[BBSTools] §fVerbose destruction chat: §aON §7(per-fire diagnostics will print to chat)"));
                        return 1;
                    }))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("off")
                    .executes(ctx -> {
                        com.afrobama.bbstools.perf.BBSPerfConfig.VERBOSE_DESTRUCTION_CHAT = false;
                        ctx.getSource().sendFeedback(Component.literal("§a[BBSTools] §fVerbose destruction chat: §cOFF §7(silent during recording, log file still has full diagnostics)"));
                        return 1;
                    })))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("editorcam")
                .executes(ctx -> {
                    runEditorCamInspect(ctx.getSource());
                    return 1;
                }))
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("add-camera-replay")
                .executes(ctx -> {
                    runAddCameraReplay(ctx.getSource());
                    return 1;
                })));
    }

    /**
     * Create a "camera-style" replay in the active film: regular replay
     * minus the actor/form attachment. Position seeded from the player's
     * current view. After this the user can author the keyframes
     * normally OR attach a tracker clip; F8 (editor-cam) will pick it
     * up immediately because EditorCamHandler.isActorReplay() returns
     * false for actor=false + form=null.
     */
    private static void runAddCameraReplay(FabricClientCommandSource src)
    {
        try
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.player == null)
            {
                sendChat(src, "§c[BBSTools] No player — can't read camera position.");
                return;
            }

            java.util.List<mchorse.bbs_mod.film.BaseFilmController> ctls =
                mchorse.bbs_mod.BBSModClient.getFilms().getControllers();
            if (ctls == null || ctls.isEmpty())
            {
                sendChat(src, "§c[BBSTools] No active film — open a film first.");
                return;
            }
            mchorse.bbs_mod.film.BaseFilmController fc = ctls.get(ctls.size() - 1);
            if (fc.film == null)
            {
                sendChat(src, "§c[BBSTools] Active film controller has no film loaded.");
                return;
            }

            mchorse.bbs_mod.film.replays.Replay r = fc.film.replays.addReplay();
            if (r == null)
            {
                sendChat(src, "§c[BBSTools] film.replays.addReplay returned null — BBS API might have changed.");
                return;
            }

            /* Seed keyframes at tick 0 from the player's current view.
             *  Yaw flipped by 180° to match BBS's convention (the
             *  built-in addReplay does the same). */
            double px = mc.player.getX();
            double py = mc.player.getY() + mc.player.getEyeHeight();
            double pz = mc.player.getZ();
            float pitch = mc.player.getXRot();
            float yaw = mc.player.getYRot() + 180F;

            r.keyframes.x.insert(0F, px);
            r.keyframes.y.insert(0F, py);
            r.keyframes.z.insert(0F, pz);
            r.keyframes.pitch.insert(0F, (double) pitch);
            r.keyframes.yaw.insert(0F, (double) yaw);
            r.keyframes.headYaw.insert(0F, (double) yaw);
            r.keyframes.bodyYaw.insert(0F, (double) yaw);

            /* Mark as camera-style: actor=false, form remains
             *  uninitialized (null/empty) so EditorCamHandler treats
             *  it as a "camera replay" by default. */
            r.actor.set(false);

            /* Give the replay a memorable default name so F8 cycle
             *  output lists it usefully. User can rename in the
             *  replay editor sidebar. */
            try
            {
                int nth = fc.film.replays.getList().size();
                r.label.set("camera_" + nth);
            }
            catch (Throwable ignored) {}

            sendChat(src, "§a[BBSTools] §fCamera-replay '§e" + r.getName() + "§f' created at your current view.");
            sendChat(src, "§7Press §fF8§7 to cycle to it. Edit its keyframes in the replay sidebar to move/animate the camera.");
        }
        catch (Throwable t)
        {
            sendChat(src, "§c[BBSTools] add-camera-replay failed: " + t);
        }
    }

    private static void runEditorCamInspect(FabricClientCommandSource src)
    {
        boolean active = com.afrobama.bbstools.client.editorcam.EditorCamHandler.isActive();
        int cycleIdx = com.afrobama.bbstools.client.editorcam.EditorCamHandler.getCycleIndex();
        com.afrobama.bbstools.client.editorcam.EditorCamController ctrl =
            com.afrobama.bbstools.client.editorcam.EditorCamHandler.getController();
        mchorse.bbs_mod.film.replays.Replay activeReplay = ctrl == null ? null : ctrl.getReplay();

        sendChat(src, "§e=== Editor-cam state ===");
        sendChat(src, "§7active = §f" + (active ? "§ayes" : "§cno (player view)"));
        sendChat(src, "§7cycleIndex = §f" + cycleIdx);
        if (activeReplay != null)
        {
            String name;
            try { name = activeReplay.getName(); }
            catch (Throwable t) { name = "(?)"; }
            sendChat(src, "§7activeReplay = §a" + (name == null || name.isEmpty() ? "(unnamed)" : name));
        }

        try
        {
            java.util.List<mchorse.bbs_mod.film.BaseFilmController> ctls = mchorse.bbs_mod.BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                mchorse.bbs_mod.film.BaseFilmController fc = ctls.get(ctls.size() - 1);
                if (fc.film != null)
                {
                    java.util.List<mchorse.bbs_mod.film.replays.Replay> replays = fc.film.replays.getList();
                    sendChat(src, "§7replays in current film = §f" + replays.size());
                    int i = 0;
                    for (mchorse.bbs_mod.film.replays.Replay r : replays)
                    {
                        if (r == null) continue;
                        String nm;
                        try { nm = r.getName(); }
                        catch (Throwable t) { nm = "(?)"; }

                        boolean isActor = false;
                        try { isActor = Boolean.TRUE.equals(r.actor.get()) || (r.form != null && r.form.get() != null); }
                        catch (Throwable ignored) {}

                        String tag = isActor ? "§7[actor]" : "§a[camera]";
                        sendChat(src, "  §7[" + i + "] " + tag + " §f" + (nm == null || nm.isEmpty() ? "(unnamed)" : nm));
                        i++;
                    }
                }
                else
                {
                    sendChat(src, "§c(no film loaded in latest controller)");
                }
            }
            else
            {
                sendChat(src, "§c(no film controllers active)");
            }
        }
        catch (Throwable t)
        {
            sendChat(src, "§cReplay enumeration failed: " + t);
        }

        sendChat(src, "§7Hint: §fF8 §7cycles cameras, §fShift+F8 §7cycles all replays");
    }

    private static void runHelp(FabricClientCommandSource src)
    {
        sendChat(src, "§e=== BBSTools Fracture Help ===");
        sendChat(src, "§7Three action clip types are available for cinematic destruction:");
        sendChat(src, "");
        sendChat(src, "§e§l1) bbs:model_fracture§r §7— shatter a BBS actor's BOBJ model");
        sendChat(src, "§7Trigger modes: manual, on_collision, on_velocity_drop");
        sendChat(src, "§7Key fields: pieces per bone (1–30), joint stiffness (ragdoll mode 0–1),");
        sendChat(src, "§7  slow motion (0.05–3.0), block shatter, impact propagation, camera shake,");
        sendChat(src, "§7  destroy blocks (singleplayer), force jitter, lifetime, gravity, damping");
        sendChat(src, "");
        sendChat(src, "§e§l2) bbs:physics_block_destruction§r §7— destroy a region of MC blocks");
        sendChat(src, "§7Mark Min/Max corners + impact point via 'Set from looked-at' buttons.");
        sendChat(src, "§7Force fields: scatter force (0–10), upward force, direction X/Y/Z,");
        sendChat(src, "§7  scatter jitter, gravity bias. Wave: speed, max radius, chunks per block.");
        sendChat(src, "§7Use cases: tree splinters, ground katapult, directional wall blast.");
        sendChat(src, "");
        sendChat(src, "§e§l3) bbs:model_ragdoll_proxy§r §7— PhysicsMod entity-proxy ragdoll");
        sendChat(src, "§7Spawns invisible villager → killed → BBS actor inherits ragdoll motion.");
        sendChat(src, "§7Bone aliases: head/body/arm_left/arm_right/leg_left/leg_right.");
        sendChat(src, "§7Singleplayer only. Pair with model_fracture for hybrid effect.");
        sendChat(src, "");
        sendChat(src, "§e§lGravity-gun controls§r");
        sendChat(src, "§7  §fG§7 — grab fracture body within 16 blocks of crosshair");
        sendChat(src, "§7  §fG§7 + scroll wheel — pull body closer / push away (1.5–16m)");
        sendChat(src, "§7  Release §fG§7 — throw forward at 2.5 blocks/tick");
        sendChat(src, "");
        sendChat(src, "§e§lEditor camera (F8)§r");
        sendChat(src, "§7  §fF8§7 — cycle through camera-style replays in current film");
        sendChat(src, "§7  §fShift+F8§7 — cycle through ALL replays (incl. actors)");
        sendChat(src, "§7  Use a tracker clip on the chosen replay to follow a moving target.");
        sendChat(src, "");
        sendChat(src, "§e§lCommands§r");
        sendChat(src, "§7  §f/bbstools fracture-debug§7 — live state + counts");
        sendChat(src, "§7  §f/bbstools fracture-clear§7 — kill all fractures + restore blocks");
        sendChat(src, "§7  §f/bbstools fracture-restore-blocks§7 — restore destroyed blocks only");
        sendChat(src, "§7  §f/bbstools fracture-test§7 — sanity-spawn a stone debris cube");
        sendChat(src, "§7  §f/bbstools editorcam§7 — list replays + active F8 cycle state");
        sendChat(src, "§7  §f/bbstools debug-everything§7 — full subsystem dump (fracture / physics / settings)");
        sendChat(src, "§7  §f/bbstools verbose-chat on|off§7 — toggle per-fire chat diagnostics");
        sendChat(src, "");
        sendChat(src, "§7See §fFRACTURE_PRESETS.md§7 in the BBSTools repo for 16 ready-to-copy recipes.");
    }

    private static void runDebug(FabricClientCommandSource src)
    {
        sendChat(src, "§e=== BBSTools Fracture Debug ===");
        sendChat(src, "§7Bridge available: " + (com.afrobama.bbstools.client.fracture.FracturePhysicsBridge.isAvailable() ? "§ayes" : "§cno (PhysicsMod missing)"));
        sendChat(src, "§7PhysicsMod smoke available: " + (com.afrobama.bbstools.client.fracture.FracturePhysicsBridge.isSmokeAvailable() ? "§ayes" : "§cno"));
        sendChat(src, "§7Live PhysicsMod bodies: §f" + com.afrobama.bbstools.client.fracture.FracturePhysicsBridge.liveBodyCount());
        sendChat(src, "§7Phase-B bone bodies: §f" + com.afrobama.bbstools.client.fracture.BonePhysicsSimulator.activeBodyCount());
        sendChat(src, "§7Active fractures (actors): §f" + com.afrobama.bbstools.client.fracture.FractureRegistry.activeFractureCount());
        sendChat(src, "§7Hidden bones (Phase-A legacy): §f" + com.afrobama.bbstools.client.fracture.FracturedBoneRegistry.activeActorCount());
        sendChat(src, "§7Active CollisionWatcher: §f" + com.afrobama.bbstools.client.fracture.CollisionWatcher.activeWatcherCount());
        sendChat(src, "§7Active VelocityDropWatcher: §f" + com.afrobama.bbstools.client.fracture.VelocityDropWatcher.activeWatcherCount());
        sendChat(src, "§7Active block-shatter waves: §f" + com.afrobama.bbstools.client.fracture.BlockShatterPropagator.activeEventCount());
        sendChat(src, "§7Snapshotted blocks: §f" + com.afrobama.bbstools.client.fracture.BlockSnapshotRegistry.snapshotCount());
        sendChat(src, "§7Gravity-gun holding: " + (com.afrobama.bbstools.client.fracture.GravityGunHandler.isHolding() ? "§ayes" : "§7no"));
        sendChat(src, "§7PhysicsMod ragdolls (grabbable): §f" + com.afrobama.bbstools.client.ragdoll.PhysicsRagdollRegistry.size());
    }

    private static void runTest(FabricClientCommandSource src)
    {
        boolean available = FracturePhysicsBridge.isAvailable();
        sendChat(src, available
            ? "§aFracturePhysicsBridge initialised — attempting spawn"
            : "§cFracturePhysicsBridge unavailable. Check log for init failure reason.");

        if (!available) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
        {
            sendChat(src, "§cNo player entity — run inside a world");
            return;
        }

        double px = mc.player.getX();
        double py = mc.player.getY() + mc.player.getEyeHeight();
        double pz = mc.player.getZ();

        /* Synthetic cube vertices — 8 corners of a unit cube around the
         *  origin. PhysX cooks this into a convex hull, ConvexRigidBody
         *  wraps it, the world gets a falling cube. */
        Vector3f[] cube = new Vector3f[]{
            new Vector3f(-0.5F, -0.5F, -0.5F),
            new Vector3f( 0.5F, -0.5F, -0.5F),
            new Vector3f(-0.5F,  0.5F, -0.5F),
            new Vector3f( 0.5F,  0.5F, -0.5F),
            new Vector3f(-0.5F, -0.5F,  0.5F),
            new Vector3f( 0.5F, -0.5F,  0.5F),
            new Vector3f(-0.5F,  0.5F,  0.5F),
            new Vector3f( 0.5F,  0.5F,  0.5F),
        };

        Vector3f initialVelocity = new Vector3f(0F, 0.6F, 0F); /* upward kick */

        /* SANITY TEST 1: simplest possible spawn — addBlockParticleBox
         *  with a stone-block PhysicsEntity. PhysicsMod auto-builds the
         *  visual cube using stone's block model. If THIS is invisible,
         *  the problem is bigger than our mesh-data setup. */
        Object boxHandle = FracturePhysicsBridge.spawnSimpleBox(px, py + 2D, pz);
        if (boxHandle == null)
        {
            sendChat(src, "§cspawnSimpleBox returned null — base render path broken");
        }
        else
        {
            sendChat(src, "§aSimple stone-box spawned at (" + (int) px + ", " + (int) (py + 2) + ", " + (int) pz + ")");
        }

        /* SANITY TEST 2: convex hull from raw vertices — what we
         *  ultimately need for per-bone fracture. Currently visible
         *  rendering depends on PhysicsMod auto-creating Models from
         *  the meshes which it may or may not do without UV/normal data. */
        Object convexHandle = FracturePhysicsBridge.spawnConvex(
            cube, px, py + 1D, pz,
            new org.joml.Quaternionf(),
            initialVelocity);

        if (convexHandle == null)
        {
            sendChat(src, "§cspawnConvex returned null — check log for spawnConvex failure");
        }
        else
        {
            sendChat(src, "§aConvex hull spawned at (" + (int) px + ", " + (int) (py + 1) + ", " + (int) pz + ")");
        }

        sendChat(src, "§7Live bodies: " + FracturePhysicsBridge.liveBodyCount());
    }

    private static void sendChat(FabricClientCommandSource src, String msg)
    {
        src.sendFeedback(Component.literal(msg));
    }

}
