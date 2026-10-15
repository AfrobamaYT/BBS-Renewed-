package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.renderer.item.ModelBlockItemRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Task 114: animate the model block a replay holds, like any other form.
 *
 * <p>The held block becomes a prop of its own -- a replay "Held: name" with a copy of the
 * block's form, anchored to the holder's main-hand item bone at exactly the place the item
 * renders (ModelFormRenderer.renderItems plus ModelBlockItemRenderer.submit). That is how
 * animation tools treat props (an object constrained to the hand) and in BBS it brings every
 * track and editor along; collapsed it is one row. Where the holder held this block it keeps a
 * copy that draws nothing in third person, and the prop is visible exactly there.</p>
 */
public final class GehaltenerModellblock
{
    public static final IKey ANIMATE = IKey.raw("Animate held model block");

    private GehaltenerModellblock()
    {}

    /** "From model block" (moved here so UIReplayList keeps its size), then this action when it applies. */
    public static void eintraege(ContextMenuManager menu, UIReplayList list, Runnable fromModelBlock)
    {
        menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK, fromModelBlock);

        List<Replay> selected = list.panel == null ? List.of() : list.getSelectedReplays();

        if (selected.size() == 1 && list.panel.getData() != null && slot(selected.get(0)) != null
            && held(selected.get(0), list.panel.getCursor()) != null)
        {
            menu.action(Icons.BLOCK, ANIMATE, () -> create(list, selected.get(0)));

            if (mchorse.bbs_mod.ui.utils.Tutorials.link("held_model_block") != null)
            {
                menu.action(Icons.HELP, IKey.raw("How does this work? (video)"), () -> mchorse.bbs_mod.ui.utils.Tutorials.open("held_model_block"));
            }
        }
    }

    private static ItemStack held(Replay replay, int tick)
    {
        ItemStack stack = mchorse.bbs_mod.film.replays.HandAusHotbar.hand(replay.keyframes, tick);

        return stack != null && stack.getItem() == BBSMod.MODEL_BLOCK_ITEM ? stack : null;
    }

    /** The first main-hand item slot of the holder's model, where renderItems draws the item. */
    private static ArmorSlot slot(Replay replay)
    {
        if (!(replay.form.get() instanceof ModelForm form))
        {
            return null;
        }

        ModelInstance model = BBSModClient.getModels().getModel(form.model.get());

        return model == null || model.getItemsMain().isEmpty() ? null : model.getItemsMain().get(0);
    }

    private static void create(UIReplayList list, Replay holder)
    {
        Film film = list.panel.getData();
        ItemStack stack = held(holder, list.panel.getCursor());
        ArmorSlot slot = slot(holder);
        ModelBlockItemRenderer.Item item = stack == null ? null : ModelBlockItemRenderer.get(stack);
        ItemDisplayContext mode = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

        if (item == null || slot == null || item.entity.getProperties().getForm(mode) == null)
        {
            return;
        }

        /* The item's path from the bone, as renderItems and ModelBlockItemRenderer.submit apply it:
         * X 90, Y 180, up 0.125, the slot transform, ItemTransform's (-0.5, -0.5, -0.5) plus
         * submit's (0.5, 0, 0.5) -- the template model has no display block -- then the block's
         * own third-person transform. */
        Matrix4f path = new Matrix4f().rotateX((float) Math.toRadians(90)).rotateY((float) Math.toRadians(180))
            .translate(0F, 0.125F, 0F).mul(slot.transform.createMatrix()).translate(0F, -0.5F, 0F)
            .mul(item.entity.getProperties().getTransform(mode).createMatrix());
        Anchor anchor = new Anchor(film.replays.getList().indexOf(holder), slot.group, false, false);
        Transform offset = anchor.transform;

        path.getTranslation(offset.translate);
        path.getScale(offset.scale);
        path.getNormalizedRotation(offset.quat);
        offset.rotationMode = Transform.RotationMode.QUATERNION;

        Form form = FormUtils.copy(item.entity.getProperties().getForm(mode));
        Replay prop = film.replays.addReplay();

        form.anchor.set(anchor);
        prop.form.set(form);
        prop.label.set("Held: " + form.getDisplayName());
        prop.category.set(holder.category.get());
        prop.shadow.set(false);
        handOver(holder, prop, form, stack);

        list.refreshReplayList();
        list.update();
        list.panel.replayEditor.setReplay(prop);
        list.scrollToReplay(prop);
        list.panel.getController().createEntities();
        list.panel.replayEditor.updateChannelsList();
    }

    /**
     * Wherever the holder held this block it now holds a copy whose third-person form is invisible:
     * the hand stays full, so the arm keeps its holding pose (emptying it lowered the arm and turned
     * the prop with it, measured 2026-10-12), and the block is not drawn twice. The prop is visible
     * exactly there.
     */
    private static void handOver(Replay holder, Replay prop, Form form, ItemStack stack)
    {
        KeyframeChannel<ItemStack> hand = holder.keyframes.mainHand;
        KeyframeChannel visible = prop.properties.getOrCreate(form, "visible");
        ItemStack hollow = hollow(stack);
        boolean always = true;

        for (Keyframe<ItemStack> keyframe : hand.getKeyframes())
        {
            boolean same = ItemStack.isSameItemSameComponents(keyframe.getValue(), stack);

            always &= same;
            visible.insert(keyframe.getTick(), same);
            if (same)
            {
                keyframe.setValue(hollow.copy());
            }
        }

        if (always || hand.getKeyframes().isEmpty())
        {
            visible.removeAll();
        }

        /* The first-person hand shows the selected HOTBAR slot, not the main hand: a recording
         * keys both, and only hollowing the main hand left the hand drawing the old block
         * (owner's film, 2026-10-13). */
        for (KeyframeChannel<ItemStack> slot : holder.keyframes.hotbarSlots)
        {
            for (Keyframe<ItemStack> keyframe : slot.getKeyframes())
            {
                if (ItemStack.isSameItemSameComponents(keyframe.getValue(), stack))
                {
                    keyframe.setValue(hollow.copy());
                }
            }
        }
    }

    /** The same block with an invisible third-person form, written the way ModelBlock writes its item. */
    private static ItemStack hollow(ItemStack stack)
    {
        Level world = Minecraft.getInstance().level;
        ModelBlockEntity entity = new ModelBlockEntity(BlockPos.ZERO, BBSMod.MODEL_BLOCK.defaultBlockState());
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        ItemStack copy = stack.copy();

        if (data == null || world == null)
        {
            return copy;
        }

        data.loadInto(entity, world.registryAccess());

        Form hidden = FormUtils.copy(entity.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));

        hidden.visible.set(false);
        entity.getProperties().setFormThirdPerson(hidden);
        copy.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(entity.saveWithId(world.registryAccess())));

        return copy;
    }
}
