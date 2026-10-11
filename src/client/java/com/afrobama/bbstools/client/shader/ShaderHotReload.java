// Coded by Afrobama/Claude — Tier 4c shader hot-reload.
//
// SCAFFOLD STATUS: API in place. F5 keybind + iris-fork reload reflection
// is TODO. Single API call: ShaderHotReload.reload() triggers everything.
package com.afrobama.bbstools.client.shader;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public final class ShaderHotReload implements IFeatureDebugger
{
    public static final ShaderHotReload INSTANCE = new ShaderHotReload();
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/shader-reload");

    public static volatile boolean ENABLED = true;

    public final AtomicLong reloadCount = new AtomicLong();
    public final AtomicLong reloadFailures = new AtomicLong();
    private volatile long lastReloadMs;

    private ShaderHotReload() {}

    /**
     * Reflectively trigger Iris's shaderpack reload, picking up edited
     * shader files from the JAR classpath.
     */
    public boolean reload()
    {
        if (!ENABLED) return false;
        long start = System.nanoTime();
        try
        {
            Class<?> irisCls = Class.forName("net.irisshaders.iris.Iris");
            java.lang.reflect.Method reloadMethod = irisCls.getMethod("reload");
            reloadMethod.invoke(null);
            lastReloadMs = (System.nanoTime() - start) / 1_000_000L;
            reloadCount.incrementAndGet();
            LOG.info("Shader reload completed in {}ms", lastReloadMs);
            return true;
        }
        catch (Throwable t)
        {
            reloadFailures.incrementAndGet();
            LOG.warn("Shader reload failed: {}", t.toString());
            return false;
        }
    }

    @Override public String getName() { return "shader-reload"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }
    @Override public int priority() { return 140; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append('\n');
        sb.append("lastReloadMs=").append(lastReloadMs).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("reloadCount=").append(reloadCount.get()).append('\n');
        sb.append("reloadFailures=").append(reloadFailures.get()).append('\n');
    }

}
