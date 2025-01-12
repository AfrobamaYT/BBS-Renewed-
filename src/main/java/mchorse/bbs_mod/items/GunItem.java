package mchorse.bbs_mod.items;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class GunItem extends Item
{
    public GunItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)
    {
        GunProjectileEntity projectile = new GunProjectileEntity(BBSMod.GUN_PROJECTILE_ENTITY, world);

        projectile.setPos(user.getX(), user.getY() + user.getEyeHeight(user.getPose()), user.getZ());
        projectile.setVelocity(user, user.getPitch(), user.getHeadYaw(), 0F, 1F, 0F);

        world.spawnEntity(projectile);

        return super.use(world, user, hand);
    }
}