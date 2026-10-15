package net.minecraft.client.renderer.state;

/**
 * BBS-Shim fuer Minecraft vor 1.21.9: nur der Teil des Weltzustands, den BBS liest (Kamera,
 * Spielzeit). Es gibt ein Exemplar je Spiel (GameRendererAlt.getLevelRenderState).
 */
public class LevelRenderState
{
    public static final LevelRenderState INSTANZ = new LevelRenderState();

    public CameraRenderState cameraRenderState = new CameraRenderState();
    public long gameTime;
    /** Leer: 1.21.4 sammelt keine Entitaetszustaende vorab (nur der Vulkan-Schattenweg liest das). */
    public final java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState> entityRenderStates = java.util.List.of();
}
