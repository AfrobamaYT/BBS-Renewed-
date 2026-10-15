package mchorse.bbs_mod.compat.alt;

/** vor1212: Mixin-Ziele von 1.21.1 (siehe vor261/MixinZiele). */
public final class MixinZiele
{
    public static final String PERSPEKTIVE = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;";
    public static final String WELT_ZEICHNEN = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";

    public static final String ABSCHNITT_EBENE = "renderSectionLayer";

    private MixinZiele()
    {}
}
