package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;

/**
 * Legt eine Farbe ueber alles, was durch diesen Verbraucher geschrieben wird.
 *
 * <h2>⚠️ PORT 26.2 -- warum jede Methode {@code this} zurueckgibt und nicht den inneren Verbraucher</h2>
 *
 * <p>Vorher gab jede Methode {@code this.consumer.xyz(...)} zurueck, also den EINGEPACKTEN
 * Verbraucher. Bei einem einzelnen Aufruf faellt das nicht auf; bei einer Kette schon, und 26.2
 * schreibt Ecken fast nur noch in Ketten. {@code VertexConsumer.putBakedQuad} -- der Weg, ueber
 * den Bloecke und Items ihre Vierecke abliefern -- ruft
 * {@code addVertex(...).setColor(...).setUv(...)}: der erste Aufruf haette die Umfaerbung
 * verlassen, und JEDE weitere Ecke waere ungefaerbt durchgelaufen. Der Block erschiene, nur eben
 * immer weiss -- ein Fehler, der nichts meldet.
 *
 * <p>{@code this} zurueckzugeben ist das, was ein Dekorator ohnehin tun muss. Die Rueckgabewerte
 * dienen nur der Verkettung; niemand braucht hier den inneren Verbraucher.
 */
public class RecolorVertexConsumer implements VertexConsumer
{
    public static Color newColor;

    protected VertexConsumer consumer;
    protected Color color;

    public RecolorVertexConsumer(VertexConsumer consumer, Color color)
    {
        this.consumer = consumer;
        this.color = color;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z)
    {
        this.consumer.addVertex(x, y, z);

        return this;
    }

    @Override
    public VertexConsumer addVertex(Matrix4fc matrix, float x, float y, float z)
    {
        this.consumer.addVertex(matrix, x, y, z);

        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha)
    {
        red = MathUtils.clamp((int) (this.color.r * red), 0, 255);
        green = MathUtils.clamp((int) (this.color.g * green), 0, 255);
        blue = MathUtils.clamp((int) (this.color.b * blue), 0, 255);
        alpha = MathUtils.clamp((int) (this.color.a * alpha), 0, 255);

        this.consumer.setColor(red, green, blue, alpha);

        return this;
    }

    /**
     * 26.2: die gepackte Fassung ist eigenstaendig abstrakt, nicht mehr ein Standardrumpf, der
     * auf die vierteilige zeigt. Wer sie nicht selbst umfaerbt, laesst genau den Weg ungefaerbt,
     * ueber den Bloecke und Items zeichnen -- {@code putBakedQuad} setzt die Farbe gepackt.
     */
    @Override
    public VertexConsumer setColor(int argb)
    {
        return this.setColor(ARGB.red(argb), ARGB.green(argb), ARGB.blue(argb), ARGB.alpha(argb));
    }

    @Override
    public VertexConsumer setUv(float u, float v)
    {
        this.consumer.setUv(u, v);

        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v)
    {
        this.consumer.setUv1(u, v);

        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v)
    {
        this.consumer.setUv2(u, v);

        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z)
    {
        this.consumer.setNormal(x, y, z);

        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v)
    {
        this.consumer.setUv3(u, v);

        return this;
    }

    /** Nur fuer Linienzuege; die Umfaerbung hat daran nichts zu tun. */
    @Override
    public VertexConsumer setLineWidth(float breite)
    {
        this.consumer.setLineWidth(breite);

        return this;
    }
}
