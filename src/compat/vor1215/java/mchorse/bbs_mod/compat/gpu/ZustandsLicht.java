package mchorse.bbs_mod.compat.gpu;

/**
 * Das Licht der gerade gezeichneten Entitaet vor 1.21.5. Ab 1.21.9 steht es als lightCoords am
 * Render-Zustand; 1.21.4 reicht es als Argument an EntityRenderer.render, wo
 * EntityRenderDispatcherMixin es hier ablegt (nur Renderthread).
 */
public final class ZustandsLicht
{
    public static int aktuell;

    private ZustandsLicht()
    {}
}
