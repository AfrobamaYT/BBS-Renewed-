package mchorse.bbs_mod.ui.framework.tooltips.styles;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

public class LightTooltipStyle extends TooltipStyle
{
    @Override
    public void renderBackground(UIContext context, Area area)
    {
        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 4, Colors.A50, 0);

        /* P2: weiche Karte statt hartem Kasten. */
        if (mchorse.bbs_mod.BBSSettings.vectorUi != null && mchorse.bbs_mod.BBSSettings.vectorUi.get())
        {
            context.batcher.rundeck(area.x, area.y, area.w, area.h, 4F, Colors.WHITE);
        }
        else
        {
            area.render(context.batcher, Colors.WHITE);
        }
    }

    @Override
    public int getTextColor()
    {
        return 0;
    }

    @Override
    public int getForegroundColor()
    {
        return 0;
    }
}