package mchorse.bbs_mod.compat.alt;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Model.root() von 1.21.2+ vor 1.21.2: nur hierarchische Modelle haben dort eine Wurzel.
 * Listenmodelle (HumanoidModel u. a.) liefern null — wer Teile darueber sucht, findet bei ihnen
 * vor 1.21.2 nichts (Crowd-LOD blendet dort keine Teile aus).
 */
public final class ModellWurzel
{
    private ModellWurzel()
    {}

    public static ModelPart von(Model modell)
    {
        return modell instanceof HierarchicalModel<?> h ? h.root() : null;
    }
}
