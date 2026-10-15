package mchorse.bbs_mod.compat.alt;

/** Mixin-Ziele, die sich zwischen Versionen nur im Text unterscheiden (je Stufe ersetzt). */
public final class MixinZiele
{
    public static final String PERSPEKTIVE = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;";

    public static final String ABSCHNITT_EBENE = "renderSectionLayer";

    private MixinZiele()
    {}
}
