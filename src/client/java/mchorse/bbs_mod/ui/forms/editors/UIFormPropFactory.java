package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Widgets aus Form-Values erzeugen (P8c) — das Form-Gegenstueck zur Settings-
 * {@link mchorse.bbs_mod.settings.ui.UIValueFactory}: Widget-Wahl, set()-Bindung
 * und Metadaten (Range, Drag-Feinheit) kommen aus der Value-DEFINITION, das Panel
 * deklariert nur noch Layout. Blender-Muster: layout.prop(data, "name").
 *
 * <p>⚠ FALLE — Form-Panels werden fuer wechselnde Forms WIEDERVERWENDET, darum
 * binden alle Widgets ueber {@link Supplier} an "die Value der aktuellen Form",
 * nie an eine Instanz. {@link #sync()} zieht bei startEdit die Werte der neuen
 * Form in alle gebauten Widgets — der handgeschriebene setValue-Block je Panel
 * entfaellt.</p>
 *
 * <p>Labels bleiben Parameter (UIKeys): die Konventions-Sprachkeys der Settings-
 * Factory brauchen einen stabilen Pfad-Praefix, den Form-Values nicht haben.</p>
 */
public class UIFormPropFactory
{
    private final List<Runnable> syncs = new ArrayList<>();

    /** Bei startEdit aufrufen: alle Widgets zeigen die Werte der aktuellen Form. */
    public void sync()
    {
        for (Runnable sync : this.syncs)
        {
            sync.run();
        }
    }

    public UITextbox text(Supplier<ValueString> quelle)
    {
        UITextbox textbox = new UITextbox(10000, (t) -> quelle.get().set(t));
        Runnable sync = () -> textbox.setText(quelle.get().get());

        this.syncs.add(sync);

        return mchorse.bbs_mod.ui.utils.values.UIValues.resettable(textbox, quelle, sync);
    }

    public UIToggle toggle(IKey label, Supplier<ValueBoolean> quelle)
    {
        UIToggle toggle = new UIToggle(label, (b) -> quelle.get().set(b.getValue()));

        Runnable sync = () -> toggle.setValue(quelle.get().get());

        this.syncs.add(sync);

        return mchorse.bbs_mod.ui.utils.values.UIValues.resettable(toggle, quelle, sync);
    }

    public UIColor color(Supplier<ValueColor> quelle)
    {
        UIColor color = new UIColor((c) -> quelle.get().set(Color.rgba(c))).withAlpha();

        Runnable sync = () -> color.setColor(quelle.get().get().getARGBColor());

        this.syncs.add(sync);

        return mchorse.bbs_mod.ui.utils.values.UIValues.resettable(color, quelle, sync);
    }

    /** Zahlenfeld; Range/Drag-Feinheit/Increment liefert die Value-Definition. */
    public UITrackpad zahl(Supplier<? extends BaseValueNumber<? extends Number>> quelle)
    {
        UITrackpad trackpad = new UITrackpad((v) -> setzeZahl(quelle.get(), v));

        Runnable sync = () ->
        {
            BaseValueNumber<? extends Number> wert = quelle.get();

            metaAnwenden(trackpad, wert);
            trackpad.setValue(wert.get().doubleValue());
        };

        this.syncs.add(sync);

        return mchorse.bbs_mod.ui.utils.values.UIValues.resettable(trackpad, quelle, sync);
    }

    /** Schieberegler fuer Values, deren Definition slider() traegt (Range Pflicht). */
    public mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad schieber(Supplier<? extends BaseValueNumber<? extends Number>> quelle)
    {
        mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad regler =
            new mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad((v) -> setzeZahl(quelle.get(), v));

        Runnable sync = () ->
        {
            BaseValueNumber<? extends Number> wert = quelle.get();

            metaAnwenden(regler, wert);
            regler.setValue(wert.get().doubleValue());
        };

        this.syncs.add(sync);

        return mchorse.bbs_mod.ui.utils.values.UIValues.resettable(regler, quelle, sync);
    }

    private static void setzeZahl(BaseValueNumber<? extends Number> wert, double v)
    {
        if (wert instanceof ValueInt vi) vi.set((int) v);
        else if (wert instanceof ValueFloat vf) vf.set((float) v);
        else if (wert instanceof ValueDouble vd) vd.set(v);
        else throw new IllegalStateException("kein zahl()-weg fuer " + wert.getClass());
    }

    /** Idempotent — laeuft bei jedem sync, weil das Widget vor der ersten Form gebaut wird. */
    private static void metaAnwenden(mchorse.bbs_mod.ui.framework.elements.input.UINumericInput<?> trackpad, BaseValueNumber<? extends Number> wert)
    {
        if (wert.getMin() != null && wert.getMax() != null)
        {
            trackpad.limit(wert.getMin().doubleValue(), wert.getMax().doubleValue(), wert instanceof ValueInt);
        }
        else if (wert instanceof ValueInt)
        {
            trackpad.integer();
        }

        float[] drag = wert.getDragWerte();

        if (drag != null)
        {
            trackpad.values(drag[0], drag[1], drag[2]);
        }

        if (wert.getDragIncrement() != null)
        {
            trackpad.increment(wert.getDragIncrement());
        }
    }
}
