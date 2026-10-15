package mchorse.bbs_mod.compat;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/** ArmorTrim.layerAssetId von 1.21.5+ vor 1.21.5: dieselbe Kennung, die 1.21.4s Ruestungsrenderer baut. */
public final class TrimAlt
{
    private TrimAlt()
    {}

    public static ResourceLocation textur(ArmorTrim zier, EquipmentClientInfo.LayerType ebene, ResourceKey<EquipmentAsset> asset)
    {
        return new EquipmentLayerRenderer.TrimSpriteKey(zier, ebene, asset).textureId();
    }
}
