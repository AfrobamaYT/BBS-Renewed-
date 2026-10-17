package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.GehaltenerModellblock;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Alt-click chooses the drawn item instance, never another prop with the same model asset. */
public final class GehaltenerBlockPicker
{
    private static boolean zeichnen;

    public static boolean zeichnet()
    {
        return zeichnen;
    }

    public static boolean klick(UIFilmPanel panel, UIContext context)
    {
        if (context.mouseButton != 0 || !Window.isAltPressed() || panel.getData() == null
            || panel.getController().isControlling() || panel.isFlying()
            || !panel.preview.getViewport().isInside(context) || context.hasContextMenu())
        {
            return false;
        }
        List<GehaltenerBlockBild.Bild> bilder = GehaltenerBlockBild.bilder();
        bilder.removeIf(bild -> !panel.getData().replays.getList().contains(bild.halter())
            || !ItemStack.isSameItemSameComponents(bild.stack(), GehaltenerBlockBild.hand(bild.halter(), panel.getCursor(), bild.nebenhand())));
        if (bilder.isEmpty())
        {
            return false;
        }

        try
        {
            zeichnen = true;
            GehaltenerBlockBild.Bild hit = GehaltenerBlockPickPass.treffen(panel, context, bilder);
            return hit != null && waehlen(panel, hit);
        }
        catch (RuntimeException error)
        {
            error.printStackTrace();
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(IKey.raw("Held model block"),
                IKey.raw("This held model block could not be selected. See the game log.")));
            return true;
        }
        finally
        {
            zeichnen = false;
        }
    }

    public static boolean waehlen(UIFilmPanel panel, GehaltenerBlockBild.Bild hit)
    {
        Film film = panel.getData();
        UIReplayList list = panel.replayEditor.replaysList.replays;
        GehaltenerPropDaten.Verweis link = GehaltenerPropDaten.lesen(hit.stack());
        Replay prop = GehaltenerPropDaten.ziel(film, link);
        if (prop != null && link.holder().equals(hit.halter().uuid.get()) && link.off() == hit.nebenhand())
        {
            String legacy = "Held: " + prop.form.get().getDisplayName();
            if (prop.label.get().equals(legacy))
            {
                String name = name(film, prop.form.get(), hit.nebenhand());
                BaseValue.edit(prop.label, value -> value.set(name));
            }
            zeigen(panel, prop);
            return true;
        }
        return GehaltenerModellblock.ausHand(list, hit.halter(), hit.nebenhand());
    }

    public static void zeigen(UIFilmPanel panel, Replay prop)
    {
        panel.replayEditor.setReplay(prop);
        panel.replayEditor.replaysList.replays.scrollToReplay(prop);
        zeigenIn(panel, prop);
    }

    private static void zeigenIn(mchorse.bbs_mod.ui.framework.elements.UIElement root, Replay prop)
    {
        if (root instanceof mchorse.bbs_mod.ui.film.UIOutlinerNano outliner)
        {
            outliner.zeigeReplay(prop);
        }
        for (mchorse.bbs_mod.ui.framework.elements.IUIElement element : root.getChildren())
        {
            if (element instanceof mchorse.bbs_mod.ui.framework.elements.UIElement child)
            {
                zeigenIn(child, prop);
            }
        }
    }

    public static String name(Film film, Form form, boolean nebenhand)
    {
        Set<String> belegt = new HashSet<>();
        for (Replay replay : film.replays.getList())
        {
            belegt.add(replay.getName());
        }
        String hand = nebenhand ? "Off hand " : "Main hand ";
        for (int i = 1; ; i++)
        {
            String name = hand + i + ": " + form.getDisplayName();
            if (!belegt.contains(name))
            {
                return name;
            }
        }
    }

    private GehaltenerBlockPicker()
    {}
}
