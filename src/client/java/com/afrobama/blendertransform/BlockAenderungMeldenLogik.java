package com.afrobama.blendertransform;

import com.blenderbridge.world.BlockDeltaEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Logik hinter {@code ClientWorldBlockDeltaMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Meldet jede Blockaenderung an die Blender-Bruecke, damit die Gegenseite ihr Abbild der
 * Welt nachziehen kann.</p>
 *
 * <p>⚠️ ZWEI QUELLEN, EIN MELDEWEG. Bloecke aendern sich auf dem Client aus zwei Richtungen:
 * der Server bestaetigt eine Aenderung ({@code setServerVerifiedBlockState}), oder der Client
 * setzt selbst ({@code setBlock}). Nur die eine zu behandeln laesst das Abbild driften — und
 * zwar unauffaellig, weil beide Wege im Normalbetrieb vorkommen.</p>
 *
 * <p>⚠️ Beim Client-Weg zaehlt der RUECKGABEWERT: {@code setBlock} kann fehlschlagen (Chunk
 * nicht geladen, gleicher Zustand). Eine Meldung ohne diese Pruefung wuerde der Gegenseite
 * Aenderungen ankuendigen, die nie stattgefunden haben.</p>
 */
public final class BlockAenderungMeldenLogik
{
    private BlockAenderungMeldenLogik() {}

    /**
     * @param welt die Client-Welt; kommt als Argument, weil der Cast {@code (ClientLevel)
     *             (Object) this} den gewobenen {@code this} braucht
     */
    public static void geaendert(ClientLevel welt, BlockPos position)
    {
        BlockDeltaEvents.notifyExternal(welt, position);
    }
}
