// Coded by Afrobama/Claude — Tier 2f shadow-pass skip statistics.
//
// Renderers call ShadowSkipStats.bump(rendererName) when they short-
// circuit a shadow pass via isIrisShadowPass(). Visible via /bbsdebug
// shadow-skip.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ShadowSkipStats implements IFeatureDebugger
{
    public static final ShadowSkipStats INSTANCE = new ShadowSkipStats();

    public static volatile boolean ENABLED = true;

    private final ConcurrentHashMap<String, AtomicLong> skipsByRenderer = new ConcurrentHashMap<>();

    private ShadowSkipStats() {}

    public void bump(String rendererName)
    {
        if (!ENABLED) return;
        skipsByRenderer.computeIfAbsent(rendererName, k -> new AtomicLong()).incrementAndGet();
    }

    @Override public String getName() { return "shadow-skip"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }
    @Override public int priority() { return 55; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append('\n');
        sb.append("renderersTracked=").append(skipsByRenderer.size()).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        for (var e : skipsByRenderer.entrySet())
        {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue().get()).append('\n');
        }
    }

}
