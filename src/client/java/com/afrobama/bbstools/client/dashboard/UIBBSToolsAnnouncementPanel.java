package com.afrobama.bbstools.client.dashboard;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * One-shot announcement popup shown the first time the dashboard
 * opens with BBSTools-Fabric installed. Replaces the upstream-fork's
 * UIFnafOverlayPanel which depended on bundled BBS asset textures.
 *
 * <p>The popup tells the user "you're running BBSTools — here's
 * what's wired" with a list of new keybinds. Gated by a one-byte
 * flag file at config/bbstools/shown_announcement.flag so it only
 * appears on first run.</p>
 */
public class UIBBSToolsAnnouncementPanel extends UIOverlayPanel
{
    public UIBBSToolsAnnouncementPanel()
    {
        super(IKey.constant("BBSTools-Fabric installed"));

        this.content.removeAll();

        UIScrollView scroll = UI.scrollView(6, 8);
        scroll.relative(this.content).full(this.content);

        UILabel title = new UILabel(IKey.constant(
            "§fBBSTools §7is wired in §8— companion-mod features ready"));
        title.color(0xFFE2E5EA);
        title.h(16);

        UIText body = new UIText(IKey.constant(
            "§8KEYBINDS  §7(rebind via Shift+F9):\n"
            + "  §fShift+F9§7  open Keybind Editor from any BBS menu\n"
            + "  §f^§7  Quick Replay picker inside the film editor\n"
            + "  §fCtrl+V§7  paste a model URL in geometry editor to download + import\n"
            + "  §fCtrl+Alt+T§7  toggle UI background transparency\n"
            + "  §fCtrl+H§7  Undo History overlay (Phase 9)\n"
            + "  §fCtrl+Shift+Z / Ctrl+Shift+Y§7  undo / redo last tracked edit\n"
            + "  §fCtrl+,§7  BBSTools Settings (transparency slider + shortcuts)\n\n"
            + "§8COMMANDS\n"
            + "  §f/mobarmy§7, §f/buildschematic§7, §f/ragdolly§7, §f/bbsdebug\n"
            + "  §f/bbsdebug transparency [0-100]§7 — set editor opacity\n"
            + "  §f/bbsdebug editor-mode on§7 — skip mob ticks/renders for fast scene setup\n"
            + "  §f/bbsdebug campreview on§7 — alt-camera viewport (Ctrl+L in film panel)\n"
            + "  §f/bbsdebug undo-history§7 — open the undo overlay\n\n"
            + "§8MODEL EDITOR\n"
            + "  §f+ Add§7 creates a fresh model with a starter cube.\n"
            + "  GLB/GLTF imports extract embedded textures into the BBS browser.\n\n"
            + "§8(This popup only shows once. Style guide: STYLE_GUIDE.md)"));
        body.color(0xFF8A9099, true).padding(0, 2).lineHeight(12);

        UIElement column = UI.column(6, title, body).marginTop(6);
        scroll.add((IUIElement) column);
        this.content.add((IUIElement) scroll);
    }

}
