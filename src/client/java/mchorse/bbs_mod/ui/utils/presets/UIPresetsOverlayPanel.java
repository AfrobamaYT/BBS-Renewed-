package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIPresetsOverlayPanel extends UIListOverlayPanel
{
    public UIPresetsOverlayPanel(PresetManager manager, Supplier<MapType> supplier, Consumer<MapType> consumer)
    {
        super(UIKeys.PRESETS_TITLE, null);

        this.callback = (l) ->
        {
            MapType load = manager.load(l.get(0));

            if (load != null)
            {
                consumer.accept(load);
                this.close();
            }
        };

        this.addValues(manager.getKeys());

        UIIcon save = new UIIcon(Icons.SAVED, (b) ->
        {
            MapType type = supplier.get();

            if (type != null)
            {
                UIPromptOverlayPanel pane = new UIPromptOverlayPanel(UIKeys.PRESETS_SAVE_TITLE, UIKeys.PRESETS_SAVE_DESCRIPTION, (t) ->
                {
                    manager.save(t, type);
                    this.list.list.clear();
                    this.addValues(manager.getKeys());
                });

                pane.text.filename();
                UIOverlay.addOverlay(this.getContext(), pane);
            }
        });

        UIIcon folder = new UIIcon(Icons.FOLDER, (b) ->
        {
            UIUtils.openFolder(manager.getFolder());
        });

        save.tooltip(UIKeys.PRESETS_SAVE, Direction.LEFT);
        folder.tooltip(UIKeys.PRESETS_OPEN, Direction.LEFT);
        this.icons.add(save, folder);
    }
}