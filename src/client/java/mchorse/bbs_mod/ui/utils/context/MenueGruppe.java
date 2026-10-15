package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import com.mcstudio.nano.NanoDesign;

/**
 * Gruppentitel im Kontextmenue („CLIP") — Kapitaelchen, gedimmt, nicht anwaehlbar.
 * Eigene Zeile, weil die Liste eine feste Zeilenhoehe hat; der Text sitzt unten,
 * damit er zum folgenden Eintrag gehoert und nicht in der Luft haengt.
 */
public class MenueGruppe extends ContextAction
{
    public MenueGruppe(IKey label)
    {
        super(Icons.NONE, label, null);
    }

    @Override
    public boolean waehlbar()
    {
        return false;
    }

    @Override
    public int getWidth(FontRenderer font)
    {
        return 26 + font.getWidth(this.label.get().toUpperCase());
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        if (this.trennerDavor)
        {
            context.batcher.box(x + 6, y, x + w - 6, y + 1, Colors.A100 | NanoDesign.KOPF);
        }

        context.batcher.text(this.label.get().toUpperCase(), x + 8,
            y + h - font.getHeight() - 3, Colors.A100 | NanoDesign.TXT_OFF, false);
    }
}
