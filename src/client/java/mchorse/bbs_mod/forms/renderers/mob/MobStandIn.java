package mchorse.bbs_mod.forms.renderers.mob;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.AnzeigeEntity;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.UUID;

/**
 * A vanilla entity standing in for a form's actor, so vanilla's own model code can be run for it: the
 * mob form renders through the game's entity renderer, and a CEM model runs the vanilla model's
 * animation under its pack's program. This makes one of the right kind in the client world, or a
 * player for the player's model, and keeps it in step with the actor tick by tick. It is remade when
 * what it is made from changes. Moved out of MobFormRenderer as in BBS FS 0a6aeef93 and d78765a7c
 * (Wemppy4/bbs-fs, MIT).
 */
public class MobStandIn
{
    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private Entity entity;

    private String lastId = "";
    private String lastNbt = "";
    private boolean lastSlim;

    private float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;

    /** The entity standing in, or null while there is none */
    public Entity entity()
    {
        return this.entity;
    }

    public Entity ensure(String id, String nbt, boolean slim, boolean player)
    {

        if (!this.lastId.equals(id) || !this.lastNbt.equals(nbt) || slim != this.lastSlim)
        {
            this.lastId = id;
            this.lastNbt = nbt;
            this.lastSlim = slim;
            this.entity = null;
        }

        net.minecraft.client.multiplayer.ClientLevel world = Minecraft.getInstance().level;

        /* One made in a world the game has since left is dropped: ticking it would reach for that world's connection (BBS FS d78765a7c) */
        if (this.entity != null && this.entity.level() != world)
        {
            this.entity = null;
        }

        if (this.entity != null || world == null)
        {
            return this.entity;
        }

        CompoundTag compound = new CompoundTag();

        try
        {
            /* ⚠️ 26.2: TagParser ist generisch (TagParser<T>) und hat keinen oeffentlichen
             * Konstruktor mehr. parseCompoundAsArgument ist das exakte Gegenstueck zum alten
             * readStruct(): es liest EIN Compound aus dem Reader und meckert NICHT ueber
             * Resttext dahinter. (parseCompoundFully waere strenger und wuerde bei Resttext
             * werfen — das waere hier eine stille Verhaltensaenderung.) */
            compound = TagParser.parseCompoundAsArgument(new StringReader(nbt));
        }
        catch (Exception e)
        {}

        /* ⚠️ 26.2: create(Level) gibt es nicht mehr. ignoreChecks=TRUE ist Absicht und kein
         * Bequemlichkeits-Flag: die kurze Variante create(Level, EntitySpawnReason) laeuft
         * intern durch canSpawn(level), und canSpawn prueft jetzt ZUSAETZLICH die
         * Schwierigkeit — ein nicht-friedlicher Mob liefert auf FRIEDLICH still null, ein
         * Zombie-MobForm waere also in Vorschau und Welt einfach verschwunden. Preis: die
         * Feature-Flag-Pruefung faellt damit weg, eine abgeschaltete Entity-Art ist
         * konstruierbar statt null — fuer ein reines Anzeige-Objekt folgenlos. Der
         * EntitySpawnReason selbst hat keine Wirkung (er erreicht die Factory nie).
         *
         * ⚠️ MUSS IDENTISCH BLEIBEN zu EntityExportBridge.createPosingEntity und
         * ReplayPoseReader.createPosingEntity — deren Klassendocs behaupten ausdruecklich,
         * dass alle drei Wegwerf-Entity-Pfade denselben Aufruf benutzen. */
        this.entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id))
            .create(Minecraft.getInstance().level, new EntitySpawnRequest(EntitySpawnReason.MOB_SUMMONED, true));

        if (this.entity == null && player)
        {
            this.entity = new RemotePlayer(Minecraft.getInstance().level, slim ? SLIM : WIDE);
            this.entity.getEntityData().set(PlayerUtils.ProtectedAccess.getModelParts(), (byte) 0b1111111);
        }

        /* 26.2: ohne Kennung stirbt der Zeichenweg mit "Tried to access entity ID before
         * ID assignment" -- siehe AnzeigeEntity. */
        AnzeigeEntity.kennungGeben(this.entity);

        if (this.entity != null)
        {
            compound.putString("id", id);

            /* ⚠️ 26.2: load() nimmt keinen CompoundTag mehr, sondern einen ValueInput — die
             * Lese-Schicht, die Fehler an einen ProblemReporter meldet. DISCARDING verwirft
             * sie, also genau das Verhalten, das die alte Signatur hatte. */
            this.entity.load(TagValueInput.create(ProblemReporter.DISCARDING, this.entity.registryAccess(), compound));

            this.entity.noPhysics = true;
        }

        return this.entity;
    }

    public void tick(IEntity entity)
    {
        /* A player's tick asks the connection's player list whether it spectates, so none is stepped without one (BBS FS d78765a7c) */
        if (this.entity != null && Minecraft.getInstance().getConnection() != null)
        {
            this.entity.tick();

            this.entity.xRotO = this.prevPitch;
            this.entity.yRotO = 0F;

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.yHeadRotO = this.prevYawHead;
                livingEntity.yBodyRotO = 0F;

                /* Limb swing is so ugly */
                if (livingEntity.walkAnimation instanceof LimbAnimatorAccessor a && entity.getLimbAnimator() instanceof LimbAnimatorAccessor b)
                {
                    a.setSpeedOld(b.getSpeedOld());
                    a.setSpeed(b.getSpeed());
                    a.setPosition(b.getPosition());
                }

                /* Arm swing */
                float handSwingProgress = entity.getHandSwingProgress(0F);

                if (handSwingProgress < this.prevHandSwing)
                {
                    this.prevHandSwing = 0;
                }

                if (handSwingProgress > 0 && this.prevHandSwing == 0)
                {
                    livingEntity.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                }

                this.prevHandSwing = handSwingProgress;
            }

            this.entity.setYRot(0F);
            this.entity.setYHeadRot(entity.getHeadYaw() - entity.getBodyYaw());
            this.entity.setXRot(entity.getPitch());
            this.entity.setYBodyRot(0F);

            this.entity.setPosRaw(entity.getX(), entity.getY(), entity.getZ());
            this.entity.setOnGround(entity.isOnGround());
            this.entity.setShiftKeyDown(entity.isSneaking());
            this.entity.setSprinting(entity.isSprinting());
            this.entity.setSwimming(entity.isSwimming());
            ((mchorse.bbs_mod.mixin.EntityFlagInvoker) this.entity).bbs$setSharedFlag(mchorse.bbs_mod.forms.entities.EntityState.FALL_FLYING_FLAG, entity.isFallFlying());
            this.entity.setPose(mchorse.bbs_mod.forms.entities.EntityState.pose(entity.isFallFlying(), entity.isSwimming(), entity.isSneaking()));
            if (this.entity instanceof LivingEntity body) mchorse.bbs_mod.film.replays.ReplayZustaende.koerper(body, entity.getLeaningPitch(1F), entity.getRoll());
            if (this.entity instanceof LivingEntity living)
            {
                living.setItemSlot(EquipmentSlot.MAINHAND, entity.getEquipmentStack(EquipmentSlot.MAINHAND));
                living.setItemSlot(EquipmentSlot.OFFHAND, entity.getEquipmentStack(EquipmentSlot.OFFHAND));
                living.setItemSlot(EquipmentSlot.HEAD, entity.getEquipmentStack(EquipmentSlot.HEAD));
                living.setItemSlot(EquipmentSlot.CHEST, entity.getEquipmentStack(EquipmentSlot.CHEST));
                living.setItemSlot(EquipmentSlot.LEGS, entity.getEquipmentStack(EquipmentSlot.LEGS));
                living.setItemSlot(EquipmentSlot.FEET, entity.getEquipmentStack(EquipmentSlot.FEET));
            }
            this.entity.tickCount = entity.getAge();
            this.entity.noPhysics = true;

            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }
}
