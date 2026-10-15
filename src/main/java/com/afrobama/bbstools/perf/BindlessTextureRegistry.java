// Coded by Afrobama/Claude — Tier 6c bindless texture registry (sequence-after-3b).
//
// Eliminates per-draw texture-bind state changes by making each registered
// GL texture resident via GL_ARB_bindless_texture; the resulting uint64
// handle is stored in an SSBO (one binding point, indexed by SSBO slot)
// so shaders can look up textures by index without ever needing a
// glBindTexture call.
//
// Hardware gate:
//   - NVIDIA Kepler (GTX 6xx, 2012)+  YES
//   - AMD     GCN     (HD 7xxx, 2012)+  YES
//   - Intel  (any iGPU)                NO  (graceful disable)
//
// Detection runs once at initIfNeeded(); if the GL_ARB_bindless_texture
// capability is absent we set extensionAvailable=false, log a single warn
// line, and every public method becomes a no-op so existing callers can
// remain unconditional. Default OFF — opt-in via /bbsdebug bindless on.
//
// Scaffold-only for now: the actual shader-side lookup
// (`uvec2[] u_bindlessHandles; sampler2D tex = sampler2D(u_bindlessHandles[idx]);`)
// is a future Tier 3b follow-up. This class just owns the SSBO lifecycle
// + extension detection so atlas integration can build on top.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import org.lwjgl.opengl.ARBBindlessTexture;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class BindlessTextureRegistry implements IFeatureDebugger
{
    public static final BindlessTextureRegistry INSTANCE = new BindlessTextureRegistry();

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/bindless");

    /** Max bindless handles we pre-allocate room for in the SSBO. uint64
     *  per handle → maxHandles * 8 bytes. 1024 handles = 8 KiB SSBO, plenty
     *  of headroom for a crowd-renderer that's usually <100 unique textures. */
    public static final int MAX_HANDLES = 1024;
    private static final int HANDLE_BYTES = 8;

    /* Singleton state — touched only from the render thread except for
     * volatile flags that are read by debug commands. */
    private int residentBufferGlId = 0;
    private final Map<Integer, Long> handleByGlTexId = new HashMap<>();
    private final LongArrayList registeredHandles = new LongArrayList(64);
    private volatile boolean extensionAvailable = false;
    private volatile boolean initialised = false;

    private BindlessTextureRegistry() {}

    /* =====================================================================
     * Public API
     * ===================================================================== */

    /**
     * One-time initialisation. Queries the GL_ARB_bindless_texture capability;
     * if absent, leaves the registry in a no-op state (every other method
     * short-circuits). If present, allocates the resident-handle SSBO via
     * glBufferStorage and flags us ready.
     *
     * Idempotent — safe to call from multiple init paths.
     */
    public void initIfNeeded()
    {
        if (initialised) return;
        initialised = true;

        if (!GL.getCapabilities().GL_ARB_bindless_texture)
        {
            LOG.info("[bindless] GL_ARB_bindless_texture NOT supported on this GPU/driver — feature unavailable");
            extensionAvailable = false;
            return;
        }
        extensionAvailable = true;

        residentBufferGlId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, residentBufferGlId);
        /* Immutable storage — handles are uploaded incrementally via
         * glBufferSubData when registerTexture() is called. */
        GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER,
            (long) MAX_HANDLES * HANDLE_BYTES,
            GL30.GL_MAP_WRITE_BIT);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        LOG.info("[bindless] GL_ARB_bindless_texture available — bindless registry initialized (ssbo gl id={}, max={} handles)",
            residentBufferGlId, MAX_HANDLES);
    }

    /**
     * Make a texture bindless and register it. Returns the SSBO index the
     * shader should use to look this handle up, or -1 if the extension is
     * unavailable, the texture is already registered (returns existing
     * index), or we've hit MAX_HANDLES.
     */
    public int registerTexture(int glTexId)
    {
        if (!initialised) initIfNeeded();
        if (!extensionAvailable) return -1;
        if (glTexId <= 0) return -1;

        Long existing = handleByGlTexId.get(glTexId);
        if (existing != null)
        {
            /* Already registered — find its slot and return. */
            int idx = registeredHandles.indexOf(existing.longValue());
            return idx;
        }

        if (registeredHandles.size() >= MAX_HANDLES)
        {
            LOG.warn("[bindless] handle table full ({}/{}) — texture {} not registered",
                registeredHandles.size(), MAX_HANDLES, glTexId);
            return -1;
        }

        long handle;
        try
        {
            handle = ARBBindlessTexture.glGetTextureHandleARB(glTexId);
            if (handle == 0L)
            {
                LOG.warn("[bindless] glGetTextureHandleARB returned 0 for tex={}", glTexId);
                return -1;
            }
            ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
        }
        catch (Throwable t)
        {
            LOG.warn("[bindless] failed to make texture {} resident: {}", glTexId, t.toString());
            return -1;
        }

        int slot = registeredHandles.size();
        registeredHandles.add(handle);
        handleByGlTexId.put(glTexId, handle);

        /* Push the single new handle into its SSBO slot. */
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush())
        {
            java.nio.LongBuffer one = stack.mallocLong(1);
            one.put(0, handle);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, residentBufferGlId);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,
                (long) slot * HANDLE_BYTES, one);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        }

        BBSPerfConfig.bindlessHandlesResident = registeredHandles.size();
        return slot;
    }

    /** Bind the resident-handle SSBO to the given binding point so shaders
     *  can declare `layout(std430, binding=N) buffer Handles { uvec2 h[]; };` */
    public void bindSSBO(int bindingPoint)
    {
        if (!extensionAvailable || residentBufferGlId == 0) return;
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, residentBufferGlId);
    }

    /** Tier 3b integration scaffold — make the crowd texture atlas bindless
     *  so shader can sample it via a handle instead of GL_TEXTURE_n binding.
     *  Logs the SSBO index; actual shader-side wiring is future work. */
    public static void integrateWithAtlas(CrowdTextureAtlas atlas)
    {
        if (atlas == null) return;
        atlas.bindAtlas();
        int atlasGlId = atlas.getAtlasGlId();
        if (atlasGlId <= 0)
        {
            LOG.warn("[bindless] integrateWithAtlas: atlas gl id is {} — atlas not initialised", atlasGlId);
            return;
        }
        int slot = INSTANCE.registerTexture(atlasGlId);
        LOG.info("[bindless] crowd atlas (gl id={}) registered at SSBO slot {}", atlasGlId, slot);
    }

    /** Release every resident handle and the SSBO. Called on game shutdown
     *  or feature-disable. */
    public void shutdown()
    {
        if (!extensionAvailable) return;
        for (int i = 0; i < registeredHandles.size(); i++)
        {
            long h = registeredHandles.getLong(i);
            try { ARBBindlessTexture.glMakeTextureHandleNonResidentARB(h); }
            catch (Throwable t) { /* best-effort */ }
        }
        registeredHandles.clear();
        handleByGlTexId.clear();
        if (residentBufferGlId != 0)
        {
            GL15.glDeleteBuffers(residentBufferGlId);
            residentBufferGlId = 0;
        }
        BBSPerfConfig.bindlessHandlesResident = 0L;
        initialised = false;
        LOG.info("[bindless] shutdown — all handles non-resident, SSBO deleted");
    }

    public boolean isExtensionAvailable() { return extensionAvailable; }
    public int getResidentHandleCount() { return registeredHandles.size(); }
    public int getSsboGlId() { return residentBufferGlId; }

    /* =====================================================================
     * IFeatureDebugger
     * ===================================================================== */

    @Override public String getName() { return "bindless"; }
    @Override public boolean isEnabled() { return BBSPerfConfig.ENABLE_BINDLESS_TEXTURES; }

    @Override
    public void setEnabled(boolean v)
    {
        BBSPerfConfig.ENABLE_BINDLESS_TEXTURES = v;
        if (v) initIfNeeded();
    }

    @Override public int priority() { return 130; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(BBSPerfConfig.ENABLE_BINDLESS_TEXTURES).append('\n');
        sb.append("initialised=").append(initialised).append('\n');
        sb.append("extensionAvailable=").append(extensionAvailable).append('\n');
        sb.append("ssboGlId=").append(residentBufferGlId).append('\n');
        sb.append("maxHandles=").append(MAX_HANDLES).append('\n');
        sb.append("residentHandles=").append(registeredHandles.size()).append('\n');
        sb.append("scaffold=true (shader-side bindless lookup is Tier 3b follow-up)\n");
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("bindlessHandlesResident=").append(BBSPerfConfig.bindlessHandlesResident).append('\n');
        sb.append("uniqueGlTexIdsTracked=").append(handleByGlTexId.size()).append('\n');
    }

}
