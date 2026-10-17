package com.afrobama.releasecontrol;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Counterfactual engine startup/resource reloads with no BBS classes or mod. */
public final class VanillaBootGameTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isDevelopmentEnvironment() || loader.isModLoaded("bbs") || loader.isModLoaded("bbsdev")
            || VanillaBootGameTest.class.getClassLoader().getResource("mchorse/bbs_mod/BBSMod.class") != null)
            throw new AssertionError("Vanilla control must run in production without the BBS mod or classes");
        String minecraft = loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        if (!minecraft.equals("26.3")) throw new AssertionError("Wrong Minecraft for the native control: " + minecraft);
        String hash = sourceHash();
        String expected = System.getProperty("bbs.ci.expectedControlSha256", "");
        if (!expected.isEmpty() && !expected.equals(hash)) throw new AssertionError("Changed frozen vanilla control JAR");
        System.out.println("VANILLA_NATIVE_CONTROL_STARTED " + new com.google.gson.Gson().toJson(Map.of(
            "minecraft", minecraft, "bbsAbsent", true, "controlJarSha256", hash)));
        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> client.gui.overlay() == null);
            String renderer = context.computeOnClient(client -> GL11.glGetString(GL11.GL_RENDERER));
            String graphicsVersion = context.computeOnClient(client -> GL11.glGetString(GL11.GL_VERSION));
            if (renderer == null || graphicsVersion == null) throw new AssertionError("No actual OpenGL context in the native control");
            context.takeScreenshot("vanilla-control-before-reload");
            for (int cycle = 1; cycle <= 3; cycle++)
            {
                System.out.println("VANILLA_NATIVE_RELOAD_BEGIN " + cycle);
                CompletableFuture<Void> reload = context.computeOnClient(Minecraft::reloadResourcePacks);
                context.waitFor(client -> reload.isDone());
                reload.join();
                context.waitFor(client -> client.gui.overlay() == null);
                world.getConnection().waitForChunksRender();
                context.waitTicks(5);
                context.takeScreenshot("vanilla-control-reload-" + cycle);
                System.out.println("VANILLA_NATIVE_RELOAD_COMPLETE " + cycle);
            }
            System.out.println("VANILLA_NATIVE_CONTROL " + new com.google.gson.Gson().toJson(Map.of(
                "minecraft", minecraft, "java", System.getProperty("java.runtime.version"),
                "renderer", renderer, "graphicsVersion", graphicsVersion, "controlJarSha256", hash,
                "bbsAbsent", true, "resourceReloads", 3,
                "modIds", loader.getAllMods().stream().map(mod -> mod.getMetadata().getId()).sorted().toList())));
        }
    }

    private static String sourceHash()
    {
        try
        {
            Path source = Path.of(VanillaBootGameTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        }
        catch (Exception failure)
        {
            throw new AssertionError("Cannot identify the actual loaded vanilla control JAR", failure);
        }
    }
}
