package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import mchorse.bbs_mod.compat.gpu.PufferNutzung;

import java.nio.ByteBuffer;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: Puffer der eigenen GL-Schicht. Erbt 1.21.4s GpuBuffer
 * (GL-Name in handle) und traegt dazu die Nutzungsbits und den Namen der 1.21.11-API.
 */
public class GlBuffer extends GpuBuffer
{
    private final int nutzung;
    private final String name;
    private boolean geschlossen;

    public GlBuffer(String name, int nutzung, int groesse)
    {
        super(typ(nutzung), art(nutzung), groesse);
        this.nutzung = nutzung;
        this.name = name;

        /* 1.21.4s GpuBuffer legt mit blosser Groesse keinen GL-Speicher an (erst beim Schreiben);
         * Lesen per Pixelpuffer scheiterte daran (GL_INVALID_VALUE, gemessen). Ueber
         * COPY_WRITE angelegt, damit kein Element-Puffer eines gebundenen VAO verstellt wird. */
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER, this.handle);
        org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER, groesse,
            (nutzung & PufferNutzung.USAGE_MAP_READ) != 0 ? org.lwjgl.opengl.GL15.GL_DYNAMIC_READ
                : (nutzung & PufferNutzung.USAGE_MAP_WRITE) != 0 ? org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW : org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    public GlBuffer(String name, int nutzung, ByteBuffer daten)
    {
        super(typ(nutzung), art(nutzung), daten);
        this.nutzung = nutzung;
        this.name = name;
    }

    private static BufferType typ(int nutzung)
    {
        if ((nutzung & PufferNutzung.USAGE_INDEX) != 0) return BufferType.INDICES;
        if ((nutzung & PufferNutzung.USAGE_VERTEX) != 0) return BufferType.VERTICES;
        if ((nutzung & PufferNutzung.USAGE_UNIFORM) != 0) return BufferType.UNIFORM;
        return BufferType.COPY_WRITE;
    }

    private static BufferUsage art(int nutzung)
    {
        if ((nutzung & PufferNutzung.USAGE_MAP_READ) != 0) return BufferUsage.DYNAMIC_READ;
        return (nutzung & PufferNutzung.USAGE_MAP_WRITE) != 0 ? BufferUsage.DYNAMIC_WRITE : BufferUsage.STATIC_WRITE;
    }

    /** Wie 1.21.11s GlBuffer.handle (dort ein Feld, ueber GlBufferAlt eine Methode). */
    public int handle()
    {
        return this.handle;
    }

    public int bbsNutzung()
    {
        return this.nutzung;
    }

    public String bbsName()
    {
        return this.name;
    }

    public boolean bbsGeschlossen()
    {
        return this.geschlossen;
    }

    @Override
    public void close()
    {
        if (!this.geschlossen)
        {
            this.geschlossen = true;
            super.close();
        }
    }

    /** Die konkrete Pufferklasse wie bei Mojang (GlBuffer$Direct), Ziel des Freigabe-Hakens der Companion-Mod. */
    public static final class Direct extends GlBuffer
    {
        public Direct(String name, int nutzung, int groesse)
        {
            super(name, nutzung, groesse);
        }

        public Direct(String name, int nutzung, ByteBuffer daten)
        {
            super(name, nutzung, daten);
        }

        @Override
        public void close()
        {
            super.close();
        }
    }
}
