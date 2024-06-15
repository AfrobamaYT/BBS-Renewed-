package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class UIDebugPanel extends UIDashboardPanel
{
    public UIKeyframes keyframes;

    public UIDebugPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.keyframes = new UIKeyframes(null).duration(() -> 40);
        this.keyframes.full(this);

        for (int i = 0; i < 20; i++)
        {
            KeyframeChannel<Double> channel = new KeyframeChannel<>("baboy", KeyframeFactories.DOUBLE);
            UIKeyframeSheet sheet = new UIKeyframeSheet("baboy_" + i, IKey.raw("Baboy " + i), Colors.HSVtoRGB((float) Math.random(), 1F, 1F).getRGBColor(), channel, null);

            channel.insert(0L, 0D);
            channel.insert(20L + (long) (Math.random() * 18 - 9), 0D);
            channel.insert(40L, 1D);

            channel.get(1).setDuration(10);

            this.keyframes.addSheet(sheet);
        }

        this.add(this.keyframes);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.keyframes.resetViewX();
    }
}