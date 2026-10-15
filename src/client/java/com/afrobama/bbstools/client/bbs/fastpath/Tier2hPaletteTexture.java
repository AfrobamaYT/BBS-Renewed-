package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** One native RGBA32F palette image, or a persistent atlas of recorded animation palettes. */
final class Tier2hPaletteTexture implements AutoCloseable
{
    private final int bones;
    private GpuTexture image;
    private GpuTextureView view;
    private int rows;
    private long fingerprint = Long.MIN_VALUE;
    private int ticks;
    private final IdentityHashMap<IEntity, Integer> entityRows = new IdentityHashMap<>();
    private final List<Object> uploadedGroups = new ArrayList<>();

    Tier2hPaletteTexture(int bones)
    {
        this.bones = bones;
    }

    GpuTextureView view()
    {
        if (this.view == null) throw new IllegalStateException("Palette has not been uploaded");
        return this.view;
    }

    void upload(ByteBuffer data, int rows)
    {
        int width = Math.multiplyExact(this.bones, 4);
        int max = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.RGBA32_FLOAT);
        if (width > max || rows > max) throw new IllegalArgumentException("Palette exceeds device limit: " + width + "x" + rows + "/" + max);
        if (this.image == null || this.rows < rows)
        {
            this.closeImage();
            this.image = RenderSystem.getDevice().createTexture("bbs-tier2h-bone-palette",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA32_FLOAT, width, rows, 1, 1);
            this.view = RenderSystem.getDevice().createTextureView(this.image);
            this.rows = rows;
        }
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.image, data, 0, 0, 0, 0, width, rows);
    }

    /** An incomplete bake uses per-frame GPU palettes until all recorded atlas rows exist. */
    boolean prepareAtlas(List<FastPathBatch.Submission> list)
    {
        if (!BBSPerfConfig.ENABLE_TIER2H_VAT) return false;
        int maxTick = AnimationBakeCache.maxTick();
        if (maxTick < 0) return false;
        int tickCount = Math.addExact(maxTick, 1);
        long stamp = BBSPerfConfig.armyBakeFrames ^ ((long) tickCount << 32)
            ^ ((long) AnimationBakeCache.signatureGroups() << 16);
        IdentityHashMap<Object, Integer> groups = new IdentityHashMap<>();
        List<Object> ordered = new ArrayList<>();
        IdentityHashMap<IEntity, Integer> newRows = new IdentityHashMap<>();
        for (FastPathBatch.Submission s : list)
        {
            Object group = s.entity == null ? null : AnimationBakeCache.groupOf(s.entity);
            if (group == null) return false;
            Integer index = groups.get(group);
            if (index == null)
            {
                index = ordered.size();
                groups.put(group, index);
                ordered.add(group);
            }
            newRows.put(s.entity, Math.multiplyExact(index, tickCount));
        }
        boolean sameGroups = this.uploadedGroups.size() == ordered.size();
        for (int i = 0; sameGroups && i < ordered.size(); i++) sameGroups = this.uploadedGroups.get(i) == ordered.get(i);
        boolean sameRows = this.entityRows.size() == newRows.size();
        for (var row : newRows.entrySet())
            sameRows &= java.util.Objects.equals(this.entityRows.get(row.getKey()), row.getValue());
        if (this.image != null && this.fingerprint == stamp && this.ticks == tickCount
            && sameGroups && sameRows) return true;
        int count = Math.multiplyExact(ordered.size(), tickCount);
        int max = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.RGBA32_FLOAT);
        if (count == 0 || count > max || (long) this.bones * 4 > max) return false;
        for (Object group : ordered)
        {
            for (int t = 0; t < tickCount; t++)
            {
                if (AnimationBakeCache.paletteOf(group, t) == null) return false;
            }
        }
        ByteBuffer data = MemoryUtil.memAlloc(Math.multiplyExact(count, Math.multiplyExact(this.bones, 64)))
            .order(ByteOrder.nativeOrder());
        try
        {
            for (Object group : ordered)
            {
                for (int t = 0; t < tickCount; t++) Tier2hVertexPacking.palette(data, AnimationBakeCache.paletteOf(group, t), this.bones);
            }
            data.flip();
            this.upload(data, count);
            BBSPerfConfig.tier2hUploadedBytes += data.remaining();
            this.fingerprint = stamp;
            this.ticks = tickCount;
            this.entityRows.clear();
            this.entityRows.putAll(newRows);
            this.uploadedGroups.clear();
            this.uploadedGroups.addAll(ordered);
            BBSPerfConfig.tier2hVatRebuilds++;
            BBSPerfConfig.tier2hVatAtlasRows = count;
            BBSPerfConfig.tier2hVatGroups = ordered.size();
            return true;
        }
        finally
        {
            MemoryUtil.memFree(data);
        }
    }

    int row(FastPathBatch.Submission s)
    {
        Integer first = this.entityRows.get(s.entity);
        if (first == null) throw new IllegalStateException("Missing native VAT entity row");
        int tick = Math.round(s.entity.getAge() + s.transition);
        return first + Math.max(0, Math.min(this.ticks - 1, tick));
    }

    private void closeImage()
    {
        if (this.view != null) this.view.close();
        if (this.image != null) this.image.close();
        this.view = null;
        this.image = null;
        this.rows = 0;
    }

    @Override
    public void close()
    {
        this.closeImage();
        this.entityRows.clear();
        this.uploadedGroups.clear();
        this.fingerprint = Long.MIN_VALUE;
    }
}
