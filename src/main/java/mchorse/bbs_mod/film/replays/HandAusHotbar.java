package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.world.item.ItemStack;

/**
 * What a replay holds in its main hand: the hotbar cell its selected slot points at, as in the
 * game and in BBS FS ({@code ReplayKeyframes.getMainHandStack}, Wemppy4/bbs-fs, MIT), so moving
 * the "Selected Slot" key changes the item in the hand (owner, 2026-10-14).
 *
 * <p>A replay without a single hotbar key keeps its own main-hand track: films recorded before
 * the hotbar tracks, and props, hold what that track says.</p>
 */
public final class HandAusHotbar
{
    private HandAusHotbar()
    {}

    public static int slot(ReplayKeyframes keyframes, float tick)
    {
        return MathUtils.clamp(keyframes.selectedSlot.interpolate(tick), 0, 8);
    }

    public static ItemStack hand(ReplayKeyframes keyframes, float tick)
    {
        for (KeyframeChannel<ItemStack> zelle : keyframes.hotbarSlots)
        {
            if (!zelle.isEmpty())
            {
                return keyframes.hotbarSlots[slot(keyframes, tick)].interpolate(tick, ItemStack.EMPTY);
            }
        }

        return keyframes.mainHand.interpolate(tick, ItemStack.EMPTY);
    }
}
