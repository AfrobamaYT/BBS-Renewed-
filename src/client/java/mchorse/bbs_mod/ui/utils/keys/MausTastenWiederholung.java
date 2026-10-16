package mchorse.bbs_mod.ui.utils.keys;

import com.mcstudio.nano.NanoTextFokus;
import com.blenderbridge.actors.ActorLeaseAccess;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import net.minecraft.client.Minecraft;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Mouse buttons have no native key-repeat events. Repeat only a shortcut that accepted the press. */
public final class MausTastenWiederholung
{
    private static final Map<UIContext, Gehalten> gehalten = new WeakHashMap<>();
    private static final long VERZOEGERUNG = 50_000_000L;
    private static final long ABSTAND = 50_000_000L;

    private MausTastenWiederholung()
    {}

    public static void beginnen(UIContext context, KeybindManager manager, Keybind bind)
    {
        if (!bind.getCombo().repeatable)
        {
            stoppen(context);
            return;
        }

        gehalten.put(context, new Gehalten(manager, bind, context.mouseButton));
    }

    /** UIBaseMenu already knows the exact element that consumed the physical click. */
    public static void zuordnen(UIContext context, IUIElement element)
    {
        Gehalten zug = gehalten.get(context);

        if (zug == null)
        {
            return;
        }

        if (context.mouseButton != zug.button || !(element instanceof UIElement owner))
        {
            stoppen(context);
            return;
        }

        if (zug.owner == null)
        {
            zug.owner = new WeakReference<>(owner);
        }
    }

    public static void stoppen(UIContext context)
    {
        gehalten.remove(context);
    }

    public static void tick(UIContext context)
    {
        Gehalten zug = gehalten.get(context);

        if (zug == null)
        {
            return;
        }

        Keybind bind = zug.bind.get();
        KeybindManager manager = zug.manager.get();
        UIElement owner = zug.owner == null ? null : zug.owner.get();

        if (bind == null || manager == null || owner == null || !owner.canBeSeen()
            || !Minecraft.getInstance().isWindowActive() || UIScreen.getCurrentMenu() != context.menu
            || !Window.isMouseButtonPressed(zug.button) || NanoTextFokus.tippt()
            || manager.focus && context.isFocused() || !manager.keybinds.contains(bind)
            || !bind.getCombo().repeatable || !bind.isActive()
            || !bind.checkMouse(zug.button, owner.area.isInside(context)))
        {
            stoppen(context);
            return;
        }

        long jetzt = System.nanoTime();
        int anzahl = 0;

        while (jetzt >= zug.naechstes && anzahl++ < 8)
        {
            int vorher = context.mouseButton;
            context.mouseButton = zug.button;

            try
            {
                if (!Boolean.TRUE.equals(ActorLeaseAccess.ui(() ->
                {
                    bind.callback.run();
                    return true;
                })))
                {
                    stoppen(context);
                    return;
                }
            }
            finally
            {
                context.mouseButton = vorher;
            }

            zug.naechstes += ABSTAND;

            if (gehalten.get(context) != zug || UIScreen.getCurrentMenu() != context.menu)
            {
                stoppen(context);
                return;
            }
        }

        if (jetzt >= zug.naechstes)
        {
            zug.naechstes = jetzt + ABSTAND;
        }
    }

    private static final class Gehalten
    {
        private final WeakReference<KeybindManager> manager;
        private final WeakReference<Keybind> bind;
        private final int button;
        private WeakReference<UIElement> owner;
        private long naechstes;

        private Gehalten(KeybindManager manager, Keybind bind, int button)
        {
            this.manager = new WeakReference<>(manager);
            this.bind = new WeakReference<>(bind);
            this.button = button;
            this.naechstes = System.nanoTime() + VERZOEGERUNG;
        }
    }
}
