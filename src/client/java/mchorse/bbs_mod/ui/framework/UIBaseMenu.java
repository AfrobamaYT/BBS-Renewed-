package mchorse.bbs_mod.ui.framework;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.IViewport;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.IViewportStack;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.keys.Zweitbelegungen;
import mchorse.bbs_mod.ui.utils.renderers.InputRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

/**
 * Base class for GUI screens using this framework
 */
public abstract class UIBaseMenu
{
    /** F8 toggle for the transform gizmo / axes. Read through {@link #shouldRenderAxes()}, which also
     *  honours the hold-to-hide key, rather than directly. */
    public static boolean renderAxes = true;

    private static InputRenderer inputRenderer = InputRenderer.INSTANCE;

    /**
     * Whether the transform gizmo / axes should be drawn (and pickable) right now: the F8 toggle
     * {@link #renderAxes} is on AND the hold-to-hide key ({@link Keys#TRANSFORMATIONS_HIDE_GIZMO}) is
     * not being held. Gizmo render, its stencil pass and picking all gate on this, so holding the key
     * hides everything at once. The held state is polled (the keybind system only dispatches presses,
     * not holds).
     */
    public static boolean shouldRenderAxes()
    {
        return renderAxes && !isHideGizmoHeld();
    }

    /** Whether the hold-to-hide gizmo key ({@link Keys#TRANSFORMATIONS_HIDE_GIZMO}) is currently held.
     *  Use this to suppress gizmo stencil/picking at sites that aren't otherwise gated by the F8
     *  {@link #renderAxes} flag, so the hold removes them without altering F8's behaviour. */
    public static boolean isHideGizmoHeld()
    {
        /* Ueber Zweitbelegungen statt direkt ueber isHeld(): Halte-Gesten laufen nicht durch
         * Keybind.check, sie fragen die combo selbst. Die zweite Belegung wuerde hier sonst
         * als einzige Stelle im Baum nichts tun — ein halbes Feature, das erst auffaellt,
         * wenn genau diese Aktion probiert wird. */
        return Zweitbelegungen.gehalten(Keys.TRANSFORMATIONS_HIDE_GIZMO) || mchorse.bbs_mod.ui.film.controller.ZielPipette.aktiv(); /* an armed eyedropper picks bodies, not handles */
    }

    private UIRootElement root;
    public UIElement main;
    public UIElement overlay;
    public UIContext context;
    public Area viewport = new Area();

    public int width;
    public int height;

    public UIBaseMenu()
    {
        this.context = new UIContext(this);

        this.root = new UIRootElement(this.context);
        this.root.markContainer().full(this.viewport);

        this.main = new UIElement();
        this.main.full(this.viewport);
        this.overlay = new UIElement();
        this.overlay.full(this.viewport);
        this.root.add(this.main, this.overlay);

        UIElement popka = new UIElement();

        popka.keys().register(Keys.KEYBINDS, () -> this.context.toggleKeybinds());
        popka.keys().register(Keys.HILFE, () -> mchorse.bbs_mod.ui.hilfe.Guides.taste(this.context));
        popka.keys().register(Keys.TRANSFORMATIONS_TOGGLE_AXES, () -> renderAxes = !renderAxes);
        /* P8a: die Befehls-Palette erntet den lebenden UI-Baum — in jedem Menue verfuegbar. */
        popka.keys().register(Keys.BEFEHLS_PALETTE, () -> mchorse.bbs_mod.ui.utils.befehle.UIBefehlsPalette.oeffnen(this.context));
        /* P8e: Skriptkonsole auf der In-Prozess-JShell (dev-Gate in BridgeExec). */
        popka.keys().register(Keys.SKRIPT_KONSOLE, () -> mchorse.bbs_mod.ui.utils.befehle.UISkriptKonsole.oeffnen(this.context));
        popka.keys().register(Keys.UI_SCALE_INC, () -> changeUIScale(0.25F));
        popka.keys().register(Keys.UI_SCALE_DEC, () -> changeUIScale(-0.25F));
        this.root.add(popka);

        this.context.keybinds.relative(this.viewport).wh(0.5F, 1F);
    }

    /**
     * Step the ui_scale setting from the keyboard shortcuts. When the setting is
     * in the "use Minecraft's scale" mode (0), stepping starts from the actual
     * on-screen scale instead of jumping to an unrelated stored value. The result
     * stays in [0.5, 4], so the shortcuts can't accidentally fall back into the
     * 0 = auto mode; the resize itself happens through the setting's callback.
     */
    private static void changeUIScale(float delta)
    {
        float current = BBSSettings.userIntefaceScale.get();

        if (current <= 0F)
        {
            current = BBSModClient.getGUIScale();
        }

        BBSSettings.userIntefaceScale.set(MathUtils.clamp(current + delta, 0.5F, 4F));
    }

    public UIRootElement getRoot()
    {
        return this.root;
    }

    public boolean canHideHUD()
    {
        return true;
    }

    public boolean canPause()
    {
        return true;
    }

    public boolean canRefresh()
    {
        return true;
    }

    public void onOpen(UIBaseMenu oldMenu)
    {}

    public void onClose(UIBaseMenu nextMenu)
    {}

    public void update()
    {
        this.context.update();
    }

    public void resize(int width, int height)
    {
        this.width = width;
        this.height = height;

        this.viewport.set(0, 0, this.width, this.height);
        this.viewportSet();

        this.context.pushViewport(this.viewport);
        this.root.resize();
        this.context.popViewport();
    }

    protected void viewportSet()
    {}

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        boolean result = false;

        this.context.setMouse(mouseX, mouseY, mouseButton);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);

            IUIElement element = this.root.mouseClicked(this.context);
            mchorse.bbs_mod.ui.utils.keys.MausTastenWiederholung.zuordnen(this.context, element);

            this.context.popViewport();

            result = element != null;

            /* Der Verbraucher steht schon HIER zur Verfuegung: UIElement.mouseClicked reicht
             * das Ergebnis der Kinder UNVERAENDERT nach oben durch, an der Wurzel liegt also
             * genau das Element, das den Klick angenommen hat.
             *
             * ⛔ NICHT in UIElement selbst melden (2026-08-01 versucht und zurueckgenommen):
             * UIElement ist die Basisklasse des GESAMTEN UI-Baums. Ein JDWP-Redefine daran
             * zwingt den Mixin-Agenten, jede Unterklasse neu zu weben — dabei scheiterte
             * sofort ein Redirector in UIReplaysEditorUndoFast ("Critical injection failure").
             * Eine Basisklasse anzufassen kostet die Webung ihrer ganzen Nachkommenschaft. */
            mchorse.bbs_mod.sonden.Sonden.klick.angenommenVon(element);
        }

        /* Ein physischer Klick faengt hier an und hoert hier auf — der einzige Punkt, an dem
         * sich GENAU EINE Zeile schreiben laesst. Auch wenn niemand angenommen hat: gerade
         * dieser Fall ist der Befund (Fehler liegt vor der Behandlung, nicht in ihr). */
        mchorse.bbs_mod.sonden.Sonden.klick.abgeschlossen(mouseX, mouseY, mouseButton);

        return result;
    }

    public boolean mouseScrolled(int x, int y, double h, double v)
    {
        boolean result = false;

        this.context.setMouseWheel(x, y, v, h);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);

            IUIElement element = this.root.mouseScrolled(this.context);

            this.context.popViewport();

            result = element != null;
        }

        return result;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        boolean result = false;

        this.context.setMouse(mouseX, mouseY, mouseButton);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);

            IUIElement element = this.root.mouseReleased(this.context);

            this.context.popViewport();

            result = element != null;
        }

        /* Ein Gizmo-Zug beginnt nur mit der LINKEN Taste (GizmoInteraction weist alle anderen
         * ab), also darf auch nur ihr Loslassen ihn beenden. Ohne die Bedingung beendete das
         * Loslassen der MITTLEREN Taste ein laufendes Hotkey-Edit als stilles acceptChanges —
         * startEdit haengt sich per trackTransform an genau dieses stop(). Gemessen an der
         * MMB-Achsenwahl: Achse gerastet, Taste losgelassen, editing war weg. */
        if (mouseButton == 0)
        {
            Gizmo.INSTANCE.stop();
        }

        return result;
    }

    public boolean handleKey(int key, int scanCode, int action, int mods)
    {
        if (action == InputConstants.PRESS)
        {
            inputRenderer.keyPressed(this.context, key);
        }

        this.context.setKeyEvent(key, scanCode, action);

        IUIElement element;

        mchorse.bbs_mod.ui.utils.keys.KeyCombo.ereignisBeginnen(key, scanCode, mods);

        try
        {
            element = this.root.keyPressed(this.context);
        }
        finally
        {
            mchorse.bbs_mod.ui.utils.keys.KeyCombo.ereignisBeenden();
        }

        /* bbs.mess: WER hat die taste angenommen — "angenommen=true ohne wirkung" ist sonst
         * nicht zuzuordnen (Akte 06, G ohne Edit). */
        if (element != null && mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("key.annahme", "key=" + key + " von=" + element.getClass().getName());
        }

        if (this.root.isEnabled() && element != null)
        {
            return true;
        }

        if (this.context.isPressed(InputConstants.KEY_ESCAPE))
        {
            this.closeMenu();

            return true;
        }

        return false;
    }

    public void handleTextInput(int key)
    {
        this.context.setKeyTyped((char) key);

        if (this.root.isEnabled())
        {
            this.root.textInput(this.context);
        }
    }

    /**
     * This method is called when this screen is about to get closed
     */
    protected void closeMenu()
    {
        mchorse.bbs_mod.ui.utils.keys.MausTastenWiederholung.stoppen(this.context);
        Minecraft.getInstance().gui.setScreen(null);
    }

    public void closeThisMenu()
    {
        this.closeMenu();
    }

    public void renderDefaultBackground()
    {
        this.context.batcher.box(0, 0, this.width, this.height, Colors.A50);
    }

    public void renderMenu(UIRenderingContext context, int mouseX, int mouseY)
    {
        /* 26.2: depthFunc(GL_ALWAYS) entfaellt ersatzlos. Die Tiefenfunktion ist kein globaler
         * Schalter mehr, sondern Teil der Pipeline des jeweiligen Zeichenwegs. Die Oberflaeche
         * laeuft inzwischen ueber Vanillas GuiRenderer: Batcher2D reicht GuiElementRenderState
         * ein und waehlt dort RenderPipelines.GUI bzw. GUI_TEXTURED, die ihren Tiefenzustand
         * mitbringen — genau wie bei jedem Vanilla-Bildschirm, der auch ohne ALWAYS ueber der
         * Welt landet. */

        this.context.resetMatrix();
        this.context.setMouse(mouseX, mouseY);
        this.context.resetCursor();

        this.preRenderMenu(context);

        if (this.root.isVisible())
        {
            this.context.reset();
            this.context.pushViewport(this.viewport);

            /* ⚗️ WACHE UM DEN GESAMTEN OBERFLAECHEN-DURCHLAUF.
             *
             * Vanilla bricht mit "Pose stack not empty" ab, aber erst beim naechsten
             * Weltdurchgang und ohne eine einzige BBS-Zeile im Aufrufstapel. Eine Wache um
             * die einzelnen Miniaturen (FormRenderer.renderUI) schlug bei 36 Panelwechseln
             * NIE an -- die Form-Ebene ist also sauber, der Verursacher liegt darueber.
             * Diese Wache faengt ihn auf der groben Ebene; ist sie rot, wird sie schrittweise
             * nach unten verschoben, bis der Name feststeht.
             *
             * Der Name traegt das Panel mit, sonst sagt die Meldung nur DASS, nicht WO. */
            /* Under the panels, over the world: the transform constraint lines when the world
             * fills the window (Blocks, Models). The film viewport draws its own (custom size). */
            if (!mchorse.bbs_mod.client.BBSRendering.isCustomSize())
            {
                mchorse.bbs_mod.ui.utils.GizmoAchsenLinien.zeichnen2D(this.context, this.viewport.x, this.viewport.y, this.viewport.w, this.viewport.h);
            }

            mchorse.bbs_mod.graphics.Stapelwache.pruefen(
                "menue:" + this.getClass().getSimpleName(), this.context.batcher.pose(),
                () -> this.root.render(this.context));

            /* ⭐ Die gesammelten Formvorschauen abgeben -- EIN bildschirmgrosser
             * Bild-im-Bild-Zug fuer alle. Muss NACH root.render stehen: vorher ist die
             * Schlange leer, weil die Elemente erst dort einreihen. Siehe Miniaturschlange. */
            mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.abgeben(
                this.context, this.viewport.w, this.viewport.h);

            this.context.popViewport();
            this.context.postRender();

            /* ⛔ ZWEITES ABGEBEN, WEIL postRender NOCH EINREIHT (2026-08-05, Bugliste #15).
             *
             * postRender zeichnet, was ueber allem liegt -- unter anderem die Vorschau am
             * Keyframe (UIKeyframeDopeSheet#postRender). Deren FormRenderer#renderUI reiht in
             * dieselbe Schlange ein, aber ERST NACH dem Abgeben oben. Ohne diese Zeile bleibt
             * die Miniatur bis zum naechsten Bild liegen und wird dort an einer Stelle
             * eingefuegt, die VOR dem Panelhintergrund liegt -- sie ist also gezeichnet und
             * sofort wieder uebermalt.
             *
             * Gemessen: beim Hovern hatte der Vorschaukasten GENAU EINE Farbe ueber 6400
             * Proben, obwohl miniatur.ModelFormRenderer lief (294172 -> 294302) und seine
             * Flaeche korrekt mit 80x80 @359,303 meldete. Und beim WEGhovern blitzte das
             * Modell fuer ein Bild auf -- das war die Miniatur des vorigen Bildes, die keinen
             * Panelhintergrund mehr ueber sich hatte. Genau so hat es der Nutzer gemeldet.
             *
             * abgeben() ist bei leerer Schlange ein no-op, der zweite Aufruf kostet also
             * nichts, wenn in postRender keine Vorschau lag. */
            mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.abgeben(
                this.context, this.viewport.w, this.viewport.h);
        }

        if (this.main.isVisible())
        {
            inputRenderer.render(this, mouseX, mouseY);
        }

        /* Last of all, after tooltips and previews: nothing may lie on the guide's video */
        mchorse.bbs_mod.ui.hilfe.Guides.obenauf(this.context);

        this.context.applyCursor();

        /* Das Zuruecksetzen auf GL_LEQUAL entfaellt mit dem ALWAYS oben: es gibt keinen
         * globalen Tiefenzustand mehr, den ein spaeterer Zeichenweg erben koennte. Fuer
         * spaetere Leser — eine mechanische Uebersetzung waere hier ohnehin falsch gewesen:
         * 26.2 rechnet die Tiefe umgekehrt (reversed-Z), GL_LEQUAL entspraeche
         * CompareOp.GREATER_THAN_OR_EQUAL, nicht LESS_THAN_OR_EQUAL. */
    }

    protected void preRenderMenu(UIRenderingContext context)
    {}

    public void startRenderFrame(float tickDelta)
    {}

    public void renderInWorld(LevelRenderContext context)
    {}

    public static class UIRootElement extends UIElement implements IViewport
    {
        private UIContext context;

        public UIRootElement(UIContext context)
        {
            super();

            this.context = context;

            this.markContainer();
        }

        public UIContext getContext()
        {
            return this.context;
        }

        @Override
        public void apply(IViewportStack stack)
        {
            stack.pushViewport(this.area);
        }

        @Override
        public void unapply(IViewportStack stack)
        {
            stack.popViewport();
        }
    }
}
