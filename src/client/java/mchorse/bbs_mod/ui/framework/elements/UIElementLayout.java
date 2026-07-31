package mchorse.bbs_mod.ui.framework.elements;

import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;
import mchorse.bbs_mod.ui.utils.resizers.Margin;
import mchorse.bbs_mod.ui.utils.resizers.constraint.BoundsResizer;
import mchorse.bbs_mod.ui.utils.resizers.layout.ColumnResizer;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.ui.utils.resizers.layout.RowResizer;

/**
 * Die Layout-Haelfte eines Oberflaechenelements: wo es sitzt, wie gross es ist, wie es sich
 * an Nachbarn und Eltern ausrichtet.
 *
 * <h2>Warum das eine eigene Datei ist</h2>
 *
 * <p>Aus {@link UIElement} herausgeloest, weil die Klasse ueber 1500 Zeilen gewachsen war und
 * damit die Grenze riss, ab der man eine Datei nicht mehr aufschlaegt wie ein Lexikon, sondern
 * durchsucht wie einen Heuhaufen. Der Schnitt verlief entlang einer echten Naht, nicht
 * irgendwo: dieser Block spricht ausschliesslich {@link #flex}, {@link #resizer} und
 * {@link #margin} an -- kein Zugriff auf Kinder, Eltern, Eingaben oder das Zeichnen.
 * Nachgemessen vor dem Verschieben, nicht vermutet.
 *
 * <p>Es ist eine BASISKLASSE und keine Hilfsklasse, weil die gesamte API fliessend ist
 * ({@code element.relative(x).x(10).w(1F)}). Jede andere Form haette jede Aufruferzeile im
 * ganzen Mod angefasst; so bleibt keine einzige davon beruehrt.
 *
 * <p>⚠️ Der Rueckgabetyp ist {@link UIElement}, obwohl die Klasse darunter sitzt. Das ist
 * Absicht: nur so bleibt die fliessende Kette an Unterklassen benutzbar. Der Cast ist sicher,
 * weil {@link UIElement} der einzige Erbe ist -- wer eine zweite Klasse hiervon ableitet, muss
 * das hier mitdenken.
 */
public abstract class UIElementLayout
{

    /**
     * Element's margin (it's used only by layout resizers)
     */
    public final Margin margin = new Margin();

    /**
     * Flex resizer of this class
     */
    protected Flex flex = new Flex();

    /**
     * Resizer of this class
     */
    protected IResizer resizer = this.flex;

    /* Resizer methods */

    public Flex getFlex()
    {
        return this.flex;
    }

    public IResizer resizer()
    {
        return this.resizer;
    }

    public UIElement resizer(IResizer resizer)
    {
        this.resizer = resizer;

        return (UIElement) this;
    }

    public UIElement resetFlex()
    {
        this.flex.x.reset();
        this.flex.y.reset();
        this.flex.w.reset();
        this.flex.h.reset();

        this.flex.relative = this.flex.post = null;

        return (UIElement) this;
    }

    public UIElement set(int x, int y, int w, int h)
    {
        this.flex.x.set(0, x);
        this.flex.y.set(0, y);
        this.flex.w.set(0, w);
        this.flex.h.set(0, h);

        return (UIElement) this;
    }

    /* X */

    public UIElement x(int offset)
    {
        this.flex.x.set(0, offset);

        return (UIElement) this;
    }

    public UIElement x(float value)
    {
        this.flex.x.set(value, 0);

        return (UIElement) this;
    }

    public UIElement x(float value, int offset)
    {
        this.flex.x.set(value, offset);

        return (UIElement) this;
    }

    /* Y */

    public UIElement y(int offset)
    {
        this.flex.y.set(0, offset);

        return (UIElement) this;
    }

    public UIElement y(float value)
    {
        this.flex.y.set(value, 0);

        return (UIElement) this;
    }

    public UIElement y(float value, int offset)
    {
        this.flex.y.set(value, offset);

        return (UIElement) this;
    }

    /* Width */

    public UIElement w(int offset)
    {
        this.flex.w.set(0, offset);

        return (UIElement) this;
    }

    public UIElement w(float value)
    {
        this.flex.w.set(value, 0);

        return (UIElement) this;
    }

    public UIElement w(float value, int offset)
    {
        this.flex.w.set(value, offset);

        return (UIElement) this;
    }

    public UIElement wTo(IResizer flex)
    {
        this.flex.w.target = flex;

        return (UIElement) this;
    }

    public UIElement wTo(IResizer flex, int offset)
    {
        this.flex.w.target = flex;
        this.flex.w.offset = offset;

        return (UIElement) this;
    }

    public UIElement wTo(IResizer flex, float anchor)
    {
        this.flex.w.target = flex;
        this.flex.w.targetAnchor = anchor;

        return (UIElement) this;
    }

    public UIElement wTo(IResizer flex, float anchor, int offset)
    {
        this.flex.w.target = flex;
        this.flex.w.targetAnchor = anchor;
        this.flex.w.offset = offset;

        return (UIElement) this;
    }

    /* Height */

    public UIElement h(int offset)
    {
        this.flex.h.set(0, offset);

        return (UIElement) this;
    }

    public UIElement h(float value)
    {
        this.flex.h.set(value, 0);

        return (UIElement) this;
    }

    public UIElement h(float value, int offset)
    {
        this.flex.h.set(value, offset);

        return (UIElement) this;
    }

    public UIElement hTo(IResizer target)
    {
        return this.hTo(target, 0);
    }

    public UIElement hTo(IResizer target, int offset)
    {
        return this.hTo(target, 0F, offset);
    }

    public UIElement hTo(IResizer target, float anchor)
    {
        return this.hTo(target, anchor, 0);
    }

    public UIElement hTo(IResizer target, float anchor, int offset)
    {
        this.flex.h.target = target;
        this.flex.h.targetAnchor = anchor;
        this.flex.h.offset = offset;

        return (UIElement) this;
    }

    /* Other variations */

    public UIElement xy(int x, int y)
    {
        this.flex.x.set(0, x);
        this.flex.y.set(0, y);

        return (UIElement) this;
    }

    public UIElement xy(float x, float y)
    {
        this.flex.x.set(x);
        this.flex.y.set(y);

        return (UIElement) this;
    }

    public UIElement wh(int w, int h)
    {
        this.flex.w.set(0, w);
        this.flex.h.set(0, h);

        return (UIElement) this;
    }

    public UIElement full(IResizer relative)
    {
        return this.relative(relative).wh(1F, 1F);
    }

    public UIElement full(UIElement relative)
    {
        return this.relative(relative).wh(1F, 1F);
    }

    public UIElement wh(float w, float h)
    {
        this.flex.w.set(w);
        this.flex.h.set(h);

        return (UIElement) this;
    }

    public UIElement minW(int max)
    {
        this.flex.w.min = max;

        return (UIElement) this;
    }

    public UIElement minH(int max)
    {
        this.flex.h.min = max;

        return (UIElement) this;
    }

    public UIElement maxW(int max)
    {
        this.flex.w.max = max;

        return (UIElement) this;
    }

    public UIElement maxH(int max)
    {
        this.flex.h.max = max;

        return (UIElement) this;
    }

    public UIElement anchor(float x)
    {
        return this.anchor(x, x);
    }

    public UIElement anchor(float x, float y)
    {
        this.flex.x.anchor = x;
        this.flex.y.anchor = y;

        return (UIElement) this;
    }

    public UIElement anchorX(float x)
    {
        this.flex.x.anchor = x;

        return (UIElement) this;
    }

    public UIElement anchorY(float y)
    {
        this.flex.y.anchor = y;

        return (UIElement) this;
    }

    /* Post resizers convenience methods */

    public RowResizer row()
    {
        return this.row(UIConstants.MARGIN);
    }

    public RowResizer row(int margin)
    {
        if (this.flex.post instanceof RowResizer)
        {
            return (RowResizer) this.flex.post;
        }

        return RowResizer.apply((UIElement) this, margin);
    }

    public ColumnResizer column()
    {
        return this.column(UIConstants.MARGIN);
    }

    public ColumnResizer column(int margin)
    {
        if (this.flex.post instanceof ColumnResizer)
        {
            return (ColumnResizer) this.flex.post;
        }

        return ColumnResizer.apply((UIElement) this, margin);
    }

    public GridResizer grid(int margin)
    {
        if (this.flex.post instanceof GridResizer)
        {
            return (GridResizer) this.flex.post;
        }

        return GridResizer.apply((UIElement) this, margin);
    }

    public BoundsResizer bounds(UIElement target, int padding)
    {
        if (this.flex.post instanceof BoundsResizer boundsResizer)
        {
            boundsResizer.target = target;
            boundsResizer.padding = padding;

            return boundsResizer;
        }

        return BoundsResizer.apply((UIElement) this, target, padding);
    }

    /* Hierarchy */

    public UIElement relative(UIElement element)
    {
        this.flex.relative = element.area;

        return (UIElement) this;
    }

    public UIElement relative(IResizer relative)
    {
        this.flex.relative = relative;

        return (UIElement) this;
    }

    public UIElement post(IResizer post)
    {
        this.flex.post = post;

        return (UIElement) this;
    }

    /* Margin */

    public UIElement margin(int all)
    {
        return this.margin(all, all);
    }

    public UIElement margin(int horizontal, int vertical)
    {
        return this.margin(horizontal, vertical, horizontal, vertical);
    }

    public UIElement margin(int left, int top, int right, int bottom)
    {
        this.margin.all(left, top, right, bottom);

        return (UIElement) this;
    }

    public UIElement marginLeft(int left)
    {
        this.margin.left(left);

        return (UIElement) this;
    }

    public UIElement marginTop(int top)
    {
        this.margin.top(top);

        return (UIElement) this;
    }

    public UIElement marginRight(int right)
    {
        this.margin.right(right);

        return (UIElement) this;
    }

    public UIElement marginBottom(int bottom)
    {
        this.margin.bottom(bottom);

        return (UIElement) this;
    }

}
