package mchorse.bbs_mod.mixin.client.sodium;

import com.afrobama.blendertransform.ChromaHimmelLogik;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chroma-Himmel ohne Gelaende — die Sodium-Haelfte.
 *
 * <p>⛔ DER VANILLA-HAKEN REICHT NICHT (gemessen 2026-09-01, Sodium 0.9.1+mc26.2): mit Sodium
 * laeuft {@code ChunkSectionsToRender.renderGroup} gar nicht, der Gelaende-Anteil im Bild war
 * mit {@code terrain} an und aus derselbe. Sodium zeichnet die Chunk-Schichten in seinem
 * eigenen Renderer; dessen einzige Zeichenmethode ist {@code DefaultChunkRenderer.render}
 * (die Umsetzung von {@code ChunkRenderer.render}).</p>
 *
 * <p>⚠️ {@code require = 0} und {@code remap = false}: ohne Sodium gibt es die Klasse nicht,
 * und Sodium-Namen werden nicht ueber Mojangs Zuordnung uebersetzt. Ohne beides bricht der
 * Start, sobald jemand den Mod ohne Sodium laedt.</p>
 *
 * <p>Der Vanilla-Haken in {@code ChunkSectionsToRenderMixin} bleibt daneben stehen: er ist der
 * richtige Punkt, wenn Sodium NICHT laeuft.</p>
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public class ChromaSodiumGelaendeMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bbs$gelaendeVerbergen(CallbackInfo info)
    {
        if (ChromaHimmelLogik.gelaendeVerbergen())
        {
            info.cancel();
        }
    }
}
