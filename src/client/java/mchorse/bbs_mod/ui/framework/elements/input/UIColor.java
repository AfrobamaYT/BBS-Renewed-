package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * Color GUI element
 *
 * This class is responsible for providing a way to edit colors, this element
 * itself is not editing the color, the picker element is the one that does color editing
 */
public class UIColor extends UIElement
{
    public UIColorPicker picker;
    public boolean label = true;
    public Direction direction;

    private UIElement target;

    public UIColor(Consumer<Integer> callback)
    {
        super();

        this.picker = new UIColorPicker(callback);
        this.picker.wh(200, 85);

        this.direction(Direction.BOTTOM).h(UIConstants.CONTROL_HEIGHT);
        /* Presets: the quick way past the picker; in the context menu, beside what the host adds (BBS FS 7aa8cf2a4) */
        this.context((menu) -> menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.COLOR, mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.color.presets"), this::presetsOeffnen));
    }

    public UIColor withTarget(UIElement target)
    {
        this.target = target;

        return this;
    }

    public UIColor withAlpha()
    {
        this.picker.editAlpha();

        return this;
    }

    public UIColor direction(Direction direction)
    {
        this.direction = direction;
        this.picker.anchor(1 - direction.anchorX, 1 - direction.anchorY);

        return this;
    }

    public UIColor onTop()
    {
        return this.direction(Direction.TOP);
    }

    public UIColor noLabel()
    {
        this.label = false;

        return this;
    }

    public void setColor(int color)
    {
        this.picker.setColor(color);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            if (!this.picker.hasParent())
            {
                int x = context.globalX(this.area.x(this.direction.anchorX) + 2 * this.direction.factorX);
                int y = context.globalY(this.area.y(this.direction.anchorY) + 2 * this.direction.factorY);

                UIElement target = this.target == null ? context.menu.overlay : this.target;

                target.add(this.picker);
                this.picker.setup(x, y);
                this.picker.bounds(context.menu.main, 2);
                this.picker.resize();
            }
            else
            {
                this.picker.removeFromParent();
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    private void presetsOeffnen()
    {
        UIContext context = this.getContext();
        mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPresets presets = new mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPresets(this.picker);

        this.picker.removeFromParent();
        presets.anchor(1 - this.direction.anchorX, 1 - this.direction.anchorY);
        (this.target == null ? context.menu.overlay : this.target).add(presets);
        presets.xy(context.globalX(this.area.x(this.direction.anchorX) + 2 * this.direction.factorX), context.globalY(this.area.y(this.direction.anchorY) + 2 * this.direction.factorY));
        presets.bounds(context.menu.main, 2);
        presets.resize();
    }

    @Override
    public void render(UIContext context)
    {
        int padding = 0;

        this.picker.renderRect(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey());

        if (this.area.isInside(context))
        {
            this.area.render(context.batcher, Colors.A12, padding);
        }

        if (this.label)
        {
            FontRenderer font = context.batcher.getFont();
            String label = this.picker.color.stringify(this.picker.editAlpha);

            context.batcher.textCard(label, this.area.mx(font.getWidth(label)), this.area.my(font.getHeight() - 1), Colors.WHITE, Colors.A25, 1);
        }

        this.renderLockedArea(context);

        super.render(context);
    }
}
