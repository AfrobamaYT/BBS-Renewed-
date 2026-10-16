package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.particles.vanilla.VanillaParticlePreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Consumer;

/** The particle type picker with a small static sample of each type (BBS FS 72705306b). */
public class UIVanillaParticleList extends UIStringList
{
    private static final int PREVIEW_SIZE = 16;

    public UIVanillaParticleList(Consumer<List<String>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = PREVIEW_SIZE + 4;
    }

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        int h = this.scroll.scrollItemSize;
        int left = x + 4;
        Identifier particle = Identifier.tryParse(element);

        VanillaParticlePreview.render(context, particle, left, y + (h - PREVIEW_SIZE) / 2, PREVIEW_SIZE, hover ? Colors.HIGHLIGHT : Colors.WHITE);
        context.batcher.textShadow(element, left + PREVIEW_SIZE + 4, y + (h - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
    }
}
