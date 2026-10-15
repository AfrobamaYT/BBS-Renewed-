package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin
{
    /* ⚠️ 26.2: keyPress nimmt (long fenster, int aktion, KeyEvent ereignis) statt fuenf ints.
     * Taste, Scancode und Modifikatoren stecken jetzt im Ereignis-Record -- dieselbe Bewegung
     * wie beim Mausklick (MouseButtonEvent). Der Rest der BBS-Seite bleibt unveraendert: die
     * fuenf Werte werden hier ausgepackt und wie bisher weitergereicht.
     *
     * Ein falscher Deskriptor faellt hier erst beim WEBEN auf, nicht beim Bauen -- gefunden
     * hat das tools/mixin-signaturen.py, nicht der Compiler. */
    //? if >=1.21.9 {
    @Inject(method = "keyPress", at = @At("HEAD"))
    public void onOnKey(long window, int action, KeyEvent ereignis, CallbackInfo info)
    {
        MixinBruecke.rufe("EingabeDurchreichenLogik", "tasteBeginn", action);
    }

    @Inject(method = "keyPress", at = @At("TAIL"))
    public void onOnEndKey(long window, int action, KeyEvent ereignis, CallbackInfo info)
    {
        MixinBruecke.rufe("EingabeDurchreichenLogik", "tasteEnde", window, ereignis, action, info);
    }
    //?} else {
    /*@Inject(method = "keyPress", at = @At("HEAD"))
    public void onOnKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        MixinBruecke.rufe("EingabeDurchreichenLogik", "tasteBeginn", action);
    }

    @Inject(method = "keyPress", at = @At("TAIL"))
    public void onOnEndKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        MixinBruecke.rufe("EingabeDurchreichenLogik", "tasteEnde", window, new KeyEvent(key, scancode, modifiers), action, info);
    }
    *///?}
}