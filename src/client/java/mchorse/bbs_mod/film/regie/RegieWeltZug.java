package mchorse.bbs_mod.film.regie;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/**
 * Zuege der Weltschicht: Netze kameranah aufbauen und je zweimal zeichnen — erst die
 * verdeckten Teile (gedaempft), dann die sichtbaren. Das Ziel waehlt GehaltenesNetz selbst
 * (Hauptbild oder Editor-Puffer).
 */
final class RegieWeltZug
{
    private RegieWeltZug()
    {
    }

    /** Ein Band als Vierecke, kameranah; sichtbar voll, verdeckt gestrichelt. */
    static void band(RegieBand band, int farbe, float[] werte, float kopf, CameraRenderState kamera)
    {
        if (band.anzahl < 2)
        {
            return;
        }

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(band.anzahl * 4 * 20))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(speicher, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);

            for (int i = 0; i < band.anzahl - 1; i++)
            {
                ecke(bauer, band.kanteA, i, kamera, band.s[i], 1F);
                ecke(bauer, band.kanteB, i, kamera, band.s[i], -1F);
                ecke(bauer, band.kanteB, i + 1, kamera, band.s[i + 1], -1F);
                ecke(bauer, band.kanteA, i + 1, kamera, band.s[i + 1], 1F);
            }

            zeichnen("trail", GehaltenesNetz.kurzlebig(bauer.build()), farbe, 1F, werte, kopf, kamera);
        }
    }

    private static void ecke(BufferBuilder bauer, float[] kante, int i, CameraRenderState kamera, float s, float v)
    {
        bauer.addVertex((float) (kante[i * 3] - kamera.pos.x), (float) (kante[i * 3 + 1] - kamera.pos.y),
            (float) (kante[i * 3 + 2] - kamera.pos.z)).setUv(s, v);
    }

    /** Zwei Zuege je Netz: erst die verdeckten Teile (gedaempft), dann die sichtbaren. */
    static void zeichnen(String material, GehaltenesNetz netz, int farbe, float deckkraft, float[] werte, float extra, CameraRenderState kamera)
    {
        Matrix4f ansicht = new Matrix4f(kamera.viewRotationMatrix);
        float zeit = (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000F;

        try (GehaltenesNetz n = netz)
        {
            for (boolean verdeckt : new boolean[] {true, false})
            {
                GpuBufferSlice info = RegieWeltPipelines.info(farbe, deckkraft, werte, zeit, verdeckt, extra, 0F);

                n.zeichnen(RegieWeltPipelines.holen(material, verdeckt), ansicht, null, null, (RenderTarget) null,
                    (pass) -> pass.setUniform(RegieWeltPipelines.INFO, info));
            }
        }
    }
}
