package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import com.afrobama.bbstools.client.blender.MixinBruecke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input
{
    /* BRUECKEN-MUSTER (Aufgabe #56): logik in
     * com.afrobama.blendertransform.KeyboardInputSteuerungLogik. tick laeuft 20x/s,
     * nicht pro frame -- der brueckenaufruf faellt nicht ins gewicht, und wie sich das
     * steuern anfuehlt, will man beim ausprobieren sofort sehen. */
    /* 26.2: tick ist parameterlos geworden -- die Schleich-Daempfung kam frueher als
     * (slowDown, slowDownFactor) herein und rechnet Minecraft inzwischen selbst am Spieler.
     *
     * null heisst "es wird gerade nichts gesteuert" -- dann bleibt Minecrafts Eingabe stehen. */
    /* ⚠️ KEIN @Shadow -- moveVector liegt auf der OBERKLASSE ClientInput, nicht auf
     * KeyboardInput. @Shadow sucht nur in der Zielklasse selbst und bricht sonst den Start
     * ("@Shadow field moveVector was not located"). Der sanktionierte Weg fuer geerbte
     * geschuetzte Felder ist, dass die Mixin-Klasse dieselbe Oberklasse erweitert -- dann ist
     * das Feld schlicht geerbt (siehe extends oben). */

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo info)
    {
        Object vektor = MixinBruecke.rufe("KeyboardInputSteuerungLogik", "uebernehmen",
            (KeyboardInput) (Object) this);

        if (vektor instanceof Vec2 neu)
        {
            //? if >=1.21.5 {
            /*this.moveVector = neu;
            *///?} else {
            this.leftImpulse = neu.x;
            this.forwardImpulse = neu.y;
            //?}
        }
    }
}
