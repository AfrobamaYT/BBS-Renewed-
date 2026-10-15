package mchorse.bbs_mod.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Zwischenspeicher der Waffen, die als Gegenstand gezeichnet werden, plus der Zeichenweg
 * dafuer.
 *
 * <p>⚠️ 26.2: dieselbe Umstellung wie bei {@link ModelBlockItemRenderer} — der Zeichner
 * haengt am MODELL statt am Gegenstand, {@code BuiltinItemRendererRegistry} ist ersatzlos
 * gestrichen. Die Anbindung an Vanillas Zeichenweg liegt in {@link GunSpecialRenderer},
 * die Anzeigeart steht in {@code assets/bbs/items/gun.json}. Warum der Zwischenspeicher
 * statisch sein MUSS, steht bei {@link ModelBlockItemRenderer}.
 */
public class GunItemRenderer
{
    private static final Map<ItemStack, Item> MAP = new HashMap<>();

    /**
     * Traegt {@link GunSpecialRenderer} unter der Kennung {@code bbs:gun} in Vanillas
     * Sonderzeichner-Tabelle ein. Ersetzt das alte
     * {@code BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.GUN_ITEM, ...)}.
     * Begruendung fuer den Ort und das "genau einmal": siehe
     * {@link ModelBlockItemRenderer#registriereSonderzeichner()}.
     */
    public static void registriereSonderzeichner()
    {
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.GUN_ITEM, (stack, mode, pose, puffer, licht, overlay) ->
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
            item.properties.update(item.formEntity);
            item.formEntity.update();
        }
    }

    /**
     * War {@code render(ItemStack, ItemDisplayContext, ...)}. Wie beim Modell-Block reicht
     * 26.2 den Gegenstands-Stapel nicht mehr durch (das Auslesen sitzt in
     * {@code SpecialModelRenderer.extractArgument}) und die Anzeigeart auch nicht — die
     * kommt aus der Modell-Beschreibung ueber {@link GunSpecialRenderer} herein.
     *
     * <p>⚠️ Die Anzeigeart traegt hier MEHR als nur die Formwahl: {@code mode.firstPerson()}
     * entscheidet, ob die Zielfernrohr-Form gezeigt wird. Steht in der Modell-Beschreibung
     * die falsche Anzeigeart, zoomt die Waffe still nicht mehr.
     */
    public static void submit(Item item, ItemDisplayContext mode, PoseStack matrices, SubmitNodeCollector sammler, int light, int overlay)
    {
        GunProperties properties = item.properties;
        Form form = properties.getForm(mode);
        Transform transform = properties.getTransform(mode);
        boolean zoom = mode.firstPerson() && BBSModClient.getGunZoom() != null && properties.getZoomForm() != null;

        if (zoom)
        {
            form = properties.getZoomForm();
            transform = properties.zoomTransform;
        }

        /* Preview zoom form */
        if (UIScreen.getCurrentMenu() instanceof UIModelBlockEditorMenu editorMenu && editorMenu.currentSection == editorMenu.sectionZoom)
        {
            form = editorMenu.getGunProperties().getZoomForm();
            transform = editorMenu.getGunProperties().zoomTransform;
        }

        if (form != null)
        {
            item.expiration = 20;

            matrices.pushPose();
            matrices.translate(0.5F, 0F, 0.5F);
            MatrixStackUtils.applyTransform(matrices, transform);

            /* 26.2: der Tiefentest ist kein Schalter mehr, er steckt in der Pipeline des
             * jeweiligen Draws. Die Form zeichnet ueber ModelVAOPipeline, deren
             * DepthStencilState den Test anhat und Tiefe schreibt — das enableDepthTest()
             * davor entfaellt damit ersatzlos. Das disableDepthTest() danach ebenso: es
             * schloss nur den globalen Schalter fuer die folgenden Draws, und der GUI-Raum,
             * in den das Item sonst zeichnet, hat ohnehin keine Tiefe mehr. */
            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.fromModelMode(mode), item.formEntity, matrices, light, overlay, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))
                .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

            matrices.popPose();
        }
    }

    public static Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.GUN_ITEM)
        {
            return null;
        }

        if (MAP.containsKey(stack))
        {
            return MAP.get(stack);
        }

        Item item = new Item(GunProperties.get(stack));

        MAP.put(stack, item);

        return item;
    }

    public static class Item
    {
        public GunProperties properties;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(GunProperties properties)
        {
            this.properties = properties;
            this.formEntity = new StubEntity(Minecraft.getInstance().level);
        }
    }
}