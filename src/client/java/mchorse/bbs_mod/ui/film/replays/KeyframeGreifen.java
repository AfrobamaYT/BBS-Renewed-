package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformNumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Blender's G and S in the dope sheet. G (Katalog E1) moves the selected keys in time, S scales their
 * distance to the playhead. Mouse: keys land on whole ticks (Shift: free); or type the amount like in
 * a bone gesture (G 5, G -3, S 2, S 0.5, S 1/3). LMB or Enter keeps, RMB or Escape puts everything back.
 * Same caching as dragging a key (cacheKeyframes/submitKeyframes): one undo. Shift+D is Blender's
 * duplicate-and-grab: the keys move as with G and copies stay where they were (inserted on keep only).
 *
 * <p>Modal like the breakdowner: an element over the whole dashboard takes clicks and keys while it
 * runs, so Escape cancels instead of reaching the editor.</p>
 */
public class KeyframeGreifen extends UIElement
{
    public static final KeyCombo TASTE = new KeyCombo("keyframes_grab", IKey.raw("Grab selected keys: move in time (G, then mouse or a number)"),
        InputConstants.KEY_G).categoryKey("keyframes");
    public static final KeyCombo STRECKEN = new KeyCombo("keyframes_scale", IKey.raw("Scale selected keys in time around the playhead (S, then mouse or a factor)"),
        InputConstants.KEY_S).categoryKey("keyframes");
    public static final KeyCombo DUPLIZIEREN = new KeyCombo("keyframes_duplicate_grab", IKey.raw("Duplicate selected keys and move the copies in time (Shift+D)"),
        InputConstants.KEY_D, InputConstants.KEY_LSHIFT).categoryKey("keyframes");

    private final UIKeyframes view;
    private final boolean skalieren;
    private final KeyframeKopien kopien = new KeyframeKopien();
    /* Playhead in graph ticks; S scales the keys' distance to it */
    private final float pivot;
    /* Ticks at the start: every step is applied from them, so loop clamping cannot drift and cancel is exact */
    private final Map<Keyframe, Float> start = new IdentityHashMap<>();
    private final TransformNumericInput zahl = new TransformNumericInput();
    private int startX = Integer.MIN_VALUE;
    private float wert;

    /** False (and a message) without a selected key */
    public static boolean starten(UIKeyframes view, boolean skalieren)
    {
        return starten(view, skalieren, false);
    }

    public static boolean starten(UIKeyframes view, boolean skalieren, boolean kopieren)
    {
        if (view.getGraph().getSelected() == null)
        {
            view.getContext().notifyInfo(IKey.raw(skalieren ? "Select keys first, then S scales them" : "Select keys first, then G moves them"));

            return false;
        }

        view.cacheKeyframes();

        KeyframeGreifen modal = new KeyframeGreifen(view, skalieren, kopieren);
        UIElement root = BBSModClient.getDashboard().getRoot();

        modal.relative(root).wh(1F, 1F);
        root.add(modal);
        modal.resize();

        return true;
    }

    /** Mouse over the film's keyframe editor: G/S act on keys there and UIPropTransform leaves them (Blender: the editor under the mouse) */
    public static boolean ueberKeys(UIContext context)
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
        UIKeyframes view = panel == null || panel.replayEditor.keyframeEditor == null ? null : panel.replayEditor.keyframeEditor.view;

        return view != null && context != null && view.canBeSeen() && view.area.isInside(context);
    }

    public static boolean laeuft()
    {
        return !BBSModClient.getDashboard().getRoot().getChildren(KeyframeGreifen.class).isEmpty();
    }

    private KeyframeGreifen(UIKeyframes view, boolean skalieren, boolean kopieren)
    {
        this.view = view;
        this.skalieren = skalieren;
        this.pivot = view.getTick();
        this.wert = skalieren ? 1F : 0F;

        for (UIKeyframeSheet sheet : view.getGraph().getSheets())
        {
            for (Keyframe key : sheet.selection.getSelected())
            {
                this.start.put(key, key.getTick());

                if (kopieren)
                {
                    this.kopien.merken(sheet.channel, key);
                }
            }
        }
    }

    /** G: offset in ticks · S: factor */
    private void anwenden(float wert, boolean runden)
    {
        this.wert = wert;

        for (Map.Entry<Keyframe, Float> e : this.start.entrySet())
        {
            Keyframe key = e.getKey();
            KeyframeChannel channel = (KeyframeChannel) key.getParent();
            float pivot = channel.getSourceTick(this.pivot);
            float tick = this.skalieren ? pivot + (e.getValue() - pivot) * wert : e.getValue() + wert;

            key.setTick(channel.constrainKeyframeTick(key, runden ? Math.round(tick) : tick), false);
        }

        this.view.triggerChange();
    }

    private void beenden(boolean behalten)
    {
        if (!behalten)
        {
            this.anwenden(this.skalieren ? 1F : 0F, false);
        }
        else if (this.wert != 0F)
        {
            this.kopien.lassen(this.view);
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

            return true;
        }

        KeyAction action = context.getKeyAction();

        if ((action == KeyAction.PRESSED || action == KeyAction.REPEAT) && this.zahl.feedKey(context.getKeyCode()) != TransformNumericInput.Result.IGNORED)
        {
            /* Typed amounts are exact; emptied, the mouse takes over again in render */
            this.anwenden(this.zahl.isActive() ? (float) this.zahl.value() : (this.skalieren ? 1F : 0F), false);
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

        if (!this.zahl.isActive())
        {
            float wert = this.mausWert(context.mouseX);

            if (wert != this.wert)
            {
                this.anwenden(wert, this.skalieren && !Window.isShiftPressed()); /* G rounds the offset in mausWert */
            }
        }

        String betrag = this.zahl.isActive() ? this.zahl.display() + "_"
            : this.skalieren ? String.format(Locale.ROOT, "x%.2f", this.wert)
            : (this.wert >= 0 ? "+" : "") + (this.wert == Math.round(this.wert) ? String.valueOf(Math.round(this.wert)) : String.format(Locale.ROOT, "%.2f", this.wert));
        String text = this.skalieren ? "Scale keys " + betrag + " around the playhead" : (this.kopien.leer() ? "Move keys " : "Move copies ") + betrag + " ticks";

        context.batcher.textCard(text + "   LMB/Enter keep · RMB/Esc cancel", context.mouseX + 12, context.mouseY + 8);

        super.render(context);
    }

    /** G: whole ticks moved since the start (Shift: free) · S: distance to the playhead now / at the start */
    private float mausWert(int mouseX)
    {
        double jetzt = this.view.fromGraphX(mouseX);
        double anfang = this.view.fromGraphX(this.startX);

        if (!this.skalieren)
        {
            return Window.isShiftPressed() ? (float) (jetzt - anfang) : Math.round(jetzt - anfang);
        }

        double abstand = anfang - this.pivot;

        /* Started on the playhead: one tick of travel is factor 1, not a division by almost zero */
        return (float) ((jetzt - this.pivot) / (Math.abs(abstand) < 1D ? Math.copySign(1D, abstand) : abstand));
    }
}
