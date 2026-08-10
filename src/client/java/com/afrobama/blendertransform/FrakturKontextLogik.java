package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.fracture.FractureRenderContext;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

/**
 * Logik hinter {@code ModelFormRendererFractureCaptureMixin}, herausgeloest damit sie
 * hotswapbar ist.
 *
 * <p>Merkt sich waehrend {@code render3D}, zu welcher Entity die gerade gezeichneten Knochen
 * gehoeren — die Bruch-Simulation braucht diese Zuordnung, der Renderer reicht sie aber nicht
 * durch.</p>
 *
 * <p>⚠️ PUSH UND POP GEHOEREN ZUSAMMEN, wie bei einem Stapel. Bleibt ein Push stehen, ordnet die
 * naechste Entity ihre Bruchstuecke der vorigen zu. Deshalb haengt {@link #ablegen()} am RETURN
 * derselben Methode und nicht an einer spaeteren Stelle. (Die Gegenprobe dazu steht im
 * Actor-Absturz: dort war es ein PoseStack, und ein fehlender Pop kostete eine halbe Nacht.)</p>
 */
public final class FrakturKontextLogik
{
    private FrakturKontextLogik() {}

    /** {@code render3D} @HEAD */
    public static void merken(FormRenderingContext kontext)
    {
        if (kontext != null)
        {
            FractureRenderContext.push(kontext.entity);
        }
    }

    /** {@code render3D} @RETURN */
    public static void ablegen()
    {
        FractureRenderContext.pop();
    }
}
