package com.afrobama.blendertransform;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

/**
 * Logik des Picker-Skip-Animate — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code ModelFormRendererPickerSkipAnimateMixin} (2026-07-29,
 * Aufgabe #56 „alle Mixins auf die Brücke"). Der gewobene Handler ist danach ein
 * Einzeiler; alles, was man beim Tunen anfassen will, steht hier und geht per Hotswap
 * live rein.</p>
 *
 * <p><b>Wofür der Skip da ist:</b> BBS' Stencil-Picker (Hover/Klick im Editor) rendert
 * JEDES ausgewählte Replay durch den vollen Form-Pfad, inklusive Animator. Bei 1500
 * ausgewählten Replays sind das 1500 volle Animator-Läufe pro Frame → 0 fps. Der Picker
 * braucht aber nur eine <em>Silhouette</em>: die Weltposition kommt aus dem Matrix-Stack,
 * nicht aus den Knochen. Also wird im Pick-Pass die Animation übersprungen.</p>
 *
 * <p>⛔ ENG GESCHNITTEN: nur wenn {@code context.stencilMap != null} — das ist der
 * Pick-Pass. Der sichtbare Render (stencilMap == null) animiert exakt wie vorher, es
 * ändert sich also nichts, was der Nutzer sieht.</p>
 *
 * <p>⚠ WENN UPSTREAM {@code evaluateChannels} ERWEITERT: der überspringende Zweig baut
 * die Methode ohne {@code applyActions} nach (resetPose + applyPose). Kommt dort ein
 * dritter Schritt dazu, fehlt er hier. {@code tools/mixin-ziele.py} meldet ein
 * VERSCHWUNDENES Ziel, nicht ein erweitertes — diese Stelle muss von Hand mitgezogen
 * werden.</p>
 */
public final class ModelFormRendererPickerSkipAnimateLogik
{
    private ModelFormRendererPickerSkipAnimateLogik() {}

    /**
     * Entscheidet UND führt aus: {@code true} heißt „ich habe den Pick-Pass erledigt,
     * der Handler soll evaluateChannels NICHT rufen".
     *
     * <p>Beides zusammen in einer Methode, damit der gewobene Handler wirklich nichts
     * mehr enthält, was man tunen möchte. Eine reine Ja/Nein-Abfrage hätte den
     * Rest-Pose-Zweig im ungewobenen Teil zurückgelassen — und genau der ist die Stelle,
     * die bei einem Upstream-Umbau nachgezogen werden muss.</p>
     */
    public static boolean ueberspringen(ModelFormRenderer self, ModelInstance model, FormRenderingContext context)
    {
        if (!BBSPerfConfig.ENABLE_ARMY_BAKE
            || !BBSPerfConfig.ENABLE_PICKER_SKIP_ANIMATE
            || context == null
            || context.stencilMap == null)
        {
            return false;
        }

        BBSPerfConfig.pickerAnimateSkips++;

        /* Das ist evaluateChannels OHNE applyActions — die beiden anderen Schritte müssen
         * bleiben, sonst behält das Modell die Pose des zuletzt gerenderten Replays. */
        model.model.resetPose();
        model.model.applyPose(self.getPose());

        return true;
    }
}
