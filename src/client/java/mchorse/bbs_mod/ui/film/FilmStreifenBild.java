package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Properties;

import static mchorse.bbs_mod.ui.film.FilmStreifen.B;
import static mchorse.bbs_mod.ui.film.FilmStreifen.FELDER;
import static mchorse.bbs_mod.ui.film.FilmStreifen.H;

/**
 * Die Datei-Seite von {@link FilmStreifen}: ein zurueckgelesenes Bild verkleinern, ins Feld
 * legen, Luecken fuellen, PNG und Merkzettel schreiben. Laeuft nur im Schreiber-Thread.
 */
final class FilmStreifenBild
{
    private static final Logger LOG = LogManager.getLogger("bbs/streifen");

    /* Speicher des zuletzt beschriebenen Films — nur unter dem Klassen-Lock. */
    private static String film;
    private static int dauer;
    private static BufferedImage bild;
    private static boolean[] echt;

    private FilmStreifenBild()
    {}

    static void schreiben(String id, int laenge, int feld, GpuReadback.Raw raw)
    {
        try
        {
            int[] klein;

            try (GpuReadback.Raw owned = raw)
            {
                klein = verkleinern(owned);
            }

            synchronized (FilmStreifenBild.class)
            {
                laden(id, laenge);
                bild.setRGB(feld * B, 0, B, H, klein, 0, B);
                echt[feld] = true;
                lueckenFuellen();

                File png = FilmStreifen.datei(id, ".strip.png");

                png.getParentFile().mkdirs();
                ImageIO.write(bild, "png", png);
                merkzettelSchreiben(id);
            }

            /* Der Textur-Zwischenspeicher haelt sonst das alte Bild bis zum Neustart. */
            Minecraft.getInstance().execute(() -> BBSModClient.getTextures().delete(Link.assets(FilmStreifen.pfad(id))));
        }
        catch (Throwable fehler)
        {
            LOG.warn("Streifenbild von " + id + " konnte nicht geschrieben werden", fehler);
        }
    }

    /** Mittig auf 16:9 beschnitten (FOV ist vertikal, ein breiter Viewport zeigt nur mehr
     *  Seite) und per Kastenfilter auf {@link #B}x{@link #H} gemittelt. */
    private static int[] verkleinern(GpuReadback.Raw raw)
    {
        int w = raw.width();
        int h = raw.height();
        int cw = w;
        int ch = h;

        if (w * 9 > h * 16)
        {
            cw = h * 16 / 9;
        }
        else
        {
            ch = w * 9 / 16;
        }

        int cx = (w - cw) / 2;
        int cy = (h - ch) / 2;
        int[] aus = new int[B * H];
        ByteBuffer rgba = raw.rgba8(GpuReadback.Origin.TOP_LEFT);

        try
        {
            for (int oy = 0; oy < H; oy++)
            {
                int y0 = cy + oy * ch / H;
                int y1 = Math.max(y0 + 1, cy + (oy + 1) * ch / H);

                for (int ox = 0; ox < B; ox++)
                {
                    int x0 = cx + ox * cw / B;
                    int x1 = Math.max(x0 + 1, cx + (ox + 1) * cw / B);
                    long r = 0, g = 0, b = 0;
                    int n = 0;

                    for (int y = y0; y < y1; y++)
                    {
                        for (int x = x0; x < x1; x++)
                        {
                            int i = (y * w + x) * 4;

                            r += rgba.get(i) & 0xFF;
                            g += rgba.get(i + 1) & 0xFF;
                            b += rgba.get(i + 2) & 0xFF;
                            n++;
                        }
                    }

                    aus[oy * B + ox] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
                }
            }
        }
        finally
        {
            MemoryUtil.memFree(rgba);
        }

        return aus;
    }

    private static void laden(String id, int laenge) throws Exception
    {
        if (id.equals(film) && laenge == dauer && bild != null)
        {
            return;
        }

        film = id;
        dauer = laenge;
        bild = new BufferedImage(B * FELDER, H, BufferedImage.TYPE_INT_ARGB);
        echt = new boolean[FELDER];

        File zettel = FilmStreifen.datei(id, ".strip.properties");
        File png = FilmStreifen.datei(id, ".strip.png");

        if (!zettel.isFile() || !png.isFile())
        {
            return;
        }

        Properties p = new Properties();

        try (Reader r = new FileReader(zettel))
        {
            p.load(r);
        }

        BufferedImage alt = ImageIO.read(png);

        /* Andere Dauer = Felder liegen an anderen Ticks: dann von vorn. */
        if (alt == null || alt.getWidth() != B * FELDER || alt.getHeight() != H
            || !String.valueOf(laenge).equals(p.getProperty("dauer")))
        {
            return;
        }

        bild.getGraphics().drawImage(alt, 0, 0, null);

        for (String s : p.getProperty("echt", "").split(","))
        {
            if (!s.isBlank())
            {
                int i = Integer.parseInt(s.trim());

                if (i >= 0 && i < FELDER)
                {
                    echt[i] = true;
                }
            }
        }
    }

    /** Jedes unechte Feld bekommt das naechste echte Bild (bei Gleichstand das fruehere). */
    private static void lueckenFuellen()
    {
        int[] puffer = new int[B * H];

        for (int i = 0; i < FELDER; i++)
        {
            if (echt[i])
            {
                continue;
            }

            for (int d = 1; d < FELDER; d++)
            {
                int quelle = i - d >= 0 && echt[i - d] ? i - d : i + d < FELDER && echt[i + d] ? i + d : -1;

                if (quelle >= 0)
                {
                    bild.getRGB(quelle * B, 0, B, H, puffer, 0, B);
                    bild.setRGB(i * B, 0, B, H, puffer, 0, B);

                    break;
                }
            }
        }
    }

    private static void merkzettelSchreiben(String id) throws Exception
    {
        StringBuilder liste = new StringBuilder();

        for (int i = 0; i < FELDER; i++)
        {
            if (echt[i])
            {
                liste.append(liste.length() == 0 ? "" : ",").append(i);
            }
        }

        Properties p = new Properties();

        p.setProperty("dauer", String.valueOf(dauer));
        p.setProperty("echt", liste.toString());

        try (Writer w = new FileWriter(FilmStreifen.datei(id, ".strip.properties")))
        {
            p.store(w, "BBS film scrub strip");
        }
    }
}
