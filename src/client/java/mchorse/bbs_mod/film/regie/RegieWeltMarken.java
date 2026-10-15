package mchorse.bbs_mod.film.regie;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.Level;

/**
 * Bodenplatten, Lichtstrahlen und Startring der Weltschicht. Werte je Zustand wie im Design:
 * Platte 2,4 m (Radius 1,2), Strahl beim Spielen 15 m, im Editor 9 m, Startring 3,6 m mit
 * Pfeil in die Blickrichtung der Startpose.
 */
final class RegieWeltMarken
{
    private RegieWeltMarken()
    {
    }

    /**
     * @param ziel     UUID des Markers, zu dem die Route gerade fuehrt (Strahl heller), oder null
     * @param wartet   Aufnahme im Countdown — dann steht der Startring noch
     */
    static void zeichnen(Level level, Regie regie, float tick, boolean spielen, boolean wartet, String ziel,
        double spielerX, double spielerZ, CameraRenderState kamera)
    {
        float n = tick / 20F;
        float zeit = (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000F;

        for (RegieMarker m : regie.marker.getList())
        {
            if (!m.istOrt())
            {
                continue;
            }

            float start = m.start.get() / 20F;
            float ende = m.ende.get() / 20F;
            boolean aktiv = tick >= m.start.get() && tick < m.ende.get();
            boolean erledigt = tick >= m.ende.get();
            float fortschritt = erledigt ? 0F : aktiv ? (ende - n) / Math.max(0.01F, ende - start)
                : 1F - RegieAnsicht.klemmen((start - n) / 6F, 0F, 1F);
            boolean drin = spielen && Math.hypot(spielerX - m.x.get(), spielerZ - m.z.get()) < RegieTreffer.ORT_RADIUS * 0.95F;
            float deckkraft = 1F;
            float strahl;

            if (spielen)
            {
                if (erledigt)
                {
                    deckkraft = 0.35F;
                    strahl = 0F;
                }
                else if (!aktiv && start - n > 10F)
                {
                    deckkraft = 0.55F;
                    strahl = 0.35F;
                }
                else
                {
                    strahl = aktiv ? 1.25F : 0.65F + 0.5F * fortschritt;
                }

                if (m.uuid.get().equals(ziel))
                {
                    strahl = Math.max(strahl, 1.2F);
                }
            }
            else
            {
                strahl = erledigt ? 0.25F : 0.7F;
                deckkraft = erledigt ? 0.6F : 1F;
            }

            double y = RegiePfadsuche.boden(level, m.x.get(), m.z.get(), m.y.get());
            float puls = aktiv ? 0.5F + 0.5F * (float) Math.sin(zeit * 6F) : 0F;

            flaeche("platte", m.x.get(), y + 0.035D, m.z.get(), RegieTreffer.ORT_RADIUS, m.farbe.get(), deckkraft,
                new float[] {fortschritt, aktiv ? 1F : 0F, erledigt ? 1F : 0F, drin ? 1F : 0F}, kamera);

            if (strahl > 0.01F)
            {
                strahl(m.x.get(), y + 0.035D, m.z.get(), spielen ? 15D : 9D, m.farbe.get(), strahl, puls, kamera);
            }
        }

        if (regie.startGesetzt.get() && (!spielen || wartet))
        {
            double y = RegiePfadsuche.boden(level, regie.startX.get(), regie.startZ.get(), regie.startY.get());

            /* Pfeil zeigt bei Gier θ nach (sin θ, cos θ) in x/z; Minecraft schaut bei ψ nach
             * (-sin ψ, cos ψ) — also θ = -ψ. */
            flaeche("start", regie.startX.get(), y + 0.03D, regie.startZ.get(), RegieTreffer.ORT_RADIUS * 1.5D,
                0xF2F5FA, spielen ? 0.8F : 1F,
                new float[] {(float) -Math.toRadians(regie.startYaw.get()), 0.5F + 0.5F * (float) Math.sin(zeit * 3F), 0F, 0F}, kamera);
        }
    }

    /** Flache Flaeche auf dem Boden, UV 0..1 in x und z. */
    private static void flaeche(String material, double x, double y, double z, double halb, int farbe, float deckkraft,
        float[] werte, CameraRenderState kamera)
    {
        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4 * 20))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(speicher, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float ky = (float) (y - kamera.pos.y);

            bauer.addVertex((float) (x - halb - kamera.pos.x), ky, (float) (z - halb - kamera.pos.z)).setUv(0F, 0F);
            bauer.addVertex((float) (x + halb - kamera.pos.x), ky, (float) (z - halb - kamera.pos.z)).setUv(1F, 0F);
            bauer.addVertex((float) (x + halb - kamera.pos.x), ky, (float) (z + halb - kamera.pos.z)).setUv(1F, 1F);
            bauer.addVertex((float) (x - halb - kamera.pos.x), ky, (float) (z + halb - kamera.pos.z)).setUv(0F, 1F);
            RegieWeltZug.zeichnen(material, GehaltenesNetz.kurzlebig(bauer.build()), farbe, deckkraft, werte, 0F, kamera);
        }
    }

    /** Lichtstrahl: senkrechtes Band, um die Hochachse zur Kamera gedreht, ab 16 m breiter. */
    private static void strahl(double x, double y, double z, double hoehe, int farbe, float staerke, float puls,
        CameraRenderState kamera)
    {
        double zuX = kamera.pos.x - x;
        double zuZ = kamera.pos.z - z;
        double abstand = Math.max(Math.hypot(zuX, zuZ), 0.001D);
        double breite = Math.max(1.2D, abstand * 0.06D);
        double rx = zuZ / abstand * breite / 2D;
        double rz = -zuX / abstand * breite / 2D;

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4 * 20))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(speicher, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float y0 = (float) (y - kamera.pos.y);
            float y1 = (float) (y + hoehe - kamera.pos.y);

            bauer.addVertex((float) (x - rx - kamera.pos.x), y0, (float) (z - rz - kamera.pos.z)).setUv(0F, 0F);
            bauer.addVertex((float) (x + rx - kamera.pos.x), y0, (float) (z + rz - kamera.pos.z)).setUv(1F, 0F);
            bauer.addVertex((float) (x + rx - kamera.pos.x), y1, (float) (z + rz - kamera.pos.z)).setUv(1F, 1F);
            bauer.addVertex((float) (x - rx - kamera.pos.x), y1, (float) (z - rz - kamera.pos.z)).setUv(0F, 1F);
            RegieWeltZug.zeichnen("strahl", GehaltenesNetz.kurzlebig(bauer.build()), farbe, 1F,
                new float[] {staerke, puls, (float) abstand, 0F}, 0F, kamera);
        }
    }
}
