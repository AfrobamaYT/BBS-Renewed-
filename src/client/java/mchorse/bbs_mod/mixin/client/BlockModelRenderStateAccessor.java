package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Gibt heraus, was {@code BlockModelResolver.update} in den Zustand geschrieben hat.
 *
 * <h2>Warum BBS das braucht</h2>
 *
 * <p>26.2 loest einen einzelnen Block in zwei Schritten auf: erst schreibt
 * {@code BlockModelResolver.update(zustand, blockzustand, kontext)} die Modellteile, die
 * Zeichenebene, die Anzeigetransformation und die Toenungsschichten in einen
 * {@link BlockModelRenderState}, dann reicht {@code zustand.submit(...)} das alles beim
 * Zeichenweg ein. Vanilla braucht dazwischen nichts, also sind alle diese Felder privat und
 * haben keinen Getter (javap -p geprueft).
 *
 * <p>BBS braucht dazwischen etwas: seine Formen tragen eine eigene Farbe (Farbwaehler je Form
 * plus additive Mischung), und die kommt nur an die Ecken, wenn BBS die Vierecke SELBST
 * einreicht -- ueber {@code submitCustomGeometry}, das einem den {@code VertexConsumer} in die
 * Hand gibt. Vanillas {@code submit} tut das nicht: es ruft {@code submitBlockModel} und setzt
 * die Toenungsfarbe dabei fest auf {@code -1} (weiss, im Bytecode nachgelesen). Wer diesen Weg
 * nimmt, bekommt einen Block ohne Form-Farbe -- und zwar lautlos.
 *
 * <p>Darum dieser Accessor: er ist das kleinste Loch, durch das die Zwischenergebnisse passen.
 * Die Alternative waere gewesen, die Aufloesung selbst nachzubauen (Modellwahl, Anzeige-
 * transformation, Toenungsschichten, Sonderzeichner) -- also genau die vier Dinge, die uns
 * Vanilla hier fertig hinlegt.
 *
 * <p>⚠️ Nur zwischen {@code update} und dem naechsten {@code update}/{@code clear} gueltig. Die
 * Listen sind WIEDERVERWENDET, nicht kopiert: wer sie aufhebt und spaeter liest, liest den
 * naechsten Block. {@link mchorse.bbs_mod.graphics.Blockmodell} macht darum beides -- aufloesen
 * und einreichen -- in einem Aufruf, damit dieses Fenster gar nicht erst offensteht.
 *
 * @see mchorse.bbs_mod.graphics.Blockmodell
 */
@Mixin(BlockModelRenderState.class)
public interface BlockModelRenderStateAccessor
{
    @Accessor("modelParts")
    List<net.minecraft.client.renderer.block.model.BlockModelPart> bbs$getModelParts();

    @Accessor("renderType")
    RenderType bbs$getRenderType();

    /** {@code null}, wenn die Anzeigetransformation die Einheitsmatrix ist (Vanilla normalisiert das). */
    @Accessor("transformation")
    Matrix4fc bbs$getTransformation();

    /** Blattgruen, Grasfarbe, Wasserfarbe -- je Toenungsschicht ein ARGB. Kann leer sein. */
    @Accessor("tintLayers")
    it.unimi.dsi.fastutil.ints.IntList bbs$getTintLayers();

    /** Truhe, Schild, Bett, Kopf: Bloecke ohne Backmodell, die ein eigener Zeichner malt. */
    @Accessor("specialRenderer")
    SpecialModelRenderer<?> bbs$getSpecialRenderer();

    @Accessor("specialRendererTransformation")
    Matrix4fc bbs$getSpecialRendererTransformation();
}
