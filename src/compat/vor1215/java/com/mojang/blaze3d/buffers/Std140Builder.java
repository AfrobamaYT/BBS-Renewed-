package com.mojang.blaze3d.buffers;

import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector2ic;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import org.joml.Vector4fc;
import org.joml.Vector4ic;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public class Std140Builder
{
    private final ByteBuffer puffer;
    private final int anfang;

    private Std140Builder(ByteBuffer puffer)
    {
        this.puffer = puffer;
        this.anfang = puffer.position();
    }

    public static Std140Builder intoBuffer(ByteBuffer puffer) { return new Std140Builder(puffer); }
    public static Std140Builder onStack(MemoryStack stack, int groesse) { return new Std140Builder(stack.malloc(groesse)); }

    public ByteBuffer get() { return this.puffer.flip(); }

    public Std140Builder align(int ausrichtung)
    {
        int lage = this.puffer.position() - this.anfang;
        this.puffer.position(this.anfang + (lage + ausrichtung - 1) / ausrichtung * ausrichtung);
        return this;
    }

    public Std140Builder putFloat(float f) { this.align(4); this.puffer.putFloat(f); return this; }
    public Std140Builder putInt(int i) { this.align(4); this.puffer.putInt(i); return this; }
    public Std140Builder putVec2(float x, float y) { this.align(8); this.puffer.putFloat(x).putFloat(y); return this; }
    public Std140Builder putVec2(Vector2fc v) { return this.putVec2(v.x(), v.y()); }
    public Std140Builder putIVec2(int x, int y) { this.align(8); this.puffer.putInt(x).putInt(y); return this; }
    public Std140Builder putIVec2(Vector2ic v) { return this.putIVec2(v.x(), v.y()); }
    /* Wie Mojang: vec3 belegt 16 Byte (4 Byte Fuellung danach). */
    public Std140Builder putVec3(float x, float y, float z) { this.align(16); this.puffer.putFloat(x).putFloat(y).putFloat(z); this.puffer.position(this.puffer.position() + 4); return this; }
    public Std140Builder putVec3(Vector3fc v) { return this.putVec3(v.x(), v.y(), v.z()); }
    public Std140Builder putIVec3(int x, int y, int z) { this.align(16); this.puffer.putInt(x).putInt(y).putInt(z); this.puffer.position(this.puffer.position() + 4); return this; }
    public Std140Builder putIVec3(Vector3ic v) { return this.putIVec3(v.x(), v.y(), v.z()); }
    public Std140Builder putVec4(float x, float y, float z, float w) { this.align(16); this.puffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w); return this; }
    public Std140Builder putVec4(Vector4fc v) { return this.putVec4(v.x(), v.y(), v.z(), v.w()); }
    public Std140Builder putIVec4(int x, int y, int z, int w) { this.align(16); this.puffer.putInt(x).putInt(y).putInt(z).putInt(w); return this; }
    public Std140Builder putIVec4(Vector4ic v) { return this.putIVec4(v.x(), v.y(), v.z(), v.w()); }

    public Std140Builder putMat4f(Matrix4fc m)
    {
        this.align(16);
        m.get(this.puffer.position(), this.puffer);
        this.puffer.position(this.puffer.position() + 64);
        return this;
    }
}
