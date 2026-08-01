package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * ⚗️ MESSFUEHLER: liest ein Renderziel DIREKT auf der GPU aus, statt den fertigen
 * Screenshot zu deuten.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Bleibt ein Bildbereich dunkel, stecken zwei voellig verschiedene Ursachen dahinter,
 * die von aussen gleich aussehen:</p>
 *
 * <ul>
 * <li>Es steht gar nichts im Puffer — der Zeichendurchgang liefert nichts.</li>
 * <li>Es steht etwas drin und geht erst beim Blit ins Vorschaufeld verloren.</li>
 * </ul>
 *
 * <p>Genau an diesem Unterschied hing die Modellvorschau wochenlang, und am Himmel des
 * 26.2-Ports haengt er gerade wieder: der Himmelspass laeuft (121x), mit richtigem Ziel und
 * richtiger Groesse ({@code zielIstBBS=true, ziel=1280x720}), aber im Vorschaufeld ist
 * nichts davon zu sehen.</p>
 *
 * <h2>⛔ Warum das nicht ueber den EXEC-Kanal geht</h2>
 *
 * <p>Rohe GL-Aufrufe gehoeren auf den Render-Thread; EXEC laeuft auf dem Main-Thread und
 * ein direkter Aufruf reisst das Spiel ohne Logzeile ab. Die Wache im EXEC-Kanal blockt
 * das zu Recht. Deshalb sitzt die Messung HIER und wird aus einem echten Render-Haken
 * gerufen.</p>
 *
 * <h2>Bedienung</h2>
 *
 * <p>Eine Messung wird ANGEFORDERT und laeuft dann GENAU EINMAL — {@code glReadPixels}
 * haelt die Pipeline an, das darf nicht jedes Bild passieren:</p>
 *
 * <pre>
 * System.setProperty("bbs.pufferprobe", "an");     // anfordern
 * // ... ein Bild abwarten ...
 * System.getProperty("bbs.pufferprobe.ergebnis");  // abholen
 * </pre>
 *
 * <p>Systemeigenschaften statt Felder, weil die einen Hotswap ueberleben — neue statische
 * Felder sind nach dem Tausch null (siehe {@link Messpunkt}).</p>
 *
 * <p>⚠️ GL-URSPRUNG IST UNTEN LINKS. Der Vorschau-Blit spiegelt v, der Himmel liegt im
 * Puffer also bei HOHEM y. Die Anteile unten sind darum von oben nach unten gelesen.</p>
 */
public class Pufferprobe
{
    public static final String ANFORDERUNG = "bbs.pufferprobe";
    public static final String ERGEBNIS = "bbs.pufferprobe.ergebnis";

    /** Von oben (95 % Hoehe) nach unten (5 %) — in GL-Koordinaten, also y von gross nach klein. */
    private static final float[] ANTEILE = {0.95F, 0.80F, 0.60F, 0.40F, 0.20F, 0.05F};

    private Pufferprobe()
    {}

    /**
     * Liest das Ziel aus, WENN eine Messung angefordert wurde. Sonst kehrt der Aufruf
     * sofort zurueck — er darf in einem Render-Haken stehen, ohne etwas zu kosten.
     */
    public static void vielleichtLesen(RenderTarget ziel, String stelle)
    {
        if (System.getProperty(ANFORDERUNG) == null)
        {
            return;
        }

        /* Zuerst loeschen: schlaegt das Lesen fehl, darf die Anforderung nicht stehenbleiben
         * und in jedem folgenden Bild erneut zuschlagen. */
        System.clearProperty(ANFORDERUNG);

        try
        {
            System.setProperty(ERGEBNIS, lesen(ziel, stelle));
        }
        catch (Throwable t)
        {
            System.setProperty(ERGEBNIS, "FEHLER " + t);
        }
    }

    /**
     * Wie {@link #vielleichtLesen}, aber fuer einen fertigen GL-Rahmenpuffer statt eines
     * {@code RenderTarget} — gebraucht fuer BBS' Export-Rahmenpuffer, der kein RenderTarget
     * ist. Beantwortet die Frage, mit welchem Alpha die Vorschau-Textur tatsaechlich
     * ankommt, statt sie aus dem Quelltext zu erschliessen.
     */
    public static void vielleichtLesenFbo(int fbo, int breite, int hoehe, String stelle)
    {
        if (System.getProperty(ANFORDERUNG) == null)
        {
            return;
        }

        System.clearProperty(ANFORDERUNG);

        int vorher = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);

            StringBuilder sb = new StringBuilder(stelle);

            sb.append(" ").append(breite).append("x").append(hoehe);

            ByteBuffer puffer = BufferUtils.createByteBuffer(4);

            for (float anteil : ANTEILE)
            {
                puffer.clear();
                GL11.glReadPixels(breite / 2, (int) (hoehe * anteil), 1, 1,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, puffer);

                sb.append(" | ").append((int) (anteil * 100)).append("%=")
                    .append(puffer.get(0) & 255).append(",")
                    .append(puffer.get(1) & 255).append(",")
                    .append(puffer.get(2) & 255).append(",")
                    .append(puffer.get(3) & 255);
            }

            System.setProperty(ERGEBNIS, sb.toString());
        }
        catch (Throwable t)
        {
            System.setProperty(ERGEBNIS, "FEHLER " + t);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);
        }
    }

    private static String lesen(RenderTarget ziel, String stelle)
    {
        if (ziel == null)
        {
            return stelle + ": ziel ist null";
        }

        int textur = ((com.mojang.blaze3d.opengl.GlTexture) ziel.getColorTexture()).glId();
        int vorher = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int fbo = GL30.glGenFramebuffers();

        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, textur, 0);

            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                return stelle + ": rahmenpuffer unvollstaendig (" + status + ")";
            }

            StringBuilder sb = new StringBuilder(stelle);

            sb.append(" ").append(ziel.width).append("x").append(ziel.height);

            int x = ziel.width / 2;
            ByteBuffer puffer = BufferUtils.createByteBuffer(4);

            for (float anteil : ANTEILE)
            {
                puffer.clear();
                GL11.glReadPixels(x, (int) (ziel.height * anteil), 1, 1,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, puffer);

                sb.append(" | ").append((int) (anteil * 100)).append("%=")
                    .append(puffer.get(0) & 255).append(",")
                    .append(puffer.get(1) & 255).append(",")
                    .append(puffer.get(2) & 255).append(",")
                    .append(puffer.get(3) & 255);
            }

            return sb.toString();
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);
            GL30.glDeleteFramebuffers(fbo);
        }
    }
}
