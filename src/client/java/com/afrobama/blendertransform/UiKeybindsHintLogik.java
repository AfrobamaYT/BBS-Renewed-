package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybinds;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Logik hinter {@code UIKeybindsHintMixin} — siehe
 * {@link com.afrobama.bbstools.client.blender.MixinBruecke} für das muster.
 *
 * <p>{@code self.area} ist ein geerbtes/öffentliches feld der zielklasse (kein
 * {@code @Shadow}) — direkt über den BBS-typ {@code UIKeybinds} in der signatur
 * erreichbar, kein feldLesen nötig.</p>
 */
public final class UiKeybindsHintLogik
{
    private UiKeybindsHintLogik() {}

    public static void drawEditorHint(UIKeybinds self, UIContext context)
    {
        String editorCombo = BBSToolsKeys.KEYBIND_EDITOR_OPEN == null
            ? "?"
            : BBSToolsKeys.KEYBIND_EDITOR_OPEN.getKeyCombo();

        FontRenderer font = context.batcher.getFont();
        int hintColor = Colors.A100 | 0xFFCC33;
        String hintText = "Press " + editorCombo + " to open the Keybind Editor";
        int w = font.getWidth(hintText);

        /* Draw at the bottom-right of the viewer area so it doesn't
         *  overlap with the existing category content. */
        int x = self.area.ex() - w - 10;
        int y = self.area.ey() - font.getHeight() - 10;

        context.batcher.box(x - 2, y - 2, x + w + 2, y + font.getHeight() + 2, hintColor);
        context.batcher.text(hintText, x, y);
    }
}
