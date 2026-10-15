package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.Pair;

import java.util.HashMap;
import java.util.Map;

public class StencilMap
{
    public int objectIndex;
    public Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();
    public boolean increment = true;

    /** Wenn gesetzt: nur diese knochen landen im stencil-puffer, alles andere wird uebersprungen.
     *
     *  AUTONOMIE 2026-07-27: aus dem CML-fork — der bone-hover-picker (campreview) faerbt nur
     *  die markierten knochen ein, sonst trifft der klick immer den ganzen koerper. null = alle. */
    public java.util.Set<String> allowedBones;

    public void setIncrement(boolean increment)
    {
        this.increment = increment;
    }

    public void setup()
    {
        /* Pickable form parts start right after every gizmo stencil id so they
         * never share an index with a gizmo handle (the sphere and view ring
         * included), which would otherwise hijack the click. STENCIL_MAX is the
         * highest handle id, so forms begin one past it. */
        this.objectIndex = Gizmo.STENCIL_MAX + 1;
        this.widgetBasis = 0;

        /* Reset map and seed a pair per gizmo handle (move/scale/rotate axes,
         * planes, the trackball sphere and the shared view-plane ring). */
        this.indexMap.clear();

        for (Gizmo.Handle handle : Gizmo.Handle.values())
        {
            this.indexMap.put(handle.index, new Pair<>(null, handle.name().toLowerCase()));
        }
    }

    /**
     * Der Grundindex der Form, die gerade eingetragen wird — der erste Summand der Kennung.
     *
     * <p>⛔ WOFUER (2026-08-02 gemessen). {@code picker_models.fsh:43} rechnet
     * {@code totalIndex = Modus.y + LichtOverlay.x}: die Kennung ist eine SUMME aus dem
     * Grundindex der FORM und dem Index des KNOCHENS. Den Knochenteil backt
     * {@code BOBJModelVAO.updateMesh} ins Licht-Attribut; der Formteil reist als Parameter
     * {@code kennung} in den Modell-Block — und wurde in {@code ModelInstance} fest als 0
     * uebergeben. Ergebnis: jede Form teilte sich denselben Zahlenraum, der Auswahlpuffer
     * trug Alpha (100928 Punkte) aber RGB = 0, und {@code pick()} dekodierte immer 0.
     * Daran hingen zugleich der fehlende orange Umriss und das tote Alt+Klick.
     *
     * <p>Festgehalten wird er dort, wo er entsteht: beim ERSTEN Eintrag einer Form steht
     * {@link #objectIndex} noch auf ihrem Anfang. Nach dem Fuellen zeigt er auf den letzten
     * vergebenen Wert — wer ihn erst beim Zeichnen abgreift, bekommt die falsche Zahl.
     */
    public int aktuelleBasis;

    /** Erste Widget-Kennung (Rig-Controls, Marker) — der Umriss endet davor. 0 = keine Widgets. */
    public int widgetBasis;

    /** Beim ERSTEN Widget-Eintrag rufen — merkt sich die Grenze Knochen/Widgets. */
    public void widgetBeginn()
    {
        if (this.widgetBasis == 0)
        {
            this.widgetBasis = this.objectIndex;
        }
    }

    public void addPicking(Form form)
    {
        this.addPicking(form, "");
    }

    public void addPicking(Form form, String bone)
    {
        if (this.increment)
        {
            this.indexMap.put(this.objectIndex, new Pair<>(form, bone));

            this.objectIndex += 1;
        }
        else
        {
            this.indexMap.put(this.objectIndex, new Pair<>(form, ""));
        }
    }
}