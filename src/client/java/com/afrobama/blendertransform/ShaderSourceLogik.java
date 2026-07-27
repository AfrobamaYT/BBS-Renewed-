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

    /** HEAD von {@code glShaderSource}: baut den quelltext-string und meldet ihn. */
    public static void shaderSource(int shader, java.util.List<String> source)
    {
        if (!com.afrobama.bbstools.client.debug.master.shader.ShaderDebugger.ENABLED) return;

        try
        {
            StringBuilder sb = new StringBuilder(2048);

            if (source != null)
            {
                for (String chunk : source)
                {
                    if (chunk != null) sb.append(chunk);
                }
            }

            com.afrobama.bbstools.client.debug.master.shader.ShaderDebugger.onShaderSource(shader, sb);
        }
        catch (Throwable ignored)
        {
            /* Nie den GL-aufruf abreissen. */
        }
    }
}
