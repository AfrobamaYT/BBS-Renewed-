package mchorse.bbs_mod.mixin.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gibt die Zuordnung "Kennung -&gt; Sonderzeichner" heraus, damit BBS eigene Zeichner
 * eintragen kann.
 *
 * <h2>Warum BBS das braucht</h2>
 *
 * <p>26.2 hat Gegenstands-Zeichner vom GEGENSTAND ans MODELL gehaengt. Ein Modell vom Typ
 * {@code minecraft:special} nennt in seinem Feld {@code model.type} eine Kennung, und die
 * loest {@link SpecialModelRenderers#CODEC} in einen {@code SpecialModelRenderer.Unbaked}
 * auf. Die Tabelle dahinter ist ein PRIVATES statisches Feld:
 *
 * <pre>
 * private static final ExtraCodecs.LateBoundIdMapper&lt;ResourceLocation,
 *     MapCodec&lt;? extends SpecialModelRenderer.Unbaked&lt;?&gt;&gt;&gt; ID_MAPPER;
 * </pre>
 *
 * <p>Gefuellt wird sie ausschliesslich in {@code SpecialModelRenderers.bootstrap()}, mit
 * festverdrahteten {@code ID_MAPPER.put(new ResourceLocation("bell"),
 * BellSpecialRenderer.Unbaked.MAP_CODEC)}-Zeilen (im Bytecode nachgelesen). Es gibt weder
 * eine oeffentliche Eintrage-Methode in Vanilla noch einen Haken in fabric-rendering:
 * {@code BuiltinItemRendererRegistry} ist in fabric-rendering-v1 25.3.2 ERSATZLOS
 * gestrichen, ein Nachfolger wurde nicht gebaut. Dieser Accessor ist damit der einzige
 * Weg, einen eigenen Zeichner anzumelden.
 *
 * <p>⚠️ {@code LateBoundIdMapper.put} wirft bei doppelter Kennung. Also genau einmal je
 * Kennung eintragen — der Aufruf steht in {@code BBSModClient.onInitializeClient} und
 * darf nicht bei einem Ressourcen-Neuladen wiederholt werden.
 *
 * <p>Die Tabelle wird erst beim Einlesen der Gegenstands-Modelle abgefragt (der Codec ist
 * "late bound"), darum ist die Reihenfolge gegenueber {@code bootstrap()} egal — solange
 * der Eintrag vor dem ersten Ressourcen-Ladevorgang steht, und Fabrics Client-Initialisierung
 * laeuft davor.
 *
 * @see mchorse.bbs_mod.client.renderer.item.ModelBlockSpecialRenderer
 * @see mchorse.bbs_mod.client.renderer.item.GunSpecialRenderer
 */
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccessor
{
    /**
     * Statischer Accessor auf ein statisches Feld — Mixin verlangt dafuer einen Rumpf,
     * den es beim Weben ersetzt. Wird der Mixin nicht angewandt, fliegt der Fehler hier
     * LAUT heraus statt still den falschen Wert zu liefern.
     */
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<ResourceLocation, MapCodec<? extends SpecialModelRenderer.Unbaked>> bbs$getIdMapper()
    {
        throw new AssertionError("SpecialModelRenderersAccessor wurde nicht gewoben");
    }
}
