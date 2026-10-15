// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export, villager texture-layer-stack).
package com.blenderbridge.entity;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * VillagerTextureLayers — villager texture LAYER-STACK resolver + compositor, split out of
 * {@link EntityExporter} as its own NEW class per this project's deploy-safety convention (see
 * {@code com/blenderbridge/CLAUDE.md} "DEPLOY-KLASSEN-REGEL"): {@code EntityExporter} is an
 * ALREADY-LOADED class on this W1+ JVM — adding new members to it would need a boot window even
 * though this specific JVM's DCEVM has, on a different class, tolerated hotswap-add-method in
 * practice (measured on {@code SkyReachability} — not a rule to lean on, per that class's own
 * doc). Putting all new logic here instead means {@code EntityExporter}'s only change is a BODY
 * edit of its existing {@code doExport} method (the texture-writing lines swapped for a call into
 * this class) — zero new members on the already-loaded class, this class itself is lazy-loadable,
 * no boot needed.
 *
 * <h2>GROUND TRUTH (decompiled, not guessed — task rule: "keine geratenen API-Namen")</h2>
 * <p>{@code EntityExporter}'s own class doc, written when task 4.1's grundgerüst first landed,
 * describes {@code EntityRenderer.getTexture(VillagerEntity)} as returning "the resolved BASE
 * texture identifier (biome/type-specific...)". <b>That line is WRONG</b> — decompiled straight
 * from this project's own mapped Minecraft jar ({@code cml-fresh/.gradle/loom-cache/minecraftMaven}
 * client-only artifact, yarn build.3, resolved via CFR 0.152, no guessing) via
 * {@code net.minecraft.client.render.entity.VillagerEntityRenderer}:</p>
 * <pre>
 * private static final ResourceLocation TEXTURE = ResourceLocation.ofVanilla("textures/entity/villager/villager.png");
 * public ResourceLocation getTexture(VillagerEntity villagerEntity) { return TEXTURE; }
 * </pre>
 * <p>{@code getTexture} is a HARDCODED CONSTANT — every villager, every biome, every profession,
 * returns the exact same identifier (empirically re-confirmed 2026-07-17: a live farmer/plains
 * test villager's pre-change export was byte-identical, 841 bytes, to the raw jar asset). The
 * biome-specific look (plan v2 panel H34's "type" folder) comes entirely from a SEPARATE,
 * ALWAYS-ACTIVE overlay layer, drawn by {@code net.minecraft.client.render.entity.feature.
 * VillagerClothingFeatureRenderer.render(...)} (decompiled, same jar) — reproduced here
 * field-for-field, not re-derived:</p>
 * <pre>
 * ResourceLocation typeTex = findTexture("type", Registries.VILLAGER_TYPE.getId(villagerType));        // ALWAYS drawn
 * render(base-model, typeTex, ...);
 * if (profession != VillagerProfession.NONE &amp;&amp; !entity.isBaby()) {
 *     ResourceLocation profTex = findTexture("profession", Registries.VILLAGER_PROFESSION.getId(profession));
 *     render(base-model, profTex, ...);
 *     if (profession != VillagerProfession.NITWIT) {
 *         ResourceLocation levelTex = findTexture("profession_level", LEVEL_TO_ID.get(clamp(level, 1, 5)));
 *         render(base-model, levelTex, ...);
 *     }
 * }
 * // findTexture(keyType, id) == "textures/entity/villager/" + keyType + "/" + id.getPath() + ".png"
 * // level 1..5 -&gt; stone/iron/gold/emerald/diamond (literal Int2ObjectMap in the decompiled class)
 * </pre>
 * <p>So a villager with NO job still gets base+type (never just base alone) — the OLD
 * base-texture-only export was incomplete for EVERY villager, not just employed ones. See
 * {@link #resolveLayers} for the exact reproduction of this gate logic.</p>
 *
 * <h2>WHY COMPOSITE INTO ONE PNG (not separate files + multi-material MTL)</h2>
 * <p>Chosen over "separate PNGs + MTL-doku" because the semantics genuinely match: the vanilla
 * renderer draws the SAME {@code entityModel} (identical geometry, identical UVs) once per active
 * layer, rebinding only the bound texture between draws — it is not compositing textures, it is
 * re-rendering the same mesh multiple times with alpha blending happening in the framebuffer.
 * {@link EntityExporter} captures geometry/UVs exactly ONCE (a single static bind-pose OBJ), so the
 * only way to reproduce "N draws of the same UVs with different textures, blended" on a SINGLE
 * static mesh is to blend the N textures themselves, in the SAME draw order, into the SAME UV
 * space, ahead of time — exactly the alpha-over composite this class does. Separate PNGs plus a
 * multi-material MTL would need either duplicating the mesh per layer (defeats "one static
 * export") or a shader-side multi-texture blend the plain OBJ/MTL format has no mechanism for, and
 * the addon has no importer for yet either. All 4 real vanilla layer assets were MEASURED (not
 * assumed) at 64x64 identical canvas size via {@code ImageIO} before writing this class (see task
 * dossier), so plain 0,0-aligned alpha-over is exact — no scaling/cropping logic needed.</p>
 *
 * <h2>KNOWN, DOCUMENTED BOUNDARY (not fixed here — texture-layer task only)</h2>
 * <p>The real renderer also toggles ONE geometry part's visibility per layer draw
 * ({@code ModelWithHat.setHatVisible(...)}, gated on {@code VillagerResourceMetadata.HatType} read
 * from each texture's {@code .mcmeta}) — e.g. some type/profession textures suppress the model's
 * "hat" cuboid so a different head-covering silhouette reads correctly from the same mesh's other
 * parts. {@code EntityExporter} exports ONE static geometry snapshot independent of any texture
 * layer (its existing {@code ModelPart.visible} gate, read once in bind pose) — this class only
 * changes what texture file that ONE geometry gets; it does not attempt per-layer geometry
 * swapping. Left open, not silently papered over (task scope: texture layers, not hat geometry).</p>
 */
public final class VillagerTextureLayers
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private VillagerTextureLayers()
    {
        /* static-only helper */
    }

    /**
     * Result of {@link #resolveAndComposite}: the composited image plus the ordered list of layer
     * identifiers that actually contributed (for the caller's JSON diagnostics / pixel-diff proof).
     */
    public static final class Result
    {
        public final BufferedImage image;
        public final List<String> layerIds;

        Result(BufferedImage image, List<String> layerIds)
        {
            this.image = image;
            this.layerIds = layerIds;
        }
    }

    /**
     * Resolve the ordered (bottom-to-top), gate-checked list of texture layer {@link ResourceLocation}s
     * for one entity — mirrors {@code VillagerClothingFeatureRenderer.render} exactly (class doc
     * GROUND TRUTH section): base is ALWAYS first, "type" is ALWAYS second (every villager,
     * employed or not), "profession"/"profession_level" only when the entity actually carries
     * {@link VillagerDataHolder} data AND has a real job.
     *
     * @param baseTexture the entity's {@code EntityRenderer.getTexture(...)} result (layer 0) —
     *                    passed in rather than re-resolved so non-villager callers still get a
     *                    correct single-layer (base-only) list back.
     */
    public static List<ResourceLocation> resolveLayers(LivingEntity entity, ResourceLocation baseTexture)
    {
        List<ResourceLocation> layers = new ArrayList<>();
        layers.add(baseTexture);

        if (!(entity instanceof VillagerDataHolder container))
        {
            /* Not villager-shaped data (e.g. any other SinglePartEntityModel mob) -> base only,
             * same as EntityExporter's old behaviour. Not a failure, just nothing to layer. */
            return layers;
        }

        /* 26.2: VillagerData ist ein record (type()/profession()/level() statt getX()), und
         * beide Felder sind jetzt HOLDER statt der Werte selbst -- Typ und Beruf sind
         * datengetrieben geworden, stehen also in einer dynamischen Registry und nicht mehr
         * in BuiltInRegistries. Damit faellt der Registry-Nachschlag ganz weg: der Holder
         * TRAEGT seinen Schluessel, unwrapKey().location() ist die Kennung.
         *
         * ⚠️ VillagerProfession.NONE/NITWIT sind jetzt ResourceKey, nicht mehr die Werte --
         * "profession != NONE" wuerde einen Holder mit einem Schluessel vergleichen und ist
         * deshalb gar nicht mehr uebersetzbar. holder.is(schluessel) ist der Vergleich. */
        //? if >=1.21.5 {
        /*VillagerData data = container.getVillagerData();
        Holder<VillagerProfession> beruf = data.profession();

        data.type().unwrapKey().ifPresent(k -> layers.add(overlayTexture("type", k.location())));

        if (!beruf.is(VillagerProfession.NONE) && !entity.isBaby())
        {
            beruf.unwrapKey().ifPresent(k -> layers.add(overlayTexture("profession", k.location())));

            if (!beruf.is(VillagerProfession.NITWIT))
            {
                layers.add(overlayTexture("profession_level", new ResourceLocation(levelBadgeId(data.level()))));
            }
        }
        *///?} else {
        VillagerData data = container.getVillagerData();
        VillagerProfession beruf = data.getProfession();

        layers.add(overlayTexture("type", net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType())));

        if (beruf != VillagerProfession.NONE && !entity.isBaby())
        {
            layers.add(overlayTexture("profession", net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(beruf)));

            if (beruf != VillagerProfession.NITWIT)
            {
                layers.add(overlayTexture("profession_level", new ResourceLocation(levelBadgeId(data.getLevel()))));
            }
        }
        //?}

        return layers;
    }

    /**
     * villager profession level 1..5 -> badge folder id. Literal reproduction of the decompiled
     * {@code VillagerClothingFeatureRenderer.LEVEL_TO_ID} map (an {@code Int2ObjectMap} in the
     * original; a plain switch here — same 5 entries, same clamp range {@code [1, MAX_LEVEL]}).
     */
    private static String levelBadgeId(int level)
    {
        int clamped = Mth.clamp(level, 1, VillagerData.MAX_VILLAGER_LEVEL);

        return switch (clamped)
        {
            case 1 -> "stone";
            case 2 -> "iron";
            case 3 -> "gold";
            case 4 -> "emerald";
            default -> "diamond"; /* 5 */
        };
    }

    /**
     * {@code textures/entity/villager/<keyType>/<id path>.png} — literal reproduction of the
     * decompiled {@code VillagerClothingFeatureRenderer.findTexture}, entity-type prefix hardcoded
     * to "villager" (this class is villager-specific; a future per-family adapter, per plan v2
     * task 4.1's "kein universal-walker" note, would need its own prefix here — e.g. for
     * zombie_villager, which reuses the identical type/profession/profession_level folder shape).
     */
    private static ResourceLocation overlayTexture(String keyType, ResourceLocation registryId)
    {
        return registryId.withPath(path -> "textures/entity/villager/" + keyType + "/" + path + ".png");
    }

    /**
     * Load+decode one layer as a {@link BufferedImage} (not a raw byte copy — {@link EntityExporter}'s
     * old {@code extractTexture} copied bytes directly because there was only ever one file;
     * compositing needs decoded pixels). Missing/corrupt resource -> {@code null} (logged), never a
     * thrown exception — mirrors the rest of this package's "skip, don't crash" texture handling
     * (e.g. {@code EntityExporter.extractTexture}'s own resource-missing branch).
     */
    private static BufferedImage loadLayerImage(Minecraft client, ResourceLocation textureId)
    {
        try
        {
            Optional<Resource> resource = client.getResourceManager().getResource(textureId);

            if (resource.isEmpty())
            {
                LOGGER.warn("[blenderbridge] villager texture layer not found, skipping: {}", textureId);
                return null;
            }

            InputStream stream = resource.get().open();

            try
            {
                return ImageIO.read(stream);
            }
            finally
            {
                stream.close();
            }
        }
        catch (IOException e)
        {
            LOGGER.warn("[blenderbridge] villager texture layer could not be read, skipping {}: {}", textureId, e.toString());
            return null;
        }
    }

    /**
     * Alpha-over composite, bottom-to-top, onto a fresh {@code TYPE_INT_ARGB} canvas sized to the
     * FIRST image (class doc: all 4 real vanilla layer assets measured 64x64, identical canvas — no
     * scaling attempted; a hypothetical mismatched layer just draws at 0,0 same as the others,
     * clipped/left-transparent by {@link Graphics2D} rather than crashing).
     */
    private static BufferedImage compositeOver(List<BufferedImage> layersBottomToTop)
    {
        BufferedImage first = layersBottomToTop.get(0);
        BufferedImage out = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        try
        {
            g.setComposite(AlphaComposite.SrcOver);

            for (BufferedImage layer : layersBottomToTop)
            {
                g.drawImage(layer, 0, 0, null);
            }
        }
        finally
        {
            g.dispose();
        }

        return out;
    }

    /**
     * Public entry point: resolve this entity's active layers (class doc GROUND TRUTH gates),
     * load+decode each, alpha-composite them in order, and return both the image and the list of
     * layer ids that actually loaded (for the caller's JSON diagnostics). Returns {@code null} only
     * if EVERY layer failed to load (including the base) — a partial layer failure just logs a
     * warning and continues with what did load (same "skip, don't crash" convention as the rest of
     * this package). {@code images.size() == 1} (base-only, e.g. a non-villager or a fully-missing
     * layer set) skips the composite step entirely and returns the decoded base image as-is.
     */
    public static Result resolveAndComposite(Minecraft client, LivingEntity entity, ResourceLocation baseTexture)
    {
        List<ResourceLocation> layerIds = resolveLayers(entity, baseTexture);
        List<BufferedImage> images = new ArrayList<>();
        List<String> loadedIds = new ArrayList<>();

        for (ResourceLocation id : layerIds)
        {
            BufferedImage image = loadLayerImage(client, id);

            if (image == null)
            {
                continue;
            }

            images.add(image);
            loadedIds.add(id.toString());
        }

        if (images.isEmpty())
        {
            return null;
        }

        BufferedImage composite = images.size() == 1 ? images.get(0) : compositeOver(images);

        return new Result(composite, loadedIds);
    }
}
