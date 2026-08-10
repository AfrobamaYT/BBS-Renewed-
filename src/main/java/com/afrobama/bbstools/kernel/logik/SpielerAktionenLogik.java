package com.afrobama.bbstools.kernel.logik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Logik hinter den Mixins, die SPIELERHANDLUNGEN fuer die Aufzeichnung mitschreiben —
 * {@code ServerPlayerEntityMixin} (Gegenstand fallen lassen) und
 * {@code ServerPlayNetworkHandlerMixin} (Befehl, Block-Interaktion).
 *
 * <p>Drei Handler, eine Klasse: alle haengen am selben Aufzeichnungsweg
 * ({@code BBSMod.getActions().addAction}) und werden bei einer Aenderung am Clip-Format
 * ohnehin zusammen angefasst.</p>
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
}
