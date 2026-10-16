package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;
import com.afrobama.bbstools.client.keybinds.UIKeybindEditor;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;

/**
 * Logik des UIBaseMenu-keybind-wiring-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIBaseMenuKeybindMixin} (2026-07-26, brücken-umbau). Verdrahtet
 * BBSToolsKeys (Shift+F9 für den editor, undo/redo, replay-dock, ...) in JEDES UIBaseMenu, damit
 * der user die BBSTools-UI von überall aus öffnen kann.</p>
 *
 * <p>Ansatz unverändert: das {@code root}-element kommt über {@link UIBaseMenuAccessor} (ein
 * {@code @Accessor}-mixin-interface, das auf {@code UIBaseMenu} selbst gewoben ist — nicht zu
 * verwechseln mit einem kernel-typ, es lebt im stabilen loader wie jeder andere gewobene
 * mixin-interface-typ auch). Ein frisches child-element bekommt die keybinds registriert.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link UIBaseMenu} ist ein BBS-typ, der cast auf
 * {@code UIBaseMenuAccessor} bleibt hier drin (das accessor-interface liegt im
 * {@code mixin}-paket, nicht im kernel).</p>
 */
public final class UIBaseMenuKeybindLogik
{
    private UIBaseMenuKeybindLogik() {}

    public static void wireKeybindEditor(UIBaseMenu self)
    {
        /* Touch BBSToolsKeys so its KeyCombo constructors run and register with BBS's
         *  pendingOverrides system at first menu open. Cheap and idempotent. */
        BBSToolsKeys.touch();

        com.afrobama.bbstools.client.mixin.UIBaseMenuAccessor acc =
            (com.afrobama.bbstools.client.mixin.UIBaseMenuAccessor) self;

        UIElement holder = new UIElement();
        holder.keys().register(BBSToolsKeys.KEYBIND_EDITOR_OPEN, () ->
        {
            UIKeybindEditor editor = new UIKeybindEditor();
            editor.setInitialOffset(40, 40);
            UIOverlay.addOverlay(self.context, editor);
        });
        holder.keys().register(BBSToolsKeys.UNDO_HISTORY_OPEN, () ->
        {
            com.afrobama.bbstools.client.ui.undo.UIBBSToolsUndoOverlay.open();
        });
        holder.keys().register(BBSToolsKeys.UNDO_STEP, () ->
        {
            com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack.undo();
        });
        holder.keys().register(BBSToolsKeys.REDO_STEP, () ->
        {
            com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack.redo();
        });
        holder.keys().register(BBSToolsKeys.SETTINGS_OPEN, () ->
        {
            com.afrobama.bbstools.client.ui.settings.UIBBSToolsSettingsOverlay.open();
        });
        holder.keys().register(BBSToolsKeys.LAUNCHPAD_OPEN, () ->
        {
            com.afrobama.bbstools.client.ui.launcher.UIBBSToolsLaunchPad.open();
        });
        /* TRANSFORM_KERNEL_TOGGLE entfernt 2026-07-26 (user-order): der Blender-transform
         * ist raus, damit auch sein umschalter. */
        acc.bbstools$getRoot().add(holder);
    }
}
