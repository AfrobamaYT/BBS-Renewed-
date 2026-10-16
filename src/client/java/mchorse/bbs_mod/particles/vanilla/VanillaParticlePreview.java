package mchorse.bbs_mod.particles.vanilla;

import com.google.gson.JsonParser;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.particle.ParticleDescription;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static particle samples shared by the particle type picker and the form's thumbnail: one sprite
 * of the type, tinted the way vanilla's factory tints it. Opening the picker never spawns or ticks
 * world particles. From BBS FS 72705306b (Wemppy4/bbs-fs, MIT).
 */
public class VanillaParticlePreview
{
    private static final Map<Identifier, Optional<Preview>> PREVIEWS = new HashMap<>();

    public static void clearCache()
    {
        PREVIEWS.clear();
    }

    public static void render(UIContext context, Identifier particle, float x, float y, float size, int fallbackColor)
    {
        if (size <= 0 || particle == null)
        {
            return;
        }

        Preview preview = PREVIEWS.computeIfAbsent(particle, VanillaParticlePreview::loadPreview).orElse(null);

        if (preview != null && Minecraft.getInstance().getTextureManager().getTexture(preview.atlas()) instanceof TextureAtlas atlas)
        {
            /* Current UVs every time: sprites are not kept across atlas reloads. */
            TextureAtlasSprite sprite = atlas.getSprite(preview.sprite());

            context.batcher.texturedBox(() -> RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(atlas.getTextureView(), atlas.getSampler()), preview.color(),
                x, y, size, size, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), 1, 1);
        }
        else
        {
            /* Model particles and modded types without a sprite still keep their slot. */
            context.batcher.icon(Icons.PARTICLE, fallbackColor, x + size / 2F, y + size / 2F, 0.5F, 0.5F);
        }
    }

    private static Optional<Preview> loadPreview(Identifier particle)
    {
        int color = Colors.WHITE;

        if (particle.getNamespace().equals("minecraft"))
        {
            /* These use block or item models, or emit another particle, instead of owning sprites;
             * types that take an argument get a representative sample. */
            String blockSprite = switch (particle.getPath())
            {
                case "block" -> "block/stone";
                case "block_marker" -> "item/barrier";
                case "item", "item_snowball" -> "item/snowball";
                case "item_slime" -> "item/slime_ball";
                default -> null;
            };

            if (blockSprite != null)
            {
                return Optional.of(new Preview(TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace(blockSprite), color));
            }

            color = sampleColor(particle.getPath());

            if (particle.getPath().equals("explosion_emitter"))
            {
                particle = Identifier.withDefaultNamespace("explosion");
            }
            else if (particle.getPath().equals("gust_emitter"))
            {
                particle = Identifier.withDefaultNamespace("gust");
            }
        }

        Identifier definition = Identifier.fromNamespaceAndPath(particle.getNamespace(), "particles/" + particle.getPath() + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(definition);

        if (resource.isPresent())
        {
            try (Reader reader = resource.get().openAsReader())
            {
                List<Identifier> textures = ParticleDescription.fromJson(JsonParser.parseReader(reader).getAsJsonObject()).getTextures();

                if (textures != null && !textures.isEmpty())
                {
                    /* A middle frame reads even for effects that start on an empty frame. */
                    return Optional.of(new Preview(TextureAtlas.LOCATION_PARTICLES, textures.get(textures.size() / 2), color));
                }
            }
            catch (Exception e)
            {
                /* A missing or broken resource-pack definition must not keep a type from being picked. */
            }
        }

        return Optional.empty();
    }

    /** Vanilla tints these in the particle factories, not in the texture definitions. */
    private static int sampleColor(String particle)
    {
        return switch (particle)
        {
            case "dust" -> 0xFFFF3030;
            case "dust_color_transition" -> 0xFF55CCBB;
            case "falling_dust" -> 0xFFDBCAA0;
            case "smoke", "large_smoke", "ash" -> 0xFF888888;
            case "dust_plume" -> 0xFFBCA795;
            case "dripping_water", "falling_water", "dripping_dripstone_water", "falling_dripstone_water",
                "rain", "splash", "fishing" -> 0xFF4060FF;
            case "dripping_lava", "falling_lava", "landing_lava", "dripping_dripstone_lava", "falling_dripstone_lava" -> 0xFFFF6600;
            case "dripping_honey", "falling_honey", "landing_honey", "falling_nectar" -> 0xFFFFB52E;
            case "dripping_obsidian_tear", "falling_obsidian_tear", "landing_obsidian_tear" -> 0xFF8245E6;
            case "portal", "reverse_portal", "dragon_breath", "witch" -> 0xFFB34DE6;
            case "happy_villager", "composter", "totem_of_undying" -> 0xFF66CC33;
            case "sneeze" -> 0xFFB3C733;
            case "falling_spore_blossom", "spore_blossom_air" -> 0xFF519B24;
            case "crimson_spore" -> 0xFFE66666;
            case "warped_spore" -> 0xFF19B3A6;
            case "mycelium" -> 0xFFAA88AA;
            case "underwater", "dolphin" -> 0xFF6699FF;
            case "squid_ink" -> 0xFF333344;
            case "glow_squid_ink", "glow", "scrape" -> 0xFF66DDCC;
            case "wax_on" -> 0xFFFF994D;
            case "note" -> 0xFF66CC33;
            default -> Colors.WHITE;
        };
    }

    private record Preview(Identifier atlas, Identifier sprite, int color)
    {}
}
