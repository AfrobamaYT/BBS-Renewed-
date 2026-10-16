package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Katalog F2: Blender 4.x "Blend to Neighbor" (Graph Editor, Key > Blend; AnimAide) for keys of any
 * kind. Shift+Alt+E over the keyframe editor blends the values of the selected keys toward the key
 * before their run (mouse left) or after it (mouse right), 0 to 100 %. LMB/Enter keeps, RMB/Escape
 * restores; one undo through cacheKeyframes/submitKeyframes, as dragging keys.
 */
public class KeyframeMischen extends UIElement
{
    public static final KeyCombo TASTE = new KeyCombo("keyframes_blend_neighbor", IKey.raw("Blend selected keys toward the key before or after (mouse left/right)"),
        InputConstants.KEY_E, InputConstants.KEY_LSHIFT, InputConstants.KEY_LALT).categoryKey("keyframes");

    /** Mouse travel (GUI pixels) for the whole way to a neighbour */
    private static final float WEG = 240F;

    private record Ziel(Keyframe key, IKeyframeFactory factory, Object alt, Object davor, Object danach)
    {}

    private final UIKeyframes view;
    private final List<Ziel> ziele = new ArrayList<>();
    private int startX = Integer.MIN_VALUE;
    private float faktor;

    /** False (and a message) without a selected key */
    public static boolean starten(UIKeyframes view)
    {
        if (view.getGraph().getSelected() == null)
        {
            view.getContext().notifyInfo(IKey.raw("Select keys first, then Shift+Alt+E blends them to a neighbour"));

            return false;
        }

        view.cacheKeyframes();

        KeyframeMischen modal = new KeyframeMischen(view);
        UIElement root = BBSModClient.getDashboard().getRoot();

        modal.relative(root).wh(1F, 1F);
        root.add(modal);
        modal.resize();

        return true;
    }

    public static boolean laeuft()
    {
        return !BBSModClient.getDashboard().getRoot().getChildren(KeyframeMischen.class).isEmpty();
    }

    private KeyframeMischen(UIKeyframes view)
    {
        this.view = view;

        for (UIKeyframeSheet sheet : view.getGraph().getSheets())
        {
            List<?> keys = sheet.channel.getKeyframes();
            IKeyframeFactory factory = sheet.channel.getFactory();

            for (int i = 0; i < keys.size(); i++)
            {
                if (!sheet.selection.has(i))
                {
                    continue;
                }

                /* The neighbours of the whole run of selected keys, as Blender: inner keys blend outward too */
                int links = i;
                int rechts = i;

                while (links >= 0 && sheet.selection.has(links)) links--;
                while (rechts < keys.size() && sheet.selection.has(rechts)) rechts++;

                Keyframe key = (Keyframe) keys.get(i);
                Object davor = links >= 0 ? factory.copy(((Keyframe) keys.get(links)).getValue()) : null;
                Object danach = rechts < keys.size() ? factory.copy(((Keyframe) keys.get(rechts)).getValue()) : null;

                this.ziele.add(new Ziel(key, factory, factory.copy(key.getValue()), davor, danach));
            }
        }
    }

    private void anwenden(float f)
    {
        this.faktor = f;

        for (Ziel z : this.ziele)
        {
            Object nachbar = f < 0F ? z.davor : z.danach;
            Object wert = z.factory.copy(z.alt);

            if (nachbar != null && f != 0F)
            {
                /* Copies in, a copy out: pose interpolation adds bones to its inputs and returns a shared buffer */
                Object a = z.factory.copy(z.alt);
                Object b = z.factory.copy(nachbar);

                wert = z.factory.copy(z.factory.interpolate(a, a, b, b, Interpolations.LINEAR, Math.abs(f)));
            }

            z.key.setValue(wert, false);
        }

        this.view.triggerChange();
    }

    private void beenden(boolean behalten)
    {
        if (!behalten)
        {
            this.anwenden(0F);
        }

        this.view.submitKeyframes();
        this.view.getGraph().pickSelected();
        this.removeFromParent();
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.beenden(context.mouseButton == 0);

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(InputConstants.KEY_RETURN) || context.isPressed(InputConstants.KEY_NUMPADENTER) || context.isPressed(InputConstants.KEY_ESCAPE))
        {
            this.beenden(!context.isPressed(InputConstants.KEY_ESCAPE));
        }

        return true; /* modal: nothing else may act on the keys while they are held */
    }

    @Override
    public void render(UIContext context)
    {
        if (this.startX == Integer.MIN_VALUE)
        {
            this.startX = context.mouseX;
        }

        float f = MathUtils.clamp((context.mouseX - this.startX) / WEG, -1F, 1F);

        if (f != this.faktor)
        {
            this.anwenden(f);
        }

        String richtung = f < 0F ? "toward the key before" : f > 0F ? "toward the key after" : "(move left or right)";

        context.batcher.textCard("Blend to neighbour " + Math.round(Math.abs(f) * 100) + " % " + richtung + "   LMB/Enter keep · RMB/Esc cancel",
            context.mouseX + 12, context.mouseY + 8);

        super.render(context);
    }
}
