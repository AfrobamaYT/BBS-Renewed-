package com.afrobama.blendertransform;

/**
 * Logik von {@code MixinGlStateManager_ShaderEvents#bbstools$shaderSource} — im kernel-paket,
 * damit sie hotswapbar ist.
 *
 * <p>BRÜCKEN-MUSTER (2026-07-26): der handler wird beim klassenladen in
 * {@code GlStateManager} gewoben und liegt damit im {@code KnotClassLoader} — vom kernel-swap
 * unerreichbar. Nur {@code glShaderSource} wanderte hierher (der einzige der vier handler in
 * der datei, der über der gate-schwelle lag); die anderen drei bleiben ein-zeilige
 * {@code try/catch}-aufrufe direkt im mixin.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@code List<String>} ist JDK, erlaubt.</p>
 */
public final class ShaderSourceLogik
{
    private ShaderSourceLogik() {}

    /**
     * HEAD von {@code glShaderSource}: meldet den quelltext des shaders.
     *
     * <p>⛔ 26.2 REICHT EINEN STRING, NICHT MEHR EINE LISTE (2026-08-02 per javap gemessen:
     * {@code public static void glShaderSource(int, java.lang.String)}). Bis 1.21.1 nahm die
     * methode eine {@code List<String>} aus mehreren teilstuecken, die hier
     * zusammengehaengt wurden.</p>
     *
     * <p>Das war nicht nur eine typaenderung: der zugehoerige injektionspunkt im mixin trug
     * weiterhin den alten deskriptor {@code (ILjava/util/List;)V} und traf damit ins leere.
     * Weil er {@code require = 0} hat, schwieg er beim start — der shader-mitschnitt war
     * seit dem port tot, ohne eine einzige logzeile.</p>
     */
    public static void shaderSource(int shader, String source)
    {
        /* ⛔ KEIN SCHALTER MEHR DAVOR: die Standardfassung der Sonde nimmt die Quelle
         * entgegen und wirft sie weg, und der JIT setzt den Aufruf dann weg. Der frueher
         * hier abgefragte ShaderDebugger.ENABLED liegt in der Companion. */
        com.afrobama.bbstools.sonden.Meldesonde.HALTER.aktiv.schattierung(shader, source);
    }
}
