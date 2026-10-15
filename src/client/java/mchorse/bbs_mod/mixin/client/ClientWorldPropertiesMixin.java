package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Die Tageszeit fuer den Film ueberschreiben.
 *
 * <h2>⚠️ 26.2: ANDERE ZIELKLASSE, weil die Tageszeit keine Zahl an der Welt mehr ist</h2>
 *
 * <p>Frueher hing der Haken an {@code ClientLevel.ClientLevelData.getDayTime()}. Die Methode
 * gibt es nicht mehr -- {@code ClientLevelData} fuehrt nur noch {@code getGameTime()}. Die
 * Tageszeit kommt in 26.2 aus dem UHREN-System ({@code net.minecraft.world.clock}): jede
 * Dimension nennt eine Uhr, und {@code Level.getDayTime()} liest deren Gesamtticks.</p>
 *
 * <p>Deshalb sitzt der Haken jetzt dort. {@code Level} ist gemeinsamer Code, die Registrierung
 * bleibt aber in der CLIENT-Konfiguration -- gewoben wird also nur clientseitig, und der
 * Zugriff auf {@link BBSRendering} bleibt gueltig.</p>
 *
 * <p>⚠️ Warum nicht {@code ClientLevel}: die Methode ist dort nur GEERBT, nicht deklariert.
 * In geerbte Methoden kann Mixin nicht injizieren -- derselbe Fehler, der in dieser Sitzung
 * schon {@code Player.getDefaultDimensions} gekostet hat.</p>
 *
 * <p><b>Gefunden erst beim WELTLADEN.</b> Am Titelbildschirm feuert der Haken nie, also blieb
 * das tote Ziel unsichtbar, bis eine Welt geladen wurde. {@code tools/mixin-ziele.py} hat es
 * nicht gemeldet, weil es verschachtelte Zielklassen ({@code Outer$Inner}) nicht aufloest --
 * bekannte Luecke des Gates.</p>
 */
@Mixin(Level.class)
public class ClientWorldPropertiesMixin
{
    @Inject(method = "getDefaultClockTime", at = @At("HEAD"), cancellable = true)
    public void onGetTimeOfDay(CallbackInfoReturnable<Long> info)
    {
        Object zeit = MixinBruecke.rufe("WeltUeberschreibungLogik", "tageszeit");

        if (zeit != null)
        {
            info.setReturnValue((Long) zeit);
        }
    }
}
