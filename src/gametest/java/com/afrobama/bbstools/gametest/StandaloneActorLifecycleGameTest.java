package com.afrobama.bbstools.gametest;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Cold standalone world entity, resource reloads and removal; inspect the actual captured pixels. */
public final class StandaloneActorLifecycleGameTest implements FabricClientGameTest
{
    private static final String MODEL = "player/steve_bends_3d";

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client ->
            {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.player.setYRot(0F);
                client.player.setYHeadRot(0F);
                client.player.setXRot(0F);
            });
            int entityId = world.getServer().computeOnServer(server ->
            {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ActorEntity actor = new ActorEntity(BBSMod.ACTOR_ENTITY, level);
                ModelForm form = new ModelForm();
                form.model.set(MODEL);
                actor.setForm(form);
                actor.setPos(player.getX(), player.getY(), player.getZ() + 5D);
                actor.setYRot(180F);
                actor.setYBodyRot(180F);
                actor.setYHeadRot(180F);
                actor.setNoGravity(true);
                if (!level.addFreshEntity(actor)) throw new AssertionError("Cannot spawn the actual standalone Actor");
                return actor.getId();
            });
            context.waitFor(client -> client.level.getEntity(entityId) instanceof ActorEntity actor
                && actor.getForm() instanceof ModelForm form && form.model.get().equals(MODEL));
            context.waitTicks(20);
            Map<String, Object> before = context.computeOnClient(client -> state(client, entityId));
            context.takeScreenshot("standalone-actor-before-reload");
            List<Map<String, Object>> afterReloads = new ArrayList<>();
            for (int cycle = 1; cycle <= 3; cycle++)
            {
                CompletableFuture<Void> reload = context.computeOnClient(Minecraft::reloadResourcePacks);
                context.waitFor(client -> reload.isDone());
                reload.join();
                context.waitTicks(10);
                Map<String, Object> after = context.computeOnClient(client -> state(client, entityId));
                if (!before.get("uuid").equals(after.get("uuid")) || !before.get("position").equals(after.get("position")))
                    throw new AssertionError("Resource reload replaced or displaced the standalone Actor");
                afterReloads.add(after);
                context.takeScreenshot("standalone-actor-reload-" + cycle);
            }
            world.getServer().runOnServer(server ->
            {
                Entity actor = server.overworld().getEntity(entityId);
                if (!(actor instanceof ActorEntity)) throw new AssertionError("Standalone Actor disappeared on the server");
                actor.discard();
            });
            context.waitFor(client -> client.level.getEntity(entityId) == null);
            context.waitTicks(10);
            context.takeScreenshot("standalone-actor-removed");
            System.out.println("BBS_STANDALONE_ACTOR_LIFECYCLE " + new com.google.gson.Gson().toJson(
                Map.of("before", before, "afterReloads", afterReloads, "removed", true,
                    "visualAcceptance", "requires inspection of the captured images")));
        }
    }

    private static Map<String, Object> state(Minecraft client, int id)
    {
        Entity entity = client.level.getEntity(id);
        if (!(entity instanceof ActorEntity actor) || !(actor.getForm() instanceof ModelForm form)
            || !form.model.get().equals(MODEL) || actor.isInvisible())
            throw new AssertionError("Expected the synchronized visible standalone Actor ModelForm");
        return Map.of("entityId", id, "uuid", actor.getUUID().toString(), "model", form.model.get(),
            "position", List.of(actor.getX(), actor.getY(), actor.getZ()), "ticks", actor.tickCount);
    }
}
