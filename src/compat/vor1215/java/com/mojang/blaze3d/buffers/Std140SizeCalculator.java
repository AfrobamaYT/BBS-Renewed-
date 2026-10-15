package com.mojang.blaze3d.buffers;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public class Std140SizeCalculator
{
    private int groesse;

    public int get() { return this.groesse; }

    public Std140SizeCalculator align(int ausrichtung)
    {
        this.groesse = (this.groesse + ausrichtung - 1) / ausrichtung * ausrichtung;
        return this;
    }

    private Std140SizeCalculator feld(int ausrichtung, int bytes) { this.align(ausrichtung); this.groesse += bytes; return this; }

    public Std140SizeCalculator putFloat() { return this.feld(4, 4); }
    public Std140SizeCalculator putInt() { return this.feld(4, 4); }
    public Std140SizeCalculator putVec2() { return this.feld(8, 8); }
    public Std140SizeCalculator putIVec2() { return this.feld(8, 8); }
    public Std140SizeCalculator putVec3() { return this.feld(16, 12); }
    public Std140SizeCalculator putIVec3() { return this.feld(16, 12); }
    public Std140SizeCalculator putVec4() { return this.feld(16, 16); }
    public Std140SizeCalculator putIVec4() { return this.feld(16, 16); }
    public Std140SizeCalculator putMat4f() { return this.feld(16, 64); }
}
