package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
//? if >=1.21.6 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?} else {
import net.minecraft.nbt.CompoundTag;
//?}
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For some unknown reason to me, if these methods are used in {@link PlayerEntityMorphMixin}
 * then the world will be locked for some reason... by extracting write/read NBT method to
 * a separate mixin fixes it...
 */
@Mixin(Player.class)
public class PlayerEntityMixin
{
    /* ⚠️ 26.2: Entity-NBT laeuft nicht mehr ueber CompoundTag, sondern ueber die
     * Schnittstellen ValueOutput/ValueInput. Der DESKRIPTOR aendert sich damit, nicht nur der
     * Typname -- und ein falscher Deskriptor faellt erst beim WEBEN auf, nicht beim Bauen:
     *     InvalidInjectionException: Invalid descriptor on ...@Inject::onWriteCustomDataToNbt
     *
     * BBS' Morph kennt weiterhin nur CompoundTag (toNbt/fromNbt). Statt das Datenmodell
     * anzufassen, wird der Tag als WERT ueber CompoundTag.CODEC abgelegt bzw. gelesen -- das
     * ist der von 26.2 vorgesehene Weg fuer fremde Strukturen und laesst die Morph-Seite
     * unveraendert. */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    //? if >=1.21.6 {
    /*public void onWriteCustomDataToNbt(ValueOutput ausgabe, CallbackInfo info)
    *///?} else {
    public void onWriteCustomDataToNbt(CompoundTag ausgabe, CallbackInfo info)
    //?}
    {
        KernelBruecke.rufe("SpielerFormSpeichernLogik", "schreiben", this, ausgabe);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //? if >=1.21.6 {
    /*public void onReadCustomDataFromNbt(ValueInput eingabe, CallbackInfo info)
    *///?} else {
    public void onReadCustomDataFromNbt(CompoundTag eingabe, CallbackInfo info)
    //?}
    {
        KernelBruecke.rufe("SpielerFormSpeichernLogik", "lesen", this, eingabe);
    }

    /* ⛔ UMGEZOGEN 2026-07-31 nach LivingEntityMixin.
     *
     * Hier stand der Injektor, der die Trefferbox einer Form auf den Spieler legt. Er zielte
     * auf Player.getDefaultDimensions -- und DIE METHODE DEKLARIERT PLAYER NICHT MEHR. Sie
     * sitzt auf LivingEntity, Player erbt sie nur. Ein Mixin kann nicht in eine geerbte
     * Methode injizieren, die die Zielklasse selbst nicht traegt: der Start bricht ab mit
     *     could not find any targets matching 'getDefaultDimensions' in .../player/Player
     *
     * Der Injektor sitzt jetzt auf LivingEntity und behaelt seinen IMorphProvider-Riegel --
     * er wirkt also weiterhin nur auf Traeger einer BBS-Form. */

}
