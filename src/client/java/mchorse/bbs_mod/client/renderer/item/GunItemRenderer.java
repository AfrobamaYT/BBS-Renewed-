package mchorse.bbs_mod.client.renderer.item;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GunItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer
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
            item.properties.update(item.formEntity);
        }
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        Item item = this.get(stack);

        if (item != null)
        {
            ModelProperties properties = item.properties;
            Form form = this.getForm(properties, mode);

            if (form != null)
            {
                item.expiration = 20;

                Transform transform = this.getTransform(properties, mode);

                matrices.push();
                matrices.translate(0.5F, 0F, 0.5F);
                MatrixStackUtils.applyTransform(matrices, transform);

                RenderSystem.enableDepthTest();
                FormUtilsClient.render(form, FormRenderingContext.set(item.formEntity, matrices, light, overlay, MinecraftClient.getInstance().getTickDelta()));
                RenderSystem.disableDepthTest();

                matrices.pop();
            }
        }
    }

    private Form getForm(ModelProperties properties, ModelTransformationMode mode)
    {
        Form form = properties.getForm();

        if (mode == ModelTransformationMode.GUI && properties.getFormInventory() != null)
        {
            form = properties.getFormInventory();
        }
        else if ((mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND) && properties.getFormThirdPerson() != null)
        {
            form = properties.getFormThirdPerson();
        }
        else if ((mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) && properties.getFormFirstPerson() != null)
        {
            form = properties.getFormFirstPerson();
        }

        return form;
    }

    private Transform getTransform(ModelProperties properties, ModelTransformationMode mode)
    {
        Transform transform = properties.getTransformThirdPerson();

        if (mode == ModelTransformationMode.GUI)
        {
            transform = properties.getTransformInventory();
        }
        else if (mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND)
        {
            transform = properties.getTransformFirstPerson();
        }
        else if (mode == ModelTransformationMode.GROUND)
        {
            transform = properties.getTransform();
        }

        return transform;
    }

    public Item get(ItemStack stack)
    {
        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        NbtCompound nbt = stack.getNbt();
        ModelProperties properties = new ModelProperties();
        Item item = new Item(properties);

        this.map.put(stack, item);

        if (nbt == null)
        {
            ExtrudedForm form = new ExtrudedForm();
            Transform tp = properties.getTransformThirdPerson();
            Transform fp = properties.getTransformFirstPerson();

            form.transform.get().translate.set(0F, 0.5F, 0F);
            form.texture.set(Link.assets("textures/gun.png"));
            properties.setForm(form);

            fp.translate.set(0.25F, 0.125F, -0.25F);
            fp.rotate.y = -MathUtils.PI / 2;
            fp.rotate2.z = MathUtils.PI / 4;

            tp.translate.y = 0.375F;
            tp.translate.z = 0.125F;
            tp.scale.set(0.666F);
            tp.rotate.y = -MathUtils.PI / 2;
            tp.rotate2.z = MathUtils.PI / 4;

            return item;
        }

        BaseType data = DataStorageUtils.readFromNbtCompound(nbt, "GunData");

        if (data.isMap())
        {
            properties.fromData(data.asMap());
        }

        return item;
    }

    public static class Item
    {
        public ModelProperties properties;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(ModelProperties properties)
        {
            this.properties = properties;
            this.formEntity = new StubEntity(MinecraftClient.getInstance().world);
        }
    }
}