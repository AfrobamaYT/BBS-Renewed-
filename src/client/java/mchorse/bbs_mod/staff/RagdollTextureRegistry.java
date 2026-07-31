package mchorse.bbs_mod.staff;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Client-side registry that maps an entity UUID to a custom texture loaded
 * either from the staff PNG folder or from the BBS assets folder. The texture
 * override is consumed by the LivingEntityRendererTextureMixin so vanilla mob
 * renderers get the override without needing renderer subclasses.
 *
 * Ported from NeoForge BBSTools.
 */
public final class RagdollTextureRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/ragdoll-texture");

    private static final Map<UUID, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, DynamicTexture> DYNAMIC = new ConcurrentHashMap<>();

    private RagdollTextureRegistry()
    {
    }

    public static Identifier get(UUID uuid)
    {
        return uuid == null ? null : TEXTURES.get(uuid);
    }

    public static int size()
    {
        return TEXTURES.size();
    }

    public static boolean has(UUID uuid)
    {
        return uuid != null && TEXTURES.containsKey(uuid);
    }

    public static void clearAll()
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc == null)
        {
            doClearAll();
        }
        else
        {
            mc.execute(RagdollTextureRegistry::doClearAll);
        }
    }

    private static void doClearAll()
    {
        int n = DYNAMIC.size();

        for (Map.Entry<UUID, DynamicTexture> e : DYNAMIC.entrySet())
        {
            try
            {
                e.getValue().close();
            }
            catch (Throwable ignored)
            {
            }
        }

        DYNAMIC.clear();
        TEXTURES.clear();
        LOG.info("Cleared {} ragdoll textures", n);
    }

    public static void remove(UUID uuid)
    {
        if (uuid == null)
        {
            return;
        }

        TEXTURES.remove(uuid);
        DynamicTexture tex = DYNAMIC.remove(uuid);

        if (tex != null)
        {
            try
            {
                Minecraft mc = Minecraft.getInstance();

                if (mc != null)
                {
                    mc.execute(() ->
                    {
                        try
                        {
                            tex.close();
                        }
                        catch (Throwable ignored)
                        {
                        }
                    });
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    /**
     * Schedule a texture registration on the client thread. Safe to call from
     * any thread (server-thread in single-player included).
     *
     * @param uuid    entity UUID the override should apply to
     * @param bbsLink BBS texture link, e.g. {@code assets:models/Grox/villager.png}
     */
    public static void scheduleRegister(UUID uuid, String bbsLink)
    {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT)
        {
            return;
        }

        if (uuid == null || bbsLink == null || bbsLink.isEmpty())
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc == null)
        {
            return;
        }

        mc.execute(() -> registerNow(uuid, bbsLink));
    }

    /**
     * Register a texture for {@code uuid} loaded directly from an absolute
     * file path. Used by the Texture Staff item.
     */
    public static void scheduleRegisterFromFile(UUID uuid, Path file)
    {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT)
        {
            return;
        }

        if (uuid == null || file == null)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc == null)
        {
            return;
        }

        mc.execute(() -> registerNowFromFile(uuid, file));
    }

    private static void registerNowFromFile(UUID uuid, Path file)
    {
        try
        {
            if (!Files.isRegularFile(file))
            {
                LOG.warn("Texture file not found: {}", file);

                return;
            }

            NativeImage img;

            try (InputStream in = Files.newInputStream(file))
            {
                img = NativeImage.read(in);
            }

            String texId = "ragdoll/" + uuid.toString().toLowerCase(Locale.ROOT).replace('-', '_');
            Identifier id = Identifier.fromNamespaceAndPath("bbs", texId);

            /* ⚠️ 26.2: DynamicTexture(NativeImage) gibt es nicht mehr — jede Textur muss
             * einen Namen mitbringen (Supplier<String>). Der Name ist rein diagnostisch
             * (Beschriftung im GPU-Debugger und in Absturzberichten); genommen wird der
             * Bezeichner, unter dem die Textur unmittelbar danach registriert wird, damit
             * beides im Log zusammenpasst. */
            DynamicTexture tex = new DynamicTexture(id::toString, img);

            Minecraft.getInstance().getTextureManager().register(id, tex);

            DynamicTexture prev = DYNAMIC.put(uuid, tex);

            if (prev != null)
            {
                try
                {
                    prev.close();
                }
                catch (Throwable ignored)
                {
                }
            }

            TEXTURES.put(uuid, id);
            LOG.info("Registered staff texture {} -> {}", uuid, file.getFileName());
        }
        catch (Throwable t)
        {
            LOG.error("Failed to register staff texture for {} file={}", uuid, file, t);
        }
    }

    private static void registerNow(UUID uuid, String bbsLink)
    {
        try
        {
            Path file = resolveBBSPath(bbsLink);

            if (file == null || !Files.isRegularFile(file))
            {
                LOG.warn("Ragdoll texture file not found for link {} -> {}", bbsLink, file);

                return;
            }

            NativeImage img;

            try (InputStream in = Files.newInputStream(file))
            {
                img = NativeImage.read(in);
            }

            String texId = "ragdoll/" + uuid.toString().toLowerCase(Locale.ROOT).replace('-', '_');
            Identifier id = Identifier.fromNamespaceAndPath("bbs", texId);

            /* ⚠️ 26.2: DynamicTexture(NativeImage) gibt es nicht mehr — jede Textur muss
             * einen Namen mitbringen (Supplier<String>). Der Name ist rein diagnostisch
             * (Beschriftung im GPU-Debugger und in Absturzberichten); genommen wird der
             * Bezeichner, unter dem die Textur unmittelbar danach registriert wird, damit
             * beides im Log zusammenpasst. */
            DynamicTexture tex = new DynamicTexture(id::toString, img);

            Minecraft.getInstance().getTextureManager().register(id, tex);

            DynamicTexture prev = DYNAMIC.put(uuid, tex);

            if (prev != null)
            {
                try
                {
                    prev.close();
                }
                catch (Throwable ignored)
                {
                }
            }

            TEXTURES.put(uuid, id);
            LOG.info("Registered ragdoll texture {} -> {}", uuid, id);
        }
        catch (Throwable t)
        {
            LOG.error("Failed to register ragdoll texture for {} link={}", uuid, bbsLink, t);
        }
    }

    /**
     * Resolve a BBS link of the form {@code assets:models/foo/bar.png} to an
     * absolute file path under {@code <gameDir>/config/bbs/assets/models/foo/bar.png}.
     */
    private static Path resolveBBSPath(String bbsLink)
    {
        String link = bbsLink.trim();
        int colon = link.indexOf(':');
        String source;
        String rel;

        if (colon > 0)
        {
            source = link.substring(0, colon);
            rel = link.substring(colon + 1);
        }
        else
        {
            source = "assets";
            rel = link;
        }

        rel = rel.replace('\\', '/');

        if (rel.startsWith("/"))
        {
            rel = rel.substring(1);
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();

        return gameDir.resolve("config").resolve("bbs").resolve(source).resolve(rel);
    }

}
