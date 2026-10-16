package com.afrobama.blendertransform;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.afrobama.bbstools.client.fracture.FractureRenderContext;
import com.afrobama.bbstools.client.fracture.FracturedBoneRegistry;
import com.afrobama.bbstools.client.mixin.BOBJModelVAOAccessor;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;

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
     * @param zielVao die VAO-instanz (der accessor liefert uns die typ-brücke; die knochen-
     *                tabelle rechnen wir selbst aus vao.data, siehe dominanteKnochen())
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
    /**
     * Zeichnet das Modell und laesst gebrochene Knochen aus -- die 26.2-Fassung.
     *
     * ⛔ WARUM ES DIESE METHODE GIBT. Das Mixin wurde beim Port auf die neue Signatur
     * gezogen (RenderPass statt glDrawArrays, deutsche Namen), die Logikklasse nicht. Der
     * Brueckenaufruf suchte seither `bedingtZeichnen` mit sechs Argumenten und fand nur
     * `conditionalBulkDraw` mit vier. Ergebnis im Log, bei JEDEM Start:
     *
     *     MixinBruecke: keine STATISCHE methode mit passender parameter-zahl --
     *     BOBJModelVAOFractureLogik#bedingtZeichnen. Der handler ist WIRKUNGSLOS.
     *
     * Das Fracture-feature war damit seit dem Port tot -- ohne dass etwas abstuerzte. Der
     * Fehler stand die ganze Zeit im Log (Aufgabe 100).
     *
     * <p>⚠️ RUECKGABE UMGEKEHRT ZUR ALTEN FASSUNG. Die alte zeichnete im Normalfall selbst
     * und meldete "erledigt". Hier meldet {@code false} "ich habe nichts getan, zeichne du"
     * -- genau das erwartet der Aufrufer, und es spart den Normalfall komplett.
     *
     * <p>{@code draw} nimmt (Eckenzahl, Instanzen, erste Ecke, erste Instanz) -- an der
     * laufenden JVM abgefragt, nicht geraten.
     */
    public static boolean bedingtZeichnen(Object zielVao, RenderPass durchgang,
        int eckenZahl, int instanzen, int ersteEcke, int ersteInstanz)
    {
        IEntity actor = FractureRenderContext.current();
        Set<Integer> gebrochen = (actor == null) ? java.util.Collections.emptySet()
                                                 : FracturedBoneRegistry.snapshot(actor);

        if (gebrochen.isEmpty())
        {
            return false;
        }

        int[] knochen = dominanteKnochen((BOBJModelVAO) zielVao, eckenZahl);

        if (knochen == null)
        {
            return false;
        }

        int start = -1;

        for (int i = 0; i < knochen.length; i++)
        {
            boolean zeichnen = !gebrochen.contains(knochen[i]);

            if (zeichnen && start == -1)
            {
                start = i;
            }
            else if (!zeichnen && start != -1)
            {
                durchgang.draw((i - start) * 3, instanzen, ersteEcke + start * 3, ersteInstanz);
                start = -1;
            }
        }

        if (start != -1)
        {
            durchgang.draw((knochen.length - start) * 3, instanzen,
                ersteEcke + start * 3, ersteInstanz);
        }

        /* Wir HABEN gezeichnet -- der Aufrufer darf nicht nochmal, sonst laegen die
         * gebrochenen Dreiecke wieder darueber. */
        return true;
    }

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
        /* AUTONOMIE 2026-07-27: lokal berechnet statt per @Accessor gelesen — siehe
         * dominanteKnochen() unten. Das feld dominantBonePerTriangle gibt es im fs-kern nicht. */
        int[] bones = dominanteKnochen((BOBJModelVAO) (Object) zielVao, count);
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
    /* AUTONOMIE 2026-07-27: dominanten knochen je dreieck SELBST berechnen.
     *
     * Der CML-fork hielt dafuer ein feld `dominantBonePerTriangle` auf BOBJModelVAO, das ein
     * @Accessor auslas. Im fs-kern gibt es dieses feld nicht — der accessor fand es nicht und
     * riss den ganzen mixin mit (und damit das fracture-feature). Die rohdaten liegen aber
     * offen: `vao.data.weightData` und `vao.data.boneIndexData` sind public. Also rechnen wir
     * es hier aus, einmal je VAO, gehalten in einer schwachen map — identische mathematik wie
     * CMLs buildDominantBones(). */
    private static final java.util.Map<Object, int[]> KNOCHEN_CACHE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static int[] dominanteKnochen(BOBJModelVAO vao, int count)
    {
        if (vao == null || vao.data == null
            || vao.data.weightData == null || vao.data.boneIndexData == null)
        {
            return null;                       /* aufrufer rendert dann bulk (kein black screen) */
        }

        int[] zwischen = KNOCHEN_CACHE.get(vao);

        if (zwischen != null && zwischen.length == count / 3)
        {
            return zwischen;
        }

        int[] proDreieck = new int[count / 3];

        for (int dreieck = 0; dreieck < proDreieck.length; dreieck++)
        {
            int basis = dreieck * 3;
            int a = dominanterKnochen(vao, basis);
            int b = dominanterKnochen(vao, basis + 1);
            int c = dominanterKnochen(vao, basis + 2);

            /* Mehrheit der drei ecken gewinnt; bei drei verschiedenen die erste — wie CML. */
            proDreieck[dreieck] = (a == b || a == c) ? a : (b == c ? b : a);
        }

        KNOCHEN_CACHE.put(vao, proDreieck);

        return proDreieck;
    }

    /** Der knochen mit dem groessten gewicht an diesem vertex, oder -1 wenn keiner greift. */
    private static int dominanterKnochen(BOBJModelVAO vao, int vertex)
    {
        int basis = vertex * 4;
        float max = -1F;
        int knochen = -1;

        for (int i = 0; i < 4; i++)
        {
            if (basis + i >= vao.data.weightData.length) break;

            float gewicht = vao.data.weightData[basis + i];
            int index = vao.data.boneIndexData[basis + i];

            if (index >= 0 && gewicht > max)
            {
                max = gewicht;
                knochen = index;
            }
        }

        return knochen;
    }

}
