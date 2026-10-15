package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIOverlayCloseEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIOverlayPanel extends UIElement
{
    /** Design-System: Overlay-Kopf ist 30 px hoch, quer ueber die volle Breite. */
    public static final int KOPF = 30;

    public UILabel title;
    public UIElement icons;
    public UIIcon close;
    public UIElement content;

    private boolean moving;
    private int lastX;
    private int lastY;

    private int initialOffsetX;
    private int initialOffsetY;
    /* AUTONOMIE 2026-07-27: groessen-anfasser unten rechts (bzw. links, siehe resizeFromLeft),
     * portiert aus dem CML-fork. fs' overlay kann nur VERSCHIEBEN (feld `moving` oben) —
     * unsere panels (keybind-editor, launchpad, replay-dock, settings-overlay)
     * rufen resizable() und brauchen den zieh-rand. Standardmaessig AUS: bestehende
     * overlays verhalten sich unveraendert. */
    private boolean resizing;
    private boolean resizable;
    private boolean resizeFromLeft;
    private int resizeMargin = 12;
    private int minWidth = 180;
    private int minHeight = 120;
    private int resizeMinWidth = 180;
    private int resizeMinHeight = 120;

    public UIOverlayPanel(IKey title)
    {
        super();

        this.title = UI.label(title);
        this.close = new UIIcon(Icons.CLOSE, (b) -> this.close());
        this.content = new UIElement();
        this.icons = new UIElement();

        /* Vorher stand die Icon-Spalte SENKRECHT am rechten Rand und nahm dem Inhalt 20 px
         * Breite. Der Entwurf setzt sie waagerecht in den Kopf; reverse() haelt sie
         * rechtsbuendig, damit Schliessen immer aussen sitzt, egal wie viele Icons ein
         * Panel dazuhaengt (gemessen: hoechstens fuenf, UILanguageEditorOverlayPanel). */
        this.title.labelAnchor(0, 0.5F).relative(this).xy(11, 0).w(0.5F, -8).h(KOPF);
        this.icons.relative(this).x(0.5F).y(3).w(0.5F, -6).h(24).row(2).reverse().width(24);
        this.content.relative(this).xy(0, KOPF).w(1F).h(1F, -KOPF);

        this.icons.add(this.close);

        this.add(this.title, this.icons, this.content);

        this.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
    }

    public UIOverlayPanel resizable()
    {
        return this.resizable(true);
    }

    public UIOverlayPanel resizable(boolean value)
    {
        this.resizable = value;

        return this;
    }

    /** Kleinstmoegliche groesse beim ziehen. Untergrenze 60px, sonst verschwindet der anfasser selbst. */
    public UIOverlayPanel minSize(int width, int height)
    {
        this.minWidth = Math.max(60, width);
        this.minHeight = Math.max(60, height);

        return this;
    }

    public UIOverlayPanel resizeMargin(int margin)
    {
        this.resizeMargin = Math.max(6, margin);

        return this;
    }

    /** Anfasser nach unten LINKS statt rechts — fuer panels die am rechten bildschirmrand andocken. */
    public UIOverlayPanel resizeFromLeft()
    {
        this.resizeFromLeft = true;

        return this;
    }

    /** Maus im anfasser-dreieck? Nur dann faengt subMouseClicked den klick ab. */
    private boolean isResizeHandleInside(UIContext context)
    {
        boolean side = this.resizeFromLeft
            ? context.mouseX <= this.area.x + this.resizeMargin
            : context.mouseX >= this.area.ex() - this.resizeMargin;

        return this.resizable
            && side
            && context.mouseY >= this.area.ey() - this.resizeMargin;
    }

    public void setInitialOffset(int x, int y)
    {
        this.initialOffsetX = x;
        this.initialOffsetY = y;
    }

    /**
     * Put the window back where it was designed to sit — the escape hatch behind Ctrl+click
     * on the title, and what {@link UIOverlay} falls back to when a stored position would
     * open the panel off-screen.
     */
    public void resetOffsetToInitial()
    {
        this.flex.x.offset = this.initialOffsetX;
        this.flex.y.offset = this.initialOffsetY;
    }

    /** Das Fenster so verschieben, dass die TITELMITTE bei (x, y) liegt — fuer "abgedockt
     *  an der Mausposition weiterschweben". Gegenstueck zu resetOffsetToInitial. */
    public void titelZuPosition(int x, int y)
    {
        this.flex.x.offset += x - this.area.mx();
        this.flex.y.offset += y - (this.area.y + 8);

        if (this.getParent() != null)
        {
            this.getParent().resize();
        }
    }

    public void onClose(Consumer<UIOverlayCloseEvent> callback)
    {
        this.events.register(UIOverlayCloseEvent.class, callback);
    }

    public void close()
    {
        UIElement parent = this.getParent();

        if (parent instanceof UIOverlay)
        {
            ((UIOverlay) parent).closeItself();
        }
    }

    public void confirm()
    {}

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        /* Anfasser hat vorrang vor allem anderen — er liegt ueber dem inhalt. */
        if (this.isResizeHandleInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.flex.w.offset = this.initialWidthOffset;
                this.flex.h.offset = this.initialHeightOffset;

                this.getParent().resize();

                return true;
            }

            this.resizing = true;
            this.resizeMinWidth = Math.min(this.minWidth, this.area.w);
            this.resizeMinHeight = Math.min(this.minHeight, this.area.h);
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        if (this.title.area.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.resetOffsetToInitial();
                this.getParent().resize();

                return true;
            }

            this.moving = true;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        return super.subMouseClicked(context);
    }

    /** Zieht der Nutzer gerade am TITEL (nicht Resize, nicht Inhalt)? Fuer Subklassen,
     *  die an den Zug eine Geste haengen (Undo-History: andocken). */
    protected boolean istAmTitelZiehen()
    {
        return this.moving;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.resizing = false;
        this.moving = super.subMouseReleased(context);

        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (!context.isFocused())
        {
            if (context.isPressed(Keys.CLOSE))
            {
                this.close();

                return true;
            }
            else if (context.isPressed(Keys.CONFIRM))
            {
                this.confirm();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.moving && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;
            int lastX = this.area.x;
            int lastY = this.area.y;

            this.flex.x.offset += dx;
            this.flex.y.offset += dy;

            this.getParent().resize();

            if (lastX == this.area.x) this.flex.x.offset -= dx;
            if (lastY == this.area.y) this.flex.y.offset -= dy;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        if (this.resizing && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;
            int maxW = this.getParent() == null ? Integer.MAX_VALUE : this.getParent().area.w;
            int maxH = this.getParent() == null ? Integer.MAX_VALUE : this.getParent().area.h;

            /* Von links ziehen verschiebt zusaetzlich den ursprung, sonst waechst das panel
             * nach rechts weg statt zum cursor hin. */
            if (this.resizeFromLeft)
            {
                int desiredW = Math.min(maxW, Math.max(this.resizeMinWidth, this.area.w - dx));
                int desiredH = Math.min(maxH, Math.max(this.resizeMinHeight, this.area.h + dy));

                this.flex.x.offset += this.area.w - desiredW;
                this.flex.w.offset += desiredW - this.area.w;
                this.flex.h.offset += desiredH - this.area.h;
            }
            else
            {
                int desiredW = Math.min(maxW, Math.max(this.resizeMinWidth, this.area.w + dx));
                int desiredH = Math.min(maxH, Math.max(this.resizeMinHeight, this.area.h + dy));

                this.flex.w.offset += desiredW - this.area.w;
                this.flex.h.offset += desiredH - this.area.h;
            }

            this.getParent().resize();

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        /* AUFGEHEND ÖFFNEN — aus dem CML-Upstream nachgezogen.
         *
         * USER 2026-07-29: „im alten fork hat bbs cml smoothe animationen für all ihre uis
         * aber das funktioniert in unserem fork nicht".
         *
         * Der Wert dafür lief bei uns längst: UIOverlay zieht openTransition pro Frame auf 1
         * hoch (und bei simplify_animations sofort). Nur GEZEICHNET wurde er nie —
         * getOpenTransition() hatte keinen einzigen Aufrufer, während CML ihn hier benutzt
         * (UIOverlayPanel:292). Deshalb blendete bei uns nur die Hintergrund-Abdunklung ein
         * und das Panel selbst stand sofort in voller Größe da.
         *
         * Skaliert wird um die MITTE des Panels, nicht um seine Ecke — sonst wächst es aus
         * der linken oberen Ecke heraus statt aufzugehen.
         *
         * ⛔ AUF WELCHEN STAPEL — der Port hatte hier den falschen (26.2). Die Verschiebung
         * muss auf {@code batcher.getContext().bbsPose()}, den Flaechenstapel des Extractors:
         * NUR den lesen {@code Batcher2D.quad()}, {@code blit()} und die Textausgabe. BBS'
         * eigener {@code batcher.pose()} ist der RAEUMLICHE Stapel fuer Modellvorschauen;
         * er wird von keinem Zeichenaufruf der Flaeche gelesen. Auf ihm zu schieben
         * uebersetzt sauber und bewirkt NICHTS — die Panels standen deshalb ohne Animation
         * sofort da. 1.21.1 stand hier {@code getContext().getMatrices()}, also derselbe
         * Flaechenstapel; 26.1 macht es in UIContext genauso. */
        float transition = 1.0F;

        if (this.getParent() instanceof UIOverlay overlay)
        {
            transition = overlay.getOpenTransition();
        }

        boolean waechst = transition < 1.0F;

        if (waechst)
        {
            /* Nicht auf 0 skalieren: eine Matrix mit Faktor 0 ist nicht invertierbar, und
             * das erste Bild wäre ohnehin unsichtbar. */
            float skalierung = Math.max(0.01F, transition);
            float cx = this.area.mx();
            float cy = this.area.my();

            org.joml.Matrix3x2fStack pose = context.render.batcher.getContext().bbsPose();

            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(skalierung, skalierung);
            pose.translate(-cx, -cy);
        }

        this.renderBackground(context);

        super.render(context);

        if (this.resizable)
        {
            /* Zwei striche als winkel — hell wenn die maus drueber ist. */
            int resizeColor = this.isResizeHandleInside(context) ? Colors.WHITE : Colors.GRAY;
            int left = this.area.x;
            int right = this.area.ex();
            int bottom = this.area.ey();

            if (this.resizeFromLeft)
            {
                context.batcher.box(left + 1, bottom - 1, left + 9, bottom, resizeColor);
                context.batcher.box(left, bottom - 9, left + 1, bottom - 1, resizeColor);
            }
            else
            {
                context.batcher.box(right - 9, bottom - 1, right - 1, bottom, resizeColor);
                context.batcher.box(right - 1, bottom - 9, right, bottom - 1, resizeColor);
            }
        }

        /* Erst hier zurück, nicht direkt nach super.render: der Anfasser zum Größe-Ziehen
         * gehört zum Panel und muss mitwachsen, sonst zappelt er während der Animation an
         * seiner Endposition. */
        if (waechst)
        {
            /* Gegenstueck zum pushMatrix oben — dieselbe Bedingung, damit der Flaechenstapel
             * ausgeglichen bleibt. Ein liegengebliebener Eintrag reisst spaeter den
             * Weltdurchgang mit "Pose stack not empty" mit. */
            context.render.batcher.getContext().bbsPose().popMatrix();
        }
    }

    protected void renderBackground(UIContext context)
    {
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());
        /* Design-System, Overlay-Rahmen: Koerper --region (baseSurface), Radius 8, 1-px-Schwarzkante.
         * Die Kante ist ein schwarzes Rundeck einen Punkt groesser mit dem Koerper darueber —
         * Batcher2D.outline() ist eckig und liefe ueber die runden Ecken. */
        if (mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.an())
        {
            context.batcher.rundeck(this.area.x - 1, this.area.y - 1, this.area.w + 2, this.area.h + 2, 9F, Colors.A100);
            context.batcher.rundeck(this.area.x, this.area.y, this.area.w, this.area.h, 8F, BBSSettings.baseSurface());
        }
        else
        {
            this.area.render(context.batcher, BBSSettings.baseSurface());
        }

        /* Kopfstreifen quer, oben rund wie die Karte: ein rundeck ueber Kopfhoehe + Radius,
         * dann den Ueberstand mit der Koerperfarbe zudecken — unten bleibt eine gerade Kante. */
        if (mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.an())
        {
            context.batcher.rundeck(this.area.x, this.area.y, this.area.w, KOPF + 8F, 8F, BBSSettings.chromeSurface());
            context.batcher.box(this.area.x, this.area.y + KOPF, this.area.ex(), this.area.y + KOPF + 8, BBSSettings.baseSurface());
        }
        else
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + KOPF, BBSSettings.chromeSurface());
        }

        if (this.close.area.isInside(context))
        {
            this.close.area.render(context.batcher, Colors.RED | Colors.A100);
        }

        if (this.title.area.isInside(context))
        {
            context.batcher.icon(Icons.ALL_DIRECTIONS, Colors.GRAY, this.area.mx(), this.title.area.my(), 0.5F, 0.5F);
        }
    }

    public void onClose()
    {
        this.events.emit(new UIOverlayCloseEvent(this));
    }

    /**
     * Stabiler schluessel dieses overlays (klassenname + titel).
     *
     * MERGE 2026-07-27: aus dem CML-upstream — die uebernommene UIOverlay-fassung nutzt ihn,
     * um die weichen ein-/ausblend-animationen pro panel zu merken (editorSimplifyAnimations).
     */
    public String getKey()
    {
        String className = this.getClass().getSimpleName();

        if (className.isEmpty())
        {
            className = this.getClass().getSuperclass().getSimpleName();
        }

        if (this.title != null && this.title.label != null && this.title.label.get() != null && !this.title.label.get().isEmpty())
        {
            return className + "_" + this.title.label.get();
        }

        return className;
    }

    /* MERGE 2026-07-27: aus dem CML-upstream — die uebernommene UIOverlay-fassung merkt sich
     * die zielgroesse, um das panel weich darauf aufwachsen zu lassen. */
    private int initialWidthOffset;
    private int initialHeightOffset;

    /** See {@link #initialWidthOffset}. */
    public void setInitialSizeOffset(int w, int h)
    {
        this.initialWidthOffset = w;
        this.initialHeightOffset = h;
    }
}
