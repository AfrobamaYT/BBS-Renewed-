package mchorse.bbs_mod.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Haengt {@link GunItemRenderer} an Vanillas Sonderzeichner-Weg (26.2).
 *
 * <p>Aufbau und Begruendung sind dieselben wie bei {@link ModelBlockSpecialRenderer} —
 * dort steht ausfuehrlich, warum die Anzeigeart in der Modell-Beschreibung steht und nicht
 * von Vanilla gereicht wird. Die Kennung hier ist {@code bbs:gun}, die Beschreibung liegt
 * in {@code assets/bbs/items/gun.json}.
 */
public class GunSpecialRenderer implements SpecialModelRenderer<GunItemRenderer.Item>
{
    private final ItemDisplayContext anzeige;

    public GunSpecialRenderer(ItemDisplayContext anzeige)
    {
        this.anzeige = anzeige;
    }

    /**
     * ⚠️ Hier haengen die Waffen-Eigenschaften dran: {@link GunItemRenderer#get} liest sie
     * aus {@code DataComponents.CUSTOM_DATA} des Stapels (Unterknoten {@code GunData}) und
     * merkt sie sich je Stapel. Ohne diesen Weg zeigt jede Waffe die Standardwaffe.
     */
    @Override
    public GunItemRenderer.Item extractArgument(ItemStack stack)
    {
        return GunItemRenderer.get(stack);
    }

    @Override
    //? if >=26.1 {
    /*public void submit(GunItemRenderer.Item item, PoseStack matrices, SubmitNodeCollector sammler, int light, int overlay, boolean glitzern, int farbe)
    *///?} else if >=1.21.5 {
    /*public void submit(GunItemRenderer.Item item, ItemDisplayContext kontext, PoseStack matrices, SubmitNodeCollector sammler, int light, int overlay, boolean glitzern, int farbe)
    *///?} else {
    public void render(GunItemRenderer.Item item, ItemDisplayContext kontext, PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource puffer, int light, int overlay, boolean glitzern)
    //?}
    {
        //? if <1.21.5 {
        SubmitNodeCollector sammler = new mchorse.bbs_mod.compat.gpu.SofortSammler(puffer);
        //?}
        if (item == null)
        {
            return;
        }

        /* glitzern/farbe absichtlich ungenutzt — siehe ModelBlockSpecialRenderer. */
        GunItemRenderer.submit(item, this.anzeige, matrices, sammler, light, overlay);
    }

    /**
     * Naeherung wie beim Modell-Block: Einheitswuerfel. Eine Waffenform ist meist flacher
     * als ein Block, also ist der gemeldete Umriss eher zu gross als zu klein — das ist die
     * unschaedlichere Richtung (nichts wird faelschlich weggeschnitten).
     */
    //? if >=1.21.5 {
    /*@Override
    public void getExtents(Consumer<Vector3fc> verbraucher)
    {
        for (int x = 0; x <= 1; x ++)
        {
            for (int y = 0; y <= 1; y ++)
            {
                for (int z = 0; z <= 1; z ++)
                {
                    verbraucher.accept(new Vector3f(x, y, z));
                }
            }
        }
    }
    *///?}

    /** @param anzeige die Ansicht, fuer die dieser Eintrag gilt; Feld {@code context} in der JSON. */
    public record Unbaked(ItemDisplayContext anzeige) implements SpecialModelRenderer.Unbaked
    {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            ItemDisplayContext.CODEC.optionalFieldOf("context", ItemDisplayContext.NONE).forGetter(Unbaked::anzeige)
        ).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<GunItemRenderer.Item> bake(net.minecraft.client.model.geom.EntityModelSet kontext)
        {
            return new GunSpecialRenderer(this.anzeige);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
