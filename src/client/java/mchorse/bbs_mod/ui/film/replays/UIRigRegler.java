package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.cubic.ik.RigMechanik;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Map;

/**
 * Das Bedienfeld eines Control-Rigs — Rigifys N-Panel.
 *
 * <p>Zeigt die Regler des ausgewaehlten Rigs: IK_FK, IK_Stretch, IK_parent,
 * pole_vector, pole_parent, FK_limb_follow. Jeder Regler steht an einem
 * Eigenschaften-Knochen je Glied ({@code upper_arm_parent.L}); in Blender sitzt
 * dort das Zahnrad-Widget, das der Animator anklickt.
 *
 * <p>⛔ Es erscheinen NUR Regler, an denen wirklich ein Treiber haengt — die
 * Vorauswahl trifft schon {@code rig-extrakt.py}. Ein Schieber, der nichts bewegt,
 * ist eine Luege im Bedienfeld, und die faellt erst auf, wenn jemand eine Stunde
 * lang sucht, warum sein Arm nicht umschaltet.
 */
public class UIRigRegler extends UIElement
{
    private String gebautFuer = "";

    public UIRigRegler()
    {
        this.column().vertical().stretch();
    }

    /** Die Mechanik des Modells, an dem diese Entity haengt — oder null. */
    public static RigMechanik mechanikVon(IEntity entity)
    {
        if (entity == null
            || !(entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            || !(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form)
                instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer))
        {
            return null;
        }

        mchorse.bbs_mod.cubic.ModelInstance instance = renderer.getModel();

        if (instance == null
            || !(instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj))
        {
            return null;
        }

        RigMechanik.installieren(bobj, form.model.get());

        return bobj.getArmature().zwang instanceof RigMechanik m ? m : null;
    }

    /**
     * Baut das Feld neu, wenn ein anderes Rig ausgewaehlt ist. Je Frame billig:
     * bei gleicher Kennung passiert nichts.
     */
    public void aktualisiere(IEntity entity)
    {
        RigMechanik mechanik = mechanikVon(entity);
        String kennung = mechanik == null ? "" : String.valueOf(mechanik.hashCode());

        if (kennung.equals(this.gebautFuer))
        {
            return;
        }

        this.gebautFuer = kennung;
        this.removeAll();

        if (mechanik == null || mechanik.treiber.regler.leer())
        {
            this.resize();

            return;
        }

        for (String bone : mechanik.treiber.regler.knochen())
        {
            this.add(UI.label(IKey.raw(bone))
                .background(Colors.A50 | Colors.GRAY));

            for (Map.Entry<String, MapType> e : mechanik.treiber.regler.reglerVon(bone).entrySet())
            {
                this.add(this.schieber(mechanik, bone, e.getKey(), e.getValue()));
            }
        }

        this.resize();
    }

    private UIElement schieber(RigMechanik mechanik, String bone, String prop, MapType d)
    {
        UITrackpad pad = new UITrackpad((v) ->
            mechanik.treiber.regler.setze(bone, prop, v.floatValue()));

        pad.setValue(mechanik.treiber.regler.wertVon(bone, prop, d.getFloat("wert", 0F)));

        /* Blenders Grenzen mitnehmen: ohne sie liesse sich IK_FK auf 7 stellen und
         * die Kette verschwaende in einer Lage, die es in Blender nicht gibt.
         * Die int-Regler (IK_parent, pole_parent) tragen Blenders Default-Grenzen
         * ±2^31 — die sind keine echte Grenze, also nur die Rasterung uebernehmen. */
        boolean ganz = "int".equals(d.getString("art")) || "bool".equals(d.getString("art"));

        if (ganz)
        {
            pad.integer();
        }

        if (d.has("min") && d.has("max") && Math.abs(d.getFloat("max")) < 1.0E6F)
        {
            pad.limit(d.getFloat("min"), d.getFloat("max"));
        }
        else if ("bool".equals(d.getString("art")))
        {
            pad.limit(0D, 1D);
        }

        String text = d.getString("text");

        pad.tooltip(IKey.raw(text == null || text.isEmpty() ? prop : text));

        UIElement zeile = UI.label(IKey.raw(prop)).marginTop(4).h(12);

        zeile.add(pad.w(1F));

        return zeile;
    }
}
