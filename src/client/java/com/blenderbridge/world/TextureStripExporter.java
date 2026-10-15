// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 2 world export).
package com.blenderbridge.world;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

/**
 * TextureStripExporter — for animated block sprites (water, lava, fire, ...), exports the FULL
 * animation strip PNG (all frames) plus its timing, next to the single-frame crop
 * {@link WorldExporter#extractTextures} already writes.
 *
 * <p>WHY A SEPARATE CLASS: {@link WorldExporter} is a GELADENE (already loaded) class in the
 * running game — new fields/methods there need a reboot (see the dossier's "canAddMethod=false"
 * note + the {@code <clinit>} trap). This class is new, so it lazy-loads from the dev classpath;
 * {@link WorldExporter#writeWorldMeta} only needs a body edit that calls in here. Same pattern as
 * {@link RegionClamp} / {@link SkyReachability}.</p>
 *
 * <p>HOW "IS THIS SPRITE ANIMATED" IS DECIDED — pragmatically, via the RESOURCE, not the baked
 * {@code Sprite}: {@code SpriteContents}' animation field is package-private with no public
 * accessor in 1.21.1 (same wall {@link WorldExporter#extractTextures}'s class-doc already hit for
 * pixels). But the animation metadata is just the sprite's OWN {@code .mcmeta} companion file, and
 * that we CAN read straight from the resource manager: {@code textures/block/water_still.png.mcmeta}
 * decodes via {@link AnimationMetadataSection#SERIALIZER}. A sprite with no {@code .mcmeta} resource,
 * or one whose {@code .mcmeta} carries no {@code "animation"} key, is not animated — the
 * {@link Optional} from {@link ResourceMetadata#getSection} comes back empty in both cases, so both
 * "no file" and "file without animation section" fall through to the same "not animated" branch
 * with no separate existence check needed.</p>
 *
 * <p>FRAME COUNT — read off the STRIP FILE ITSELF: an animated sprite's PNG on disk is a vertical
 * stack of {@code frameWidth x frameHeight} frames ({@code water_still.png} is 16x512 = 32 frames),
 * exactly what {@link WorldExporter#extractTextures}'s crop already relies on. Frame height divides
 * the strip height evenly by construction (vanilla format), so {@code stripHeight / frameHeight} is
 * exact and needs no help from {@link AnimationMetadataSection#forEachFrame} — that describes a
 * PLAYBACK order/duration-per-frame sequence that can revisit frames, not the frame count on disk.
 * {@code forEachFrame} IS used, separately, for {@link AnimatedTexture#order} (schema addition
 * 2026-07-17) — the two are deliberately independent sources so a ping-pong sprite's {@code frames}
 * stays the plain strip count while {@code order} carries the full resolved playback sequence.</p>
 *
 * <p>THE STRIP FILE ITSELF is a plain byte copy of the resource (same {@code Files.copy} used by
 * the untinted-crop fallback below it) -- no re-encode, no pixel walk, because unlike the single
 * frame crop we want EVERY pixel, unmodified.</p>
 *
 * <p>STATIC HOLDER, NOT A {@code writeWorldMeta} PARAMETER (leader decision 2026-07-17): the natural
 * shape would thread the export's {@code materials} map straight into {@code writeWorldMeta}, but
 * that method lives on {@link WorldExporter}, which is ALREADY LOADED in the running game — adding a
 * parameter changes its descriptor, which is the same risk class as adding a field/method there
 * (see the dossier's {@code <clinit>} trap; DCEVM has let that through before on this JVM, but the
 * leader's deploy-fenster-vertrag for this session is body-only for {@code WorldExporter}). So the
 * hand-off goes through a plain static field instead, EXACTLY like {@link RegionClamp}: {@link
 * WorldExporter#runExport} calls {@link #armForExport} right before its {@code writeWorldMeta} call
 * (a body-only edit — the call site changes, the signature does not), and {@code writeWorldMeta}'s
 * body-only edit calls {@link #appendAnimatedJson(BufferedWriter)} with NO extra argument, which
 * reads the holder and clears it in a {@code finally} (leak-schutz, same discipline as {@link
 * RegionClamp#clear}). Alt-path export is render-thread-only (see {@link WorldExporter} class doc),
 * so a plain static suffices — no volatile/ThreadLocal needed. TODO next boot window: fold this back
 * into a real {@code writeWorldMeta} parameter once a reboot is scheduled anyway; the holder is a
 * deploy-window workaround; not the design that would be chosen greenfield.</p>
 */
final class TextureStripExporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Sub-directory (under the export's {@code outDir}) that animation strips land in. */
    private static final String ANIM_SUBDIR = "textures/anim";

    /**
     * Hand-off from {@link WorldExporter#runExport} to {@link WorldExporter#writeWorldMeta} — see
     * the class doc's STATIC HOLDER note. Armed by {@link #armForExport}, consumed + cleared by
     * {@link #appendAnimatedJson(BufferedWriter)}. Never {@code null} between exports (armed always
     * installs a fresh list, even if empty) so a missed {@code armForExport} call reads as "no
     * animated textures" (empty list) rather than a stale list from a previous export.
     */
    private static List<AnimatedTexture> pending = new ArrayList<AnimatedTexture>();

    private TextureStripExporter()
    {
        /* static-only helper */
    }

    /**
     * One animated material's exported strip + timing — exactly the fields the schema contract
     * (agreed with the Blender-consumer builder) needs in {@code world_meta.json}'s
     * {@code "animated"} array.
     */
    static final class AnimatedTexture
    {
        final String material;
        final int frames;
        final int frametime;
        final boolean interpolate;

        /** Path RELATIVE to the export's {@code outDir}, forward-slashed (e.g.
         *  {@code "textures/anim/water_still_strip.png"}). */
        final String file;

        /**
         * The RESOLVED playback sequence — one {@code [tileIndex, timeTicks]} pair per playback
         * step, straight from {@link AnimationMetadataSection#forEachFrame}. Schema addition
         * 2026-07-17 (leader/contract-owner sign-off): a bare {@code frametime} is a lie for any
         * sprite whose {@code .mcmeta} carries an explicit {@code frames} list — lava_still and
         * fire play a PING-PONG sequence (forward then back down, e.g. 0..19,18..1 = 38 steps for
         * a 20-frame strip) with a per-tile time override that a linear "frame i at time
         * i*frametime" playback cannot reproduce; Blender would visibly diverge from the game.
         * {@link #frames} stays the plain strip frame COUNT (B3 already builds against it as a
         * count, per the leader) — {@code order} is the additional, separate resolved-sequence
         * list. A sprite with NO explicit {@code frames} list in its {@code .mcmeta} (water_still)
         * makes {@code forEachFrame} callback ZERO times (MEASURED, not vanilla behaviour we get
         * for free — see {@link #exportIfAnimated}) — for that case {@code order} is the LINEAR
         * DEFAULT we synthesise ourselves: {@code [[0,frametime],[1,frametime],...,
         * [frames-1,frametime]]}.
         */
        final List<int[]> order;

        private AnimatedTexture(String material, int frames, int frametime, boolean interpolate, String file, List<int[]> order)
        {
            this.material = material;
            this.frames = frames;
            this.frametime = frametime;
            this.interpolate = interpolate;
            this.file = file;
            this.order = order;
        }
    }

    /**
     * Arm the hand-off for one export run — see the class doc's STATIC HOLDER note. Called from
     * {@link WorldExporter#runExport}'s body right before its {@code writeWorldMeta} call, since
     * that is the one place still holding the live {@code materials} map (a
     * {@code Map<String, WorldExporter.Material>} this class cannot type directly — {@code Material}
     * is {@code private} to {@code WorldExporter}). The caller unpacks it to
     * {@code textureName -> spriteId} (both public/plain types) and de-duplicates by
     * {@code textureName} itself, exactly mirroring {@link WorldExporter#extractTextures}'s own
     * "same sprite, several tint materials, one file" rule.
     *
     * <p>Always installs a FRESH list (even if every sprite turns out non-animated), so a export
     * that emits zero animated textures still clears out whatever a previous export armed — no
     * stale carry-over is possible.</p>
     *
     * @param client              for the resource manager
     * @param outDir              the export's output directory (same one the OBJ/MTL/textures live under)
     * @param spriteIdByMaterial  de-duplicated {@code textureName -> spriteId}, one entry per distinct sprite
     */
    static void armForExport(Minecraft client, File outDir, Map<String, ResourceLocation> spriteIdByMaterial)
    {
        List<AnimatedTexture> result = new ArrayList<AnimatedTexture>();
        Set<String> seen = new HashSet<String>();

        for (Map.Entry<String, ResourceLocation> entry : spriteIdByMaterial.entrySet())
        {
            String materialName = entry.getKey();

            if (!seen.add(materialName))
            {
                continue;
            }

            AnimatedTexture animated = exportIfAnimated(client, entry.getValue(), materialName, outDir);

            if (animated != null)
            {
                result.add(animated);
            }
        }

        pending = result;
    }

    /**
     * If {@code spriteId}'s resource carries animation metadata, write its full strip to
     * {@code <outDir>/textures/anim/<materialName>_strip.png} and return the timing; otherwise
     * return {@code null} (not an error — most sprites are not animated).
     *
     * <p>{@code materialName} is the SPRITE-keyed name ({@link WorldExporter.Material#textureName}
     * equivalent), not the tint-suffixed material key, so a tinted variant of an animated sprite
     * (not something vanilla actually has today, but kept consistent with {@code extractTextures}'
     * own de-duplication) still shares the one strip file.</p>
     *
     * @param client       for the resource manager
     * @param spriteId     the sprite identifier, e.g. {@code minecraft:block/water_still}
     * @param materialName sprite-keyed safe name, e.g. {@code minecraft_block_water_still}
     * @param outDir       the export's output directory (same one the OBJ/MTL/textures live under)
     * @return the animation entry, or {@code null} if this sprite is not animated
     */
    private static AnimatedTexture exportIfAnimated(Minecraft client, ResourceLocation spriteId, String materialName, File outDir)
    {
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");

        Optional<Resource> resourceOpt;

        try
        {
            resourceOpt = client.getResourceManager().getResource(resourceId);
        }
        catch (Exception e)
        {
            /* Never let one sprite's lookup take the whole export down. */
            LOGGER.warn("[blenderbridge] texture strip lookup failed for {}: {}", resourceId, e.toString());
            return null;
        }

        if (resourceOpt.isEmpty())
        {
            return null;
        }

        Resource resource = resourceOpt.get();
        Optional<AnimationMetadataSection> animOpt;

        try
        {
            /* ⚠️ SERIALIZER heisst jetzt TYPE und ist ein MetadataSectionType statt eines
             * Readers — getSection nimmt genau den. Gleiche Sektion, gleiches Ergebnis. */
            animOpt = resource.metadata().getSection(AnimationMetadataSection.TYPE);
        }
        catch (IOException e)
        {
            /* Malformed .mcmeta — treat as "not animated" rather than aborting the export. */
            LOGGER.warn("[blenderbridge] could not read animation metadata for {}: {}", resourceId, e.toString());
            return null;
        }

        if (animOpt.isEmpty())
        {
            /* No .mcmeta, or one without an "animation" section — not animated. */
            return null;
        }

        AnimationMetadataSection anim = animOpt.get();

        File animDir = new File(outDir, ANIM_SUBDIR);

        if (!animDir.exists() && !animDir.mkdirs())
        {
            LOGGER.warn("[blenderbridge] could not create anim texture directory: {}", animDir.getAbsolutePath());
            return null;
        }

        String fileName = materialName + "_strip.png";
        File target = new File(animDir, fileName);

        int frameHeight;

        try
        {
            frameHeight = copyStripAndMeasure(resource, target);
        }
        catch (IOException e)
        {
            LOGGER.warn("[blenderbridge] could not write animation strip for {}: {}", resourceId, e.toString());
            return null;
        }

        if (frameHeight <= 0)
        {
            /* Shouldn't happen -> a .png that failed to decode as an image at all. Nothing sane to
             * report; the strip file (if written) is left in place for inspection. */
            LOGGER.warn("[blenderbridge] animation strip {} decoded to a non-positive frame height", resourceId);
            return null;
        }

        int frames = frameHeight;

        /* Resolved playback sequence — see AnimatedTexture#order. forEachFrame hands out
         * (tileIndex, effectiveTimeTicks) pairs, but ONLY for sprites whose .mcmeta carries an
         * explicit "frames" list (lava_still/fire: ping-pong expansion + per-tile time overrides,
         * already resolved by vanilla before we ever see a callback — we just collect it, no
         * reimplementation of that logic here).
         *
         * MEASURED (not assumed) 2026-07-17: for a sprite with NO "frames" list in its .mcmeta
         * (water_still — verified both live against the running JVM and by reflecting
         * AnimationResourceMetadata's private "frames" field, which is an EMPTY immutable list, not
         * null), forEachFrame calls back ZERO times. Vanilla's own reader does not synthesise the
         * implicit "one step per strip frame at defaultFrameTime" sequence anywhere in this class —
         * that resolution happens one layer up, in SpriteContents/atlas stitching, which knows the
         * sprite's total frame count and we do not want to depend on (same package-private wall
         * extractTextures already hit). So: an empty result here means "linear default", and we
         * synthesise it ourselves from the frame count we already measured off the strip file. */
        List<int[]> order = new ArrayList<int[]>();

        /* ⚠️ 26.2: AnimationMetadataSection ist ein record, forEachFrame gibt es nicht mehr.
         * Dieselbe Liste liegt jetzt offen als frames() da — leeres Optional statt "null
         * callbacks", also bleibt die oben beschriebene Messung gueltig: KEINE explizite
         * frames-Liste in der .mcmeta => order bleibt leer => wir synthetisieren den
         * linearen Default selbst. frame.timeOr(default) ist genau das, was der alte
         * Callback als zweiten Wert lieferte. */
        //? if >=1.21.2 {
        anim.frames().ifPresent((list) ->
        {
            for (AnimationFrame frame : list)
            {
                order.add(new int[] {frame.index(), frame.timeOr(anim.defaultFrameTime())});
            }
        });
        //?} else {
        /*anim.forEachFrame((index, time) -> order.add(new int[] {index, time}));
        *///?}

        if (order.isEmpty())
        {
            int defaultFrameTime = anim.defaultFrameTime();

            for (int tileIndex = 0; tileIndex < frames; tileIndex++)
            {
                order.add(new int[] {tileIndex, defaultFrameTime});
            }
        }

        return new AnimatedTexture(materialName, frames, anim.defaultFrameTime(), anim.interpolatedFrames(), ANIM_SUBDIR + "/" + fileName, order);
    }

    /**
     * Byte-copy the resource's PNG to {@code target} (every frame, untouched — unlike
     * {@link WorldExporter}'s single-frame crop we want the whole strip) and return how many
     * frames it contains.
     *
     * <p>Frame count comes from the STRIP'S OWN pixel dimensions divided by its square frame width
     * (vanilla animation strips are N stacked {@code width x width} frames -- verified against the
     * 1.21.1 jar: {@code water_still.png} 16x512 / 16 = 32, {@code lava_still.png} 16x320 / 16 = 20),
     * not from {@link WorldExporter.Material#frameWidth}/{@code frameHeight} -- those belong to
     * {@code WorldExporter}'s private {@code Material} and are deliberately NOT threaded across this
     * class boundary; re-deriving frame count from the file we just copied keeps this class
     * self-contained and needs no access to the caller's private state.</p>
     */
    private static int copyStripAndMeasure(Resource resource, File target) throws IOException
    {
        InputStream copySource = resource.open();

        try
        {
            Files.copy(copySource, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            copySource.close();
        }

        InputStream probe = resource.open();
        NativeImage image = null;

        try
        {
            image = NativeImage.read(probe);

            int width = image.getWidth();
            int height = image.getHeight();

            if (width <= 0)
            {
                return 0;
            }

            /* Vanilla's own animation strips are stacked SQUARE frames (frame side == strip
             * width) for every vanilla animated block texture (water/lava/fire/magma/...). A
             * resource-pack texture that is not square-framed would misreport here, but that is
             * true of the rest of this exporter's animation handling too (frame size is not part
             * of the .mcmeta contract this class reads) and out of scope for this task. */
            return height / width;
        }
        finally
        {
            if (image != null)
            {
                image.close();
            }

            probe.close();
        }
    }

    /**
     * Append the {@code "animated": [...]} array to an already-open {@code world_meta.json} writer,
     * SCHEMA FIXED BY THE LEADER (the Blender-consumer builder builds against this exact shape —
     * do not change field names/types without a SendMessage round-trip):
     * <pre>
     * "animated": [{"material": "minecraft_block_water_still", "frames": 32, "frametime": 2,
     *               "interpolate": false, "file": "textures/anim/water_still_strip.png",
     *               "order": [[0,2],[1,2], ... ,[31,2]]}]
     * </pre>
     * {@code order} is schema addition #2 (2026-07-17, leader/contract-owner sign-off): the
     * RESOLVED playback sequence, {@code [tileIndex, timeTicks]} per step, straight from
     * {@link AnimationMetadataSection#forEachFrame} — see {@link AnimatedTexture#order}'s doc for
     * why a bare {@code frametime} cannot stand in for it (lava/fire ping-pong + per-tile times).
     * {@code frames} stays the plain strip frame count, unchanged, B3 already builds against it.
     *
     * <p>Reads (and, in the {@code finally}, CLEARS — see the class doc's STATIC HOLDER note) the
     * list {@link #armForExport} filled in. Takes no extra argument on purpose: this is the whole
     * reason for the holder — {@link WorldExporter#writeWorldMeta}'s SIGNATURE must not change
     * (that method lives on an already-loaded class), only its body, which is exactly a call to this
     * method with the writer it already has open.</p>
     *
     * <p>Called from {@link WorldExporter#writeWorldMeta} (body-edit only) right after the
     * {@code emissive} array — this is the ONLY line that class needed to change; all the
     * resource/.mcmeta logic lives in this file so it hotswaps independently.</p>
     */
    static void appendAnimatedJson(BufferedWriter meta) throws IOException
    {
        List<AnimatedTexture> animated = pending;

        try
        {
            meta.write("  \"animated\": [\n");

            for (int i = 0; i < animated.size(); i++)
            {
                AnimatedTexture entry = animated.get(i);

                meta.write("    {\"material\": \"" + entry.material + "\", \"frames\": " + entry.frames
                    + ", \"frametime\": " + entry.frametime + ", \"interpolate\": " + entry.interpolate
                    + ", \"file\": \"" + entry.file + "\", \"order\": [");

                for (int j = 0; j < entry.order.size(); j++)
                {
                    int[] step = entry.order.get(j);

                    meta.write("[" + step[0] + ", " + step[1] + "]" + (j + 1 < entry.order.size() ? ", " : ""));
                }

                meta.write("]}" + (i + 1 < animated.size() ? "," : "") + "\n");
            }

            meta.write("  ]\n");
        }
        finally
        {
            /* Leak-schutz, same discipline as RegionClamp.clear(): a second export that forgets to
             * re-arm must never silently replay this one's list. */
            pending = new ArrayList<AnimatedTexture>();
        }
    }
}
