package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
import net.minecraft.client.player.FirstPersonHandsAndItems;
//?} else {
/*import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.ItemInHandRenderer;
*///?}
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

/**
 * Restores the exact player and first-person cache captured before native item extraction.
 * Before 26.3 the hand is not extracted into a render state: the scope lasts the whole
 * level pass and also carries the hand heights, the swing and the first-person view into
 * the ItemInHandRenderer and the player, as the 26.2 release did.
 */
public final class TrackedHandExtraction implements AutoCloseable
{
    private static final Field[] CACHE_FIELDS = {
        field("mainHandItem", "field_4047", ItemStack.class), field("offHandItem", "field_4048", ItemStack.class),
        field("mainHandHeight", "field_4043", Float.TYPE), field("oMainHandHeight", "field_4053", Float.TYPE),
        field("offHandHeight", "field_4052", Float.TYPE), field("oOffHandHeight", "field_4051", Float.TYPE)
    };

    private final LocalPlayer player;
    //? if >=26.3 {
    private final FirstPersonHandsAndItems cache;
    //?} else {
    /*private final ItemInHandRenderer cache;
    private final float attackAnim;
    private final float oAttackAnim;
    private final boolean swinging;
    private final CameraType cameraType;
    *///?}
    private final int slot;
    private final int selected;
    private final int hurtTime;
    private final ItemStack originalMain;
    private final ItemStack originalOff;
    private final Object[] originalCache = new Object[CACHE_FIELDS.length];
    private boolean closed;

    /** 26.3 form without heights; BBSToolsTrackerClientClip still calls it. */
    public static TrackedHandExtraction open(IEntity source)
    {
        return open(source, 0F, 0F, 0F, 0F, 0F);
    }

    /**
     * The heights are the tracked entity's smoothed equip progress times the show transition;
     * 26.3 applies them to the extracted state instead (TrackerClientClip.applyExtractedHandState).
     */
    public static TrackedHandExtraction open(IEntity source, float mainHeight, float oMainHeight,
        float offHeight, float oOffHeight, float tickDelta)
    {
        TrackedHandExtraction scope = new TrackedHandExtraction(source.getSelectedSlot());

        try
        {
            ItemStack main = source.getHotbarItem(scope.slot);
            ItemStack off = source.getEquipmentStack(EquipmentSlot.OFFHAND);
            main = main == null ? ItemStack.EMPTY : main.copy();
            off = off == null ? ItemStack.EMPTY : off.copy();
            scope.player.getInventory().setSelectedSlot(scope.slot);
            scope.player.getInventory().setItem(scope.slot, main);
            scope.player.getInventory().setItem(Inventory.SLOT_OFFHAND, off);
            scope.player.hurtTime = source.getHurtTimer();
            CACHE_FIELDS[0].set(scope.cache, main);
            CACHE_FIELDS[1].set(scope.cache, off);
            //? if <26.3 {
            /*CACHE_FIELDS[2].setFloat(scope.cache, mainHeight);
            CACHE_FIELDS[3].setFloat(scope.cache, oMainHeight);
            CACHE_FIELDS[4].setFloat(scope.cache, offHeight);
            CACHE_FIELDS[5].setFloat(scope.cache, oOffHeight);

            float swing = source.getHandSwingProgress(tickDelta);

            scope.player.attackAnim = Math.max(scope.player.attackAnim, swing);
            scope.player.oAttackAnim = Math.max(scope.player.oAttackAnim, swing);

            if (scope.player.attackAnim > 0F)
            {
                scope.player.swinging = true;
            }

            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            *///?}

            return scope;
        }
        catch (Throwable error)
        {
            try
            {
                scope.close();
            }
            catch (Throwable restoreError)
            {
                error.addSuppressed(restoreError);
            }

            throw new IllegalStateException("Cannot prepare tracked native hand items", error);
        }
    }

    /** intermediary: Name in Release-Jars vor 26.1 (siehe LaufzeitFeld). */
    private static Field field(String name, String intermediary, Class<?> type)
    {
        try
        {
            //? if >=26.3 {
            Field field = FirstPersonHandsAndItems.class.getDeclaredField(name);
            //?} else {
            /*Field field = mchorse.bbs_mod.utils.LaufzeitFeld.holen(ItemInHandRenderer.class, name, intermediary);
            *///?}

            if (field.getType() != type)
            {
                throw new IllegalStateException("Unexpected first-person field type: " + field);
            }

            field.setAccessible(true);

            return field;
        }
        catch (ReflectiveOperationException error)
        {
            throw new ExceptionInInitializerError(error);
        }
    }

    private TrackedHandExtraction(int slot)
    {
        this.player = Minecraft.getInstance().player;

        if (this.player == null || slot < 0 || slot >= 9)
        {
            throw new IllegalStateException("Tracked hand extraction needs a player and valid hotbar slot: " + slot);
        }

        //? if >=26.3 {
        this.cache = this.player.firstPersonHandsAndItems();
        //?} else {
        /*this.cache = Minecraft.getInstance().gameRenderer.itemInHandRenderer;
        this.attackAnim = this.player.attackAnim;
        this.oAttackAnim = this.player.oAttackAnim;
        this.swinging = this.player.swinging;
        this.cameraType = Minecraft.getInstance().options.getCameraType();
        *///?}
        this.slot = slot;
        this.selected = this.player.getInventory().getSelectedSlot();
        this.hurtTime = this.player.hurtTime;
        this.originalMain = this.player.getInventory().getItem(slot).copy();
        this.originalOff = this.player.getInventory().getItem(Inventory.SLOT_OFFHAND).copy();

        try
        {
            for (int i = 0; i < CACHE_FIELDS.length; i++)
            {
                this.originalCache[i] = CACHE_FIELDS[i].get(this.cache);
            }
        }
        catch (ReflectiveOperationException error)
        {
            throw new IllegalStateException("Cannot capture native first-person state", error);
        }
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.player.getInventory().setItem(this.slot, this.originalMain);
        this.player.getInventory().setItem(Inventory.SLOT_OFFHAND, this.originalOff);
        this.player.getInventory().setSelectedSlot(this.selected);
        this.player.hurtTime = this.hurtTime;
        //? if <26.3 {
        /*this.player.attackAnim = this.attackAnim;
        this.player.oAttackAnim = this.oAttackAnim;
        this.player.swinging = this.swinging;
        Minecraft.getInstance().options.setCameraType(this.cameraType);
        *///?}

        try
        {
            for (int i = 0; i < CACHE_FIELDS.length; i++)
            {
                CACHE_FIELDS[i].set(this.cache, this.originalCache[i]);
            }

            this.closed = true;
        }
        catch (ReflectiveOperationException error)
        {
            throw new IllegalStateException("Cannot restore captured first-person state", error);
        }
    }
}
