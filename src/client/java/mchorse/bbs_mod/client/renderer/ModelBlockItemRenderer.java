package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ModelBlockItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    private Map<ItemStack, Item> map = new HashMap<>();

    public void update()
    {
        Iterator<Item> it = this.map.values().iterator();

        while (it.hasNext())
        {
            Item item = it.next();

            if (item.expiration <= 0)
            {
                it.remove();
            }

            item.expiration -= 1;

            item.entity.getForm().update(item.formEntity);
        }
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        Item item = this.get(stack);

        if (item != null && item.entity.getForm() != null)
        {
            item.expiration = 20;

            matrices.push();
            matrices.translate(0.5F, 0F, 0.5F);
            MatrixStackUtils.applyTransform(matrices, item.entity.getTransform());

            RenderSystem.enableDepthTest();
            FormUtilsClient.render(item.entity.getForm(), FormRenderingContext.set(item.formEntity, matrices, light, MinecraftClient.getInstance().getTickDelta()));
            RenderSystem.disableDepthTest();

            matrices.pop();
        }
    }

    private Item get(ItemStack stack)
    {
        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        NbtCompound nbt = stack.getNbt();

        if (nbt == null)
        {
            return null;
        }

        ModelBlockEntity entity = new ModelBlockEntity(new BlockPos(0, 0, 0), BBSMod.MODEL_BLOCK.getDefaultState());
        Item item = new Item(entity);

        entity.readNbt(nbt.getCompound("BlockEntityTag"));

        this.map.put(stack, item);

        return item;
    }

    class Item
    {
        public ModelBlockEntity entity;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(ModelBlockEntity entity)
        {
            this.entity = entity;
            this.formEntity = new StubEntity(MinecraftClient.getInstance().world);
        }
    }
}