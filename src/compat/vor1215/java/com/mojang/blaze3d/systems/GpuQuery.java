package com.mojang.blaze3d.systems;

import java.util.OptionalLong;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public interface GpuQuery extends AutoCloseable
{
    OptionalLong getValue();

    @Override
    void close();
}
