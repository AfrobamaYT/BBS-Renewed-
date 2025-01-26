package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIPresetContextMenu extends UISimpleContextMenu
{
    public UIElement row;
    public UIIcon copy;
    public UIIcon paste;

    private final PresetManager manager;
    private final String copyPrefix;
    private final Supplier<MapType> supplier;
    private final Consumer<MapType> consumer;

    public UIPresetContextMenu(PresetManager manager, String copyPrefix, Supplier<MapType> supplier, Consumer<MapType> consumer)
    {
        super();

        this.manager = manager;
        this.copyPrefix = copyPrefix;
        this.supplier = supplier;
        this.consumer = consumer;

        this.copy = new UIIcon(Icons.COPY, (b) -> this.copy());
        this.paste = new UIIcon(Icons.PASTE, (b) -> this.paste());
        UIIcon presets = new UIIcon(Icons.MORE, (b) -> this.openPresets());

        presets.tooltip(UIKeys.PRESETS_VIEW);

        this.row = UI.row(0, this.copy, this.paste, presets);
        this.row.relative(this).row().resize();
        this.actions.y(21).h(1F, -21);

        this.add(this.row);
    }

    public UIPresetContextMenu labels(IKey copy, IKey paste)
    {
        this.copy.tooltip(copy);
        this.paste.tooltip(paste);

        return this;
    }

    private void copy()
    {
        MapType type = this.supplier.get();

        if (type != null)
        {
            Window.setClipboard(type, this.copyPrefix);
        }

        this.removeFromParent();
    }

    private void paste()
    {
        MapType type = Window.getClipboardMap(this.copyPrefix);

        if (type != null)
        {
            this.consumer.accept(type);
        }

        this.removeFromParent();
    }

    private void openPresets()
    {
        UIOverlay.addOverlay(this.getContext(), new UIPresetsOverlayPanel(this.manager, this.supplier, this.consumer), 240, 0.5F);
        this.removeFromParent();
    }

    @Override
    public void setMouse(UIContext context)
    {
        super.setMouse(context);

        this.w(Math.max(60, this.getFlex().w.offset));
        this.h(this.getFlex().h.offset + 21);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int color = BBSSettings.primaryColor.get();

        context.batcher.box(this.area.x + 2, this.area.y + 20, this.area.ex() - 2, this.area.y + 21, Colors.mulRGB(Colors.WHITE, 0.1F));
    }
}