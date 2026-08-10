package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;

/**
 * Logik hinter {@code ChunkSectionsToRenderMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Spuelt die verzoegerte Form-Durchsichtigkeit, bevor das durchsichtige Gelaende gezeichnet
 * wird — damit BBS-Formen unter Wasser und Glas so mischen wie Vanilla-Entities.</p>
 *
 * <p>⚠️ IRIS' SCHATTENDURCHGANG DARF NICHT SPUELEN. Er zeichnet die Schichten frueh im Bild ein
 * zweites Mal; loeste er das Spuelen aus, schaltete sich die Warteschlange ab, bevor der
 * Hauptdurchgang die Formen ueberhaupt gezeichnet hat.</p>
 *
 * <p>⚠️ GRUPPE, NICHT EBENE. 26.2 uebergibt eine {@link ChunkSectionLayerGroup} (nur OPAQUE und
 * TRANSLUCENT), nicht mehr eine einzelne Ebene mit SOLID/CUTOUT/TRANSLUCENT. Fuer diese Abfrage
 * aendert das nichts — sie haengt ohnehin nur an TRANSLUCENT.</p>
 */
public final class ChunkSectionsToRenderLogik
{
    private ChunkSectionsToRenderLogik() {}

    /**
     * @param gruppe die Schichtgruppe, die Vanilla gleich zeichnet
     */
    public static void renderGroup(ChunkSectionLayerGroup gruppe)
    {
        if (gruppe == ChunkSectionLayerGroup.TRANSLUCENT && !BBSRendering.isIrisShadowPass())
        {
            FormTranslucentQueue.flush();
        }
    }
}
