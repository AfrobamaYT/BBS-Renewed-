package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ik.RigCtrl;
import mchorse.bbs_mod.cubic.ik.RigMechanik;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/** Imported controls have a visible display switch even on rigs without custom properties. */
public class UIRigDisplay extends UISection
{
    private final UIToggle widgets;
    private final UILabel status;

    public UIRigDisplay()
    {
        super(IKey.raw("Rig Display"));

        this.widgets = new UIToggle(IKey.raw("Control widgets"), BBSSettings.ikDebug.enabled.get(), this::toggle);
        this.widgets.tooltip(IKey.raw("Show hand, finger and facial controls. F5 also toggles the physics overlay."));

        UIIcon settings = new UIIcon(Icons.GEAR, b -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(BBSSettings.ikDebug)));

        settings.tooltip(IKey.raw("Control appearance: visibility, opacity and drawing through the model"));
        settings.wh(20, 14);
        this.status = UI.label(IKey.EMPTY);
        this.fields.add(UI.row(this.widgets, settings), this.status);
    }

    private void toggle(UIToggle toggle)
    {
        BBSSettings.ikDebug.enabled.set(toggle.getValue());

        /* Both marker groups off previously hid every imported control as well. */
        if (toggle.getValue() && !BBSSettings.ikDebug.target.visible.get() && !BBSSettings.ikDebug.pole.visible.get())
        {
            BBSSettings.ikDebug.target.visible.set(true);
            BBSSettings.ikDebug.pole.visible.set(true);
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.widgets.setValue(BBSSettings.ikDebug.enabled.get());
        this.status.label = IKey.raw(this.statusText());
        super.render(context);
    }

    private String statusText()
    {
        UIFilmPanel panel = BBSModClient.getDashboard() != null
            && BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;

        if (panel == null)
        {
            return "Select a rigged model";
        }

        mchorse.bbs_mod.forms.entities.IEntity entity = panel.getController().getCurrentEntity();
        java.util.List<RigCtrl.Eintrag> controls = RigCtrl.fuerEntity(entity);
        mchorse.bbs_mod.bobj.BOBJArmature armature = RigCtrl.armatureVon(entity);

        if (controls == null)
        {
            return "No imported control widgets";
        }

        if (armature != null && armature.zwang instanceof RigMechanik rig)
        {
            int unsupported = rig.unbekannt.size() + rig.treiber.unbekannt.size();

            if (unsupported > 0)
            {
                return "Unsupported rig logic: " + unsupported + " types";
            }
        }

        if (BBSSettings.ikDebug.enabled.get() && BBSSettings.ikDebug.opacity.get() <= 0F)
        {
            return "Widgets hidden: opacity is zero";
        }

        return controls.size() + " imported controls";
    }
}
