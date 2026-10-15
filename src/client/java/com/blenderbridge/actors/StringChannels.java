// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 3, task 5.2 string channels).
package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * StringChannels — the transport for the 15 keyframe channels whose value is an {@link ItemStack},
 * not a number. Task 5.2 (HUD wave): equipment + hotbar item IDs so Blender can rebuild the actor's
 * hand item and hotbar for the first-person HUD.
 *
 * <h2>WHY A SEPARATE CLASS (deploy-window-free wiring)</h2>
 * <p>{@link ActorExporter} and {@link ActorImporter} are already loaded in the running JVM, so they
 * can only take BODY edits via hotswap — no new static field, no new method signature (the
 * {@code canAddMethod}/{@code <clinit>} traps, see the package class-doc). All the NEW state this
 * feature needs — the channel-id set, the {@code "strings"} payload key, the ItemStack&lt;-&gt;id
 * codec — lives here in a NEW class, which lazy-loads off the dev classpath the first time it is
 * referenced, exactly like {@code RegionClamp}/{@code EntityExportTarget}/{@code StringChannels}'
 * siblings in {@code com.blenderbridge.world}. The two loaded classes then only gain a single body
 * call each: {@code ActorExporter.exportReplay} adds {@code out.add(STRINGS, serialize(replay))}
 * and {@code ActorImporter.applyReplay} adds {@code apply(replayObject, replay)}.</p>
 *
 * <h2>WHY ITEM IDs, NOT KEYFRAME CURVES</h2>
 * <p>These channels hold {@link ItemStack}s, which have no meaningful float-curve representation, so
 * {@code ActorExporter.exportChannel} returns null for them and they land in
 * {@code skippedChannels}. That list is the ANCHOR this feature was built against (kanal-matrix
 * §I): "data exists, cannot be sent as a numeric curve". We keep reporting them in
 * {@code skippedChannels} (unchanged) and ADD the {@code "strings"} object beside {@code channels}
 * — an old add-on that does not read {@code "strings"} sees byte-identical old behaviour and still
 * knows the data is there.</p>
 *
 * <h2>WIRE SHAPE</h2>
 * <pre>
 * "strings": {
 *   "item_main_hand": [[0.0, "minecraft:diamond_sword"], [40.0, "minecraft:air"]],
 *   "hotbar_slot_1":  [[0.0, "minecraft:cobblestone"]],
 *   ...
 * }
 * </pre>
 * <p>Only NON-EMPTY channels appear (same rule {@code channels} uses — a replay carries all 15,
 * most unused). Each keyframe is a two-element array {@code [tick, id]}: {@code tick} is a float
 * (matches {@code KF_TICK}), {@code id} is the item's registry id via
 * {@code Registries.ITEM.getId(stack.getItem())} — the exact resolution
 * {@code BlockDeltaTracker.changeEntry} uses for blocks. An EMPTY stack serialises as
 * {@code "minecraft:air"} (its real registry id) rather than being dropped: an
 * equip-then-unequip is genuine animation data, and "air" round-trips cleanly back to
 * {@link ItemStack#EMPTY}.</p>
 *
 * <h2>NOT TRANSPORTED (documented v1 limits)</h2>
 * <ul>
 *   <li><b>NBT / components</b> — only the item TYPE (registry id) travels, not enchantments,
 *       custom names, damage, count. The HUD/hand icon needs the item type; a v2 could add an
 *       optional components blob. Interpolation between two DIFFERENT items is a hard cut anyway
 *       (see {@code ItemStackKeyframeFactory.interpolate}), so the id is the animatable part.</li>
 *   <li><b>Interpolation key / handles</b> — an item channel is a step function (you hold an item
 *       or you do not), so per-keyframe interp is meaningless here and is not sent. Import inserts
 *       plain keyframes (BBS keeps its own default interp).</li>
 * </ul>
 *
 * <p>THREADING: MAIN THREAD ONLY, like the exporter/importer that call it — it reads/writes live
 * film state. Registry lookups are read-only.</p>
 */
public final class StringChannels
{
    /** Top-level replay-object key holding the per-channel item-id keyframe arrays. */
    public static final String STRINGS = "strings";

    /**
     * The 15 ItemStack channel ids, in the order {@code ReplayKeyframes} registers them (6 equipment
     * then 9 hotbar). Kept as a constant so a reader/gate can see the full expected set; the exporter
     * does NOT iterate this — it walks the replay's live channels and asks each whether its factory is
     * {@link KeyframeFactories#ITEM_STACK}, so a future channel id needs no edit here.
     */
    public static final String[] ITEM_CHANNELS =
    {
        "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet",
        "hotbar_slot_1", "hotbar_slot_2", "hotbar_slot_3", "hotbar_slot_4", "hotbar_slot_5",
        "hotbar_slot_6", "hotbar_slot_7", "hotbar_slot_8", "hotbar_slot_9",
    };

    private StringChannels()
    {
        /* static-only helper */
    }

    /* ================================================================= *
     *  Export: BBS -> Blender                                           *
     * ================================================================= */

    /**
     * Serialise every non-empty ItemStack channel of one replay into the {@code "strings"} object.
     *
     * <p>Walks the replay's live channels (not {@link #ITEM_CHANNELS}) and picks the ones whose
     * factory is {@link KeyframeFactories#ITEM_STACK} — the same runtime test the exporter draws for
     * "numeric" ({@code KeyframeFactories.isNumeric}). Empty channels are skipped, matching
     * {@code channels}. Never throws — a resolution failure on a single keyframe degrades that entry
     * to {@code "minecraft:air"}, it does not abort the export.</p>
     *
     * @return the {@code "strings"} object; may be empty (a replay with no items), never null
     */
    public static JsonObject serialize(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null)
        {
            return out;
        }

        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
        {
            if (channel == null || channel.isEmpty() || channel.getFactory() != KeyframeFactories.ITEM_STACK)
            {
                continue;
            }

            JsonArray keyframes = serializeChannel(channel);

            if (keyframes.size() > 0)
            {
                out.add(channel.getId(), keyframes);
            }
        }

        return out;
    }

    /** One ItemStack channel -> array of {@code [tick, "namespace:id"]} pairs. */
    private static JsonArray serializeChannel(KeyframeChannel<?> channel)
    {
        JsonArray out = new JsonArray();

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            if (keyframe == null)
            {
                continue;
            }

            Object value = keyframe.getValue();

            if (!(value instanceof ItemStack stack))
            {
                /* Belt-and-braces: the factory said ItemStack, so this cannot normally happen. */
                continue;
            }

            JsonArray entry = new JsonArray();

            /* Tick stays a float, like KF_TICK in ActorExporter — Gson renders 0.0F as "0.0". */
            entry.add(keyframe.getTick());
            entry.add(itemId(stack));

            out.add(entry);
        }

        return out;
    }

    /**
     * The registry id of an ItemStack's item ("minecraft:diamond_sword").
     *
     * <p>{@code Registries.ITEM.getId(item)} — the exact resolution
     * {@code BlockDeltaTracker.changeEntry} uses for blocks. An empty stack reports
     * {@code "minecraft:air"} (its true registry id), the value that round-trips back to
     * {@link ItemStack#EMPTY} on import.</p>
     */
    public static String itemId(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "minecraft:air";
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /* ================================================================= *
     *  Import: Blender -> BBS                                           *
     * ================================================================= */

    /**
     * Apply an inbound {@code "strings"} object onto a replay's ItemStack channels — the reverse of
     * {@link #serialize(Replay)}, called from {@code ActorImporter.applyReplay} inside the same echo
     * guard as the numeric channels.
     *
     * <p>FULL REPLACEMENT per named channel, exactly like {@code ActorImporter.applyChannel}: a
     * channel named in {@code "strings"} is cleared and rebuilt; a channel NOT named is left
     * untouched. So a Blender payload that only carries {@code item_main_hand} edits exactly that
     * channel. Unknown channel ids, or ids whose factory is not {@link KeyframeFactories#ITEM_STACK},
     * are ignored (a stray {@code "x"} in {@code strings} cannot corrupt the numeric curve). Never
     * throws — a malformed entry is skipped.</p>
     *
     * @return the number of channels that were rebuilt
     */
    @SuppressWarnings("unchecked")
    public static int apply(JsonObject replayObject, Replay replay)
    {
        if (replayObject == null || replay == null)
        {
            return 0;
        }

        JsonElement element = replayObject.get(STRINGS);

        if (element == null || !element.isJsonObject())
        {
            return 0;
        }

        int applied = 0;

        for (java.util.Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
        {
            if (!entry.getValue().isJsonArray())
            {
                continue;
            }

            BaseValue value = replay.keyframes.get(entry.getKey());

            if (!(value instanceof KeyframeChannel<?> channel)
                || channel.getFactory() != KeyframeFactories.ITEM_STACK)
            {
                continue;
            }

            applyChannel((KeyframeChannel<ItemStack>) channel, entry.getValue().getAsJsonArray());
            applied += 1;
        }

        return applied;
    }

    /** Rebuild one ItemStack channel from its {@code [[tick, id], ...]} payload array. */
    private static void applyChannel(KeyframeChannel<ItemStack> channel, JsonArray keyframes)
    {
        /* Full replacement: Blender owns this channel now. Clearing first is what makes an item
         * keyframe DELETED in Blender actually disappear in BBS (same reasoning as
         * ActorImporter.applyChannel). */
        channel.removeAll();

        for (JsonElement element : keyframes)
        {
            if (!element.isJsonArray())
            {
                continue;
            }

            JsonArray pair = element.getAsJsonArray();

            if (pair.size() < 2)
            {
                continue;
            }

            float tick;
            String id;

            try
            {
                tick = pair.get(0).getAsFloat();
                id = pair.get(1).getAsString();
            }
            catch (RuntimeException e)
            {
                /* Malformed entry (non-number tick / non-string id) — skip, do not abort. */
                continue;
            }

            channel.insert(tick, itemStack(id));
        }
    }

    /**
     * Build an ItemStack from a registry id string. An unknown id, or {@code "minecraft:air"},
     * yields {@link ItemStack#EMPTY} — {@code Registries.ITEM.get} returns {@code Items.AIR} for an
     * id it does not know, and an air stack is empty.
     */
    private static ItemStack itemStack(String id)
    {
        if (id == null || id.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        ResourceLocation identifier = ResourceLocation.tryParse(id);

        if (identifier == null)
        {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.get(identifier);

        return new ItemStack(item);
    }
}
