package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import com.afrobama.bbstools.client.blender.MixinBruecke;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin
{
    /* BRUECKEN-MUSTER (Aufgabe #56): logik in
     * com.afrobama.blendertransform.KeyboardInputSteuerungLogik. tick laeuft 20x/s,
     * nicht pro frame -- der brueckenaufruf faellt nicht ins gewicht, und wie sich das
     * steuern anfuehlt, will man beim ausprobieren sofort sehen. */
    /* 26.2: tick ist parameterlos geworden -- die Schleich-Daempfung kam frueher als
     * (slowDown, slowDownFactor) herein und rechnet Minecraft inzwischen selbst am Spieler.
     *
     * moveVector ist geschuetzt und liegt auf der Oberklasse ClientInput; von aussen kommt
     * niemand dran (der accessWidener ist waehrend der classTweaker-Umstellung abgeklemmt).
     * Der @Shadow ist der sanktionierte Weg: die Logik rechnet den Vektor, wir schreiben ihn.
     * null heisst "es wird gerade nichts gesteuert" -- dann bleibt Minecrafts Eingabe stehen. */
    @Shadow
    protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo info)
    {
        Object vektor = MixinBruecke.rufe("KeyboardInputSteuerungLogik", "uebernehmen",
            (KeyboardInput) (Object) this);

        if (vektor instanceof Vec2 neu)
        {
            this.moveVector = neu;
        }
    }
}
