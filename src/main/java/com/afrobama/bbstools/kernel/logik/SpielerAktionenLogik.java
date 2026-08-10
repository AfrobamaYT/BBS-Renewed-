package com.afrobama.bbstools.kernel.logik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.actions.types.blocks.PlaceBlockActionClip;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import mchorse.bbs_mod.actions.types.item.UseBlockItemActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Logik hinter allen Mixins, die SPIELERHANDLUNGEN fuer die Aufzeichnung mitschreiben:
 * {@code ServerPlayerEntityMixin} (werfen), {@code ServerPlayNetworkHandlerMixin} (Befehl,
 * Block-Interaktion), {@code ServerWorldMixin} (Abbauen, Spawn), {@code BlockItemMixin}
 * (Setzen) und {@code ItemStackMixin} (Benutzen).
 *
 * <p>Eine Klasse statt fuenf: alle haengen am selben Aufzeichnungsweg
 * ({@code BBSMod.getActions().addAction}) und werden bei einer Aenderung am Clip-Format
 * ohnehin zusammen angefasst. Was NICHT hierher gehoert, sind Blockaenderungen der Welt —
 * die laufen ohne Spielerbezug ueber {@code BlockAufzeichnungLogik}.</p>
 *
 * <p>⚠️ DIE CLIPS WERDEN VERZOEGERT GEBAUT. {@code addAction} nimmt einen Lieferanten und
 * ruft ihn erst, wenn wirklich aufgezeichnet wird — was hier im Lambda steht, laeuft im
 * Normalbetrieb also gar nicht. Wer die Werte VOR dem Lambda ausliest, verschiebt genau
 * diese Kosten in jeden Spielzug.</p>
 */
public final class SpielerAktionenLogik
{
    private SpielerAktionenLogik() {}

    /**
     * {@code ServerPlayer.drop} @RETURN — ein Gegenstand ist geflogen.
     *
     * @param spieler der werfende Spieler; kommt als Argument, weil der Cast den gewobenen
     *                {@code this} braucht
     * @param entity  das entstandene ItemEntity, oder {@code null} wenn nichts flog
     */
    public static void gegenstandGeworfen(Object spieler, ItemEntity entity)
    {
        if (entity == null || !(spieler instanceof ServerPlayer p))
        {
            return;
        }

        BBSMod.getActions().addAction(p, () ->
        {
            ItemDropActionClip clip = new ItemDropActionClip();
            Vec3 v = entity.getDeltaMovement();
            Vec3 pos = entity.position();

            clip.velocityX.set((float) v.x);
            clip.velocityY.set((float) v.y);
            clip.velocityZ.set((float) v.z);
            clip.posX.set(pos.x);
            clip.posY.set(pos.y);
            clip.posZ.set(pos.z);
            clip.itemStack.set(entity.getItem().copy());

            return clip;
        });
    }

    /** {@code ServerGamePacketListenerImpl.parseCommand} @HEAD */
    public static void befehlGetippt(ServerPlayer spieler, String befehl)
    {
        if (spieler == null)
        {
            return;
        }

        BBSMod.getActions().addAction(spieler, () ->
        {
            CommandActionClip clip = new CommandActionClip();

            clip.command.set(befehl);

            return clip;
        });
    }

    /** {@code handleUseItemOn} — Rechtsklick auf einen Block. */
    public static void blockBenutzt(ServerPlayer spieler, BlockHitResult treffer, InteractionHand hand)
    {
        if (spieler == null)
        {
            return;
        }

        BBSMod.getActions().addAction(spieler, () ->
        {
            InteractBlockActionClip clip = new InteractBlockActionClip();

            clip.hit.setHitResult(treffer);
            clip.hand.set(hand == InteractionHand.MAIN_HAND);

            return clip;
        });
    }

    /**
     * {@code ServerLevel.destroyBlockProgress} @HEAD — der Abbau-Fortschritt an einem Block.
     *
     * <p>⚠️ DER SPIELER STECKT IN EINER ID, nicht im Aufruf. Vanilla meldet hier nur
     * {@code entityId}; ohne das Nachschlagen in der Welt bekaeme die Aufzeichnung keinen
     * Urheber. Steht dort etwas anderes als ein Spieler (TNT, ein anderer Mod), wird nichts
     * aufgezeichnet — das ist richtig so, ein Clip braucht einen Spieler.</p>
     *
     * @param weltObjekt der gewobene {@code this}; kommt als Argument, weil der Cast ihn braucht
     */
    public static void blockAbbauFortschritt(Object weltObjekt, int entitaetsId, BlockPos pos, int fortschritt)
    {
        if (!(weltObjekt instanceof ServerLevel welt) || !(welt.getEntity(entitaetsId) instanceof ServerPlayer spieler))
        {
            return;
        }

        BBSMod.getActions().addAction(spieler, () ->
        {
            BreakBlockActionClip clip = new BreakBlockActionClip();

            clip.x.set(pos.getX());
            clip.y.set(pos.getY());
            clip.z.set(pos.getZ());
            clip.progress.set(fortschritt);

            return clip;
        });
    }

    /** {@code ServerLevel.addFreshEntity} @HEAD — eigener Meldeweg, kein Clip. */
    public static void entitaetErschienen(Entity entitaet)
    {
        BBSMod.getActions().spawnedEntity(entitaet);
    }

    /** {@code BlockItem.placeBlock} @HEAD — der Spieler hat einen Block gesetzt. */
    public static void blockGesetzt(BlockPlaceContext kontext, BlockState zustand)
    {
        if (!(kontext.getPlayer() instanceof ServerPlayer spieler))
        {
            return;
        }

        BBSMod.getActions().addAction(spieler, () ->
        {
            PlaceBlockActionClip clip = new PlaceBlockActionClip();
            BlockPos pos = kontext.getClickedPos();

            clip.x.set(pos.getX());
            clip.y.set(pos.getY());
            clip.z.set(pos.getZ());
            clip.state.set(zustand);

            return clip;
        });
    }

    /**
     * {@code ItemStack.use} @HEAD — Rechtsklick in die Luft.
     *
     * <p>⚠️ {@code welt} wird nicht gelesen und steht trotzdem da: die Bruecke sucht die
     * Methode ueber die ANZAHL der Argumente. Wer den Parameter streicht, muss den Aufruf im
     * Mixin mitziehen, sonst findet die Bruecke sie nicht mehr.</p>
     */
    public static void gegenstandBenutzt(Level welt, Player benutzer, InteractionHand hand)
    {
        if (!(benutzer instanceof ServerPlayer spieler))
        {
            return;
        }

        BBSMod.getActions().addAction(spieler, () ->
        {
            UseItemActionClip clip = new UseItemActionClip();

            clip.itemStack.set(benutzer.getItemInHand(hand).copy());
            clip.hand.set(hand == InteractionHand.MAIN_HAND);

            return clip;
        });
    }

    /**
     * {@code LivingEntity.actuallyHurt} @HEAD — ein Spieler hat zugeschlagen.
     *
     * <p>⚠️ {@code getClass() == ServerPlayer.class}, NICHT {@code instanceof}. Die exakte
     * Klasse schliesst Unterklassen aus, die andere Mods als Spieler-Stellvertreter einsetzen
     * (Fake-Player fuer Maschinen). Ein {@code instanceof} wuerde deren Schlaege dem Spieler
     * zuschreiben und Angriffe in die Aufzeichnung legen, die er nie gefuehrt hat.</p>
     *
     * <p>⚠️ {@code source.isDirect()} haelt Fernwirkung draussen — Explosionen, Gift, Feuer,
     * das der Spieler gelegt hat. Aufgezeichnet wird der Schlag, nicht die Schuld.</p>
     */
    public static void schadenZugefuegt(DamageSource quelle, float schaden)
    {
        Entity angreifer = quelle.getEntity();

        if (!quelle.isDirect() || angreifer == null || angreifer.getClass() != ServerPlayer.class)
        {
            return;
        }

        BBSMod.getActions().addAction((ServerPlayer) angreifer, () ->
        {
            AttackActionClip clip = new AttackActionClip();

            clip.damage.set(schaden);

            return clip;
        });
    }

    /** {@code ItemStack.useOn} @HEAD — Rechtsklick mit einem Gegenstand auf einen Block. */
    public static void gegenstandAufBlock(UseOnContext kontext)
    {
        if (!(kontext.getPlayer() instanceof ServerPlayer spieler))
        {
            return;
        }

        BBSMod.getActions().addAction(spieler, () ->
        {
            UseBlockItemActionClip clip = new UseBlockItemActionClip();

            clip.hit.setHitResult(kontext);
            clip.itemStack.set(kontext.getItemInHand().copy());
            clip.hand.set(kontext.getHand() == InteractionHand.MAIN_HAND);

            return clip;
        });
    }
}
