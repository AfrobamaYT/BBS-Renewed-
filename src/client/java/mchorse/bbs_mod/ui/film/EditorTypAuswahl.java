package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import com.mcstudio.nano.NanoSymbole;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/** The area chooses its editor; camera preview is a real choice, not a global Window toggle. */
public final class EditorTypAuswahl
{
    private EditorTypAuswahl()
    {}

    public static float kopf(long vg, int breite, float k, String symbol, NanoFlaeche f, UIElement owner, String id)
    {
        float x = NanoBausteine.kopfzeile(vg, breite, k, symbol);
        binden(f, owner, id, k);
        return x;
    }

    public static void nano(long vg, NanoFlaeche f, UIElement owner, String id, String symbol, float k)
    {
        NanoStifte.widget(vg, 6F * k, 3F * k, 30F * k, 20F * k, 4F * k, NanoDesign.WIDGET, k);
        NanoSymbole.symbol(vg, symbol, 15F * k, 13F * k, NanoDesign.TXT, k);
        NanoSymbole.pfeilAb(vg, 29F * k, 13F * k, NanoDesign.TXT_DIM, k * 0.8F);
        binden(f, owner, id, k);
    }

    private static void binden(NanoFlaeche f, UIElement owner, String id, float k)
    {
        f.zone("editor-type", 6F * k, 2F * k, 37F * k, 22F * k,
            new Oeffnen(owner, id), "Editor type", "");
    }

    public static void oeffnen(UIElement owner, String id)
    {
        UIFilmPanel panel = owner.getParent(UIFilmPanel.class);
        if (panel == null) return;

        String original = FilmKameraDock.PANEL_ID.equals(id) ? panel.kameraDock.urspruenglicherTyp() : id;
        owner.getContext().unfocus();
        owner.getContext().replaceContextMenu(menu ->
        {
            menu.action(icon(original), IKey.raw(titel(original)), original.equals(id),
                new Wechsel(panel.kameraDock, id, original));
            menu.action(Icons.CAMERA, IKey.raw("Camera View"), FilmKameraDock.PANEL_ID.equals(id),
                new Wechsel(panel.kameraDock, id, FilmKameraDock.PANEL_ID));
        });
    }

    public static void kameraKopf(UIContext context, UIElement owner)
    {
        int x = owner.area.x;
        int y = owner.area.y;
        context.batcher.box(x, y, owner.area.ex(), y + 26, BBSSettings.chromeSurface());
        context.batcher.icon(Icons.CAMERA, Colors.WHITE, x + 15, y + 13, 0.5F, 0.5F);
        context.batcher.textShadow("\u25BE", x + 29, y + 7, Colors.WHITE);
        context.batcher.textShadow("Camera View", x + 46, y + 7, Colors.WHITE);
    }

    public static boolean kameraKlick(UIContext context, UIElement owner)
    {
        if (context.mouseButton == 0 && owner.area.isInside(context)
            && context.mouseX < owner.area.x + 43 && context.mouseY < owner.area.y + 26)
        {
            oeffnen(owner, FilmKameraDock.PANEL_ID);
            return true;
        }

        return false;
    }

    private static String titel(String id)
    {
        return switch (id)
        {
            case "main" -> "Timeline";
            case "replayProps" -> "Properties";
            case "replaysList" -> "Outliner";
            default -> "3D Viewport";
        };
    }

    private static Icon icon(String id)
    {
        return switch (id)
        {
            case "main" -> Icons.FILM;
            case "replayProps" -> Icons.PROPERTIES;
            case "replaysList" -> Icons.LIST;
            default -> Icons.VIDEO_CAMERA;
        };
    }

    private record Oeffnen(UIElement owner, String id) implements Runnable
    {
        @Override
        public void run()
        {
            oeffnen(this.owner, this.id);
        }
    }

    private record Wechsel(FilmKameraDock dock, String von, String nach) implements Runnable
    {
        @Override
        public void run()
        {
            this.dock.editorTyp(this.von, this.nach);
        }
    }
}
