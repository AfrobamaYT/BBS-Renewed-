package mchorse.bbs_mod.cubic.render.vanilla;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import java.util.Map;

public class ArmorRenderer
{
    private static final Map<String, Identifier> ARMOR_TEXTURE_CACHE = Maps.newHashMap();
    private final HumanoidModel innerModel;
    private final HumanoidModel outerModel;
    private final TextureAtlas armorTrimsAtlas;

    public ArmorRenderer(HumanoidModel innerModel, HumanoidModel outerModel, ModelManager bakery)
    {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        this.armorTrimsAtlas = bakery.getAtlas(Sheets.ARMOR_TRIMS_SHEET);
    }

    public void renderArmorSlot(PoseStack matrices, SubmitNodeCollector vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light)
    {
        ItemStack itemStack = entity.getEquipmentStack(armorSlot);
        Item item = itemStack.getItem();

        if (item instanceof ArmorItem armorItem)
        {
            if (armorItem.getEquipmentSlot() == armorSlot)
            {
                boolean innerModel = this.usesInnerModel(armorSlot);
                HumanoidModel bipedModel = this.getModel(armorSlot);
                ModelPart part = this.getPart(bipedModel, type);

                bipedModel.setAllVisible(true);

                part.x = part.y = part.z = 0F;
                part.xRot = part.yRot = part.zRot = 0F;
                part.xScale = part.yScale = part.zScale = 1F;

                DyedItemColor dyed = itemStack.get(DataComponents.DYED_COLOR);

                if (dyed != null)
                {
                    int color = dyed.rgb();
                    float r = (float)(color >> 16 & 255) / 255.0F;
                    float g = (float)(color >> 8 & 255) / 255.0F;
                    float b = (float)(color & 255) / 255.0F;

                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, r, g, b, null);
                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, 1F, 1F, 1F, "overlay");
                }
                else
                {
                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, 1F, 1F, 1F, null);
                }

                ArmorTrim trim = itemStack.get(DataComponents.TRIM);

                if (trim != null)
                {
                    this.renderTrim(part, armorItem.getMaterial(), matrices, vertexConsumers, light, trim, innerModel);
                }

                if (itemStack.hasFoil())
                {
                    this.renderGlint(part, matrices, vertexConsumers, light);
                }
            }
        }
    }

    private ModelPart getPart(HumanoidModel bipedModel, ArmorType type)
    {
        switch (type)
        {
            case HELMET -> {
                return bipedModel.head;
            }
            case CHEST, LEGGINGS -> {
                return bipedModel.body;
            }
            case LEFT_ARM -> {
                return bipedModel.leftArm;
            }
            case RIGHT_ARM -> {
                return bipedModel.rightArm;
            }
            case LEFT_LEG, LEFT_BOOT -> {
                return bipedModel.leftLeg;
            }
            case RIGHT_LEG, RIGHT_BOOT -> {
                return bipedModel.rightLeg;
            }
        }

        return bipedModel.head;
    }

    /**
     * 26.2: es gibt keinen Puffer mehr zum Hineinschreiben — die Geometrie wird als AUFTRAG
     * eingereicht und spaeter gezeichnet. Der Auftrag bekommt nur die KOPIERTE Pose des
     * Einreich-Zeitpunkts ({@code CustomGeometryRenderer#render(PoseStack.Pose, VertexConsumer)}),
     * {@link ModelPart#render} verlangt aber einen ganzen Stapel. Den uebergebenen Stapel im
     * Auftrag weiterzubenutzen waere falsch: bis der Auftrag laeuft, ist er laengst
     * weitergewandert. Also wird er hier aus der Pose neu aufgesetzt — {@code Pose#set} kopiert
     * pose UND normal-matrix, der render-Aufruf selbst bleibt unveraendert.
     */
    private static PoseStack poseStack(PoseStack.Pose entry)
    {
        PoseStack stack = new PoseStack();

        stack.last().set(entry);

        return stack;
    }

    private void renderArmorParts(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ArmorItem item, boolean secondTextureLayer, float red, float green, float blue, String overlay)
    {
        vertexConsumers.submitCustomGeometry(matrices, RenderTypes.armorCutoutNoCull(this.getArmorTexture(item, secondTextureLayer, overlay)), (entry, base) ->
        {
            VertexConsumer vertexConsumer = new RecolorVertexConsumer(base, new Color(red, green, blue, 1F));

            part.render(poseStack(entry), vertexConsumer, light, OverlayTexture.NO_OVERLAY);
        });
    }

    private void renderTrim(ModelPart part, Holder<ArmorMaterial> material, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ArmorTrim trim, boolean leggings)
    {
        TextureAtlasSprite sprite = this.armorTrimsAtlas.getSprite(leggings ? trim.innerTexture(material) : trim.outerTexture(material));

        vertexConsumers.submitCustomGeometry(matrices, Sheets.armorTrimsSheet(trim.pattern().value().decal()), (entry, base) ->
        {
            VertexConsumer vertexConsumer = sprite.wrap(base);

            part.render(poseStack(entry), vertexConsumer, light, OverlayTexture.NO_OVERLAY);
        });
    }

    private void renderGlint(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light)
    {
        vertexConsumers.submitCustomGeometry(matrices, RenderTypes.armorEntityGlint(), (entry, base) ->
        {
            part.render(poseStack(entry), base, light, OverlayTexture.NO_OVERLAY);
        });
    }

    private HumanoidModel getModel(EquipmentSlot slot)
    {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }

    private Identifier getArmorTexture(ArmorItem item, boolean secondLayer, String overlay)
    {
        ResourceKey<ArmorMaterial> key = item.getMaterial().unwrapKey().orElse(null);
        String materialName = key == null ? "iron" : key.location().getPath();
        String id = "textures/models/armor/" + materialName + "_layer_" + (secondLayer ? 2 : 1) + (overlay == null ? "" : "_" + overlay) + ".png";

        Identifier found = ARMOR_TEXTURE_CACHE.get(id);

        if (found == null)
        {
            found = Identifier.fromNamespaceAndPath("minecraft", id);
            ARMOR_TEXTURE_CACHE.put(id, found);
        }

        return found;
    }
}
