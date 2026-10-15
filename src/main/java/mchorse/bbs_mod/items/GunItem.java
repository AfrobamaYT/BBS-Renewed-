package mchorse.bbs_mod.items;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import mchorse.bbs_mod.utils.CommandSourceUtils;

public class GunItem extends Item
{
    public static Entity actor;

    public GunItem(Properties settings)
    {
        super(settings);
    }

    /* 26.2: use() gibt InteractionResult direkt zurueck. InteractionResultHolder gab es
     * nur, um den (moeglicherweise veraenderten) ItemStack mitzuliefern -- das macht das
     * spiel jetzt selbst ueber die hand. Der stack wird hier ohnehin nie ersetzt. */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand)
    {
        return new net.minecraft.world.InteractionResultHolder<>(this.bbsBenutzen(world, user, hand), user.getItemInHand(hand));
    }

    public InteractionResult bbsBenutzen(Level world, Player user, InteractionHand hand)
    {
        Entity owner = actor == null ? user : actor;
        ItemStack stack = user.getItemInHand(hand);
        GunProperties properties = this.getProperties(stack);

        /* Launch the player */
        if (properties.launch)
        {
            Vec3 rotationVector = owner.getLookAngle().scale(properties.launchPower);

            if (properties.launchAdditive)
            {
                owner.push(rotationVector);
            }
            else
            {
                owner.setDeltaMovement(rotationVector);
            }

            return InteractionResult.SUCCESS;
        }

        if (!world.isClientSide())
        {
            /* Shoot projectiles */
            int projectiles = Math.max(properties.projectiles, 1);

            for (int i = 0; i < projectiles; i++)
            {
                GunProjectileEntity projectile = new GunProjectileEntity(BBSMod.GUN_PROJECTILE_ENTITY, world);
                float yaw = owner.getYHeadRot() + (float) (properties.scatterY * (Math.random() - 0.5D));
                float pitch = owner.getXRot() + (float) (properties.scatterX * (Math.random() - 0.5D));

                projectile.setProperties(properties);
                projectile.setForm(FormUtils.copy(properties.projectileForm));
                projectile.setPosRaw(owner.getX(), owner.getY() + owner.getEyeHeight(owner.getPose()), owner.getZ());
                projectile.shootFromRotation(owner, pitch, yaw, 0F, properties.speed, 0F);
                projectile.refreshDimensions();

                world.addFreshEntity(projectile);
            }

            if (!properties.cmdFiring.isEmpty())
            {
                /* 26.2: siehe CommandActionClip -- gleicher aufbau wie vorher, welt als argument. */
                ServerLevel level = (ServerLevel) world;

                level.getServer().getCommands().performPrefixedCommand(CommandSourceUtils.of(owner, level), properties.cmdFiring);
            }
        }

        return InteractionResult.PASS;
    }

    private GunProperties getProperties(ItemStack stack)
    {
        return GunProperties.get(stack);
    }
}