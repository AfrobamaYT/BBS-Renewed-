package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
    public void onWriteCustomDataToNbt(ValueOutput ausgabe, CallbackInfo info)
    {
        if (this instanceof IMorphProvider provider)
        {
            /* ⚠️ toNbt() ist als Tag deklariert, liefert aber einen CompoundTag (fromNbt
             * nimmt genau den wieder entgegen). Der Codec braucht den konkreten Typ, also
             * einmal pruefen statt blind casten -- ein anderer Tag-Typ waere ein echter
             * Fehler im Morph und soll nicht still als leeres Feld enden. */
            if (provider.getMorph().toNbt() instanceof CompoundTag daten)
            {
                ausgabe.store("BBSMorph", CompoundTag.CODEC, daten);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void onReadCustomDataFromNbt(ValueInput eingabe, CallbackInfo info)
    {
        if (this instanceof IMorphProvider provider)
        {
            /* read gibt ein Optional -- die alte contains-Pruefung faellt damit weg, und der
             * Fall "Feld da, aber kaputt" wird still uebersprungen statt zu werfen. */
            eingabe.read("BBSMorph", CompoundTag.CODEC)
                .ifPresent(tag -> provider.getMorph().fromNbt(tag));
        }
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
