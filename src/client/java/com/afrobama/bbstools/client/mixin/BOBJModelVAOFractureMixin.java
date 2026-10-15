package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.mojang.blaze3d.systems.RenderPass;

import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Laesst dreiecke aus, deren dominanter knochen "fracturiert" wurde — vom actor geloest und
 * als eigenstaendiger PhysicsMod-rigidbody neu gespawnt.
 *
 * <h2>26.2: aus zwei einhaengungen ist eine geworden</h2>
 *
 * <p>Frueher gab es zwei zeichenwege in {@link BOBJModelVAO} und darum zwei injektionen:</p>
 * <ul>
 *   <li>{@code drawTriangles(IntPredicate)} — der langsame weg fuer modelle mit
 *       textur-overrides je knochen. Das predicate wurde am HEAD abgefangen und mit dem
 *       fracture-filter UND-verknuepft.</li>
 *   <li>{@code render(...)} — der schnelle weg, ein einziges {@code glDrawArrays} ueber alle
 *       dreiecke. Dieser aufruf wurde umgeleitet.</li>
 * </ul>
 *
 * <p><b>Beide methoden gibt es nicht mehr</b>, und der predicate-weg hat auch keinen
 * nachfolger: 26.2 kennt kein rohes GL mehr, der VAO legt seine puffer an eine pipeline und
 * schickt EINEN zug ab ({@code zeichnen(RenderPass)} -> {@code pass.draw(...)}). Damit faellt
 * die erste injektion ersatzlos weg — sie deckte einen zweig ab, den es nicht mehr gibt —,
 * und die zweite haengt jetzt an diesem einen zug. Seit dem Vitrail-Port liegt dieser
 * in BOBJDrawInputs.draw; dessen owner ist die urspruengliche VAO. Inhaltlich verliert das nichts, im
 * gegenteil: {@code BOBJModelSimpleVAO} erbt {@code zeichnen} und ist damit mit abgedeckt.</p>
 *
 * <p>Die argumentfolge von {@code RenderPass.draw} ist
 * {@code (eckenZahl, instanzen, ersteEcke, ersteInstanz)} — nachgesehen im OpenGL-unterbau
 * ({@code GlRenderPass.draw} -> {@code executeDraw} -> {@code drawFromBuffers} ->
 * {@code glDrawArraysInstancedBaseInstance(mode, first, count, primcount, baseinstance)}),
 * nicht geraten. Ein per-knochen-lauf ist damit {@code draw(laenge * 3, 1, start * 3, 0)}.</p>
 *
 * <p><b>Identität:</b> der gerenderte IEntity kommt ueber
 * {@code FractureRenderContext} (gesetzt von {@code ModelFormRendererFractureCaptureMixin} an
 * render3D HEAD/TAIL). Ist er null (z.B. preview-render ausserhalb der actor-pipeline), faellt
 * die logik auf "kein filter" zurueck und das modell rendert normal.</p>
 */
@Mixin(targets = "mchorse.bbs_mod.cubic.render.vao.BOBJDrawInputs", remap = false)
public abstract class BOBJModelVAOFractureMixin
{
    @Shadow @Final private BOBJModelVAO owner;
    /**
     * Der einzige zeichen-zug des modells. Ist kein knochen fracturiert, laeuft er unveraendert
     * durch (ein zug, kein aufwand); sonst zerlegt ihn die logik in laeufe, die die
     * fracturierten knochen ueberspringen.
     */
    @Redirect(method = "draw",
              at = @At(value = "INVOKE",
                       target = "Lcom/mojang/blaze3d/systems/RenderPass;draw(IIII)V"))
    private void bbstools$bedingtZeichnen(RenderPass pass, int eckenZahl, int instanzen,
        int ersteEcke, int ersteInstanz)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.BOBJModelVAOFractureLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc.
         *
         * Die urspruengliche VAO (`owner`) geht als Object hinueber, nicht als BOBJModelVAOAccessor: die
         * logik hat den accessor-typ ohnehin nur entgegengenommen und sofort auf BOBJModelVAO
         * zurueckgecastet. Ein CHECKCAST auf ein accessor-interface im heissesten zeichenpfad
         * ist ein risiko ohne gegenwert — faellt der accessor-mixin aus, flaeche das hier pro
         * zug mit einer ClassCastException auf, und die kann `rufe` nicht auffangen, weil sie
         * VOR dem aufruf passiert.
         *
         * ⚠ DER FALLBACK IST PFLICHT (befund aus dem adversarischen review 2026-07-26).
         * Dies ist ein @Redirect: der ERSETZTE aufruf ist der zeichen-zug selbst. Kommt die
         * brücke nicht durch, zeichnet niemand — das modell verschwindet lautlos. Ein
         * @Redirect ohne fallback verhaelt sich bei einem brücken-fehler SCHLECHTER als gar
         * kein mixin.
         *
         * `rufe` liefert bei erfolg `Boolean.TRUE` (die kernel-methode hat gezeichnet), bei
         * jedem fehler `null` — und meldet den fehler EINMAL laut ueber BrueckenMeldung. Nur
         * dann holen wir den zug hier nach. */
        Object gezeichnet = MixinBruecke.rufe("BOBJModelVAOFractureLogik", "bedingtZeichnen",
            this.owner, pass, eckenZahl, instanzen, ersteEcke, ersteInstanz);

        if (!(gezeichnet instanceof Boolean b) || !b)
        {
            pass.draw(eckenZahl, instanzen, ersteEcke, ersteInstanz);
        }
    }

}
