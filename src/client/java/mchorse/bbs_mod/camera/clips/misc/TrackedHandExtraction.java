package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

/** Restores the exact player and first-person cache captured before native item extraction. */
public final class TrackedHandExtraction implements AutoCloseable
{
    private static final Field[] CACHE_FIELDS = {
        field("mainHandItem", ItemStack.class), field("offHandItem", ItemStack.class),
        field("mainHandHeight", Float.TYPE), field("oMainHandHeight", Float.TYPE),
        field("offHandHeight", Float.TYPE), field("oOffHandHeight", Float.TYPE)
    };

    private final LocalPlayer player;
    private final FirstPersonHandsAndItems cache;
    private final int slot;
    private final int selected;
    private final int hurtTime;
    private final ItemStack originalMain;
    private final ItemStack originalOff;
    private final Object[] originalCache = new Object[CACHE_FIELDS.length];
    private boolean closed;

    public static TrackedHandExtraction open(IEntity source)
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

    private static Field field(String name, Class<?> type)
    {
        try
        {
            Field field = FirstPersonHandsAndItems.class.getDeclaredField(name);

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

        this.cache = this.player.firstPersonHandsAndItems();
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
