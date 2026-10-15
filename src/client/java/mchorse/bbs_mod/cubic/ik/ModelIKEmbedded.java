package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.bobj.BOBJIKChain;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bringt die in der Modelldatei mitgereisten IK-Ketten ({@code arm_ik}) als Vorgabe in
 * {@code form.ik} — die eine Stelle, aus der alles Nachgelagerte (Renderer, Film-Spuren,
 * Panel, Gizmo) ohnehin liest. Handarbeit im Panel schreibt {@code form.ik} und gewinnt;
 * Panel leeren ({@code ik = null}) holt beim naechsten Bind wieder das importierte Rig.
 */
public final class ModelIKEmbedded
{
    /** Importiertes Rig je Instanz — Registry statt Feld, damit ModelInstance (gelisteter
     *  Monolith) unberuehrt bleibt. Weak: Eintrag stirbt mit der Instanz. NICHT final und
     *  nur ueber {@link #registry()}: ein Hotswap laesst neue statics null (belegter
     *  Absturz 2026-08-12, crash-2026-08-12_14.59.14 — NPE je Frame im Render-Thread). */
    private static java.util.WeakHashMap<ModelInstance, MapType> REGISTRY;

    private ModelIKEmbedded()
    {}

    private static java.util.Map<ModelInstance, MapType> registry()
    {
        if (REGISTRY == null)
        {
            REGISTRY = new java.util.WeakHashMap<>();
        }

        return REGISTRY;
    }

    /** Vom Modell-Loader gerufen; ohne Ketten kein Eintrag. */
    public static void register(ModelInstance model, List<BOBJIKChain> chains)
    {
        MapType map = toMap(chains);

        if (model != null && map != null)
        {
            registry().put(model, map);
        }
    }

    /** Baut aus den arm_ik-Ketten die form.ik-Datenform; {@code null} ohne Ketten. */
    public static MapType toMap(List<BOBJIKChain> chains)
    {
        if (chains == null || chains.isEmpty())
        {
            return null;
        }

        List<ModelIKConfig.Chain> list = new ArrayList<>(chains.size());

        for (BOBJIKChain chain : chains)
        {
            list.add(new ModelIKConfig.Chain(
                chain.tip(), chain.target(), chain.chainLength(),
                !chain.poleTarget().isEmpty(), chain.poleTarget(), chain.poleAngle(),
                ModelIKConfig.DEFAULT_SOFTNESS, chain.weight(),
                true, chain.tipRotation(), chain.stretch()));
        }

        MapType map = ModelIKIO.toData(new ModelIKConfig(list, Map.of()));

        return map.isEmpty() ? null : map;
    }

    /** Nach einem Hotswap ist das Setting-Feld null — dann bleibt die Uebernahme aus. */
    public static void uebernehmen(ModelForm form, ModelInstance model)
    {
        MapType embedded = model == null ? null : registry().get(model);

        if (form == null || embedded == null)
        {
            return;
        }

        if (BBSSettings.modelEmbeddedIk == null || !BBSSettings.modelEmbeddedIk.get())
        {
            return;
        }

        if (form.ik.get() != null)
        {
            return;
        }

        /* SILENT: Renderer-Befuellung ist kein User-Edit — kein Undo-Eintrag, kein
         * collectAllUndoData je Bind (Crash-Kette 2026-08-13 08:20). */
        form.ik.set(embedded.copy(), mchorse.bbs_mod.settings.values.IValueListener.FLAG_SILENT);
    }
}
