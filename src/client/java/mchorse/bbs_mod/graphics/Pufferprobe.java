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

    /* ⛔ EIGENER SCHLUESSEL JE MESSART, sonst gewinnt die haeufigste Sonde jedes Rennen.
     * Am 2026-08-01 dreimal hintereinander passiert: die Sonde im Export-Blit laeuft jedes
     * Bild, die im Stencil nur rund viermal je Sekunde -- die Anforderung war weg, bevor die
     * zweite ueberhaupt dran war, und die Antwort kam verlaesslich von der falschen Stelle.
     * Das sieht aus wie ein Ergebnis und ist keins. */
    public static final String ANFORDERUNG_FARBE = "bbs.pufferprobe.farbe";
    public static final String ERGEBNIS_FARBE = "bbs.pufferprobe.farbe.ergebnis";

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

    /**
     * Sucht in einer BBS-Textur nach einer FARBE und zaehlt die Treffer.
     *
     * <p>Fuer duenne Sachen — einen Umriss, eine Kante, ein paar Linien — taugt das
     * Abtasten einzelner Punkte nichts: sechs Punkte in der Bildmitte treffen eine
     * Ein-Pixel-Kante praktisch nie, und „nicht getroffen" liest sich dann wie „nicht da".
     * Diese Messung liest die ganze Textur und zaehlt.</p>
     *
     * <p>Meldet ausserdem, wie viele Punkte ueberhaupt ein Alpha ungleich 0 tragen — damit
     * laesst sich „das Bild ist leer" von „das Bild ist voll, nur nicht in dieser Farbe"
     * trennen.</p>
     */
    public static void vielleichtFarbeZaehlen(int texturId, int breite, int hoehe,
        int r, int g, int b, int toleranz, String stelle)
    {
        String anforderung = System.getProperty(ANFORDERUNG_FARBE);

        if (anforderung == null)
        {
            return;
        }

        /* ⛔ DER SCHLUESSEL ALLEIN REICHT NICHT MEHR -- ER MUSS DIE STELLE NENNEN KOENNEN
         * (2026-08-05, bei #13 ertappt).
         *
         * Der Kommentar oben trennt schon nach MESSART. Innerhalb der Farbsonde teilen sich
         * aber MEHRERE Aufrufstellen denselben Schluessel (Stencil, Sammeltextur, ...), und
         * es gilt dasselbe Rennen: wer zuerst im Bild drankommt, verbraucht die Anforderung.
         * Beim Versuch, die Sammeltextur zu messen, kam verlaesslich die Antwort des Stencils
         * zurueck -- eine plausible Zahl von der falschen Stelle, also genau die Sorte
         * Messfehler, die als Produktbefund gebucht wird.
         *
         * Der Wert der Eigenschaft ist jetzt ein FILTER auf `stelle`:
         *   "1" oder "*"   jede Stelle (altes Verhalten)
         *   "sammeltextur" nur Stellen, deren Name das enthaelt
         */
        if (!anforderung.equals("1") && !anforderung.equals("*")
            && !stelle.toLowerCase().contains(anforderung.toLowerCase()))
        {
            return;
        }

        System.clearProperty(ANFORDERUNG_FARBE);

        int vorher = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int fbo = GL30.glGenFramebuffers();

        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texturId, 0);

            /* ⛔ ERST DIE VOLLSTAENDIGKEIT PRUEFEN. Eine ungueltige Texturnummer (0, oder eine
             * Textur aus dem neuen GpuDevice-Weg, die sich nicht klassisch anhaengen laesst)
             * ergibt einen unvollstaendigen Rahmenpuffer, und glReadPixels liefert dann stumm
             * lauter Nullen. Das liest sich exakt wie „das Bild ist leer" — ein Messgeraet,
             * das nicht kann was verlangt wird, muss LAUT scheitern, sonst buche ich seinen
             * Ausfall als Produktfehler. */
            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                System.setProperty(ERGEBNIS_FARBE, stelle + ": MESSUNG UNGUELTIG — rahmenpuffer"
                    + " unvollstaendig (status " + status + ", texturId " + texturId + ", "
                    + breite + "x" + hoehe + "). Die Nullen unten waeren erfunden.");

                return;
            }

            ByteBuffer puffer = BufferUtils.createByteBuffer(breite * hoehe * 4);

            GL11.glReadPixels(0, 0, breite, hoehe, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, puffer);

            int treffer = 0;
            int sichtbar = 0;
            int farbig = 0;

            /* ⭐ WO liegt der Inhalt, nicht nur WIEVIEL (2026-08-04). "1815 Punkte mit Alpha"
             * beantwortet nicht, ob sie an der richtigen Stelle liegen -- und genau daran hing
             * die Frage, ob eine unsichtbare Vorschau am Blit oder an den Koordinaten liegt.
             * Der Puffer wird ohnehin ganz gelesen; das hier sind zwei Vergleiche mehr, keine
             * zweite Messung. */
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;

            for (int i = 0; i < breite * hoehe; i++)
            {
                int pr = puffer.get(i * 4) & 255;
                int pg = puffer.get(i * 4 + 1) & 255;
                int pb = puffer.get(i * 4 + 2) & 255;
                int pa = puffer.get(i * 4 + 3) & 255;

                if (pa != 0)
                {
                    sichtbar++;

                    int px = i % breite;
                    int py = i / breite;

                    if (px < minX) minX = px;
                    if (py < minY) minY = py;
                    if (px > maxX) maxX = px;
                    if (py > maxY) maxY = py;
                }

                /* ⛔ RGB GETRENNT VON ALPHA ZAEHLEN. Die erste Fassung verlangte alpha != 0 und
                 * haette einen Puffer voller Kennungen als "leer" gemeldet, wenn der Shader nur
                 * RGB schreibt -- genau der Fall, der beim Himmel zwei Sitzungen gekostet hat
                 * (#ADCBFF mit Alpha 0). Ein Messgeraet darf die Annahme nicht enthalten, die
                 * gerade geprueft wird. */
                if (pr != 0 || pg != 0 || pb != 0)
                {
                    farbig++;
                }

                if (Math.abs(pr - r) <= toleranz && Math.abs(pg - g) <= toleranz
                    && Math.abs(pb - b) <= toleranz)
                {
                    treffer++;
                }
            }

            String lage = maxX < 0 ? " | inhalt: KEINER"
                : " | inhalt liegt in (" + minX + "," + minY + ")-(" + maxX + "," + maxY + ")";

            /* Spaltenprofil: WO hoert der Inhalt auf? Eine globale Zahl ("71 % Alpha") kann
             * eine senkrechte Kante nicht von Streuung unterscheiden -- genau die Frage bei
             * Bugliste #1/#3. */
            int letzteSpalte = -1;
            int ersteLeereSpalte = -1;

            for (int x = 0; x < breite; x++)
            {
                boolean voll = false;

                for (int y = 0; y < hoehe; y += 4)
                {
                    if ((puffer.get((y * breite + x) * 4 + 3) & 255) != 0)
                    {
                        voll = true;
                        break;
                    }
                }

                if (voll)
                {
                    letzteSpalte = x;
                }
                else if (ersteLeereSpalte < 0 && x > 0)
                {
                    ersteLeereSpalte = x;
                }
            }

            lage += " | letzte gefuellte spalte " + letzteSpalte + " von " + breite
                + " (" + String.format("%.3f", letzteSpalte / (double) breite) + ")"
                + " | erste leere spalte " + ersteLeereSpalte;

            System.setProperty(ERGEBNIS_FARBE, stelle + " " + breite + "x" + hoehe
                + " (texturId " + texturId + ")" + lage
                + " | gesuchte farbe " + r + "," + g + "," + b + " (+-" + toleranz + ") = " + treffer
                + " punkte | RGB!=0 = " + farbig
                + " | alpha!=0 = " + sichtbar
                + " von " + (breite * hoehe));
        }
        catch (Throwable t)
        {
            System.setProperty(ERGEBNIS_FARBE, "FEHLER " + t);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);
            GL30.glDeleteFramebuffers(fbo);
        }
    }

    private static String lesen(RenderTarget ziel, String stelle)
    {
        if (ziel == null)
        {
            return stelle + ": ziel ist null";
        }

        int textur = ((com.mojang.renderpearl.backend.opengl.GlTexture) ziel.getColorTexture()).glId();
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
