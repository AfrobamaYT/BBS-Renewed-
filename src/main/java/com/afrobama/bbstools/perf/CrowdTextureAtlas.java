// Coded by Afrobama/Claude — Tier 3b texture atlas consolidation.
//
// Consolidates up to 256 unique replay textures into a single 4K×4K
// GL_RGBA8 atlas (16×16 grid of 256-px tiles, 2-px padding for mip
// edge-bleed). Eliminates per-replay glBindTexture state changes in
// the Veil-instanced replay batch path — every batched draw can stay
// bound to the atlas while per-instance vec4 atlasUV (xy = origin,
// zw = origin+size in 0..1) selects which tile that instance samples.
//
// Java-side prep ONLY for this session: the API and CPU-side blitting
// are wired up, instance-level UV lookup is exposed via uvFor(...).
// The shader-side change (vsh attribute + UV-remap in fragment) is
// intentionally deferred to a follow-up because BBS-replay's existing
// vsh is shared with the per-replay path and we don't want a regress
// during cinematic work. Until the shader-side lands, the atlas can
// stay OFF (default) and replays continue using their per-replay
// glBindTexture path.
//
// CAVEAT: AnimatedTexture (live form-animation skins) would need a
// per-frame glTexSubImage2D to keep the tile in sync with the source
// texture. Not handled here — static skins only. Filtered by skipping
// any Texture whose getParent() returns non-null.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CrowdTextureAtlas implements IFeatureDebugger
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/perf/atlas");

    public static final CrowdTextureAtlas INSTANCE = new CrowdTextureAtlas();

    public static final int ATLAS_SIZE     = 4096;
    public static final int TILE_SIZE      = 256;
    public static final int PADDING        = 2;
    public static final int TILES_PER_ROW  = ATLAS_SIZE / TILE_SIZE;
    public static final int MAX_TILES      = TILES_PER_ROW * TILES_PER_ROW;
    public static final int MIP_LEVELS     = 8;

    /** Stable instance-attribute fallback when atlas is OFF or texture missing.
     *  xy=0, zw=1 means "use the source texture's full UV [0..1] range" — i.e.,
     *  identity remap, so the existing shader continues to behave as before. */
    public static final Vector4f IDENTITY_UV = new Vector4f(0F, 0F, 1F, 1F);

    /** Per-tile metadata returned from addTexture/lookup. atlasUV.xy is
     *  the origin (0..1 inside the atlas, post-padding); atlasUV.zw is
     *  origin + size. Mesh UV's [0..1] are linearly mixed across this. */
    public static final class AtlasTile
    {
        public final int tileIndex;
        public final Vector4f atlasUV;

        AtlasTile(int tileIndex, Vector4f atlasUV)
        {
            this.tileIndex = tileIndex;
            this.atlasUV = atlasUV;
        }
    }

    public static volatile boolean ENABLED = false;

    /* ------------- counters ------------- */
    public final AtomicLong uniqueTextures           = new AtomicLong();
    public final AtomicLong stateChangeReductions    = new AtomicLong();
    public final AtomicLong atlasTilesUsed           = new AtomicLong();
    public final AtomicLong atlasLookupHits          = new AtomicLong();
    public final AtomicLong atlasOverflows           = new AtomicLong();
    public final AtomicLong atlasBlitFailures        = new AtomicLong();
    public final AtomicLong atlasAnimatedSkipped     = new AtomicLong();

    /* ------------- GL/CPU state ------------- */
    private final ConcurrentHashMap<Integer, AtlasTile> tileByTextureId = new ConcurrentHashMap<>();
    private volatile int  atlasGlId    = 0;
    private volatile int  nextTileIndex = 0;
    private volatile boolean initialized = false;

    /* Reflective accessor for mchorse.bbs_mod.graphics.texture.Texture.
     * Resolved lazily on first addTexture() so this class stays portable
     * (build-time independent of BBS). */
    private volatile Method m_pixelsFromTexture; /* static Pixels pixelsFromTexture(Texture) */
    private volatile Method m_getParent;         /* AnimatedTexture getParent() */
    private volatile Method m_pixels_getBuffer;  /* ByteBuffer getBuffer() */
    private volatile Method m_pixels_delete;     /* void delete() */
    private volatile java.lang.reflect.Field f_pixels_width;
    private volatile java.lang.reflect.Field f_pixels_height;
    private volatile java.lang.reflect.Field f_texture_id;
    private volatile java.lang.reflect.Field f_texture_width;
    private volatile java.lang.reflect.Field f_texture_height;
    private volatile boolean reflectionResolved;
    private volatile boolean reflectionFailed;

    private CrowdTextureAtlas() {}

    public int getAtlasGlId() { return atlasGlId; }

    /** Public probe for callers that only need a UV (e.g., VeilSkinnedBatch
     *  uploadInstanceData). Returns IDENTITY_UV if atlas is disabled or
     *  the texture isn't atlased yet. Never returns null. */
    public Vector4f uvForOrIdentity(int srcTextureGlId)
    {
        if (!ENABLED) return IDENTITY_UV;
        AtlasTile t = tileByTextureId.get(srcTextureGlId);
        if (t == null) return IDENTITY_UV;
        atlasLookupHits.incrementAndGet();
        return t.atlasUV;
    }

    /** Returns existing or null. Does NOT create. */
    public AtlasTile lookup(Object bbsTexture)
    {
        if (bbsTexture == null) return null;
        Integer id = readTextureId(bbsTexture);
        if (id == null) return null;
        return tileByTextureId.get(id);
    }

    /** Add a BBS Texture (passed as Object to avoid build-time coupling
     *  to the BBS source). Returns the created AtlasTile or null on
     *  overflow / blit failure / animated-texture skip. Initialises the
     *  atlas if needed; mip generation is amortized to once per
     *  addTexture so the latest tile's mips are always available. */
    public AtlasTile addTexture(Object bbsTexture)
    {
        if (bbsTexture == null) return null;
        if (!resolveReflection()) return null;

        Integer key = readTextureId(bbsTexture);
        if (key == null) return null;

        AtlasTile existing = tileByTextureId.get(key);
        if (existing != null)
        {
            atlasLookupHits.incrementAndGet();
            return existing;
        }

        /* Skip AnimatedTexture parents — caller would need per-frame
         * glTexSubImage2D updates to keep tiles fresh, which we don't
         * implement in this version. */
        try
        {
            if (m_getParent.invoke(bbsTexture) != null)
            {
                atlasAnimatedSkipped.incrementAndGet();
                return null;
            }
        }
        catch (Throwable t)
        {
            /* getParent failed — treat as non-animated (best-effort). */
        }

        synchronized (this)
        {
            existing = tileByTextureId.get(key);
            if (existing != null) return existing;

            if (nextTileIndex >= MAX_TILES)
            {
                atlasOverflows.incrementAndGet();
                LOG.warn("CrowdTextureAtlas: overflow at {} unique textures — "
                    + "caller must fall back to direct binding (texGlId={})",
                    MAX_TILES, key);
                return null;
            }

            initIfNeeded();

            int tileIndex = nextTileIndex;
            int row = tileIndex / TILES_PER_ROW;
            int col = tileIndex % TILES_PER_ROW;
            int tilePxX = col * TILE_SIZE;
            int tilePxY = row * TILE_SIZE;

            /* Inner usable region after 2-px padding ring. */
            int innerX  = tilePxX + PADDING;
            int innerY  = tilePxY + PADDING;
            int innerWH = TILE_SIZE - 2 * PADDING;

            AtlasTile tile = blitInto(bbsTexture, key, tileIndex,
                innerX, innerY, innerWH);
            if (tile == null)
            {
                atlasBlitFailures.incrementAndGet();
                return null;
            }

            tileByTextureId.put(key, tile);
            nextTileIndex++;
            uniqueTextures.incrementAndGet();
            atlasTilesUsed.set(nextTileIndex);

            /* Regenerate mips after the freshly-blitted tile. Cheap enough
             * since this only runs once per unique texture (≤256 ever). */
            int vorherTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int vorherEinheit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasGlId);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, vorherTex);
            GL13.glActiveTexture(vorherEinheit);

            return tile;
        }
    }

    /** Bind the atlas to GL_TEXTURE0. Caller should restore prior state. */
    /**
     * ⛔ LAESST DEN ZUSTAND STEHEN — das ist hier Absicht (der Shader soll den Atlas
     * lesen), aber Mojangs Merker erfaehrt nichts davon.
     *
     * <p>{@code GlStateManager._bindTexture} ist ein Zwischenspeicher; wer daran vorbei
     * bindet, laesst ihn falsch stehen, und Mojangs naechster Upload landet in der hier
     * gebundenen Textur. Genau das liess am 2026-08-31 die Schrift in jedem Menue
     * zerfallen (9 von 12 Laeufen), Ursache war dieselbe Zeile in {@code Texture.bind()}.
     *
     * <p>⚠️ NICHT UMGESTELLT, weil diese Klasse in {@code src/main} liegt und
     * {@code com.mojang.renderpearl.backend.opengl} dort nicht existiert. Die drei aufraeumenden
     * Bloecke dieser Klasse stellen den Zustand jetzt exakt wieder her und sind damit
     * unschaedlich; nur dieser eine bleibt offen. Preis fuer die saubere Loesung:
     * diese Klasse und {@link BindlessTextureRegistry} nach {@code src/client}
     * verschieben. Der Pfad ist per Vorgabe aus ({@code ENABLED = false}).
     */
    public void bindAtlas()
    {
        if (!initialized || atlasGlId == 0) return;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasGlId);
        stateChangeReductions.incrementAndGet();
    }

    /** Force a full mip regeneration. Called automatically inside
     *  addTexture; exposed publicly for callers that bulk-add. */
    public void generateMipmaps()
    {
        if (!initialized || atlasGlId == 0) return;
        int vorherTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int vorherEinheit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasGlId);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, vorherTex);
        GL13.glActiveTexture(vorherEinheit);
    }

    /* ===================== internals ===================== */

    private void initIfNeeded()
    {
        if (initialized) return;
        int vorherTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int vorherEinheit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        atlasGlId = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasGlId);

        /* Immutable storage: 8 mip levels of 4K×4K GL_RGBA8 = ~85 MiB
         * GPU-side. Reasonable for a max-256-tile crowd renderer. */
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, MIP_LEVELS,
            GL11.GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
            GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
            GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
            GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
            GL11.GL_CLAMP);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, vorherTex);
        GL13.glActiveTexture(vorherEinheit);

        initialized = true;
        LOG.info("CrowdTextureAtlas init: {}x{} GL_RGBA8 {} levels — gl id {}",
            ATLAS_SIZE, ATLAS_SIZE, MIP_LEVELS, atlasGlId);
    }

    /** Reads the source texture's pixels via BBS reflection, uploads
     *  them into the supplied inner-tile rect via glTexSubImage2D. */
    private AtlasTile blitInto(Object bbsTexture, int srcTextureId,
                               int tileIndex, int dstX, int dstY,
                               int dstWH)
    {
        Object pixels = null;
        try
        {
            pixels = m_pixelsFromTexture.invoke(null, bbsTexture);
            if (pixels == null) return null;

            int srcW = f_pixels_width.getInt(pixels);
            int srcH = f_pixels_height.getInt(pixels);
            ByteBuffer buf = (ByteBuffer) m_pixels_getBuffer.invoke(pixels);
            if (buf == null || srcW <= 0 || srcH <= 0) return null;

            /* For tiles where source ≠ TILE_SIZE-2P, we currently upload
             * at native resolution into a sub-rect of TILE_SIZE-2P. The
             * shader-side UV is normalised to the inner rect, so meshes
             * still sample from [0..1] of their tile correctly. If src
             * > inner, we'll only upload an inner-sized window — but the
             * common BBS-replay skin is 64×64 / 128×128 / 256×256 ≤ 252,
             * so this is acceptable for v1. */
            int uploadW = Math.min(srcW, dstWH);
            int uploadH = Math.min(srcH, dstWH);

            /* If source is smaller than inner: upload smaller rect at
             * tile origin; remaining padding inside the tile stays at
             * whatever GL_RGBA8 storage default is — fine since outer
             * padding pixels are never sampled (mesh UV stays in inner). */
            int vorherTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int vorherEinheit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasGlId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, srcW);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);

            buf.position(0);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                dstX, dstY, uploadW, uploadH,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, vorherTex);
            GL13.glActiveTexture(vorherEinheit);

            /* Inner-rect UVs (xy=origin, zw=origin+size), normalised to
             * atlas size. Mesh UV [0..1] mixes between xy and zw. */
            float invAtlas = 1F / (float) ATLAS_SIZE;
            float u0 = dstX * invAtlas;
            float v0 = dstY * invAtlas;
            float u1 = (dstX + uploadW) * invAtlas;
            float v1 = (dstY + uploadH) * invAtlas;
            Vector4f uv = new Vector4f(u0, v0, u1, v1);

            LOG.debug("atlas tile {} src={}x{} → dst({},{} {}x{}) uv=({},{} → {},{})",
                tileIndex, srcW, srcH, dstX, dstY, uploadW, uploadH,
                u0, v0, u1, v1);

            return new AtlasTile(tileIndex, uv);
        }
        catch (Throwable t)
        {
            LOG.warn("CrowdTextureAtlas.blitInto failed for texGlId={}: {}",
                srcTextureId, t.toString());
            return null;
        }
        finally
        {
            if (pixels != null)
            {
                try { m_pixels_delete.invoke(pixels); }
                catch (Throwable ignored) { /* best-effort free */ }
            }
        }
    }

    /** Reads the public {@code int id} field on the BBS Texture. */
    private Integer readTextureId(Object bbsTexture)
    {
        if (!resolveReflection()) return null;
        try
        {
            return f_texture_id.getInt(bbsTexture);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private boolean resolveReflection()
    {
        if (reflectionResolved) return true;
        if (reflectionFailed) return false;
        synchronized (this)
        {
            if (reflectionResolved) return true;
            if (reflectionFailed) return false;
            try
            {
                Class<?> texClass    = Class.forName("mchorse.bbs_mod.graphics.texture.Texture");
                Class<?> pixelsClass = Class.forName("mchorse.bbs_mod.utils.resources.Pixels");
                m_pixelsFromTexture = texClass.getMethod("pixelsFromTexture", texClass);
                m_getParent         = texClass.getMethod("getParent");
                f_texture_id        = texClass.getField("id");
                f_texture_width     = texClass.getField("width");
                f_texture_height    = texClass.getField("height");
                m_pixels_getBuffer  = pixelsClass.getMethod("getBuffer");
                m_pixels_delete     = pixelsClass.getMethod("delete");
                f_pixels_width      = pixelsClass.getField("width");
                f_pixels_height     = pixelsClass.getField("height");
                reflectionResolved = true;
                LOG.info("CrowdTextureAtlas reflection resolved against BBS Texture+Pixels");
                return true;
            }
            catch (Throwable t)
            {
                reflectionFailed = true;
                LOG.warn("CrowdTextureAtlas reflection FAILED — feature disabled "
                    + "until BBS classes are reachable: {}", t.toString());
                return false;
            }
        }
    }

    /* ===================== IFeatureDebugger ===================== */

    @Override public String  getName() { return "atlas"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void    setEnabled(boolean v) { ENABLED = v; }
    @Override public int     priority() { return 100; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append('\n');
        sb.append("initialized=").append(initialized).append('\n');
        sb.append("atlasGlId=").append(atlasGlId).append('\n');
        sb.append("atlasSize=").append(ATLAS_SIZE).append('x').append(ATLAS_SIZE);
        sb.append(" tiles=").append(TILES_PER_ROW).append('x').append(TILES_PER_ROW);
        sb.append(" (").append(MAX_TILES).append(" max)\n");
        sb.append("tileSize=").append(TILE_SIZE).append(" padding=").append(PADDING).append('\n');
        sb.append("tilesUsed=").append(nextTileIndex).append(" / ").append(MAX_TILES).append('\n');
        sb.append("reflectionResolved=").append(reflectionResolved);
        sb.append(" reflectionFailed=").append(reflectionFailed).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("uniqueTextures=").append(uniqueTextures.get()).append('\n');
        sb.append("atlasTilesUsed=").append(atlasTilesUsed.get()).append('\n');
        sb.append("atlasLookupHits=").append(atlasLookupHits.get()).append('\n');
        sb.append("atlasOverflows=").append(atlasOverflows.get()).append('\n');
        sb.append("atlasAnimatedSkipped=").append(atlasAnimatedSkipped.get()).append('\n');
        sb.append("atlasBlitFailures=").append(atlasBlitFailures.get()).append('\n');
        sb.append("stateChangeReductions=").append(stateChangeReductions.get()).append('\n');
    }

}
