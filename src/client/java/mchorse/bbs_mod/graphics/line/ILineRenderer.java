package mchorse.bbs_mod.graphics.line;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Schreibt die Ecken EINES Linienpunktes.
 *
 * <p>⚠️ Der Parameter ist {@link VertexConsumer}, nicht {@code BufferBuilder}, und das ist
 * kein kosmetischer Unterschied: in der Oberflaeche werden die Ecken NICHT sofort
 * geschrieben, sondern spaeter aus einem Lambda heraus, wenn der Bildgraph an der Reihe ist
 * ({@code Batcher2D.netz}). Dort gibt es keinen BufferBuilder, nur den allgemeineren
 * VertexConsumer.
 *
 * <p>Wer hier wieder einen BufferBuilder verlangt, zwingt den Aufrufer zurueck auf den
 * Sofortmodus — und der zeichnet in der Oberflaeche UNTER das gesamte GUI. Siehe
 * {@link LineBuilder#render}, wo genau das die Kurven unsichtbar gemacht hat.
 */
public interface ILineRenderer <T>
{
    public void render(VertexConsumer builder, Matrix4f matrix, LinePoint<T> point);
}
