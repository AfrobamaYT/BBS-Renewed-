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
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Zwischenspeicher der Modell-Bloecke, die als GEGENSTAND gezeichnet werden, plus der
 * Zeichenweg dafuer.
 *
 * <h2>⚠️ 26.2 — der Zeichner haengt am MODELL, nicht mehr am Gegenstand</h2>
 *
 * <p>Diese Klasse war ein {@code BuiltinItemRendererRegistry.DynamicItemRenderer}. Der
 * Einstiegspunkt ist in fabric-rendering-v1 25.3.2 ERSATZLOS gestrichen; Mojangs Nachfolger
 * ist {@code SpecialModelRenderer}, und der wird nicht mehr AM GEGENSTAND angemeldet,
 * sondern von der Modell-Beschreibung des Gegenstands benannt
 * ({@code assets/bbs/items/model.json}, Typ {@code minecraft:special}).
 *
 * <p>Deshalb ist diese Klasse jetzt zweigeteilt: hier liegt der Zustand (Zwischenspeicher
 * je Gegenstands-Stapel, Verfall, Zeichnen), in {@link ModelBlockSpecialRenderer} liegt die
 * Anbindung an Vanillas Zeichenweg.
 *
 * <h2>Warum der Zwischenspeicher STATISCH ist</h2>
 *
 * <p>Vanilla baeckt je Anzeigeart (Inventar, erste Person, ...) einen EIGENEN
 * {@code SpecialModelRenderer}, und jeder davon fragt hier nach demselben Gegenstand. Haette
 * jeder seinen eigenen Speicher, wuerde {@link #update()} — das {@code BBSModClient} genau
 * einmal je Takt ruft — nur einen davon fortschreiten lassen: Animationen liefen in der Hand
 * und stuenden im Inventar still. Vorher gab es genau ein Exemplar in {@code BBSModClient};
 * ein gemeinsamer statischer Speicher haelt genau diesen Zustand.
 */
public class ModelBlockItemRenderer
{
    private static final Map<ItemStack, Item> MAP = new HashMap<>();

    /**
     * Traegt {@link ModelBlockSpecialRenderer} unter der Kennung {@code bbs:model_block} in
     * Vanillas Sonderzeichner-Tabelle ein. Ersetzt das alte
     * {@code BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.MODEL_BLOCK_ITEM, ...)}.
     *
     * <p>Steht hier und nicht in {@code BBSModClient}, weil dort sonst zwei weitere Importe
     * noetig waeren; der Aufruf selbst sitzt unveraendert an derselben Stelle wie vorher die
     * Anmeldung.
     *
     * <p>⚠️ Genau EINMAL aufrufen — {@code LateBoundIdMapper.put} wirft bei doppelter
     * Kennung. Nicht an ein Ressourcen-Neuladen haengen.
     */
    public static void registriereSonderzeichner()
    {
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.MODEL_BLOCK_ITEM, (stack, mode, pose, puffer, licht, overlay) ->
        {
            Item item = get(stack);

            if (item != null) submit(item, mode, pose, new mchorse.bbs_mod.compat.gpu.SofortSammler(puffer), licht, overlay);
        });
    }

    public static void update()
    {
        Iterator<Item> it = MAP.values().iterator();

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

    /**
     * War {@code render(ItemStack, ItemDisplayContext, ...)}. Zwei Aenderungen aus 26.2:
     *
     * <p>Der Gegenstands-Stapel ist weg. Vanilla trennt jetzt "was haengt am Gegenstand"
     * ({@code SpecialModelRenderer.extractArgument}, einmal je Bild) vom Zeichnen
     * ({@code submit}) — das Ergebnis des Auslesens kommt hier als {@code item} herein.
     *
     * <p>Die Anzeigeart kommt NICHT mehr von Vanilla: {@code submit} bekommt sie nicht
     * gereicht (im Bytecode geprueft: {@code ItemStackRenderState.LayerRenderState.submit}
     * reicht Pose, Sammler, Licht, Overlay, Glitzern, Farbe — mehr nicht). Sie steht darum
     * in der Modell-Beschreibung und kommt ueber {@link ModelBlockSpecialRenderer} herein.
     *
     * <p>Die Pose ist dieselbe wie vorher: {@code ItemTransform.apply} endet in BEIDEN
     * Zweigen mit {@code translate(-0.5F, -0.5F, -0.5F)} (nachgelesen), genau wie der alte
     * {@code ItemRenderer} es vor dem Aufruf des Fabric-Zeichners tat. Der Ursprung liegt
     * also weiterhin an der Blockecke, und das {@code translate(0.5F, 0F, 0.5F)} unten
     * bleibt richtig.
     */
    public static void submit(Item item, ItemDisplayContext mode, PoseStack matrices, SubmitNodeCollector sammler, int light, int overlay)
    {
        ModelProperties properties = item.entity.getProperties();
        IEntity prop = GehaltenerPropEgo.ersatz(item, mode);
        Form form = prop != null ? prop.getForm() : properties.getForm(mode);

        if (form != null)
        {
            item.expiration = 20;

            Transform transform = properties.getTransform(mode);

            matrices.pushPose();
            matrices.translate(0.5F, 0F, 0.5F);
            MatrixStackUtils.applyTransform(matrices, transform);

            if (prop != null)
            {
                /* The prop is a copy of the third-person form: its own transform is the
                 * third-person one plus the animation. Swap that base for the first-person
                 * form's, so the hand shows the block at its first-person size and place
                 * (measured 2026-10-13: without this, the owner's face filled the screen). */
                Form first = properties.getForm(mode);
                Form third = properties.getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

                if (first != null && third != null)
                {
                    matrices.mulPose(first.transform.get().createMatrix().mul(third.transform.get().createMatrix().invert()));
                }
            }

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
                .set(FormRenderType.fromModelMode(mode), prop != null ? prop : item.formEntity, matrices, light, overlay, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))
                .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

            matrices.popPose();
        }
    }

    public static Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM)
        {
            return null;
        }

        if (MAP.containsKey(stack))
        {
            return MAP.get(stack);
        }

        ModelBlockEntity entity = new ModelBlockEntity(BlockPos.ZERO, BBSMod.MODEL_BLOCK.defaultBlockState());
        Item item = new Item(entity);

        MAP.put(stack, item);

        /* 26.2: BLOCK_ENTITY_DATA traegt kein CustomData mehr, sondern TypedEntityData -- die
         * Kennung des Blockentity-Typs reist jetzt MIT den Daten. Und das Laden macht man nicht
         * mehr selbst: loadInto ist genau der Weg, den Vanillas BlockItem geht (im Bytecode
         * nachgesehen). Es prueft nebenbei, dass der Typ passt, und ruft loadWithComponents --
         * das alte handgeschriebene loadAdditional(tag, registries) sprang genau diese Pruefung. */
        net.minecraft.world.item.component.CustomData daten =
            stack.get(DataComponents.BLOCK_ENTITY_DATA);

        if (daten == null)
        {
            return item;
        }

        Level world = Minecraft.getInstance().level;

        if (world != null)
        {
            daten.loadInto(entity, world.registryAccess());
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