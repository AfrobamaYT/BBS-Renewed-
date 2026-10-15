package net.minecraft.client.renderer.chunk;

/**
 * BBS-Shim fuer Minecraft vor 1.21.6: die beiden Gruppen der Chunk-Schichten. Vor 1.21.6 zeichnet
 * LevelRenderer.renderSectionLayer je RenderType; ChunkSectionsToRenderMixin bildet solid auf
 * OPAQUE und translucent auf TRANSLUCENT ab.
 */
public enum ChunkSectionLayerGroup
{
    OPAQUE, TRANSLUCENT
}
