package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.storage.DataStorage;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class UIDebugPanel extends UIDashboardPanel
{
    public UIKeyframes keyframes;
    public UIButton button;

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

        this.button = new UIButton(IKey.raw("Herro"), (b) ->
        {
            File file = new File(BBSMod.getExportFolder(), "abc.dat");
            MapType type = new MapType(false);

            for (int i = 0; i < 256; i++)
            {
                type.putInt("K" + i, 0);
            }

            try
            {
                DataStorage.writeToStream(new FileOutputStream(file), type);
                MapType read = (MapType) DataStorage.readFromStream(new FileInputStream(file));

                System.out.println(read);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });

        this.button.relative(this).xy(10, 10).w(80);

        this.add(this.button);
        // this.add(this.keyframes);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.keyframes.resetViewX();
    }
}