package mchorse.bbs_mod.cubic.render.vanilla;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=26.3 {
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.palette.PalettedTextureManager;
//?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//? if >=26.3 {
//?} else {
/*import net.minecraft.client.renderer.Sheets;
*///?}
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=26.3 {
//?} else {
/*import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
*///?}
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArmorRenderer
{
    /* 26.2: der Texturpfad wird nicht mehr aus dem Materialnamen gebaut, sondern aus der
     * Ausruestungs-Beschreibung (assets/<ns>/equipment/<asset>.json) gelesen. Der Cache
     * bleibt, weil Layer#getTextureLocation bei JEDEM Aufruf einen neuen Identifier baut
     * und das hier pro Bild und Ruestungsteil passiert (Vanilla memoisiert es genauso,
     * siehe EquipmentLayerRenderer#layerTextureLookup). Schluessel = Ebenentyp + Textur-ID,
     * denn dieselbe Textur-ID ergibt je Ebenentyp einen ANDEREN Pfad
     * (humanoid/ vs. humanoid_leggings/). */
    private static final Map<String, Identifier> ARMOR_TEXTURE_CACHE = Maps.newHashMap();
    private final HumanoidModel innerModel;
    private final HumanoidModel outerModel;
    //? if >=26.3 {
    //?} else {
    /*private final TextureAtlas armorTrimsAtlas;
    *///?}
    private final EquipmentAssetManager equipmentAssets;

    /**
     * ⚠️ Nimmt jetzt den ganzen {@link EntityRendererProvider.Context} statt eines
     * {@code ModelManager}: 26.2 hat {@code ModelManager#getAtlas} entfernt (Atlanten haengen
     * am {@code AtlasManager}), und die Ruestungstexturen kommen ueberhaupt erst ueber den
     * {@link EquipmentAssetManager} — den gibt es NUR an diesem Context, nicht an
     * {@code Minecraft.getInstance()}. Aufrufer muss {@code ctx} durchreichen.
     */
    public ArmorRenderer(HumanoidModel innerModel, HumanoidModel outerModel, EntityRendererProvider.Context ctx)
    {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        //? if >=26.3 {
        //?} else {
        /*/^* ⛔ DAS WAR DER SCHWARZE BILDSCHIRM. Sheets.ARMOR_TRIMS_SHEET ist der TEXTURPFAD
         * (minecraft:textures/atlas/armor_trims.png); getAtlas will in 26.2 eine ATLAS-KENNUNG
         * (net.minecraft.data.AtlasIds.ARMOR_TRIMS). Der falsche Wert wirft
         *     IllegalArgumentException: Invalid atlas id: minecraft:textures/atlas/armor_trims.png
         * und zwar in EntityRenderDispatcher.onResourceManagerReload -- also MITTEN IM
         * RESSOURCEN-RELOAD. Der bricht dadurch ab, kein Modell wird fertig gebacken, und das
         * Spiel zeichnet ab da NICHTS mehr.
         *
         * ⚠️ WARUM DAS SO LANGE GEDAUERT HAT: es sieht nicht wie ein Renderfehler aus. Der
         * Renderthread laeuft weiter, es kommt KEINE Ausnahme pro Bild, der Titelbildschirm
         * meldet sich normal -- nur das Bild bleibt schwarz. Die Ausnahme faellt EINMAL, waehrend
         * des Ladens, und steht danach still im Log. Der Hinweis, der es aufloeste, kam vom
         * Nutzer: "Ladebildschirm war kurz da, DANN schwarz" -- also kippt es beim Reload-Ende. *^/
        this.armorTrimsAtlas = ctx.getAtlas(net.minecraft.data.AtlasIds.ARMOR_TRIMS);
        *///?}
        this.equipmentAssets = ctx.getEquipmentAssets();
    }

    public void renderArmorSlot(PoseStack matrices, SubmitNodeCollector vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light)
    {
        ItemStack itemStack = entity.getEquipmentStack(armorSlot);

        /* ⚠️ ArmorItem gibt es in 26.2 nicht mehr — Ruestung ist datengetrieben und haengt
         * am Bestandteil EQUIPPABLE. Damit faellt auch der Umweg ueber ArmorItem#getMaterial
         * weg: was gezeichnet wird, sagt die assetId (z.B. minecraft:iron). Fehlt sie, ist
         * das Teil zwar tragbar, hat aber keine Ruestungsdarstellung (z.B. Kuerbis). */
        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);

        if (equippable == null || equippable.slot() != armorSlot)
        {
            return;
        }

        ResourceKey<EquipmentAsset> asset = equippable.assetId().orElse(null);

        if (asset == null)
        {
            return;
        }

        boolean innerModel = this.usesInnerModel(armorSlot);
        HumanoidModel bipedModel = this.getModel(armorSlot);
        ModelPart part = this.getPart(bipedModel, type);

        /* ⚠️ HumanoidModel#setAllVisible ist ersatzlos weg (Vanilla baut Ruestungsmodelle
         * heute schon nur mit den Teilen des jeweiligen Slots). Hier wird trotzdem ein
         * einzelnes Teil aus einem geteilten Modell gezeichnet, also muss die Sichtbarkeit
         * weiterhin von Hand zurueckgesetzt werden — sonst bleibt ein Teil unsichtbar, das
         * ein frueherer Aufruf ausgeblendet hat.
         * Der Weg ueber root() ist ABSICHT und keine Umstaendlichkeit: die Felder oben sind
         * ein ROHER HumanoidModel-Typ, und an einem rohen Typ verliert Model#allParts() durch
         * Typloeschung sein List<ModelPart> und liefert eine rohe List. ModelPart ist nicht
         * generisch, getAllParts() bleibt dort also typisiert (self + alle Nachfahren). */
        for (ModelPart modelPart : bipedModel.root().getAllParts())
        {
            modelPart.visible = true;
        }

        part.x = part.y = part.z = 0F;
        part.xRot = part.yRot = part.zRot = 0F;
        part.xScale = part.yScale = part.zScale = 1F;

        /* Der Ebenentyp ersetzt das alte "_layer_1"/"_layer_2" am Dateinamen. */
        EquipmentClientInfo.LayerType layerType = innerModel
            ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
            : EquipmentClientInfo.LayerType.HUMANOID;
        int dyeColor = DyedItemColor.getOrDefault(itemStack, 0);
        //? if >=26.3 {
        EquipmentClientInfo equipmentInfo = this.equipmentAssets.get(asset);
        List<EquipmentClientInfo.Layer> layers = equipmentInfo.getLayers(layerType);
        //?} else {
        /*List<EquipmentClientInfo.Layer> layers = this.equipmentAssets.get(asset).getLayers(layerType);
        *///?}

        //? if >=26.3 {
        if (layers.isEmpty())
        {
            return;
        }

        ArmorTrim trim = itemStack.get(DataComponents.TRIM);
        boolean drawTrim = trim != null && layerType != EquipmentClientInfo.LayerType.HUMANOID_BABY;
        boolean drawLayerGlint = itemStack.hasFoil() && !drawTrim;
        int order = 1;

        //?} else {
        /*/^* ⚠️ Verhaltensaenderung: frueher waren es fest ein bzw. zwei Ebenen (Grundtextur +
         * "_overlay" bei gefaerbtem Leder). Jetzt bestimmt die Beschreibung, WIE VIELE Ebenen
         * es gibt und welche davon faerbbar ist. Fuer Vanilla-Ruestung ist das Ergebnis
         * dasselbe; Datenpakete/Mods mit mehr Ebenen werden jetzt richtig gezeichnet. *^/
        *///?}
        for (EquipmentClientInfo.Layer layer : layers)
        {
            int color = getColorForLayer(layer, dyeColor);

            //? if >=26.3 {
            //?} else {
            /*/^* 0 heisst: faerbbare Ebene ohne Farbe — die wird gar nicht gezeichnet. *^/
            *///?}
            if (color == 0)
            {
                continue;
            }

            //? if >=26.3 {
            this.renderArmorParts(part, matrices, vertexConsumers.order(order++), light,
                this.getArmorTexture(layer, layerType), color, drawLayerGlint);
            drawLayerGlint = false;
            //?} else {
            /*this.renderArmorParts(part, matrices, vertexConsumers, light, this.getArmorTexture(layer, layerType), color);
            *///?}
        }

        //? if >=26.3 {
        if (drawTrim)
        {
            PalettedTextureManager.Handle handle = resolveTrim(trim, layerType, equipmentInfo);
            this.renderTrim(part, matrices, vertexConsumers.order(order++), light, trim, handle);
        //?} else {
        /*ArmorTrim trim = itemStack.get(DataComponents.TRIM);
        *///?}

            //? if >=26.3 {
            if (itemStack.hasFoil())
            {
                this.renderGlint(part, matrices, vertexConsumers.order(order), light);
            }
            //?} else {
        /*if (trim != null)
        {
            this.renderTrim(part, asset, layerType, matrices, vertexConsumers, light, trim);
        }

        if (itemStack.hasFoil())
        {
            this.renderGlint(part, matrices, vertexConsumers, light);
            *///?}
        }
    }

    /**
     * 1:1 aus {@code EquipmentLayerRenderer#getColorForLayer}: nicht faerbbare Ebenen werden
     * weiss gezeichnet (-1), faerbbare nehmen die Faerbung des Gegenstands, und ohne Faerbung
     * die Vorgabefarbe der Beschreibung — fehlt auch die, ist das Ergebnis 0 = Ebene faellt aus.
     */
    private static int getColorForLayer(EquipmentClientInfo.Layer layer, int dyeColor)
    {
        Optional<EquipmentClientInfo.Dyeable> dyeable = layer.dyeable();

        if (dyeable.isPresent())
        {
            int undyed = dyeable.get().colorWhenUndyed().map(ARGB::opaque).orElse(0);

            return dyeColor != 0 ? dyeColor : undyed;
        }

        return -1;
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

    //? if >=26.3 {
    private void renderArmorParts(ModelPart part, PoseStack matrices,
        OrderedSubmitNodeCollector vertexConsumers, int light, Identifier texture, int color, boolean glint)
    //?} else {
    /*private void renderArmorParts(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, Identifier texture, int color)
    *///?}
    {
        //? if >=26.3 {
        //?} else {
        /*/^* Farbe kommt jetzt als ARGB-Zahl statt als drei floats; zerlegt wird sie wie vorher,
         * die Deckkraft bleibt fest 1 (alle Wege oben liefern deckende Farben). *^/
        *///?}
        float red = (float) (color >> 16 & 255) / 255F;
        float green = (float) (color >> 8 & 255) / 255F;
        float blue = (float) (color & 255) / 255F;

        //? if >=26.3 {
        vertexConsumers.submitCustomGeometry(matrices,
            glint ? RenderTypes.armorCutoutNoCullGlint(texture) : RenderTypes.armorCutoutNoCull(texture),
            (entry, base) ->
        //?} else {
        /*vertexConsumers.submitCustomGeometry(matrices, RenderTypes.armorCutoutNoCull(texture), (entry, base) ->
        *///?}
        {
            //? if >=26.3 {
            VertexConsumer consumer = new RecolorVertexConsumer(base, new Color(red, green, blue, 1F));
            part.render(poseStack(entry), consumer, light, OverlayTexture.NO_OVERLAY);
            //?} else {
            /*VertexConsumer vertexConsumer = new RecolorVertexConsumer(base, new Color(red, green, blue, 1F));

            part.render(poseStack(entry), vertexConsumer, light, OverlayTexture.NO_OVERLAY);
            *///?}
        });
    }

    //? if >=26.3 {
    private void renderTrim(ModelPart part, PoseStack matrices,
        OrderedSubmitNodeCollector vertexConsumers, int light, ArmorTrim trim,
        PalettedTextureManager.Handle handle)
    //?} else {
    /*private void renderTrim(ModelPart part, ResourceKey<EquipmentAsset> asset, EquipmentClientInfo.LayerType layerType, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ArmorTrim trim)
    *///?}
    {
        //? if >=26.3 {
        vertexConsumers.submitCustomGeometry(matrices,
            RenderTypes.armorTrim(handle.textureLocation(), trim.pattern().value().decal()), (entry, base) ->
        //?} else {
        /*/^* ⚠️ trim.innerTexture/outerTexture(material) gibt es nicht mehr. Die Zier-Textur
         * haengt jetzt an der Ausruestungs-assetId, nicht am Ruestungsmaterial: der Praefix
         * kommt vom Ebenentyp ("", "leggings"), der Rest aus Muster + Zier-Material. *^/
        TextureAtlasSprite sprite = this.armorTrimsAtlas.getSprite(trim.layerAssetId(layerType.trimAssetPrefix(), asset));

        vertexConsumers.submitCustomGeometry(matrices, Sheets.armorTrimsSheet(trim.pattern().value().decal()), (entry, base) ->
        *///?}
        {
            //? if >=26.3 {
            part.render(poseStack(entry), handle.wrap(base), light, OverlayTexture.NO_OVERLAY);
            //?} else {
            /*VertexConsumer vertexConsumer = sprite.wrap(base);

            part.render(poseStack(entry), vertexConsumer, light, OverlayTexture.NO_OVERLAY);
            *///?}
        });
    }

    //? if >=26.3 {
    private void renderGlint(ModelPart part, PoseStack matrices,
        OrderedSubmitNodeCollector vertexConsumers, int light)
    //?} else {
    /*private void renderGlint(ModelPart part, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light)
    *///?}
    {
        //? if >=26.3 {
        vertexConsumers.submitCustomGeometry(matrices, RenderTypes.trimmedArmorGlint(), (entry, base) ->
        //?} else {
        /*vertexConsumers.submitCustomGeometry(matrices, RenderTypes.armorEntityGlint(), (entry, base) ->
        *///?}
        {
            part.render(poseStack(entry), base, light, OverlayTexture.NO_OVERLAY);
        });
    //? if >=26.3 {
    }

    private static PalettedTextureManager.Handle resolveTrim(ArmorTrim trim,
        EquipmentClientInfo.LayerType layerType, EquipmentClientInfo equipmentInfo)
    {
        Identifier texture = trim.pattern().value().assetId();
        Identifier palette = trim.material().value().paletteId();

        for (EquipmentClientInfo.TrimOverride override : equipmentInfo.trimOverrides())
        {
            if (override.predicate().matches(trim))
            {
                texture = override.textureId().orElse(texture);
                palette = override.paletteId().orElse(null);
                break;
            }
        }

        Identifier logicalTexture = texture.withPath(path -> layerType.trimAssetPrefix() + "/" + path);

        if (palette != null)
        {
            return Minecraft.getInstance().getPalettedTextureManager().getOrPrepare(logicalTexture, palette);
        }

        Identifier directTexture = logicalTexture.withPath(path -> "textures/" + path + ".png");

        return new PalettedTextureManager.Handle()
        {
            @Override
            public Identifier textureLocation()
            {
                return directTexture;
            }

            @Override
            public float getU(float u)
            {
                return u;
            }

            @Override
            public float getV(float v)
            {
                return v;
            }
        };
    //?}
    }

    private HumanoidModel getModel(EquipmentSlot slot)
    {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }

    /**
     * ⚠️ Der alte Pfad {@code textures/models/armor/<material>_layer_<n>.png} existiert in 26.2
     * NICHT mehr — die Texturen liegen unter {@code textures/entity/equipment/<ebenentyp>/}.
     * Ihn weiterzubauen haette anstandslos kompiliert und jede Ruestung als fehlende Textur
     * gezeichnet. {@code Layer#usePlayerTexture} wird hier bewusst ignoriert: dafuer braeuchte
     * es die Spielerhaut-Textur der Entitaet, die es an dieser Stelle nicht gibt — Vanilla
     * faellt in dem Fall ebenfalls auf die Ebenentextur zurueck, wenn keine gereicht wird.
     */
    private Identifier getArmorTexture(EquipmentClientInfo.Layer layer, EquipmentClientInfo.LayerType layerType)
    {
        String id = layerType.getSerializedName() + "/" + layer.textureId();
        Identifier found = ARMOR_TEXTURE_CACHE.get(id);

        if (found == null)
        {
            found = layer.getTextureLocation(layerType);
            ARMOR_TEXTURE_CACHE.put(id, found);
        }

        return found;
    }
}
