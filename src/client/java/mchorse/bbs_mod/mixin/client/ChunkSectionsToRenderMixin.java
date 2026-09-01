package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Der ehemalige {@code LevelRenderer.renderSectionLayer}-Haken, hierher umgezogen.
 *
 * <p>26.2 zeichnet die Chunk-Schichten nicht mehr im {@code LevelRenderer}, sondern in dieser
 * Klasse. Das ist kein Umbenennen, sondern ein Klassenwechsel -- deshalb eine eigene Datei
 * und ein eigener Eintrag in der Mixin-Liste.</p>
 *
 * <h2>⚠️ GRUPPE statt EBENE -- der Unterschied ist wichtig</h2>
 *
 * <p>Der alte Haken bekam eine einzelne {@code ChunkSectionLayer} (SOLID / CUTOUT /
 * TRANSLUCENT). {@code renderGroup} bekommt eine {@link ChunkSectionLayerGroup}, und davon
 * gibt es nur ZWEI: {@code OPAQUE} und {@code TRANSLUCENT}. Die alte Trennung zwischen SOLID
 * und CUTOUT existiert auf dieser Ebene also nicht mehr -- beide stecken in OPAQUE.</p>
 *
 * <p>Fuer den einen Haken, der SOLID abfragte, aendert das nichts Sichtbares: er reichte die
 * Matrix an einen Weg weiter, der inzwischen stillgelegt ist (siehe
 * {@code BBSRendering.onRenderChunkLayer}). Fuer die Warteschlange aendert es nichts, weil sie
 * ohnehin nur an TRANSLUCENT haengt.</p>
 */
@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderMixin
{
    /**
     * Die verzoegerte Form-Durchsichtigkeit spuelen, bevor das durchsichtige Gelaende
     * gezeichnet wird -- damit BBS-Formen unter Wasser und Glas so mischen wie Vanilla-Entities.
     *
     * <p>⚠️ Iris' Schattendurchgang zeichnet die Schichten frueh im Bild ein zweites Mal. Sein
     * durchsichtiger Durchgang darf das Spuelen NICHT ausloesen, sonst schaltet sich die
     * Warteschlange ab, bevor der Hauptdurchgang die Formen ueberhaupt gezeichnet hat.</p>
     */
    @Inject(method = "renderGroup", at = @At("HEAD"))
    public void onRenderGroup(ChunkSectionLayerGroup gruppe, com.mojang.blaze3d.textures.GpuSampler abtaster, CallbackInfo info)
    {
        MixinBruecke.rufe("ChunkSectionsToRenderLogik", "renderGroup", gruppe);
    }

}
