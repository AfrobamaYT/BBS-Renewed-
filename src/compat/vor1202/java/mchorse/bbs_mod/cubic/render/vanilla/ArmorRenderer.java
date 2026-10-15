package mchorse.bbs_mod.cubic.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

import java.util.HashMap;
import java.util.Map;

/**
 * vor1202 (1.20.1): Trim ohne Abziehbild-Schalter, sonst wie vor1205. Ruestung vor 1.20.5: ArmorMaterial ohne Holder und Ebenen, Faerbung ueber DyeableLeatherItem,
 * Trim aus dem NBT. Ablauf wie Vanillas HumanoidArmorLayer#renderArmorPiece in 1.20.4
 * (Grundtextur, bei Leder zusaetzlich "_overlay", Trim aus dem Trim-Atlas, Glanz).
 */
public class ArmorRenderer
{
    private static final Map<String, ResourceLocation> TEXTUREN = new HashMap<>();

    private final HumanoidModel innerModel;
    private final HumanoidModel outerModel;
    private final TextureAtlas armorTrimsAtlas;

    public ArmorRenderer(HumanoidModel innerModel, HumanoidModel outerModel, EntityRendererProvider.Context ctx)
    {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        this.armorTrimsAtlas = ctx.getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET);
    }

    public void renderArmorSlot(PoseStack matrices, SubmitNodeCollector vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light)
    {
        ItemStack itemStack = entity.getEquipmentStack(armorSlot);

        if (!(itemStack.getItem() instanceof ArmorItem armorItem) || armorItem.getEquipmentSlot() != armorSlot)
        {
            return;
        }

        boolean innerModel = this.usesInnerModel(armorSlot);
        HumanoidModel bipedModel = this.getModel(armorSlot);
        ModelPart part = this.getPart(bipedModel, type);

        bipedModel.setAllVisible(true);

        part.x = part.y = part.z = 0F;
        part.xRot = part.yRot = part.zRot = 0F;
        part.xScale = part.yScale = part.zScale = 1F;

        ArmorMaterial material = armorItem.getMaterial();

        if (armorItem instanceof DyeableLeatherItem faerbbar)
        {
            this.renderArmorParts(part, matrices, vertexConsumers, light, textur(material, innerModel, null), faerbbar.getColor(itemStack));
            this.renderArmorParts(part, matrices, vertexConsumers, light, textur(material, innerModel, "overlay"), -1);
        }
        else
        {
            this.renderArmorParts(part, matrices, vertexConsumers, light, textur(material, innerModel, null), -1);
        }

        ArmorTrim.getTrim(Minecraft.getInstance().level.registryAccess(), itemStack)
            .ifPresent((trim) -> this.renderTrim(part, matrices, vertexConsumers, light, trim, material, innerModel));

        if (itemStack.hasFoil())
        {
            this.renderGlint(part, matrices, vertexConsumers, light);
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

    private static PoseStack poseStack(PoseStack.Pose entry)
    {
        PoseStack stack = new PoseStack();

        stack.last().set(entry);

        return stack;
    }

    private void renderArmorParts(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ResourceLocation texture, int color)
    {
        float red = (float) (color >> 16 & 255) / 255F;
        float green = (float) (color >> 8 & 255) / 255F;
        float blue = (float) (color & 255) / 255F;

        vertexConsumers.submitCustomGeometry(matrices, RenderType.armorCutoutNoCull(texture), (entry, base) ->
        {
            VertexConsumer vertexConsumer = new RecolorVertexConsumer(base, new Color(red, green, blue, 1F));

            part.render(poseStack(entry), vertexConsumer, light, OverlayTexture.NO_OVERLAY);
        });
    }

    private void renderTrim(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ArmorTrim trim, ArmorMaterial material, boolean leggings)
    {
        TextureAtlasSprite sprite = this.armorTrimsAtlas.getSprite(leggings ? trim.innerTexture(material) : trim.outerTexture(material));

        vertexConsumers.submitCustomGeometry(matrices, Sheets.armorTrimsSheet(), (entry, base) ->
        {
            part.render(poseStack(entry), sprite.wrap(base), light, OverlayTexture.NO_OVERLAY);
        });
    }

    private void renderGlint(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light)
    {
        vertexConsumers.submitCustomGeometry(matrices, RenderType.armorEntityGlint(), (entry, base) ->
        {
            part.render(poseStack(entry), base, light, OverlayTexture.NO_OVERLAY);
        });
    }

    private static ResourceLocation textur(ArmorMaterial material, boolean zweiteEbene, String zusatz)
    {
        return TEXTUREN.computeIfAbsent("textures/models/armor/" + material.getName() + "_layer_" + (zweiteEbene ? 2 : 1)
            + (zusatz == null ? "" : "_" + zusatz) + ".png", ResourceLocation::new);
    }

    private HumanoidModel getModel(EquipmentSlot slot)
    {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }
}
