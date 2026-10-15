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
 * Haengt {@link ModelBlockItemRenderer} an Vanillas Sonderzeichner-Weg (26.2).
 *
 * <h2>Wie das Modell auf diesen Zeichner zeigt</h2>
 *
 * <p>{@code assets/bbs/items/model.json} beschreibt ein Modell vom Typ
 * {@code minecraft:special}, dessen Feld {@code model.type} auf {@code bbs:model_block}
 * steht. Diese Kennung traegt {@code BBSModClient} beim Start in Vanillas Tabelle ein
 * (ueber {@code SpecialModelRenderersAccessor}, das Feld ist privat). Vanilla liest die
 * Kennung, holt sich hier {@link Unbaked#MAP_CODEC}, liest damit den Rest des Objekts und
 * baeckt daraus einen Zeichner.
 *
 * <h2>⚠️ Warum die Anzeigeart im MODELL steht</h2>
 *
 * <p>{@code SpecialModelRenderer.submit} bekommt die Anzeigeart NICHT gereicht (Pose,
 * Sammler, Licht, Overlay, Glitzern, Farbe — mehr nicht, im Bytecode von
 * {@code ItemStackRenderState.LayerRenderState.submit} nachgelesen). BBS braucht sie aber:
 * daran haengen Formwahl und Transformation je Ansicht (Inventar/erste/dritte Person/Boden).
 *
 * <p>Vanillas Weg dafuer ist {@code minecraft:select} ueber
 * {@code minecraft:display_context}: die Modell-Beschreibung nennt je Ansicht ein eigenes
 * {@code minecraft:special}, und jedes traegt seine Anzeigeart als Feld. Vanilla baeckt
 * dadurch je Ansicht ein eigenes Exemplar dieser Klasse — genau deshalb darf der
 * Zwischenspeicher in {@link ModelBlockItemRenderer} nicht am Exemplar haengen.
 */
public class ModelBlockSpecialRenderer implements SpecialModelRenderer<ModelBlockItemRenderer.Item>
{
    private final ItemDisplayContext anzeige;

    public ModelBlockSpecialRenderer(ItemDisplayContext anzeige)
    {
        this.anzeige = anzeige;
    }

    /**
     * ⚠️ HIER haengt das NBT-Modell dran. Was diese Methode herausgibt, ist das Einzige,
     * was {@code submit} spaeter zu sehen bekommt — {@link ModelBlockItemRenderer#get}
     * baut aus {@code DataComponents.BLOCK_ENTITY_DATA} des Stapels eine
     * {@code ModelBlockEntity} und merkt sie sich je Stapel. Wer hier den Stapel ignoriert
     * und etwas Festes herausgibt, bekommt lautlos das Standardmodell statt des
     * gespeicherten.
     *
     * <p>Gibt {@code null} fuer alles heraus, was nicht der Modell-Block-Gegenstand ist.
     * Vanilla ruft {@code submit} auch mit {@code null} auf, darum die Pruefung dort.
     */
    @Override
    public ModelBlockItemRenderer.Item extractArgument(ItemStack stack)
    {
        return ModelBlockItemRenderer.get(stack);
    }

    @Override
    //? if >=26.1 {
    /*public void submit(ModelBlockItemRenderer.Item item, PoseStack matrices, SubmitNodeCollector sammler, int light, int overlay, boolean glitzern, int farbe)
    *///?} else if >=1.21.5 {
    /*public void submit(ModelBlockItemRenderer.Item item, ItemDisplayContext kontext, PoseStack matrices, SubmitNodeCollector sammler, int light, int overlay, boolean glitzern, int farbe)
    *///?} else {
    public void render(ModelBlockItemRenderer.Item item, ItemDisplayContext kontext, PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource puffer, int light, int overlay, boolean glitzern)
    //?}
    {
        //? if <1.21.5 {
        SubmitNodeCollector sammler = new mchorse.bbs_mod.compat.gpu.SofortSammler(puffer);
        //?}
        if (item == null)
        {
            return;
        }

        /* glitzern/farbe kommen aus Vanillas Gegenstands-Zustand (Verzauberungsschimmer,
         * Toenung). BBS-Formen tragen ihre eigene Farbe und ihren eigenen Zeichenweg; der
         * alte Fabric-Zeichner bekam beides gar nicht erst gereicht. Absichtlich ungenutzt,
         * damit sich am Bild nichts aendert. */
        ModelBlockItemRenderer.submit(item, this.anzeige, matrices, sammler, light, overlay);
    }

    /**
     * Vanilla misst daraus den Umriss des Gegenstands (Bildschirmkasten im Inventar,
     * Huellkoerper des liegenden Gegenstands). Ein Modell-Block ist einen Block gross, also
     * die acht Ecken des Einheitswuerfels im Zeichenraum dieser Klasse (Ursprung =
     * Blockecke).
     *
     * <p>⚠️ NAEHERUNG: welche Form wirklich drinsteckt, weiss erst der Gegenstands-Stapel,
     * und den sieht diese Methode nicht (Vanilla merkt sich das Ergebnis einmal je
     * gebackenem Zeichner, {@code Suppliers.memoize}). Eine Form, die groesser als ein
     * Block ist, ragt also ueber den gemeldeten Umriss hinaus.
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

    /**
     * Die noch nicht gebackene Fassung — das, was in der Modell-Beschreibung steht.
     *
     * @param anzeige die Ansicht, fuer die dieser Eintrag gilt; Feld {@code context} in der
     *                JSON. Fehlt es, gilt {@code none} — dieselbe Behandlung wie Kopf/Rahmen,
     *                also Grundform und Transformation der dritten Person.
     */
    public record Unbaked(ItemDisplayContext anzeige) implements SpecialModelRenderer.Unbaked
    {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            ItemDisplayContext.CODEC.optionalFieldOf("context", ItemDisplayContext.NONE).forGetter(Unbaked::anzeige)
        ).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<ModelBlockItemRenderer.Item> bake(net.minecraft.client.model.geom.EntityModelSet kontext)
        {
            return new ModelBlockSpecialRenderer(this.anzeige);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
