package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ein BESTEHENDES Replay des Films schnell auswaehlen — in der Welt wie im Editor.
 *
 * <h2>Nicht zu verwechseln mit {@link mchorse.bbs_mod.ui.film.replays.UIQuickReplayMenu}</h2>
 *
 * <p>Die beiden heissen aehnlich und tun Gegensaetzliches:</p>
 *
 * <table>
 *   <tr><th>Alt+F · UIQuickReplayMenu</th><td>ERZEUGT ein Replay zu einer Form (ueberspringt
 *       den Model-Form-Picker: Name tippen, Enter, fertig).</td></tr>
 *   <tr><th>] bzw. ^ · diese Klasse</th><td>WAEHLT unter den Replays aus, die der Film schon
 *       hat.</td></tr>
 * </table>
 *
 * <p>Der Nutzer hat am 2026-07-30 auf genau diesen Unterschied hingewiesen: <em>"das hatte die
 * funktion das ich schnell in der echten welt und im dashboard ein replay auswaehlen konnte"</em>.
 * Ich hatte beides zusammengeworfen und daraufhin "ist zu 70% schon da" gemeldet — war es nicht.</p>
 *
 * <h2>Herkunft: WIEDERHERGESTELLT, nicht neu erfunden</h2>
 *
 * <p>Diese Klasse stammt aus BBS-CML (siehe {@code cml-fresh}) und ist beim Fork-Wechsel
 * verlorengegangen: bbs-fs hatte sie schon nicht mehr, und bbs-neu2 erbte die Luecke. Beweis,
 * dass hier etwas fehlte und nicht etwa nie existierte — die Uebersetzungen sind im Baum
 * geblieben, der Code nicht:</p>
 *
 * <pre>assets/bbs/lang/en_us.json:  "key.bbs.open_quick_replays": "Open quick replays"</pre>
 *
 * <p>Ein Sprach-Schluessel ohne Keybind. Das ist der Befund aus Aufgabe #70 (62 Klassen aus dem
 * alten Fork fehlen im neuen) in einem einzelnen Fall, und der Grund, warum hier
 * zurueckgeholt statt nachgebaut wurde: nachbauen haette eine schlechtere Fassung von etwas
 * erzeugt, das fertig danebenlag.</p>
 *
 * <h2>Was die Box koennen muss, damit sie SCHNELL ist</h2>
 *
 * <ul>
 *   <li>Sie zieht einen NICHT ins Dashboard: {@link #open} haengt sie in ein blankes
 *       {@link UIBaseMenu} mit {@code canHideHUD() == false} und {@code canPause() == false} —
 *       HUD bleibt, das Spiel laeuft weiter.</li>
 *   <li>Der Cursor springt beim Oeffnen in die Mitte der Box ({@link #centerCursor}), damit das
 *       Mausrad sofort die Auswahl bewegt statt ins Leere zu scrollen.</li>
 *   <li>Das Rad bewegt die AUSWAHL, nicht die Bildlaufleiste ({@link #handleWheelSelection}).</li>
 *   <li>Dieselbe Taste nochmal bestaetigt und schliesst ({@link #subKeyPressed}) — halten,
 *       raddrehen, loslassen, ohne die Hand von der Maus zu nehmen.</li>
 * </ul>
 *
 * <p>Der Editor-Weg haengt an {@code BBSToolsKeys.QUICK_REPLAY_PICKER} (^) und benutzt
 * {@link UIOverlay#addOverlay(UIContext, UIOverlayPanel)} direkt, weil dort schon ein
 * UI-Kontext offen ist; den Welt-Weg macht {@link QuickReplayWelt}.</p>
 */
public class UIQuickReplayOverlayPanel extends UIOverlayPanel
{
    public final UIList<Replay> list;
    private final Consumer<Replay> callback;
    private boolean centerCursor = true;

    /**
     * In der WELT oeffnen: eigenes, minimales Menue statt des Dashboards.
     *
     * <p>{@code canPause()/canHideHUD() == false} sind hier das ganze Feature — mit den
     * Standardwerten haette man beim Auswaehlen ein pausiertes Spiel ohne HUD, also genau das
     * Herausgerissen-Werden, das der Nutzer an der Rechte-Shift-Taste stoert.</p>
     */
    public static void open(UIQuickReplayOverlayPanel panel)
    {
        panel.onClose((event) -> Minecraft.getInstance().gui.setScreen(null));

        UIScreen.open(new UIBaseMenu()
        {
            @Override
            public boolean canHideHUD()
            {
                return false;
            }

            @Override
            public boolean canPause()
            {
                return false;
            }

            @Override
            public void onOpen(UIBaseMenu oldMenu)
            {
                super.onOpen(oldMenu);

                UIOverlay.addOverlay(this.context, panel, 240, 0.35F).noBackground();
            }
        });
    }

    /**
     * Im EDITOR oeffnen (Taste {@code BBSToolsKeys.QUICK_REPLAY_PICKER}, ^).
     *
     * <p>Hier steht schon ein UI-Kontext, also reicht ein Overlay hinein — das blanke
     * {@link UIBaseMenu} aus {@link #open} waere hier falsch, es wuerde den Editor ersetzen.
     * Der Versatz von 40/40 setzt die Box neben die obere linke Ecke statt genau darauf, wo
     * sie die Werkzeugleiste verdecken wuerde.</p>
     *
     * <p>Ohne Replays passiert nichts: eine leere Box waere nur ein Sichtblocker.</p>
     */
    public static void imEditor(UIFilmPanel panel, UIContext context)
    {
        if (panel == null || panel.getData() == null || context == null)
        {
            return;
        }

        /* ⛔ SONST STAPELN SICH DIE BOXEN. Der Welt-Weg hat diesen Test in der Tick-Schleife,
         * der Editor-Weg hatte ihn nicht: einmal auf der Taste bleiben und man hat fuenf
         * Waehler uebereinander, von denen jeder einzeln weggeklickt werden will (der Nutzer
         * am 2026-07-30: "ich kann ^ spammen und mehrere oeffnen"). Zweiter Druck schliesst,
         * dieselbe Taste also auf und zu. */
        if (isOpened())
        {
            closeOpened();

            return;
        }

        List<Replay> replays = panel.getData().replays.getList();

        if (replays.isEmpty())
        {
            return;
        }

        UIQuickReplayOverlayPanel picker = new UIQuickReplayOverlayPanel(
            replays,
            panel.replayEditor.getReplay(),
            panel.replayEditor::setReplay
        );

        picker.setInitialOffset(40, 40);

        UIOverlay.addOverlay(context, picker);
    }

    public static UIQuickReplayOverlayPanel getOpened()
    {
        Screen currentScreen = Minecraft.getInstance().gui.screen();

        if (!(currentScreen instanceof UIScreen uiScreen))
        {
            return null;
        }

        List<UIQuickReplayOverlayPanel> panels = uiScreen.getMenu().getRoot().getChildren(UIQuickReplayOverlayPanel.class);

        return panels.isEmpty() ? null : panels.get(0);
    }

    public static boolean isOpened()
    {
        return getOpened() != null;
    }

    public static void closeOpened()
    {
        UIQuickReplayOverlayPanel opened = getOpened();

        if (opened != null)
        {
            opened.close();
        }
    }

    public static void confirmAndCloseOpened()
    {
        UIQuickReplayOverlayPanel opened = getOpened();

        if (opened != null)
        {
            opened.confirmSelection();
        }
    }

    private void confirmSelection()
    {
        Replay replay = this.list.getCurrentFirst();

        if (replay != null && this.callback != null)
        {
            this.callback.accept(replay);
        }

        this.close();
    }

    /**
     * Cursor in die Mitte der Box setzen, EINMAL nach dem ersten Layout.
     *
     * <p>Das Rad wirkt nur, wenn der Zeiger ueber der Liste steht ({@code area.isInside}).
     * Beim Oeffnen aus der Welt steht er aber dort, wo man gerade hingeschaut hat — meist
     * ausserhalb. Ohne diesen Sprung muesste man erst zielen, und die Box waere nicht mehr
     * schnell.</p>
     *
     * <p>⚠ Zwei Koordinatenraeume: {@code area} ist GUI-Raum, {@link Window#moveCursor} will
     * Fenster-Pixel. Der Faktor ist die GUI-Skalierung — deshalb die Division ueber die
     * Fenstermasse und nicht einfach {@code area.mx()}.</p>
     */
    private void centerCursor(UIContext context)
    {
        if (context.menu.width <= 0 || context.menu.height <= 0 || this.area.w <= 0 || this.area.h <= 0)
        {
            return;
        }

        com.mojang.blaze3d.platform.Window mcWindow = Minecraft.getInstance().getWindow();
        double fx = mcWindow.getScreenWidth() / (double) context.menu.width;
        double fy = mcWindow.getScreenHeight() / (double) context.menu.height;
        int x = (int) Math.round(this.area.mx() * fx);
        int y = (int) Math.round(this.area.my() * fy);

        Window.moveCursor(x, y);
        context.mouseX = this.area.mx();
        context.mouseY = this.area.my();
        this.centerCursor = false;
    }

    /** Rad bewegt die Auswahl um eine Zeile, statt die Liste zu scrollen. */
    private boolean handleWheelSelection(UIContext context)
    {
        if (this.list.getList().isEmpty() || !this.list.area.isInside(context) || context.mouseWheel == 0)
        {
            return false;
        }

        int step = context.mouseWheel > 0 ? -1 : 1;
        int index = this.list.getIndex();

        if (index == -1)
        {
            index = step > 0 ? 0 : this.list.getList().size() - 1;
        }
        else
        {
            index = Math.max(0, Math.min(this.list.getList().size() - 1, index + step));
        }

        this.list.setIndex(index);
        this.list.scroll.setScroll(index * this.list.scroll.scrollItemSize);

        return true;
    }

    public UIQuickReplayOverlayPanel(List<Replay> replays, Replay selectedReplay, Consumer<Replay> callback)
    {
        super(UIKeys.FILM_REPLAY_TITLE);
        this.callback = callback;

        this.list = new UIList<>((current) ->
        {
            Replay replay = current.isEmpty() ? null : current.get(0);

            if (replay != null && this.callback != null)
            {
                this.callback.accept(replay);
            }

            this.close();
        })
        {
            @Override
            protected String elementToString(UIContext context, int i, Replay element)
            {
                return element.getName();
            }

            @Override
            public boolean subMouseScrolled(UIContext context)
            {
                if (UIQuickReplayOverlayPanel.this.handleWheelSelection(context))
                {
                    return true;
                }

                return super.subMouseScrolled(context);
            }

            @Override
            protected void renderElementPart(UIContext context, Replay element, int i, int x, int y, boolean hover, boolean selected)
            {
                int iconX = x + 2;
                int iconY = y + 2;
                int iconW = 20;
                int iconH = 20;
                int textY = y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2;

                context.batcher.box(iconX, iconY, iconX + iconW, iconY + iconH, hover ? Colors.A75 : Colors.A50);

                Form form = element.form.get();

                if (form != null)
                {
                    context.batcher.clip(iconX, iconY, iconW, iconH, context);
                    FormUtilsClient.renderUI(form, context, iconX, iconY, iconX + iconW, iconY + iconH);
                    context.batcher.unclip(context);
                }

                context.batcher.textShadow(this.elementToString(context, i, element), x + 26, textY, hover ? Colors.HIGHLIGHT : Colors.WHITE);
            }
        };

        this.list.scroll.scrollItemSize = 24;
        this.list.scroll.scrollSpeed = 48;
        this.list.add(replays);
        this.list.update();

        if (selectedReplay != null)
        {
            this.list.setCurrentScroll(selectedReplay);
        }

        int visibleRows = Math.min(5, Math.max(1, replays.size()));
        int panelHeight = 32 + visibleRows * this.list.scroll.scrollItemSize;

        this.minW(240).maxW(240);
        this.minH(panelHeight).maxH(panelHeight);
        this.list.relative(this.content).x(6).y(6).w(1F, -12).h(1F, -12);
        this.content.add(this.list);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.centerCursor)
        {
            this.centerCursor(context);
        }

        super.render(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(Keys.CLOSE))
        {
            return super.subKeyPressed(context);
        }

        KeyMapping keybind = BBSModClient.getKeyOpenQuickReplays();

        /* ⚠️ 26.2: KeyMapping.matches(keyCode, scanCode) ist weg, der Ersatz nimmt ein
         * KeyEvent-Record (key, scancode, modifiers). Der Bytecode von matches(KeyEvent) ist
         * Zeile fuer Zeile die alte Methode: bei key == -1 wird gegen den SCANCODE verglichen,
         * sonst gegen den KEYSYM — die modifiers werden NICHT angefasst. Deshalb ist die 0
         * hier kein weggelassener Wert, sondern schlicht unbenutzt; das Verhalten bleibt
         * identisch. (matches(InputConstants.Key) waere der andere Weg, aber die frueher dafuer
         * passende Fabrik InputConstants.getKey(int,int) existiert in 26.2 nicht mehr.) */
        if (keybind != null && context.getKeyAction() == KeyAction.PRESSED
            && keybind.matches(new KeyEvent(context.getKeyCode(), context.getScanCode(), 0)))
        {
            this.confirmSelection();

            return true;
        }

        return super.subKeyPressed(context);
    }
}
