package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.fracture.FractureRenderContext;
import com.afrobama.bbstools.client.fracture.FracturedBoneRegistry;
import com.afrobama.bbstools.client.mixin.BOBJModelVAOAccessor;

import mchorse.bbs_mod.forms.entities.IEntity;

import org.lwjgl.opengl.GL30;

import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Logik des BOBJ-fracture-mixins — herausgelöst aus {@code BOBJModelVAOFractureMixin}
 * (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>Überspringt beim zeichnen dreiecke, deren dominanter knochen "fracturiert" wurde —
 * vom actor gelöst und als eigenständiger PhysicsMod-rigidbody neu gespawnt. Zwei
 * ergänzende injektionen decken sowohl den bulk-draw-fastpath als auch den
 * per-bone-textur-slowpath ab.</p>
 *
 * <h2>Fall 1 — predicate in {@code drawTriangles} wrappen</h2>
 * Der slowpath ({@code drawTriangles(predicate)}) wird genutzt, wenn das modell knochen mit
 * textur-overrides hat. Das predicate wird am HEAD abgefangen und mit dem fracture-filter
 * UND-verknüpft — fracturierte knochen liefern false, ihre dreiecke werden also in der
 * per-bone-GL-übermittlung übersprungen.
 *
 * <h2>Fall 2 — bulk {@code glDrawArrays} in {@code render} umleiten</h2>
 * Der fastpath ({@code glDrawArrays(GL_TRIANGLES, 0, count)}, zeichnet JEDES dreieck) greift,
 * wenn das modell keine per-bone-textur-overrides hat. Hat der aktive actor fracturierte
 * knochen, läuft eine per-bone-schleife, die sie überspringt; sonst läuft der originale
 * bulk-draw unverändert (kein overhead für actors, die nicht fracturiert werden).
 *
 * <p><b>Identität:</b> der gerenderte {@code IEntity} kommt über {@link
 * FractureRenderContext} (gesetzt von {@code ModelFormRendererFractureCaptureMixin} an
 * render3D HEAD/TAIL). Ist er null (z.B. preview-render ausserhalb der actor-pipeline),
 * fallen beide fälle auf "kein filter" zurück und das modell rendert normal.</p>
 */
public final class BOBJModelVAOFractureLogik
{
    private BOBJModelVAOFractureLogik() {}

    public static IntPredicate wrapPredicateWithFractureFilter(IntPredicate original)
    {
        IEntity actor = FractureRenderContext.current();
        if (actor == null) return original;

        Set<Integer> fractured = FracturedBoneRegistry.snapshot(actor);
        if (fractured.isEmpty()) return original;

        return (bone) -> original.test(bone) && !fractured.contains(bone);
    }

    /**
     * Der bulk-fastpath-aufruf innerhalb von {@code render(...)}. Wenn KEINE knochen
     * fracturiert sind, bleibt der originale bulk-draw (ein GL-aufruf, kein overhead).
     * Sind EINIGE knochen fracturiert, muss durch eine per-dreieck-schleife geroutet
     * werden — inline implementiert statt die private drawTriangles zu rufen, weil das
     * "this" der {@code @Redirect}-ziel-referenz einen accessor bräuchte und inline diese
     * komplexität vermeidet.
     *
     * @param zielVao die VAO-instanz als accessor, um {@code dominantBonePerTriangle} zu lesen
     * @return {@code true}, wenn hier gezeichnet wurde
     *
     * <p>⚠ DER RÜCKGABEWERT IST NICHT KOSMETIK. Der aufrufer ist ein {@code @Redirect}, der
     * den ursprünglichen {@code glDrawArrays}-aufruf ERSETZT. Kommt die brücke nicht durch
     * (kein agent, kernel-klasse nicht ladbar, exception), liefert sie {@code null} — und
     * ohne dieses signal wüsste der handler nicht, dass niemand gezeichnet hat. Das modell
     * verschwände lautlos. Deshalb meldet jeder pfad hier {@code true}, und der handler
     * holt den bulk-draw nach, wenn er das nicht bekommt (befund aus dem adversarischen
     * review 2026-07-26).</p>
     */
    public static boolean conditionalBulkDraw(BOBJModelVAOAccessor zielVao,
                                              int mode, int first, int count)
    {
        IEntity actor = FractureRenderContext.current();
        Set<Integer> fractured = (actor == null) ? java.util.Collections.emptySet()
                                                 : FracturedBoneRegistry.snapshot(actor);

        if (fractured.isEmpty())
        {
            GL30.glDrawArrays(mode, first, count);

            return true;
        }

        /* Slowpath: dominantBonePerTriangle (via accessor gelesen) durchlaufen, per-bone-
         * läufe als glDrawArrays-aufrufe emittieren, die fracturierte knochen überspringen.
         * count ist die gesamt-vertex-zahl = triangleCount * 3, also ist die länge des
         * per-dreieck-bone-arrays = count / 3. */
        int[] bones = zielVao.bbstools$getDominantBonePerTriangle();
        if (bones == null)
        {
            /* Fallback, falls accessor nicht verfügbar — bulk rendern, um black-screen bei
             * fehlgeschlagener accessor-injektion zu vermeiden. */
            GL30.glDrawArrays(mode, first, count);

            return true;
        }

        int start = -1;
        for (int i = 0; i < bones.length; i++)
        {
            boolean draw = !fractured.contains(bones[i]);

            if (draw && start == -1)
            {
                start = i;
            }
            else if (!draw && start != -1)
            {
                GL30.glDrawArrays(GL30.GL_TRIANGLES, first + start * 3, (i - start) * 3);
                start = -1;
            }
        }

        if (start != -1)
        {
            GL30.glDrawArrays(GL30.GL_TRIANGLES, first + start * 3, (bones.length - start) * 3);
        }

        /* Auch der slowpath hat gezeichnet — der handler darf NICHT nochmal bulk-drawen,
         * sonst lägen die fracturierten dreiecke wieder drüber. */
        return true;
    }
}
