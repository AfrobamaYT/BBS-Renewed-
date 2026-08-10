package com.afrobama.blendertransform;

import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.QueueMap;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;

import java.util.List;
import java.util.Map;

/**
 * Logik hinter den Iris-Mixins rund um das SHADERPAKET — Laden, Quelltext, Optionen.
 *
 * <p>Vier Mixins, ein Lebenslauf: {@code IrisMixin} (Paket laden/abschalten, Optionsreihenfolge),
 * {@code ShaderPackMixin} (Eigenschaften uebernehmen), {@code JcppProcessorMixin} (Quelltext
 * vorbereiten), {@code CustomUniformsBuilderMixin} (Uniforms anmelden).</p>
 *
 * <p>⛔ ALLES HIER STEHT AUF IRIS-TYPEN, und Iris ist eine Abhaengigkeit, die schon einmal
 * stillgelegt war. Diese Klasse laedt ohne Iris nicht — ungefaehrlich, weil sie nur aus
 * Mixins gerufen wird, die es ohne Iris ebenfalls nicht gibt. Wie bei
 * {@link PhysikModLogik} gilt: die Fremdtypen bleiben in EINER Datei.</p>
 */
public final class ShaderPaketLogik
{
    private ShaderPaketLogik() {}

    /**
     * {@code Iris.getShaderPackOptionQueue} @RETURN.
     *
     * <p>⚠️ DIE REIHENFOLGE IST DER ZWECK. Iris gibt eine gewoehnliche Map zurueck; BBS legt
     * eine {@link QueueMap} darueber, damit die Optionen in der Reihenfolge ihrer Eintragung
     * bleiben. Ohne das springen die Regler im Shader-Menue bei jedem Oeffnen umher.</p>
     *
     * @return die umhuellte Map, oder {@code null} wenn Iris {@code null} lieferte
     */
    public static Map<String, String> optionsreihenfolge(Map<String, String> vonIris)
    {
        return vonIris == null ? null : new QueueMap<>(vonIris);
    }

    /**
     * {@code loadExternalShaderpack} @HEAD und {@code setShadersDisabled} @HEAD.
     *
     * <p>⚠️ BEIDE WEGE MUESSEN ZURUECKSETZEN. Ein Paket wechseln und die Shader ganz
     * abschalten enden im selben Zustand; faengt man nur den ersten ab, behaelt BBS die
     * Kurven-Variablen des alten Pakets und schickt sie an das neue.</p>
     */
    public static void paketZuruecksetzen()
    {
        ShaderCurves.reset();
    }

    /** {@code loadExternalShaderpack} @RETURN (ordinal 9) — das Paket steht. */
    public static void paketFertig()
    {
        ShaderCurves.finishLoading();
    }

    /** {@code ShaderPack.<init>} — die Eigenschaften des frisch gebauten Pakets uebernehmen. */
    public static void eigenschaften(ShaderProperties eigenschaften)
    {
        IrisUtils.setShaderProperties(eigenschaften);
    }

    /**
     * {@code JcppProcessor.glslPreprocessSource} @HEAD — BBS' Kurven-Uniforms in den
     * Shader-Quelltext einsetzen, BEVOR der Praeprozessor laeuft.
     *
     * @return der veraenderte Quelltext; nie {@code null}, der Aufrufer setzt ihn direkt ein
     */
    public static String quelltext(String original)
    {
        return ShaderCurves.processSource(original);
    }

    /**
     * {@code SliderElementWidget.whileDragging} @TAIL — soll der Regler nachgezogen werden?
     *
     * <p>⚠️ EIN EINZEILER MIT ABSICHT. Der eigentliche Aufruf ({@code bbs$queue()}) geht auf
     * eine Mixin-Schnittstelle der Zielklasse und kann hier nicht stehen. Was hierher gehoert,
     * ist die ENTSCHEIDUNG — heute immer ja, aber sie ist damit aenderbar, ohne die Webung
     * anzufassen (etwa um beim Ziehen zu drosseln).</p>
     */
    public static boolean reglerNachziehen()
    {
        return true;
    }

    /**
     * {@code CustomUniforms.Builder.build} @RETURN.
     *
     * <p>Die Liste kommt aus {@code CustomUniformsAccessor.bbs$uniformOrder()} — ein
     * Mixin-Accessor, der im Mixin bleibt; hierher kommt nur die Liste.</p>
     */
    public static void uniformsAnmelden(List<CachedUniform> reihenfolge)
    {
        ShaderCurves.addUniforms(reihenfolge);
    }
}
