package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin
{
    /* ⚠️ 26.2: actuallyHurt fuehrt jetzt die ServerLevel als ERSTEN Parameter. Schaden ist
     * damit ausdruecklich serverseitig -- der Injektor braucht sie nicht, muss sie aber in
     * der Signatur fuehren, sonst findet Mixin sein Ziel nicht. */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    public void onApplyDamage(net.minecraft.server.level.ServerLevel welt, DamageSource source, float amount, CallbackInfo info)
    {
        Entity attacker = source.getEntity();

        if (source.isDirect() && attacker != null && attacker.getClass() == ServerPlayer.class)
        {
            BBSMod.getActions().addAction((ServerPlayer) attacker, () ->
            {
                AttackActionClip clip = new AttackActionClip();

                clip.damage.set(amount);

                return clip;
            });
        }
    }

    /**
     * Die Trefferbox einer BBS-Form auf den Traeger legen.
     *
     * <p>⚠️ 26.2 UMGEZOGEN AUS PlayerEntityMixin. Der Injektor zielte dort auf
     * {@code Player.getDefaultDimensions} -- die Methode DEKLARIERT Player nicht mehr, sie
     * sitzt hier auf {@code LivingEntity} und wird nur geerbt. Mixin kann in eine geerbte
     * Methode nicht injizieren; der Start brach ab mit "could not find any targets".</p>
     *
     * <p>⚠️ Und sie nimmt eine {@code Pose} entgegen. Der alte Injektor fuehrte den Parameter
     * nicht -- schon auf 1.21.1 nicht, wo die Methode ihn ebenfalls hatte.</p>
     *
     * <p>Der {@code IMorphProvider}-Riegel bleibt: obwohl der Haken jetzt an ALLEN Lebewesen
     * haengt, wirkt er nur auf Traeger einer BBS-Form. Alles andere faellt in der ersten
     * Zeile durch.</p>
     */
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    public void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> info)
    {
        if (this instanceof IMorphProvider provider)
        {
            Form form = provider.getMorph().getForm();

            if (form != null && form.hitbox.get())
            {
                LivingEntity traeger = (LivingEntity) (Object) this;
                EntityDimensions dimensions = info.getReturnValue();
                float height = form.hitboxHeight.get() * (traeger.isShiftKeyDown() ? form.hitboxSneakMultiplier.get() : 1F);

                if (dimensions.fixed())
                {
                    info.setReturnValue(EntityDimensions.fixed(form.hitboxWidth.get(), height));
                }
                else
                {
                    info.setReturnValue(EntityDimensions.scalable(form.hitboxWidth.get(), height));
                }
            }
        }
    }

    /* @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    public void onSwingHand(Hand hand, boolean fromServerPlayer, CallbackInfo info)
    {
        info.cancel();
    } */
}