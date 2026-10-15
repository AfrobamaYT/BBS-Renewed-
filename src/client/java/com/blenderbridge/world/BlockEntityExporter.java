// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 2 world export).
//
// Der ursprüngliche mitschnitt-ansatz (block-entity-renderer in einen eigenen puffer zeichnen
// lassen) stammt konzeptionell von Bret-B/world_exporter (MIT-lizenz, branch 1.16.5,
// ExporterRunnable.java) — Copyright (c) 2020 Bret-B. Der lizenztext stand im kopf der
// 1.21.1-fassung dieser datei; die steht in der git-historie dieses zweigs (port-26.2), letzter
// stand vor diesem commit. Er ist hier gekürzt, weil von dem code nichts mehr ausgeführt wird.
package com.blenderbridge.world;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * BlockEntityExporter — ⛔ NICHT AUF 26.2 PORTIERT. Diese klasse schreibt derzeit KEINE geometrie:
 * kisten, betten, schilder, banner, glocken, lesepulte, shulker-kisten, verzierte töpfe und
 * verzauberungstische fehlen im export.
 *
 * <h2>WAS SIE TAT</h2>
 * <p>Diese blöcke liefern aus ihrem block-modell KEINE quads — vanilla zeichnet sie zur laufzeit
 * aus einem {@code BlockEntityRenderer} (BER). {@link WorldExporter}s normaler weg emittierte für
 * sie also null flächen, und zwar wortlos. Der fix war: den BER selbst aufrufen, ihm statt der GPU
 * einen eigenen, mitschneidenden puffer unterschieben und die vertices in 4er-gruppen als quads
 * zurücklesen. Dazu gehörte das ausschreiben ganzer gestitchter atlanten als PNG (chest.png,
 * beds.png, signs.png …), weil die UVs, die beim mitschnitt ankommen, ATLAS-UVs sind.</p>
 *
 * <h2>WARUM DAS IN 26.2 NICHT MEHR ÜBERSETZBAR IST (am 26.2-jar gemessen, javap)</h2>
 * <p>Es sind nicht umbenannte methoden, sondern eine andere architektur:</p>
 * <ol>
 * <li>{@code SubmitNodeCollector} EXISTIERT NICHT MEHR. Das war der haken, an dem der ganze
 *     mitschnitt hing ("gib mir für diesen layer einen VertexConsumer").</li>
 * <li>{@code BlockEntityRenderer.render(entity, tickDelta, poseStack, buffers, light, overlay)}
 *     existiert nicht mehr. Das interface hat jetzt zwei phasen:
 *     {@code createRenderState()} / {@code extractRenderState(entity, state, …)} und
 *     {@code submit(state, poseStack, SubmitNodeCollector, CameraRenderState)}. Ein BER zeichnet
 *     also nicht mehr, er MELDET AN — die vertices entstehen später, tiefer in der pipeline.</li>
 * <li>{@code Sheets.chestSheet()}, {@code bedSheet()}, {@code signSheet()},
 *     {@code Sheets.BED_SHEET}, {@code SIGN_SHEET} und die generischen
 *     {@code RenderType.entityCutoutNoCull/entitySmoothCutout/entityDecal/entityNoOutline}
 *     existieren nicht mehr. Damit ist die ganze "RenderType-instanz → atlas"-antworttabelle
 *     dieser klasse gegenstandslos: {@code Sheets} arbeitet jetzt mit {@code SpriteMapper} und
 *     {@code SpriteId}, die factories liegen in {@code RenderTypes} (anderes paket) und decken
 *     einen anderen satz ab.</li>
 * </ol>
 *
 * <h2>DER WEG, DER STATTDESSEN GANGBAR IST (recherchiert, NICHT gebaut)</h2>
 * <p>Er existiert — "geht nicht" wäre falsch. Er ist aber ein NEUBAU, keine übersetzung, und war
 * nicht beauftragt. Skizze mit den gemessenen anknüpfungspunkten:</p>
 * <ol>
 * <li>{@code BlockEntityRenderDispatcher.tryExtractRenderState(be, tickDelta, crumbling, bool)}
 *     liefert den render-state; {@code getRenderer(be)} den passenden BER.</li>
 * <li>Einen eigenen {@code SubmitNodeCollector} implementieren (erweitert
 *     {@code OrderedSubmitNodeCollector}, ~20 methoden) und dem BER in {@code submit(...)}
 *     unterschieben.</li>
 * <li>In den drei relevanten methoden dieses collectors die geometrie selbst erzeugen:
 *     {@code submitModel(Model, S, PoseStack, RenderType, …)} → {@code Model.renderToBuffer(...)}
 *     in unseren mitschnitt-consumer; {@code submitModelPart(ModelPart, PoseStack, RenderType,
 *     int, int, TextureAtlasSprite)} → {@code ModelPart.render(...)} ebenso;
 *     {@code submitBlockModel(PoseStack, RenderType, List&lt;net.minecraft.client.renderer.block.model.BlockModelPart&gt;, …)} → über
 *     dieselbe quad-schleife wie {@link WorldExporter}.</li>
 * <li>GEWINN GEGENÜBER FRÜHER: das sprite kommt bei {@code submitModel}/{@code submitModelPart}
 *     als PARAMETER herein. Die atlas-rätselei (memoize-tabelle, ganze atlanten dumpen) entfällt
 *     komplett — man kann pro sprite exportieren wie beim block-pfad.</li>
 * <li>Offen bleibt der {@code CameraRenderState}, den {@code submit} verlangt: er muss gebaut oder
 *     leer übergeben werden, und ob jeder vanilla-BER das verträgt, ist nur am laufenden spiel zu
 *     messen.</li>
 * </ol>
 *
 * <h2>WAS DIESE FASSUNG STATTDESSEN TUT</h2>
 * <p>{@link #emit} gibt 0 zurück und meldet das EINMAL pro spielstart als WARNUNG im log. Genau
 * das ist der unterschied zu dem bug, gegen den diese klasse einmal gebaut wurde: damals fehlten
 * die kisten SCHWEIGEND. Sie fehlen weiterhin — aber das log sagt es, und dieser klassenkopf sagt
 * warum. Die signatur ist unverändert, damit {@link WorldExporter} und
 * {@code StreamingWorldExporter} unberührt bleiben.</p>
 */
public final class BlockEntityExporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Damit die meldung EINMAL kommt und nicht einmal pro block. Reiner schalter, kein zustand,
     * der zurückgesetzt werden müsste — die lücke ist bis zum port dauerhaft.
     */
    private static boolean reported;

    private BlockEntityExporter()
    {
        /* static-only helper */
    }

    /**
     * Würde die block-entity an {@code pos} in die gerade geschriebene OBJ exportieren.
     *
     * <p>⛔ TUT DAS IN 26.2 NICHT — siehe klassendoc. Gibt immer 0 zurück und meldet die lücke
     * beim ersten aufruf im log.</p>
     *
     * <p>Signatur, {@code throws} und rückgabe-bedeutung sind ABSICHTLICH unverändert: die
     * aufrufer ({@link WorldExporter}s block-schleife, {@code StreamingWorldExporter}s
     * spalten-nachlauf) rechnen mit "wie viele quads wurden geschrieben" und haben ihre
     * index-buchführung darauf aufgebaut. 0 heißt dort korrekt "nichts geschrieben, indizes
     * bleiben stehen".</p>
     *
     * @return immer 0
     */
    public static int emit(Minecraft client, ClientLevel world, BlockPos pos, File outDir, BufferedWriter obj, int baseVertex, int baseUv, int baseNormal) throws IOException
    {
        if (!reported)
        {
            reported = true;

            LOGGER.warn("[blenderbridge] block-entity export ist auf 26.2 NICHT portiert — kisten, betten, schilder, banner, glocken, lesepulte, shulker-kisten und verzierte töpfe fehlen in jedem welt-export. Grund: SubmitNodeCollector und BlockEntityRenderer.render existieren in 26.2 nicht mehr (submit-node-pipeline). Der ersatz-weg steht im klassendoc von BlockEntityExporter.");
        }

        return 0;
    }
}
