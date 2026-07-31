package mchorse.bbs_mod.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
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
            item.entity.getProperties().update(item.formEntity);
            item.formEntity.update();
        }
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, int overlay)
    {
        Item item = this.get(stack);

        if (item != null)
        {
            ModelProperties properties = item.entity.getProperties();
            Form form = properties.getForm(mode);

            if (form != null)
            {
                item.expiration = 20;

                Transform transform = properties.getTransform(mode);

                matrices.pushPose();
                matrices.translate(0.5F, 0F, 0.5F);
                MatrixStackUtils.applyTransform(matrices, transform);

                /* 26.2: die beiden Tiefen-Schalter um diesen Aufruf herum entfallen ersatzlos.
                 * Tiefe ist kein globaler Zustand mehr, sondern Teil der Pipeline — den Zustand
                 * bringt der Form-Renderer mit, zu dem FormUtilsClient.render verzweigt (Modelle
                 * ueber ModelVAOPipeline.DURCHSICHTIG: DepthStencilState GREATER_THAN_OR_EQUAL
                 * mit Tiefenschreiben, alle uebrigen Formen ueber den RenderType, mit dem ihr
                 * Renderer zeichnet). Das enableDepthTest davor erzwang nur, was jede dieser
                 * Pipelines ohnehin traegt; das disableDepthTest danach stellte die alte
                 * GUI-Annahme wieder her, und die GUI zeichnet inzwischen selbst mit
                 * RenderPipelines.GUI/GUI_TEXTURED. */
                FormUtilsClient.render(form, new FormRenderingContext()
                    .set(FormRenderType.fromModelMode(mode), item.formEntity, matrices, light, overlay, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false))
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

                matrices.popPose();
            }
        }
    }

    public Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM)
        {
            return null;
        }

        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        ModelBlockEntity entity = new ModelBlockEntity(BlockPos.ZERO, BBSMod.MODEL_BLOCK.defaultBlockState());
        Item item = new Item(entity);

        this.map.put(stack, item);

        CustomData nbtComponent = stack.get(DataComponents.BLOCK_ENTITY_DATA);

        if (nbtComponent == null)
        {
            return item;
        }

        Level world = Minecraft.getInstance().level;

        if (world != null)
        {
            /* 26.2: getUnsafe() ist weg -- es reichte den LEBENDEN Tag heraus, an dem der
             * Aufrufer dann versehentlich mitschreiben konnte. copyTag() ist der Nachfolger
             * und gibt eine Kopie. Hier wird ohnehin nur gelesen, die Kopie kostet einen
             * Tag pro Aufruf und nichts an Verhalten. */
            entity.loadAdditional(nbtComponent.copyTag(), world.registryAccess());
        }

        return item;
    }

    public static class Item
    {
        public ModelBlockEntity entity;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(ModelBlockEntity entity)
        {
            this.entity = entity;
            this.formEntity = new StubEntity(Minecraft.getInstance().level);
        }
    }
}