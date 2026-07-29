package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import com.afrobama.bbstools.client.blender.MixinBruecke;

import net.minecraft.client.input.KeyboardInput;
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
    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(boolean slowDown, float slowDownFactor, CallbackInfo info)
    {
        MixinBruecke.rufe("KeyboardInputSteuerungLogik", "uebernehmen",
            (KeyboardInput) (Object) this, slowDown, slowDownFactor);
    }
}
