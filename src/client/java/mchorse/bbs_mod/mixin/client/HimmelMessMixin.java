package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⚗️ MESSFUEHLER, kein Feature -- laeuft nur mit eingeschaltetem {@code Messpunkt}.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Der Himmel fehlt im 26.2-Port vollstaendig: oberer Bildrand {@code #171A1F} statt
 * {@code #ADCBFF}, in der ganzen Welt, bei jeder Fenstergroesse. Die gesamte EINGABESEITE ist
 * zweifach ausgeschlossen und in Ordnung:
 *
 * <ul>
 * <li>{@code LevelRenderer.render} bekommt das Himmel-Flag 124x {@code true}, nie {@code false}</li>
 * <li>die uebergebene Nebelfarbe ist {@code 0.678, 0.796, 1.0} = genau {@code #ADCBFF}</li>
 * <li>{@code EnvironmentAttributes.SKY_COLOR} liefert {@code #78A7FF}, also ein sattes Blau</li>
 * <li>die Pipelines {@code SKY}, {@code CLOUDS}, {@code CELESTIAL}, {@code STARS} sind registriert</li>
 * <li>Tageszeit 6000, {@code hasSkyLight() = true}, Chroma-Himmel aus</li>
 * </ul>
 *
 * <p>Es bleibt die AUSFUEHRUNG. {@code LevelRenderer.addSkyPass} ist privat und haengt im
 * Bildgraphen; der oeffentliche Einstieg davor ist {@link SkyRenderer#extractRenderState}.
 * Diese Messung beantwortet die eine Frage, die alles davor nicht beantworten kann:
 * <b>wird der Himmelszustand ueberhaupt eingesammelt?</b>
 *
 * <ul>
 * <li>Zaehler bleibt bei 0 → der Pass laeuft gar nicht, die Suche geht in den Bildgraphen.</li>
 * <li>Zaehler laeuft hoch → der Zustand wird gesammelt und beim Zeichnen verworfen; dann ist
 *     das Ziel oder die Pipeline schuld, nicht die Sammlung.</li>
 * </ul>
 *
 * <p>Das ist der Unterschied zwischen "kommt gar nicht an" und "kommt an und wird nicht
 * sichtbar" -- von aussen sehen beide gleich aus (schwarze Flaeche), und genau daran hing
 * schon die Modellvorschau wochenlang.
 *
 * <p>⛔ Mixin-KLASSEN sind nicht hotswapbar. Jede Messung hier kostet einen Boot, darum
 * nimmt sie gleich mehrere Werte mit.
 */
@Mixin(SkyRenderer.class)
public class HimmelMessMixin
{
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void bbs$himmelGemessen(ClientLevel welt, float teilbild, Camera kamera,
        SkyRenderState zustand, CallbackInfo info)
    {
        MixinBruecke.rufe("HimmelMessLogik", "himmelGemessen", teilbild, kamera, zustand);
    }
}
