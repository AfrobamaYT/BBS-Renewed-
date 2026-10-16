// Coded by Afrobama/Claude — Tier 3d async mesh loading.
//
// SCAFFOLD STATUS: API + cache map in place. Integration with existing
// CrowdMeshLoader is TODO. WatchService hot-reload also TODO.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncMeshLoader implements IFeatureDebugger
{
    public static final AsyncMeshLoader INSTANCE = new AsyncMeshLoader();

    public static volatile boolean ENABLED = true;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "BBS-Mesh-Loader");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, CompletableFuture<Object>> cache = new ConcurrentHashMap<>();
    public final AtomicLong hits = new AtomicLong();
    public final AtomicLong misses = new AtomicLong();
    public final AtomicLong hotReloads = new AtomicLong();

    private AsyncMeshLoader() {}

    public CompletableFuture<Object> getOrLoad(String key, java.util.function.Supplier<Object> loader)
    {
        if (!ENABLED) return CompletableFuture.completedFuture(loader.get());
        CompletableFuture<Object> existing = cache.get(key);
        if (existing != null) { hits.incrementAndGet(); return existing; }
        misses.incrementAndGet();
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(loader, executor);
        CompletableFuture<Object> prev = cache.putIfAbsent(key, future);
        return prev != null ? prev : future;
    }

    public void invalidate(String key)
    {
        cache.remove(key);
        hotReloads.incrementAndGet();
    }

    @Override public String getName() { return "async-mesh"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }
    @Override public int priority() { return 90; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append('\n');
        sb.append("cacheSize=").append(cache.size()).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("hits=").append(hits.get()).append('\n');
        sb.append("misses=").append(misses.get()).append('\n');
        sb.append("hotReloads=").append(hotReloads.get()).append('\n');
    }

}
