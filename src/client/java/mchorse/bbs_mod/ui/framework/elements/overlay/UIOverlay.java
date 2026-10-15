package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector2i;


import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class UIOverlay extends UIElement
{
    private static final Map<String, Vector2i> offsets = new HashMap<>();
    private static final Map<String, Vector2i> sizes = new HashMap<>();
    private static boolean loaded = false;

    private int background = Colors.A50;

    /** Wieviel vom fenster in jeder achse auf dem schirm liegen muss, damit es greifbar ist. */
    private static final int MIN_SICHTBAR = 32;
    /** Hoehe der titelleiste — nur an ihr laesst sich ein fenster ziehen. */
    private static final int TITEL_HOEHE = 20;

    /** Gesetzt wenn die ausblend-animation durch ist; der NAECHSTE render entfernt dann. */
    /** Laeuft gerade eine korrektur? Sonst riefe das resize darin sich selbst. */
    private boolean klemmeLaeuft;

    private boolean abschlussFaellig;
    private float openTransition = 0.0F;
    private boolean closing = false;

    public float getOpenTransition()
    {
        return this.openTransition;
    }

    private static void ensureLoaded()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        try
        {
            File file = BBSMod.getSettingsPath("overlay_sizes.json");

            if (!file.exists())
            {
                return;
            }

            BaseType type = DataToString.read(file);

            if (type instanceof MapType)
            {
                MapType data = (MapType) type;
                MapType sizesMap = data.getMap("sizes");
                MapType offsetsMap = data.getMap("offsets");

                if (sizesMap != null)
                {
                    for (String key : sizesMap.keys())
                    {
                        MapType vec = sizesMap.getMap(key);

                        if (vec != null)
                        {
                            sizes.put(key, new Vector2i(vec.getInt("x"), vec.getInt("y")));
                        }
                    }
                }

                if (offsetsMap != null)
                {
                    for (String key : offsetsMap.keys())
                    {
                        MapType vec = offsetsMap.getMap(key);

                        if (vec != null)
                        {
                            offsets.put(key, new Vector2i(vec.getInt("x"), vec.getInt("y")));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void saveOverlayState(UIOverlayPanel panel)
    {
        if (panel == null)
        {
            return;
        }

        ensureLoaded();

        Vector2i offset = new Vector2i(panel.getFlex().x.offset, panel.getFlex().y.offset);
        Vector2i size = new Vector2i(panel.getFlex().w.offset, panel.getFlex().h.offset);
        String key = panel.getKey();

        offsets.put(key, offset);
        sizes.put(key, size);

        saveToFile();
    }

    private static void saveToFile()
    {
        try
        {
            MapType data = new MapType();
            MapType sizesMap = new MapType();
            MapType offsetsMap = new MapType();

            for (Map.Entry<String, Vector2i> entry : sizes.entrySet())
            {
                MapType vec = new MapType();

                vec.putInt("x", entry.getValue().x);
                vec.putInt("y", entry.getValue().y);
                sizesMap.put(entry.getKey(), vec);
            }

            for (Map.Entry<String, Vector2i> entry : offsets.entrySet())
            {
                MapType vec = new MapType();

                vec.putInt("x", entry.getValue().x);
                vec.putInt("y", entry.getValue().y);
                offsetsMap.put(entry.getKey(), vec);
            }

            data.put("sizes", sizesMap);
            data.put("offsets", offsetsMap);

            DataToString.writeSilently(BBSMod.getSettingsPath("overlay_sizes.json"), data, true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(0.5F, 0.5F).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, float w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, int h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).w(w).h(h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayLeft(context, panel, w, 10);
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(padding).y(padding).w(w).h(1F, -padding * 2).anchor(0F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayRight(context, panel, w, 10);
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(1F, -padding).y(padding).w(w).h(1F, -padding * 2).anchor(1F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static void setupPanel(UIContext context, UIOverlay overlay, UIOverlayPanel panel)
    {
        /* "Hat einen parent" ist NICHT dasselbe wie "ist zu sehen". Panels wie das
         * settings-fenster sind langlebige singletons: wird ihre overlay-huelle aus dem
         * baum genommen, ohne das panel abzuhaengen, behaelt es einen parent, der nirgends
         * mehr haengt. Der alte check stieg dann fuer immer aus — der knopf reagierte
         * nicht mehr, ohne fehler, ohne log. GENAU DAS ist dem nutzer am 2026-07-28
         * passiert (settings-knopf tot; gemessen: parent=UIOverlay, aber parent.hasParent()
         * war false, panel-flaeche -215,-190).
         *
         * Also: nur abbrechen, wenn das panel WIRKLICH erreichbar ist. Ein panel an einem
         * toten parent wird abgehaengt und normal neu eingehaengt. */
        if (panel.hasParent())
        {
            if (istErreichbar(context, panel))
            {
                return;
            }

            panel.removeFromParent();
        }

        ensureLoaded();

        Flex flex = panel.getFlex();
        String key = panel.getKey();
        Vector2i offset = offsets.get(key);
        Vector2i size = sizes.get(key);

        panel.setInitialOffset(flex.x.offset, flex.y.offset);
        panel.setInitialSizeOffset(flex.w.offset, flex.h.offset);

        if (offset != null)
        {
            flex.x.offset = offset.x;
            flex.y.offset = offset.y;
        }

        if (size != null)
        {
            /* A remembered size never makes a fixed-size panel smaller than this version asks
             * for: the voice-over panel was saved at 228 px and later grew to 268, so its
             * Record button hung below the window (2026-10-12). Relative sizes keep the old way. */
            flex.w.offset = flex.w.value == 0F ? Math.max(size.x, flex.w.offset) : size.x;
            flex.h.offset = flex.h.value == 0F ? Math.max(size.y, flex.h.offset) : size.y;
        }

        overlay.full(context.menu.overlay);
        context.menu.overlay.add(overlay);
        overlay.add(panel);
        context.menu.overlay.resize();
    }

    /**
     * Ein gespeicherter platz darf ein fenster nie unerreichbar machen.
     *
     * <p>Positionen werden pro key persistiert ({@link #saveOverlayState}). Zieht der nutzer
     * ein fenster fast aus dem bild, verkleinert danach das MC-fenster, oder rechnet das
     * layout einmal gegen ein eltern-element der groesse 0 (gemessen 2026-07-28: das
     * settings-fenster stand auf -215/-190, exakt minus der halben panel-groesse), dann
     * oeffnet es beim naechsten mal wieder dort — und es gibt keinen sichtbaren weg zurueck.
     * Der reset liegt auf Ctrl+klick AUF DIE TITELLEISTE, und genau die ist dann nicht mehr da.
     *
     * <p>⛔ Die pruefung haengt am RESIZE, nicht am einhaengen. Beim einhaengen steht die
     * flaeche des panels noch auf null — gemessen beim bau dieses fixes: die pruefung sah
     * 0,0 430x380, fand das in ordnung und liess einen offset von -5000 durch. Am resize
     * greift sie ausserdem dort, wo der zweite fall entsteht: wenn das MC-fenster kleiner
     * wird, rechnen overlays ihre position sonst nie neu.
     */
    @Override
    public void resize()
    {
        super.resize();

        if (this.klemmeLaeuft || this.area.w <= 0 || this.area.h <= 0)
        {
            return;
        }

        for (IUIElement kind : this.getChildren())
        {
            if (!(kind instanceof UIOverlayPanel panel) || this.liegtImBild(panel))
            {
                continue;
            }

            /* Der reset loest ein zweites resize aus — ohne die sperre riefe das sich selbst. */
            this.klemmeLaeuft = true;

            try
            {
                panel.resetOffsetToInitial();
                super.resize();
                saveOverlayState(panel);
            }
            finally
            {
                this.klemmeLaeuft = false;
            }
        }
    }

    /** Ueberlappt das fenster den schirm genug, dass man es sehen und an der titelleiste fassen kann? */
    private boolean liegtImBild(UIOverlayPanel panel)
    {
        Area fenster = panel.area;

        if (fenster.w <= 0 || fenster.h <= 0)
        {
            /* Noch kein layout — hier waere jedes urteil geraten. */
            return true;
        }

        int sichtbarX = Math.min(fenster.ex(), this.area.ex()) - Math.max(fenster.x, this.area.x);
        int sichtbarY = Math.min(fenster.ey(), this.area.ey()) - Math.max(fenster.y, this.area.y);
        boolean titelGreifbar = fenster.y >= this.area.y && fenster.y + TITEL_HOEHE <= this.area.ey();

        return sichtbarX >= MIN_SICHTBAR && sichtbarY >= MIN_SICHTBAR && titelGreifbar;
    }

    /**
     * Haengt {@code element} ueber seine parent-kette WIRKLICH an der wurzel des offenen
     * menues — ist es also auf dem schirm erreichbar?
     *
     * <p>Ein einzelnes {@code hasParent()} beantwortet das nicht: es sagt nur, dass
     * IRGENDEIN element als parent eingetragen ist, auch wenn dieses selbst schon aus dem
     * baum genommen wurde. Die tiefe ist klein (panel &rarr; overlay &rarr; overlay-schicht
     * &rarr; wurzel), der zaehler ist nur ein bruch-schutz gegen einen zyklus.</p>
     */
    private static boolean istErreichbar(UIContext context, UIElement element)
    {
        if (context == null || context.menu == null)
        {
            return true;
        }

        UIElement wurzel = context.menu.getRoot();
        UIElement aktuell = element;

        for (int schutz = 0; aktuell != null && schutz < 64; schutz++)
        {
            if (aktuell == wurzel)
            {
                return true;
            }

            aktuell = aktuell.getParent();
        }

        return false;
    }

    public static boolean has(UIContext context)
    {
        return !context.menu.getRoot().getChildren(UIOverlayPanel.class).isEmpty();
    }

    public UIOverlay()
    {
        this.mouseEventPropagataion(EventPropagation.BLOCK).keyboardEventPropagataion(EventPropagation.PASS).markContainer();
    }

    public UIOverlay background(int background)
    {
        this.background = background;

        return this;
    }

    public UIOverlay noBackground()
    {
        return this.background(0);
    }

    public void closeItself()
    {
        if (this.closing)
        {
            return;
        }

        UIUtils.playClick();

        if (BBSSettings.editorSimplifyAnimations.get())
        {
            this.openTransition = 0.0F;
            this.closing = true;
            this.performClose();
        }
        else
        {
            this.closing = true;
        }
    }

    private void performClose()
    {
        for (UIOverlayPanel element : this.getChildren(UIOverlayPanel.class))
        {
            saveOverlayState(element);

            element.removeFromParent();
            element.onClose();
        }

        this.removeFromParent();
    }

    /* Don't pass user input down the line... */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.closeItself();

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        float target = this.closing ? 0.0F : 1.0F;

        if (BBSSettings.editorSimplifyAnimations.get())
        {
            this.openTransition = target;
        }
        else
        {
            if (this.openTransition < 0.001F && !this.closing)
            {
                /* Fast start on first frame */
                this.openTransition = 0.01F;
            }
            this.openTransition += (target - this.openTransition) * 0.25F;
        }

        /* ⛔ AUTONOMIE 2026-07-27: schliessen NICHT mehr aus render() heraus.
         *
         * CRASH-URSACHE, gemessen an vier crash-reports dieser session:
         *
         *     java.util.ConcurrentModificationException
         *       at java.util.ArrayList$Itr.checkForComodification
         *       at mchorse.bbs_mod.ui.framework.elements.UIElement.render(UIElement:1429)
         *
         * performClose() ruft removeFromParent() — und wurde hier gerufen, WAEHREND der
         * eltern-UIElement.render() die kinder-liste mit einem iterator durchlaeuft. Beim
         * naechsten next() knallt es. Ausgeloest hat es jedes overlay, dessen ausblend-
         * animation waehrend eines frames endete: dashboard auf, keybind-editor zu, crash.
         *
         * fs' eigene fassung (bbs-fs-pur, 173 zeilen) hat das problem nicht — sie entfernt
         * direkt im klick-handler und kennt weder performClose noch openTransition. Die
         * verzoegerte variante kam mit CMLs animierter UI (241 zeilen), die du behalten
         * wolltest.
         *
         * ⛔ ERSTER FIX-VERSUCH WAR FALSCH (2026-07-27, vom user gefunden): er reichte
         * performClose an mc.execute() weiter. Dessen warteschlange wird in dieser
         * dev-instanz NICHT abgearbeitet (gemessen: ein per mc.execute gesetzter marker
         * bleibt aus, waehrend das game mit 118 FPS rendert). Das overlay wurde also NIE
         * entfernt — es blieb sichtbar offen, und der naechste schliess-versuch crashte
         * erneut an derselben stelle. Ein fix, der auf einer nicht laufenden queue
         * aufsetzt, ist kein fix.
         *
         * Fix jetzt ohne fremde queue: das overlay merkt sich den wunsch und entfernt
         * sich beim NAECHSTEN render-aufruf — GANZ AM ANFANG, bevor super.render() die
         * kinder-liste anfasst. Zu diesem zeitpunkt ist die iteration des vorigen frames
         * beendet, die des aktuellen noch nicht gestartet. Kostet einen frame, laeuft
         * aber garantiert. */
        if (this.abschlussFaellig)
        {
            this.abschlussFaellig = false;
            this.performClose();

            return;
        }

        if (this.closing && this.openTransition <= 0.01F)
        {
            this.abschlussFaellig = true;

            return;
        }

        /* Ein Overlay VERDECKT, was darunter liegt: wartende Miniaturen (Filmkacheln, Formlisten)
         * vorher abgeben, sonst gehen sie am Ende des Durchlaufs raus und heben sich per
         * nextStratum() UEBER das Overlay -- Filmkachel-Modelle standen 2026-10-14 mitten im
         * Einstellungsfenster (2069 Modell-Pixel darin). Dieselbe Grenze wie UIFormPalette und
         * MiniaturGrenze; eine leere Schlange kostet nichts. */
        mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.abgeben(
            context, context.menu.width, context.menu.height);

        if (Colors.getA(this.background) > 0F)
        {
            int alpha = (int) (Colors.getA(this.background) * this.openTransition * 255);
            int finalBgColor = Colors.setA(this.background, alpha / 255.0F);
            this.area.render(context.batcher, finalBgColor);
        }

        super.render(context);
    }
}
