package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.Pair;

import java.nio.ByteBuffer;
import java.util.Map;

/** Evaluation of read-back pick ids (RGBA8, as read by {@link StencilLeser}). */
final class StencilAuswertung
{
    /** {x0, y0, w, h, x, y, radius, handleMax} of the tolerance region, or {@code null} if empty. */
    static int[] region(Framebuffer framebuffer, int x, int y, int radius, int handleMax)
    {
        Texture texture = framebuffer.getMainTexture();
        int x0 = Math.max(0, x - radius);
        int y0 = Math.max(0, y - radius);
        int x1 = Math.min(texture.width - 1, x + radius);
        int y1 = Math.min(texture.height - 1, y + radius);
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        return w <= 0 || h <= 0 ? null : new int[] {x0, y0, w, h, x, y, radius, handleMax};
    }

    /**
     * Resolve the hovered id from a read region: gizmo handles within {@code radius} win,
     * then the exact pixel, then the nearest other id. Shared by the synchronous
     * {@code StencilFormFramebuffer#pick} and the asynchronous {@code pickAsync}. {@code karte} only names the hit for the probe.
     */
    static int auswerten(Framebuffer framebuffer, ByteBuffer daten, int[] lage, Map<Integer, Pair<Form, String>> karte)
    {
        int x0 = lage[0];
        int y0 = lage[1];
        int w = lage[2];
        int h = lage[3];
        int x = lage[4];
        int y = lage[5];
        int radius = lage[6];
        int handleMax = lage[7];
        int index;

        int needed = w * h * 4;

        /* ⚗️ ROHE WERTE, BEVOR DIE ALPHA-ANNAHME GREIFT (2026-08-04).
         *
         * Die Schleife unten ueberspringt jeden Texel mit alpha < 1. Steht in diesem Puffer
         * eine Kennung MIT alpha 0, sieht sie von aussen exakt aus wie ein leerer Puffer --
         * und der sichtbare Umriss, der dieselbe Textur im Shader liest, widerspricht dem.
         * Genau dieser Fehlertyp ("alpha != 0 als Annahme IM Messgeraet") hat hier schon
         * einmal Stunden gekostet. Also erst die rohen Maxima, dann urteilen. */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            float maxA = 0F;
            float maxRGB = 0F;
            int mitte = (((y - y0) * w) + (x - x0)) * 4;

            for (int i = 0; i + 3 < needed; i += 4)
            {
                maxA = Math.max(maxA, ((daten.get(i + 3) & 0xFF) / 255F));
                maxRGB = Math.max(maxRGB, Math.max(((daten.get(i) & 0xFF) / 255F), Math.max(((daten.get(i + 1) & 0xFF) / 255F), ((daten.get(i + 2) & 0xFF) / 255F))));
            }

            mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.roh",
                "maxAlpha=" + maxA + " maxRGB=" + maxRGB
                    + " mitte=" + (mitte >= 0 && mitte + 3 < needed
                        ? ((daten.get(mitte) & 0xFF) / 255F) + "/" + ((daten.get(mitte + 1) & 0xFF) / 255F) + "/"
                            + ((daten.get(mitte + 2) & 0xFF) / 255F) + "/" + ((daten.get(mitte + 3) & 0xFF) / 255F)
                        : "ausserhalb")
                    + " region=" + x0 + "," + y0 + " " + w + "x" + h);
        }

        {
            int centerId = 0;
            int nearestHandle = 0;
            int nearestAny = 0;
            long nearestDist = Long.MAX_VALUE;
            long nearestAnyDist = Long.MAX_VALUE;
            long radiusSq = (long) radius * radius;

            for (int py = 0; py < h; py++)
            {
                for (int px = 0; px < w; px++)
                {
                    int base = (py * w + px) * 4;

                    if ((daten.get(base + 3) & 0xFF) < 1)
                    {
                        continue;
                    }

                    int id = (daten.get(base) & 0xFF)
                        | ((daten.get(base + 1) & 0xFF) << 8)
                        | ((daten.get(base + 2) & 0xFF) << 16);
                    int fx = x0 + px;
                    int fy = y0 + py;

                    if (fx == x && fy == y)
                    {
                        centerId = id;
                    }

                    long dx = fx - x;
                    long dy = fy - y;
                    long dist = dx * dx + dy * dy;

                    if (id >= 1 && id <= handleMax)
                    {
                        if (dist <= radiusSq && dist < nearestDist)
                        {
                            nearestDist = dist;
                            nearestHandle = id;
                        }
                    }
                    else if (id != 0 && dist <= radiusSq && dist < nearestAnyDist)
                    {
                        nearestAnyDist = dist;
                        nearestAny = id;
                    }
                }
            }

            /* Blenders Klick-Toleranz (view3d_select: 14->9->5px-Kaskade um den Cursor):
             * Gizmo-Handles gewinnen, dann der exakte Pixel, dann die NAECHSTE beliebige
             * Kennung im Radius — sonst sind duenne Wire-Widgets und schmale Marker nur
             * pixelgenau klickbar und fuehlen sich tot an. */
            index = nearestHandle != 0 ? nearestHandle : (centerId != 0 ? centerId : nearestAny);

            /* Messfuehler 2026-08-02, zweiter Anlauf: das Leseziel stimmt inzwischen
             * (gebunden=29=eigener), der Index bleibt 0. Diese Zeile trennt die zwei
             * verbliebenen Ursachen -- LEERER Puffer (Bildgraph zeichnet erst spaeter,
             * dann ist deckung=0) gegen FALSCHE STELLE (Puffer traegt Kennungen, nur
             * nicht unter der Maus, dann ist deckung>0 bei maxId>0). */
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                int deckung = 0;
                int maxId = 0;

                for (int py = 0; py < h; py++)
                {
                    for (int px = 0; px < w; px++)
                    {
                        int base = (py * w + px) * 4;

                        if ((daten.get(base + 3) & 0xFF) < 1)
                        {
                            continue;
                        }

                        deckung++;
                        maxId = Math.max(maxId, (daten.get(base) & 0xFF)
                            | ((daten.get(base + 1) & 0xFF) << 8)
                            | ((daten.get(base + 2) & 0xFF) << 16));
                    }
                }

                Texture t = framebuffer.getMainTexture();

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.inhalt",
                    "deckung=" + deckung + "/" + (w * h) + " maxId=" + maxId
                        + " region=" + x0 + "," + y0 + " " + w + "x" + h
                        + " textur=" + t.width + "x" + t.height);
            }

            /* Messfuehler 2026-08-02: prueft die ANNAHME aus ModelInstance#fillStencilMap --
             * dass orderedGroups/orderedBones der Reihenfolge entspricht, die
             * BOBJModelVAO.updateMesh als Knochenteil ins Licht-Attribut backt. Der
             * Formteil kommt seit 869c910a7 an; ob die SUMME den richtigen Knochen
             * trifft, sagt erst der zurueckgelesene Name.
             *
             * Steht HIER und nicht im Aufrufer: UIFilmController ist Mixin-Ziel eines
             * Redirectors, dessen Neuwebung beim Hotswap scheitert (ClassFormatError).
             * Diese Klasse ist ungewoben und damit live tauschbar. */
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                Pair<Form, String> getroffen = karte.get(index);

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.getroffen",
                    "index=" + index
                        + " knochen=" + (getroffen == null ? "NICHTS" : "'" + getroffen.b + "'")
                        + " gizmoTreffer=" + nearestHandle + " mitte=" + centerId);
            }
        }

        return index;
    }

    private StencilAuswertung()
    {}
}
