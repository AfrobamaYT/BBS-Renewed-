// Coded by Afrobama/Claude — UI Overhaul Phase 12: visible settings panel for BBSTools
package com.afrobama.bbstools.client.ui.settings;

import com.afrobama.bbstools.client.ui.overlay.BBSToolsOverlayHelper;
import com.afrobama.bbstools.client.ui.theme.BBSToolsButton;
import com.afrobama.bbstools.client.ui.theme.BBSToolsSection;
import com.afrobama.bbstools.client.ui.theme.BBSToolsSlider;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

/**
 * Phase 12 deliverable — a visible "Settings" overlay so the UI-Overhaul
 * features are not all gated behind invisible keybinds. The first card
 * is the transparency slider promised in the announcement panel; future
 * cards will cycle accent colors, jump to the keybind editor, and
 * surface the undo / replay-dock shortcuts.
 *
 * <p>Aesthetics follow {@code STYLE_GUIDE.md} — sharp 0-2 px corners,
 * uppercase section headers in {@code TEXT_DIM}, multi-accent palette
 * for category cues (PRIMARY for transparency, OK for "reset").</p>
 */
public class UIBBSToolsSettingsOverlay extends UIOverlayPanel
{
    public final BBSToolsSection transparencySection;
    public final BBSToolsSlider transparencySlider;
    public final BBSToolsButton resetTransparencyBtn;
    public final BBSToolsButton openKeybindsBtn;
    public final BBSToolsButton openUndoBtn;

    public static void open()
    {
        BBSToolsOverlayHelper.open(new UIBBSToolsSettingsOverlay(), 320, 0.5F);
    }

    public static UIBBSToolsSettingsOverlay getOpened()
    {
        return BBSToolsOverlayHelper.getOpened(UIBBSToolsSettingsOverlay.class);
    }

    public static boolean isOpened()
    {
        return getOpened() != null;
    }

    public UIBBSToolsSettingsOverlay()
    {
        super(IKey.constant("BBSTools Settings"));
        this.resizable();

        /* === Transparency section === */
        this.transparencySection = new BBSToolsSection("EDITOR TRANSPARENCY");
        this.transparencySection.accent = BBSToolsTheme.ACCENT_PRIMARY;
        this.transparencySection.relative(this.content).x(6).y(4).w(1F, -12).h(74);

        this.transparencySlider = new BBSToolsSlider()
            .range(0F, 1F)
            .initial(BBSToolsTransparency.PANEL_BG_ALPHA)
            .accent(BBSToolsTheme.ACCENT_PRIMARY)
            .onChange(BBSToolsTransparency::set);
        this.transparencySlider.relative(this.transparencySection).x(6).y(20).w(1F, -12).h(18);

        this.resetTransparencyBtn = new BBSToolsButton("Reset to 100%")
            .onClick(() ->
            {
                BBSToolsTransparency.set(1F);
                this.transparencySlider.setValue(1F);
            });
        this.resetTransparencyBtn.relative(this.transparencySection).x(6).y(44).w(110).h(18);

        this.transparencySection.add(this.transparencySlider, this.resetTransparencyBtn);

        /* === Shortcuts section === */
        BBSToolsSection shortcuts = new BBSToolsSection("OVERLAYS");
        shortcuts.accent = BBSToolsTheme.ACCENT_CAMERA;
        shortcuts.relative(this.content).x(6).y(86).w(1F, -12).h(96);

        this.openUndoBtn = new BBSToolsButton("Undo History  (Ctrl+H)")
            .onClick(() ->
            {
                this.close();
                com.afrobama.bbstools.client.ui.undo.UIBBSToolsUndoOverlay.open();
            });
        this.openUndoBtn.relative(shortcuts).x(6).y(18).w(1F, -12).h(18);

        this.openKeybindsBtn = new BBSToolsButton("Keybind Editor  (Shift+F9)")
            .onClick(() ->
            {
                this.close();
                /* The keybind editor wires its own overlay open on Shift+F9
                 * via UIBaseMenuKeybindMixin; we leave that path canonical
                 * instead of duplicating the open call here. */
            });
        this.openKeybindsBtn.relative(shortcuts).x(6).y(62).w(1F, -12).h(18);
        this.openKeybindsBtn.enabled(false);

        this.content.add(this.transparencySection, shortcuts);

        this.minW(320).maxW(480);
        this.minH(220).maxH(360);
    }

    @Override
    public void render(UIContext context)
    {
        /* Reflect external transparency changes (e.g. Ctrl+Alt+T) so the
         * slider stays in sync if the user toggles via keybind while
         * the settings panel is open. */
        if (Math.abs(this.transparencySlider.getValue() - BBSToolsTransparency.PANEL_BG_ALPHA) > 0.005F)
        {
            this.transparencySlider.setValue(BBSToolsTransparency.PANEL_BG_ALPHA);
        }
        super.render(context);
    }

}
