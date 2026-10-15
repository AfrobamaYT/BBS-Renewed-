package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/** BBS-Shim fuer Minecraft vor 1.21.5: Sampler-Vorrat wie 1.21.11, Sampler entstehen bei Bedarf. */
public class SamplerCache
{
    private final Map<Integer, GpuSampler> sampler = new HashMap<>();

    public void initialize()
    {}

    public GpuSampler getSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, boolean mips)
    {
        int schluessel = u.ordinal() | v.ordinal() << 1 | min.ordinal() << 2 | mag.ordinal() << 3 | (mips ? 1 : 0) << 4;

        return this.sampler.computeIfAbsent(schluessel, k -> GlDevice.instanz().createSampler(u, v, min, mag, 1,
            mips ? OptionalDouble.empty() : OptionalDouble.of(0D)));
    }

    public GpuSampler getClampToEdge(FilterMode filter) { return this.getClampToEdge(filter, false); }
    public GpuSampler getClampToEdge(FilterMode filter, boolean mips) { return this.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, filter, filter, mips); }
    public GpuSampler getRepeat(FilterMode filter) { return this.getRepeat(filter, false); }
    public GpuSampler getRepeat(FilterMode filter, boolean mips) { return this.getSampler(AddressMode.REPEAT, AddressMode.REPEAT, filter, filter, mips); }

    public void close()
    {
        this.sampler.values().forEach(GpuSampler::close);
        this.sampler.clear();
    }
}
