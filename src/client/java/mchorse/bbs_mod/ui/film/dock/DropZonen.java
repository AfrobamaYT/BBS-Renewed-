package mchorse.bbs_mod.ui.film.dock;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Map;

/**
 * Drop-Zonen des Dock-Systems: wo unter der Maus wuerde ein Panel landen, und wie
 * sieht die Vorschau aus.
 *
 * <p>Aus {@code UIFilmPanel} herausgeloest, damit die Overlay-Andock-Geste DIESELBE
 * Zielsuche und DASSELBE visuelle Vokabular benutzt wie der Panel-Drag — zwei
 * Implementierungen wuerden auseinanderlaufen (Qt-ADS/VS-Code-Prinzip: ein einziges
 * Drop-Feedback). Das Overlay zeichnet damit UEBER seiner Abdunklung; der
 * Editor-eigene Renderer laege darunter und waere unsichtbar.</p>
 */
public final class DropZonen
{
    /** Mitte des Ziels: als Tab in dessen Stapel einreihen (kein Split). */
    public static final int CENTER = -1;

    /** Aeussere 20 % einer Kante = Split an dieser Kante. */
    public static final float KANTE = 0.2F;

    private DropZonen()
    {}

    public static final class Ziel
    {
        public final String panelId;
        public final int zone;

        public Ziel(String panelId, int zone)
        {
            this.panelId = panelId;
            this.zone = zone;
        }
    }

    /** Erstes SICHTBARES Panel unter der Maus — unsichtbare (kein Film geladen) zaehlen nicht. */
    public static Ziel zielUnterPanels(Map<String, UIElement> panels, int mouseX, int mouseY)
    {
        for (Map.Entry<String, UIElement> e : panels.entrySet())
        {
            if (!e.getValue().isVisible())
            {
                continue;
            }

            if (e.getValue().area.isInside(mouseX, mouseY))
            {
                return new Ziel(e.getKey(), zone(e.getValue().area, mouseX, mouseY));
            }
        }

        return null;
    }

    public static int zone(Area area, int mouseX, int mouseY)
    {
        float nx = area.w <= 0 ? 0.5F : (mouseX - area.x) / (float) area.w;
        float ny = area.h <= 0 ? 0.5F : (mouseY - area.y) / (float) area.h;

        if (nx < KANTE) return EditorLayoutNode.EDGE_LEFT;
        if (nx > 1F - KANTE) return EditorLayoutNode.EDGE_RIGHT;
        if (ny < KANTE) return EditorLayoutNode.EDGE_TOP;
        if (ny > 1F - KANTE) return EditorLayoutNode.EDGE_BOTTOM;

        return CENTER;
    }

    /** Vorschau: Mitte = Flaeche + Rahmen, Kante = 20%-Streifen + Linie. */
    public static void zeichnen(UIContext context, Area a, int zone)
    {
        int border = BBSSettings.primaryColor(Colors.A50);
        int fill = BBSSettings.primaryColor(Colors.A25);
        float m = KANTE;
        int strip = 2;

        switch (zone)
        {
            case EditorLayoutNode.EDGE_LEFT:
                context.batcher.box(a.x, a.y, a.x + (int) (a.w * m), a.ey(), fill);
                context.batcher.box(a.x + (int) (a.w * m) - strip, a.y, a.x + (int) (a.w * m) + strip, a.ey(), border);
                break;

            case EditorLayoutNode.EDGE_RIGHT:
                context.batcher.box(a.ex() - (int) (a.w * m), a.y, a.ex(), a.ey(), fill);
                context.batcher.box(a.ex() - (int) (a.w * m) - strip, a.y, a.ex() - (int) (a.w * m) + strip, a.ey(), border);
                break;

            case EditorLayoutNode.EDGE_TOP:
                context.batcher.box(a.x, a.y, a.ex(), a.y + (int) (a.h * m), fill);
                context.batcher.box(a.x, a.y + (int) (a.h * m) - strip, a.ex(), a.y + (int) (a.h * m) + strip, border);
                break;

            case EditorLayoutNode.EDGE_BOTTOM:
                context.batcher.box(a.x, a.ey() - (int) (a.h * m), a.ex(), a.ey(), fill);
                context.batcher.box(a.x, a.ey() - (int) (a.h * m) - strip, a.ex(), a.ey() - (int) (a.h * m) + strip, border);
                break;

            default:
                context.batcher.box(a.x, a.y, a.ex(), a.ey(), fill);

                int t = 2;

                context.batcher.box(a.x, a.y, a.ex(), a.y + t, border);
                context.batcher.box(a.x, a.ey() - t, a.ex(), a.ey(), border);
                context.batcher.box(a.x, a.y, a.x + t, a.ey(), border);
                context.batcher.box(a.ex() - t, a.y, a.ex(), a.ey(), border);
                break;
        }
    }
}
