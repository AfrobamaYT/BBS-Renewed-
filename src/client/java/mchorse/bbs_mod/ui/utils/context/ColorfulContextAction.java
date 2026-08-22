package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

public class ColorfulContextAction extends ContextAction
{
    public int color;

    public ColorfulContextAction(Icon icon, IKey label, Runnable runnable, int color)
    {
        super(icon, label, runnable);

        this.color = color;

        /* ⛔ Zerstoerend wird im Entwurf durch ROTEN TEXT gezeigt, nicht durch einen
         * Farbbalken links. Wer schon Colors.NEGATIVE uebergibt, bekommt das ohne eine
         * einzige geaenderte Aufrufstelle — der Balken entfaellt fuer diesen Fall. */
        this.rot = (color & 0xFFFFFF) == (Colors.NEGATIVE & 0xFFFFFF);
    }

    @Override
    protected void renderBackground(UIContext context, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        super.renderBackground(context, x, y, w, h, hover, selected);

        if (this.rot)
        {
            return;
        }

        context.batcher.box(x, y, x + 2, y + h, Colors.A100 | this.color);
        context.batcher.gradientHBox(x + 2, y, x + 24, y + h, Colors.A25 | this.color, this.color);
    }
}
