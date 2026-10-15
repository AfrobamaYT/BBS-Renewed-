package mchorse.bbs_mod.ui.framework.tooltips.styles;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

public class DarkTooltipStyle extends TooltipStyle
{
    @Override
    public void renderBackground(UIContext context, Area area)
    {
        int color = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 6, Colors.A25 + color, color);

        /* P2: weiche Karte statt hartem Kasten. */
        if (BBSSettings.vectorUi != null && BBSSettings.vectorUi.get())
        {
            context.batcher.rundeck(area.x, area.y, area.w, area.h, 4F, Colors.A100);
        }
        else
        {
            area.render(context.batcher, Colors.A100);
        }
    }

    @Override
    public int getTextColor()
    {
        return Colors.WHITE;
    }

    @Override
    public int getForegroundColor()
    {
        return BBSSettings.primaryColor.get();
    }
}